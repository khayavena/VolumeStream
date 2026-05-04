# iOS / HLS — End-to-End Playback Integration Guide

> **Stack**: Kotlin Multiplatform (KMP) · KMP data layer · Compose Multiplatform UI · AVFoundation / AVQueuePlayer  
> **Server**: StreamVault Spring Boot · AES-128 HLS · RSA cert-pin session auth  
> **Last updated**: May 2026

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Request Flow Diagram](#2-request-flow-diagram)
3. [Step 1 — Device Registration](#3-step-1--device-registration)
4. [Step 2 — User Authentication (JWT)](#4-step-2--user-authentication-jwt)
5. [Step 3 — Start Playback Session](#5-step-3--start-playback-session)
6. [Step 4 — Fetch HLS Master Manifest](#6-step-4--fetch-hls-master-manifest)
7. [Step 5 — Variant Playlist & Segment Delivery](#7-step-5--variant-playlist--segment-delivery)
8. [Step 6 — AES-128 Key Delivery](#8-step-6--aes-128-key-delivery)
9. [Step 7 — AVPlayer Playback](#9-step-7--avplayer-playback)
10. [Step 8 — Session Teardown](#10-step-8--session-teardown)
11. [Security Layer: RSA Cert-Pin Signing](#11-security-layer-rsa-cert-pin-signing)
12. [Token Storage](#12-token-storage)
13. [KMP Class Map](#13-kmp-class-map)
14. [Error Handling & Edge Cases](#14-error-handling--edge-cases)
15. [App Store Compliance Checklist](#15-app-store-compliance-checklist)
16. [Configuration Reference](#16-configuration-reference)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     iOS App (KMP + SwiftUI)                     │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │               Compose Multiplatform UI                   │   │
│  │  HomeScreen ──► MediaItemWidget ──► PlaybackViewModel    │   │
│  └───────────────────────┬──────────────────────────────────┘   │
│                          │                                      │
│  ┌───────────────────────▼──────────────────────────────────┐   │
│  │              KMP shared/commonMain                       │   │
│  │  PlaybackViewModel  ◄──►  SessionRepository              │   │
│  │        │                  PlaybackMediaItemRepository    │   │
│  │        │                  AuthRepository                 │   │
│  └────────┼─────────────────────────────────────────────────┘   │
│           │                                                      │
│  ┌────────▼───────────────────┐  ┌──────────────────────────┐   │
│  │  PlaybackStateController   │  │  DeviceCrypto (iOS)       │   │
│  │  (iosMain / actual)        │  │  RSA-2048 in Keychain     │   │
│  │  AVQueuePlayer             │  │  SPKI-wrapped export      │   │
│  │  prefetchForPlayback()     │  │  SecKeyCreateSignature    │   │
│  └────────────────────────────┘  └──────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                          │
              ┌───────────▼────────────┐
              │    StreamVault Server  │
              │  ┌──────────────────┐  │
              │  │  Auth Service    │  │  :8080
              │  │  /api/auth/login │  │
              │  └──────────────────┘  │
              │  ┌──────────────────┐  │
              │  │  API Service     │  │  :8081
              │  │  /api/v1/...     │  │
              │  └──────────────────┘  │
              └────────────────────────┘
```

---

## 2. Request Flow Diagram

```
iOS App                             StreamVault Server
   │                                       │
   │── (App launch, once per device) ──────│
   │  POST /api/v1/device/register         │
   │  Authorization: Bearer <jwt>          │
   │  Body: { deviceId, publicKey (SPKI) } │──► CertPinService stores key
   │◄─ 201 Created / 409 (already exists) ─│
   │                                       │
   │── (per playback) ─────────────────────│
   │  POST /api/auth/login                 │
   │  Body: { email, password }            │──► validates creds
   │◄─ 200 { jwt }  ───────────────────────│
   │                                       │
   │  POST /api/v1/session/start           │
   │  Authorization: Bearer <jwt>          │
   │  X-Device-Id: <deviceId>              │
   │  X-Cert-Timestamp: <serverEpochMs>    │──► verifies RSA sig
   │  X-Cert-Signature: <RSA-SHA256-B64>   │    starts session
   │◄─ 200 { sessionId, sessionToken } ────│
   │                                       │
   │  GET /api/v1/manifest/hls/<mediaId>   │
   │  X-Session-Token: <sessionToken>      │──► builds master playlist
   │◄─ 200 #EXTM3U master.m3u8  ───────────│    with signed variant URLs
   │       (file:// after prefetch)        │
   │                                       │
   │ ┌── AVPlayer starts ─────────────────┐│
   │ │ GET /api/v1/manifest/hls/<id>/720p │││
   │ │     ?sid=<sid>&t=<token>           │││──► verifies stream token
   │ │◄─ 200 variant playlist (m3u8)──────│││    returns segment URLs
   │ │                                    │││
   │ │ GET /api/v1/proxy/hls/<id>/seg0.ts │││
   │ │     ?t=<token>                     │││──► serves encrypted segment
   │ │◄─ 200 AES-128 encrypted .ts ───────│││
   │ │                                    │││
   │ │ GET /api/v1/manifest/<id>/key      │││
   │ │     ?sid=<sid>&t=<token>           │││──► derives 16-byte AES key
   │ │◄─ 200 <16 raw bytes> ──────────────│││    AVFoundation decrypts
   │ └────────────────────────────────────┘││
   │                                       │
   │── (on screen exit / app background) ──│
   │  DELETE /api/v1/session/<sessionId>   │──► revokes session
   │  Authorization: Bearer <jwt>          │
   │◄─ 204 No Content ─────────────────────│
   │                                        │
```

---

## 3. Step 1 — Device Registration

### When to call
Once per device install. The `SessionRepository.ensureDeviceRegistered()` API is idempotent — the server returns `409 Conflict` if the device is already registered, which is treated as success.

### What it does
1. Reads (or generates) a stable `deviceId` from `NSUserDefaults`
2. Generates an **RSA-2048** key pair in the iOS Keychain (`kSecAttrIsPermanent = true`)
3. Exports the public key in **SPKI / X.509 DER** format and Base64-encodes it
4. POSTs `{ deviceId, publicKey }` to `/api/v1/device/register`

### Key detail — SPKI wrapping (iOS-specific)
`SecKeyCopyExternalRepresentation` returns raw **PKCS#1** bytes. The server calls
`KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))` which requires
**SubjectPublicKeyInfo (SPKI)** format. `DeviceCrypto.ios.kt` wraps the raw bytes:

```kotlin
// DeviceCrypto.ios.kt — wrapInSpki()
private fun ByteArray.wrapInSpki(): ByteArray {
    val rsaOid = byteArrayOf(
        0x30, 0x0d, 0x06, 0x09,
        0x2a.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(),
        0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00
    )
    val bitStringPayload = byteArrayOf(0x00) + this
    val bitString = byteArrayOf(0x03) + derLength(bitStringPayload.size) + bitStringPayload
    val sequenceContent = rsaOid + bitString
    return byteArrayOf(0x30) + derLength(sequenceContent.size) + sequenceContent
}
```

### HTTP request
```
POST http://<host>:8081/api/v1/device/register
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
}
```

### Expected responses
| Status | Meaning |
|--------|---------|
| `201 Created` | First-time registration |
| `200 OK` | Accepted (some server versions) |
| `409 Conflict` | Already registered — treat as success |
| `401 Unauthorized` | JWT expired — refresh and retry |

---

## 4. Step 2 — User Authentication (JWT)

### When to call
On login screen submit, and automatically when `AuthRepository.ensureValidJwt()` detects expiry.

### HTTP request
```
POST http://<host>:8080/api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "s3cret"
}
```

### Response
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Storage
JWT is stored in the **iOS Keychain** (`kSecClassGenericPassword`, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, service = `"com.vdigital.volumestream"`, key = `"vs_jwt"`). It **never** touches `NSUserDefaults` or plist files.

---

## 5. Step 3 — Start Playback Session

### When to call
Every time the user taps a video — called in `PlaybackViewModel.configurePlaybackSession()` on `Dispatchers.IO` before the player initialises.

### Cert-pin signing
The app proves device identity by signing `"userId|videoId|certTimestamp"` with the hardware-backed RSA private key:

```kotlin
// SessionDataSourceImpl.startSession()
val certTimestamp = fetchServerEpochMillis()         // uses server clock, not device clock
val payload       = "$userId|$videoId|$certTimestamp"
val certSignature = deviceCrypto.signPayload(payload) // SHA256withRSA, Keychain private key
```

> **Clock skew**: the timestamp is fetched from `GET /api/v1/server/time` to guard against emulator/device clock drift. The server rejects signatures older than its tolerance window (typically ±5 min).

### HTTP request
```
POST http://<host>:8081/api/v1/session/start
Authorization: Bearer <jwt>
X-Device-Id:        550e8400-e29b-41d4-a716-446655440000
X-Cert-Timestamp:   1746307200000
X-Cert-Signature:   Base64(RSA-SHA256("userId|videoId|1746307200000"))
Content-Type:       application/json

{
  "videoId": "91994edf-7045-3377-b9d1-6391acb958f6"
}
```

### Response
```json
{
  "sessionId":    "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "sessionToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### ViewModel wires session into the player
```kotlin
// PlaybackViewModel.configurePlaybackSession()
playbackStateController.setAuthHeaders(
    mapOf(
        "Authorization"    to "Bearer $jwt",
        "X-Session-Token"  to sessionToken
    )
)
```

---

## 6. Step 4 — Fetch HLS Master Manifest

### URL built at startup
`MediaFeedResponse.kt` maps each API media item to a `PlaybackMediaItem`:

```kotlin
hlsStreamUrl = "http://$apiHost:${config.apiPort}/${config.apiBasePath}/manifest/hls/$id"
// e.g.: http://192.168.1.100:8081/api/v1/manifest/hls/91994edf-7045-3377-b9d1-6391acb958f6
```

### `prefetchForPlayback` — App Store-safe manifest download
`PlaybackViewModel` calls `prefetchForPlayback` on `Dispatchers.Main` before handing the item to AVPlayer. The iOS actual:

1. Uses `NSURLSession.sharedSession.dataTaskWithRequest` (public API) to download the manifest with `X-Session-Token` header
2. Writes the response to `NSTemporaryDirectory()/<mediaId>.m3u8`
3. Returns a copy of the item with `hlsStreamUrl = "file:///tmp/<mediaId>.m3u8"`

AVPlayer gets a **local file:// URL** — no custom HTTP headers needed at the AVPlayer layer, eliminating the undocumented `AVURLAssetHTTPHeaderFieldsKey`.

```kotlin
// PlaybackViewModel.initialise()
val playItem = playbackStateController.prefetchForPlayback(item.forPlatformPlayback())
handleInitialPlayback(mutableListOf(playItem))
```

### HTTP request (inside prefetchForPlayback)
```
GET http://<host>:8081/api/v1/manifest/hls/91994edf-7045-3377-b9d1-6391acb958f6
X-Session-Token: eyJhbGciOiJIUzI1NiJ9...
```

### Master playlist response
```m3u8
#EXTM3U
#EXT-X-VERSION:3

#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
http://192.168.1.100:8081/api/v1/manifest/hls/91994edf.../360p?sid=3fa85f64...&t=eyJ...

#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
http://192.168.1.100:8081/api/v1/manifest/hls/91994edf.../720p?sid=3fa85f64...&t=eyJ...

#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
http://192.168.1.100:8081/api/v1/manifest/hls/91994edf.../1080p?sid=3fa85f64...&t=eyJ...
```

> All variant playlist URLs and key URIs embed `?sid=<sessionId>&t=<streamToken>`.  
> AVPlayer fetches everything subsequent **without any auth headers** — the stream token in the URL provides all necessary auth.

---

## 7. Step 5 — Variant Playlist & Segment Delivery

AVPlayer automatically selects the best variant based on available bandwidth and device capabilities. No app code is involved:

```
GET http://<host>:8081/api/v1/manifest/hls/91994edf.../720p
    ?sid=3fa85f64-5717-4562-b3fc-2c963f66afa6
    &t=eyJhbGciOiJIUzI1NiJ9...
```

### Variant playlist response
```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:6
#EXT-X-KEY:METHOD=AES-128,
  URI="http://192.168.1.100:8081/api/v1/manifest/91994edf.../key?sid=3fa85f64...&t=eyJ...",
  IV=0x00000000000000000000000000000000

#EXTINF:6.0,
http://192.168.1.100:8081/api/v1/proxy/hls/91994edf.../720p/seg000.ts?t=eyJ...
#EXTINF:6.0,
http://192.168.1.100:8081/api/v1/proxy/hls/91994edf.../720p/seg001.ts?t=eyJ...
...
#EXT-X-ENDLIST
```

---

## 8. Step 6 — AES-128 Key Delivery

When AVPlayer encounters `EXT-X-KEY`, it fetches the key URI automatically:

```
GET http://<host>:8081/api/v1/manifest/91994edf.../key
    ?sid=3fa85f64-5717-4562-b3fc-2c963f66afa6
    &t=eyJhbGciOiJIUzI1NiJ9...
```

- This endpoint is `permitAll()` — no `Authorization` header needed
- Returns exactly **16 raw bytes** (AES-128 key)
- The key is deterministically derived from `(userId, mediaId, segmentIndex, nonce)` using HMAC — never stored, always recomputable
- AVFoundation transparently decrypts each `.ts` segment using this key

> **iOS vs Android difference**: Android explicitly calls `fetchAesKey()` and injects the raw bytes into `AesGcmDecryptingDataSource`. iOS skips this entirely — AVFoundation handles `EXT-X-KEY` natively.

---

## 9. Step 7 — AVPlayer Playback

### `PlaybackStateController` (iosMain actual)

```kotlin
// addItem — called by addItemItems after prefetchForPlayback
actual fun addItem(mediaItem: PlaybackMediaItem) {
    // hlsStreamUrl = "file:///tmp/<id>.m3u8" after prefetchForPlayback
    // Falls back to http:// hlsStreamUrl if prefetch returned item unchanged
    val url = nsUrlFor(mediaItem.hlsStreamUrl.ifBlank { mediaItem.streamUrl }) ?: return
    avPlayer.insertItem(AVPlayerItem(url), afterItem = null)
}
```

### Player integration in SwiftUI

```swift
// ContentView.swift — bridges KMP Compose to SwiftUI
struct ContentView: View {
    var body: some View {
        ComposeView()           // KMP MainViewController()
            .ignoresSafeArea()
            .preferredColorScheme(.dark)
    }
}
```

The KMP Compose layer hosts a `UIKitView` that wraps an `AVPlayerLayer`:

```kotlin
// UIView holding AVPlayerLayer wired to avPlayer
UIKitView(
    factory = {
        val layer = AVPlayerLayer.playerLayerWithPlayer(controller.avPlayer)
        layer.videoGravity = AVLayerVideoGravityResizeAspect
        AVPlayerUIView(layer)       // custom UIView subclass
    },
    update = { /* no-op: AVPlayer references are stable */ }
)
```

### Progress timer (500 ms ticks)
```kotlin
actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
    val timer = NSTimer.timerWithTimeInterval(0.5, repeats = true) {
        callback(currentPosition(), duration())
        // updates PlaybackViewModel._progressState and ._durationMs
    }
    NSRunLoop.mainRunLoop.addTimer(timer, forMode = NSRunLoopCommonModes)
}
```

### Orientation lock during playback
```swift
// iOSApp.swift — landscape lock via KMP-exposed OrientationManager
NotificationCenter.default.publisher(for: Notification.Name("LOCK_CHANGED_NOTIFICATION"))
    .sink { _ in
        guard let windowScene = UIApplication.shared.connectedScenes
              .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
        else { return }
        // iOS 16+
        windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .landscape))
    }
```

---

## 10. Step 8 — Session Teardown

Sessions are revoked when:
- User leaves the playback screen (`ViewModel.onCleared()`)
- User selects a different track (`selectTrack()` ends the previous session first)
- Server sends a `401` mid-playback (caught by Ktor interceptor → `SessionRevokedBus`)

```kotlin
// PlaybackViewModel.onCleared()
cleanupScope.launch {   // cleanupScope outlives viewModelScope
    runCatching {
        withTimeout(5_000L) {
            sessionRepository.endSession(jwt, activeSessionId)
        }
    }
}
```

### HTTP request
```
DELETE http://<host>:8081/api/v1/session/3fa85f64-5717-4562-b3fc-2c963f66afa6
Authorization: Bearer <jwt>
```

---

## 11. Security Layer: RSA Cert-Pin Signing

### Key generation (iOS Keychain)
```kotlin
// DeviceCrypto.ios.kt
val privateKeyAttrs = NSMutableDictionary().apply {
    setObject(NSNumber.numberWithBool(true), forKey = kSecAttrIsPermanent)
    setObject(tagData,                       forKey = kSecAttrApplicationTag)
    setObject(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
              forKey = kSecAttrAccessible)   // matches Android's default — accessible
}                                            // after first unlock, no cloud backup

val genAttrs = NSMutableDictionary().apply {
    setObject(kSecAttrKeyTypeRSA,             forKey = kSecAttrKeyType)
    setObject(NSNumber.numberWithInt(2048),   forKey = kSecAttrKeySizeInBits)
    setObject(privateKeyAttrs,                forKey = kSecPrivateKeyAttrs)
}
val privateKey = SecKeyCreateRandomKey(genAttrs, errorPtr)
```

### Signing flow
```
payload      = "userId|videoId|serverEpochMs"
signature    = RSA-SHA256(payload, Keychain private key)
              → kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
header value = Base64(signature)
```

### Server verification
```java
// CertPinService.java
PublicKey  pubKey    = KeyFactory.getInstance("RSA")
                                 .generatePublic(new X509EncodedKeySpec(deviceKey));  // expects SPKI
Signature  verifier  = Signature.getInstance("SHA256withRSA");
verifier.initVerify(pubKey);
verifier.update((userId + "|" + videoId + "|" + certTimestamp).getBytes(UTF_8));
boolean ok = verifier.verify(Base64.getDecoder().decode(signature));
```

---

## 12. Token Storage

| Data | Storage | Accessibility | iCloud Backup |
|------|---------|---------------|---------------|
| JWT | iOS Keychain (`kSecClassGenericPassword`) | `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` | ❌ No |
| User email | iOS Keychain (`kSecClassGenericPassword`) | `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` | ❌ No |
| RSA private key | iOS Keychain (`kSecClassKey` + `kSecAttrIsPermanent`) | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` | ❌ No |
| Device ID | `NSUserDefaults` | Standard | ✅ Yes (intentional — survives reinstall so registration stays idempotent) |
| HLS manifest cache | `NSTemporaryDirectory()` | Process-level | ❌ No |

---

## 13. KMP Class Map

```
commonMain
├── PlaybackViewModel            – orchestrates the full flow above
├── PlaybackStateController      – expect class; platform audio callbacks
├── SessionRepository            – startSession / endSession / fetchAesKey
├── AuthRepository               – ensureValidJwt / login
├── PlaybackMediaItemRepository  – media feed
├── PlaybackMediaItem            – data model
│     ├── streamUrl              – DASH MPD  (Android / ExoPlayer)
│     └── hlsStreamUrl           – HLS m3u8  (iOS / AVPlayer)
└── StreamVaultConfig            – all ports / paths / header names

iosMain
├── PlaybackStateController      – actual; AVQueuePlayer, NSTimer
│     ├── prefetchForPlayback()  – NSURLSession manifest download → file://
│     └── addItem()              – AVPlayerItem from file:// or http:// URL
├── DeviceCrypto                 – actual; Keychain RSA + wrapInSpki()
├── TokenStore                   – actual; Keychain CRUD
└── RemoteApiClientFactory       – actual; Darwin Ktor engine, 401→SessionRevokedBus

androidMain
├── PlaybackStateController      – actual; Media3 / ExoPlayer
├── DeviceCrypto                 – actual; AndroidKeyStore (StrongBox → TEE fallback)
└── TokenStore                   – actual; EncryptedSharedPreferences
```

---

## 14. Error Handling & Edge Cases

### 401 mid-playback (session revoked)
```kotlin
// RemoteApiClientFactory.ios.kt — Ktor response interceptor
plugin(HttpSend) {
    intercept { request ->
        val call = execute(request)
        if (call.response.status == HttpStatusCode.Unauthorized) {
            SessionRevokedBus.emit()   // signals UI to show re-login screen
        }
        call
    }
}
```

### Clock skew (emulator/dev device)
`startSession` fetches server time first:
```kotlin
private suspend fun fetchServerEpochMillis(): Long = try {
    val response = httpClient.get(".../api/v1/server/time")
    Json.parseToJsonElement(response.body<String>()).jsonObject["epochMillis"]!!.jsonPrimitive.long
} catch (e: Exception) {
    currentEpochMillis()  // fallback — may fail cert-pin if skew > server tolerance
}
```

### Manifest not ready (FFmpeg still processing)
The server retries up to 3× with 2 s delay before returning 404. The app should surface "Content is still processing" if it receives 404 on the manifest endpoint.

### Temp manifest file cleanup
`NSTemporaryDirectory()` is managed by iOS. Files are cleaned on low storage and on reboot. The app writes `<mediaId>.m3u8`; if the same video is played again the file is simply overwritten.

### `AVPlayerItem` errors
```kotlin
// PlaybackStateController.ios.kt
avPlayer.error != null -> Error(avPlayer.error!!.code().toString())
```
Common error codes:
| Code | Meaning |
|------|---------|
| `-11800` | Unknown AVFoundation error (usually networking) |
| `-11819` | `AVErrorContentNotUpdated` — HLS variant playlist stale |
| `-11833` | Decryption failure — `EXT-X-KEY` fetch failed or wrong key bytes |
| `-1022` | App Transport Security blocked `http://` — add ATS exception for dev host |

---

## 15. App Store Compliance Checklist

- [x] **No private API** — `AVURLAssetHTTPHeaderFieldsKey` removed; manifest pre-fetched via `NSURLSession`
- [x] **No deprecated API** — `SecKeyGeneratePair` replaced with `SecKeyCreateRandomKey`
- [x] **Encryption export declaration** — add to `Info.plist`:
  ```xml
  <key>ITSAppUsesNonExemptEncryption</key>
  <false/>
  ```
  > HLS AES-128 is standard transport-layer encryption, **exempt** under US EAR §740.17(b)(1) when used solely for media streaming. Consult your legal counsel to confirm.
- [x] **Keychain data-protection** — all secrets use `ThisDeviceOnly` + not backed up to iCloud
- [x] **Background playback** — ensure `UIBackgroundModes: audio` is in `Info.plist` if background play is required
- [x] **ATS for production** — use HTTPS (`StreamVaultConfig(useHttps = true)`) in production; remove any `NSExceptionDomains` before submission
- [x] **No device fingerprinting** — `deviceId` is a UUID generated by the app (`NSUUID().UUIDString`), not derived from any hardware identifier

---

## 16. Configuration Reference

### `StreamVaultConfig` defaults
```kotlin
data class StreamVaultConfig(
    val apiPort:           Int     = 8081,
    val authPort:          Int     = 8080,
    val useHttps:          Boolean = false,
    val apiBasePath:       String  = "api/v1",
    val authBasePath:      String  = "api",
    val dashManifestPath:  String  = "manifest/dash",
    val dashProxyPath:     String  = "proxy/dash",
    val headerDeviceId:    String  = "X-Device-Id",
    val headerCertTimestamp: String = "X-Cert-Timestamp",
    val headerCertSignature: String = "X-Cert-Signature",
    val deviceKeyAlias:    String  = "streamvault_device_key",
)
```

### Override for production
```kotlin
startKoin {
    modules(
        module {
            single {
                StreamVaultConfig(
                    useHttps = true,
                    apiPort  = 443,
                    authPort = 443
                )
            }
        },
        appModule
    )
}
```

### HLS endpoint summary
| Endpoint | Auth method | Query params |
|----------|-------------|-------------|
| `POST /api/v1/device/register` | `Bearer <jwt>` | — |
| `POST /api/v1/session/start` | `Bearer <jwt>` + cert-pin headers | — |
| `GET /api/v1/manifest/hls/<id>` | `X-Session-Token` header | — |
| `GET /api/v1/manifest/hls/<id>/<quality>` | none | `?sid=&t=` |
| `GET /api/v1/manifest/<id>/key` | none (permitAll) | `?sid=&t=` |
| `GET /api/v1/proxy/hls/<id>/*` | none | `?t=` |
| `DELETE /api/v1/session/<id>` | `Bearer <jwt>` | — |

---

*Generated from live KMP source at `/Users/khayavena/Documents/software`*

