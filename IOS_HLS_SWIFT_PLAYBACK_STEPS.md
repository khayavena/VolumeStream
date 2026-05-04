# iOS HLS Playback — Step-by-Step Swift Code Snippets

> Mirrors the KMP layer exactly. Each step maps 1-to-1 to the Kotlin class shown.  
> **Server default**: `http://<host>:8081` (API) · `http://<host>:8080` (Auth)

---

## Table of Contents

1. [Info.plist Prerequisites](#infoplist-prerequisites)
2. [Step 1 — Stable Device ID](#step-1--stable-device-id)
3. [Step 2 — RSA-2048 Key Pair + SPKI Export](#step-2--rsa-2048-key-pair--spki-export)
4. [Step 3 — JWT Keychain Storage](#step-3--jwt-keychain-storage)
5. [Step 4 — Login → JWT](#step-4--login--jwt)
6. [Step 5 — Device Registration](#step-5--device-registration)
7. [Step 6 — Server Time (clock-skew guard)](#step-6--server-time-clock-skew-guard)
8. [Step 7 — Start Playback Session (cert-pin)](#step-7--start-playback-session-cert-pin)
9. [Step 8 — Fetch HLS Manifest → file://](#step-8--fetch-hls-manifest--file)
10. [Step 9 — AVPlayer Controller](#step-9--avplayer-controller)
11. [Step 10 — AES-128 Key Delivery (automatic)](#step-10--aes-128-key-delivery-automatic)
12. [Step 11 — Session Teardown](#step-11--session-teardown)
13. [Full Orchestrator — PlaybackCoordinator](#full-orchestrator--playbackcoordinator)
14. [SwiftUI View](#swiftui-view)
15. [Orientation Lock](#orientation-lock-during-playback)
16. [Step Map — KMP ↔ Swift](#step-map--kmp--swift)

---

## Info.plist Prerequisites

```xml
<!-- Allow http:// to your dev server (remove for production HTTPS) -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSExceptionDomains</key>
    <dict>
        <key>192.168.1.100</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>

<!-- Required for background audio playback -->
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>

<!-- AES-128 HLS streaming is exempt from US EAR encryption export rules -->
<key>ITSAppUsesNonExemptEncryption</key>
<false/>
```

---

## Step 1 — Stable Device ID

**KMP**: `TokenStore.ios.kt → getDeviceId()`

```swift
import Foundation

struct DeviceStore {

    private static let key = "vs_device_id"

    /// Returns the stable UUID for this install.
    /// Stored in UserDefaults so it survives reinstall via iCloud backup —
    /// keeps device registration idempotent (server 409 = already registered = OK).
    static func deviceId() -> String {
        if let id = UserDefaults.standard.string(forKey: key) { return id }
        let id = UUID().uuidString
        UserDefaults.standard.set(id, forKey: key)
        return id
    }
}
```

---

## Step 2 — RSA-2048 Key Pair + SPKI Export

**KMP**: `DeviceCrypto.ios.kt → getOrCreateKey() + getOrCreatePublicKeyB64() + wrapInSpki()`

```swift
import Security, Foundation

struct DeviceCrypto {

    private static let tag = "com.vdigital.vs.devkey".data(using: .utf8)!

    // MARK: - Load or generate private key

    static func privateKey() throws -> SecKey {
        let query: [CFString: Any] = [
            kSecClass:              kSecClassKey,
            kSecAttrKeyType:        kSecAttrKeyTypeRSA,
            kSecAttrApplicationTag: tag,
            kSecAttrKeyClass:       kSecAttrKeyClassPrivate,
            kSecReturnRef:          true
        ]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess {
            return item as! SecKey
        }
        // Generate and store permanently in Keychain
        let privateAttrs: [CFString: Any] = [
            kSecAttrIsPermanent:    true,
            kSecAttrApplicationTag: tag,
            // Accessible after first unlock, never backed up to iCloud.
            // Matches kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly in DeviceCrypto.ios.kt.
            kSecAttrAccessible:     kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let attrs: [CFString: Any] = [
            kSecAttrKeyType:        kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits:  2048,
            kSecPrivateKeyAttrs:    privateAttrs
        ]
        var err: Unmanaged<CFError>?
        guard let key = SecKeyCreateRandomKey(attrs as CFDictionary, &err) else {
            throw err!.takeRetainedValue() as Error
        }
        return key
    }

    // MARK: - Public key — SPKI Base64

    /// Returns the RSA public key in SubjectPublicKeyInfo (SPKI / X.509) DER, Base64-encoded.
    /// The server calls Java's X509EncodedKeySpec which requires SPKI — NOT bare PKCS#1.
    /// SecKeyCopyExternalRepresentation returns raw PKCS#1, so we wrap it here.
    static func publicKeyBase64() throws -> String {
        let priv = try privateKey()
        guard let pub = SecKeyCopyPublicKey(priv) else { throw CryptoError.noPublicKey }
        var err: Unmanaged<CFError>?
        guard let data = SecKeyCopyExternalRepresentation(pub, &err) as Data? else {
            throw err!.takeRetainedValue() as Error
        }
        return wrapSpki(pkcs1: data).base64EncodedString()
    }

    // MARK: - Sign payload

    /// SHA-256 with RSA PKCS#1 v1.5, Base64-encoded.
    /// Algorithm: kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
    static func sign(payload: String) throws -> String {
        let priv = try privateKey()
        guard let data = payload.data(using: .utf8) else { throw CryptoError.encoding }
        var err: Unmanaged<CFError>?
        guard let sig = SecKeyCreateSignature(
            priv,
            .rsaSignatureMessagePKCS1v15SHA256,
            data as CFData,
            &err
        ) as Data? else {
            throw err!.takeRetainedValue() as Error
        }
        return sig.base64EncodedString()
    }

    // MARK: - PKCS#1 → SPKI DER wrapping

    /// Wraps raw PKCS#1 bytes in a SubjectPublicKeyInfo (SPKI) DER envelope.
    /// Exact Swift mirror of wrapInSpki() + derLength() in DeviceCrypto.ios.kt.
    private static func wrapSpki(pkcs1: Data) -> Data {
        // RSA AlgorithmIdentifier OID: 1.2.840.113549.1.1.1 + NULL params
        let oid = Data([
            0x30, 0x0d,
            0x06, 0x09,
            0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00
        ])
        // BIT STRING: leading 0x00 = 0 unused bits
        let bsPayload = Data([0x00]) + pkcs1
        let bitString = Data([0x03]) + derLen(bsPayload.count) + bsPayload
        let seqBody   = oid + bitString
        return Data([0x30]) + derLen(seqBody.count) + seqBody
    }

    private static func derLen(_ n: Int) -> Data {
        if n < 128 { return Data([UInt8(n)]) }
        if n < 256 { return Data([0x81, UInt8(n)]) }
        return Data([0x82, UInt8(n >> 8), UInt8(n & 0xFF)])
    }

    enum CryptoError: Error {
        case noPublicKey, encoding
    }
}
```

---

## Step 3 — JWT Keychain Storage

**KMP**: `TokenStore.ios.kt → keychainWrite / keychainRead / keychainDelete`

```swift
import Security

struct TokenStore {

    private static let service = "com.vdigital.volumestream"

    static func saveJwt(_ value: String) { write(key: "vs_jwt", value: value) }
    static func loadJwt() -> String?     { read(key: "vs_jwt") }
    static func clearJwt()               { delete(key: "vs_jwt") }

    private static func write(key: String, value: String) {
        guard let data = value.data(using: .utf8) else { return }
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecAttrService: service
        ]
        let update: [CFString: Any] = [kSecValueData: data]
        if SecItemUpdate(query as CFDictionary, update as CFDictionary) == errSecItemNotFound {
            var add = query
            add[kSecValueData]      = data
            add[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    private static func read(key: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecAttrService: service,
            kSecReturnData:  true,
            kSecMatchLimit:  kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func delete(key: String) {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecAttrService: service
        ]
        SecItemDelete(query as CFDictionary)
    }
}
```

---

## Step 4 — Login → JWT

**KMP**: `AuthRepositoryImpl → login()`

```swift
import Foundation

struct StreamVaultClient {

    let host:     String
    let apiPort:  Int = 8081
    let authPort: Int = 8080

    init(host: String) { self.host = host }

    // POST /api/auth/login  →  JWT
    func login(email: String, password: String) async throws -> String {
        var req = URLRequest(url: URL(string: "http://\(host):\(authPort)/api/auth/login")!)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(["email": email, "password": password])

        let (data, _) = try await URLSession.shared.data(for: req)
        struct R: Decodable { let token: String }
        let jwt = try JSONDecoder().decode(R.self, from: data).token
        TokenStore.saveJwt(jwt)    // persist in Keychain
        return jwt
    }
}
```

---

## Step 5 — Device Registration

**KMP**: `SessionDataSourceImpl → registerDevice()`

```swift
extension StreamVaultClient {

    // POST /api/v1/device/register
    // 201 = new registration, 409 = already registered — both treated as success.
    // Safe to call on every app launch.
    func registerDevice(jwt: String) async throws {
        struct Body: Encodable { let deviceId: String; let publicKey: String }

        var req = URLRequest(
            url: URL(string: "http://\(host):\(apiPort)/api/v1/device/register")!)
        req.httpMethod = "POST"
        req.setValue("Bearer \(jwt)",    forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(
            Body(deviceId:  DeviceStore.deviceId(),
                 publicKey: try DeviceCrypto.publicKeyBase64())   // SPKI-wrapped RSA
        )

        let (_, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as! HTTPURLResponse).statusCode
        guard [200, 201, 409].contains(code) else {
            throw SVError.registrationFailed(code)
        }
    }
}
```

---

## Step 6 — Server Time (clock-skew guard)

**KMP**: `SessionDataSourceImpl → fetchServerEpochMillis()`

```swift
extension StreamVaultClient {

    // GET /api/v1/server/time  →  { "epochMillis": 1746307200000 }
    //
    // The cert-pin signature payload includes the timestamp.
    // Using the SERVER'S clock guards against emulator / device clock drift
    // that would cause CERT_PIN_FAILED on the server.
    func serverEpochMs() async -> Int64 {
        guard
            let url  = URL(string: "http://\(host):\(apiPort)/api/v1/server/time"),
            let (data, _) = try? await URLSession.shared.data(from: url),
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let ms   = json["epochMillis"] as? Int64
        else {
            // Fallback to device clock — may fail cert-pin if skew > server tolerance (~5 min)
            return Int64(Date().timeIntervalSince1970 * 1000)
        }
        return ms
    }
}
```

---

## Step 7 — Start Playback Session (cert-pin)

**KMP**: `SessionDataSourceImpl → startSession()`

```swift
extension StreamVaultClient {

    struct Session: Decodable {
        let sessionId:    String
        let sessionToken: String
    }

    // POST /api/v1/session/start
    //
    // Proves device identity by signing "userId|videoId|serverTimestampMs"
    // with the RSA-2048 Keychain private key.
    // Server verifies the signature against the SPKI public key stored during registration.
    func startSession(jwt: String, videoId: String) async throws -> Session {
        let userId    = jwtSub(jwt)
        let timestamp = await serverEpochMs()
        let payload   = "\(userId)|\(videoId)|\(timestamp)"
        let signature = try DeviceCrypto.sign(payload: payload)

        var req = URLRequest(
            url: URL(string: "http://\(host):\(apiPort)/api/v1/session/start")!)
        req.httpMethod = "POST"
        req.setValue("Bearer \(jwt)",         forHTTPHeaderField: "Authorization")
        req.setValue(DeviceStore.deviceId(),  forHTTPHeaderField: "X-Device-Id")
        req.setValue("\(timestamp)",           forHTTPHeaderField: "X-Cert-Timestamp")
        req.setValue(signature,               forHTTPHeaderField: "X-Cert-Signature")
        req.setValue("application/json",      forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(["videoId": videoId])

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as! HTTPURLResponse).statusCode == 200 else {
            throw SVError.sessionStartFailed
        }
        return try JSONDecoder().decode(Session.self, from: data)
    }

    // Decodes the `sub` claim from a JWT without verifying the signature.
    private func jwtSub(_ jwt: String) -> String {
        var b64 = jwt.components(separatedBy: ".").dropFirst().first ?? ""
        b64 = b64
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64 += "=" }
        guard let d   = Data(base64Encoded: b64),
              let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any]
        else { return "" }
        return obj["sub"] as? String ?? ""
    }
}
```

---

## Step 8 — Fetch HLS Manifest → `file://`

**KMP**: `PlaybackStateController.ios.kt → prefetchForPlayback()`

```swift
extension StreamVaultClient {

    // GET /api/v1/manifest/hls/<mediaId>  (requires X-Session-Token header)
    //
    // Downloads the HLS master playlist with the session token header and
    // writes it to NSTemporaryDirectory, returning a local file:// URL.
    //
    // WHY file://:
    //   AVPlayer cannot inject custom HTTP headers per request.
    //   The string "AVURLAssetHTTPHeaderFieldsKey" is undocumented and causes
    //   App Store rejection when found by Apple's binary scanner.
    //   Writing the manifest locally eliminates both problems:
    //   • The one request that needs a header is made here via URLSession (public API).
    //   • All variant / segment / key URLs in the manifest already embed
    //     ?sid=<sid>&t=<token>, so AVPlayer fetches them as plain HTTP — no headers.
    func fetchManifest(mediaId: String, sessionToken: String) async throws -> URL {
        var req = URLRequest(
            url: URL(string: "http://\(host):\(apiPort)/api/v1/manifest/hls/\(mediaId)")!)
        req.setValue(sessionToken, forHTTPHeaderField: "X-Session-Token")

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as! HTTPURLResponse).statusCode == 200 else {
            throw SVError.manifestFetchFailed
        }

        let tmp = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(mediaId).m3u8")
        try data.write(to: tmp, options: .atomic)
        return tmp   // file:///private/tmp/<mediaId>.m3u8
    }
}
```

**What the cached master playlist looks like:**

```m3u8
#EXTM3U
#EXT-X-VERSION:3

#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
http://host:8081/api/v1/manifest/hls/<id>/360p?sid=<sid>&t=<token>

#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
http://host:8081/api/v1/manifest/hls/<id>/720p?sid=<sid>&t=<token>

#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
http://host:8081/api/v1/manifest/hls/<id>/1080p?sid=<sid>&t=<token>
```

All URLs are absolute and carry `?t=` — AVPlayer fetches them without any auth headers.

---

## Step 9 — AVPlayer Controller

**KMP**: `PlaybackStateController.ios.kt → addItem() / initPlayer() / play() / seekTo()`

```swift
import AVFoundation

@MainActor
final class HLSPlayer: ObservableObject {

    let avPlayer = AVQueuePlayer()

    @Published var isPlaying  = false
    @Published var progress   = Float(0)    // 0.0 – 1.0, drives the seek bar
    @Published var totalMs    = Int64(0)

    private var timeObserver: Any?

    // MARK: - Load

    /// Feed the file:// URL returned by fetchManifest().
    /// AVFoundation reads the cached master playlist, auto-selects the best variant,
    /// fetches segments, and decrypts AES-128 via EXT-X-KEY — all natively.
    func load(manifestURL: URL) {
        avPlayer.removeAllItems()
        avPlayer.insert(AVPlayerItem(url: manifestURL), after: nil)
    }

    // MARK: - Controls

    func play()  { avPlayer.play();  isPlaying = true  }
    func pause() { avPlayer.pause(); isPlaying = false }

    func seek(fraction: Float) {
        guard totalMs > 0 else { return }
        let t = CMTime(value: Int64(fraction * Float(totalMs)), timescale: 1000)
        avPlayer.seek(to: t)
    }

    func skipForward()  { seekMs(currentMs() + 10_000) }
    func skipBackward() { seekMs(max(0, currentMs() - 10_000)) }

    private func seekMs(_ ms: Int64) {
        avPlayer.seek(to: CMTime(value: ms, timescale: 1000))
    }

    private func currentMs() -> Int64 {
        let s = CMTimeGetSeconds(avPlayer.currentTime())
        return s.isNaN ? 0 : Int64(s * 1000)
    }

    // MARK: - Progress timer (500 ms ticks — matches KMP NSTimer interval)

    func startTimer() {
        stopTimer()
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = avPlayer.addPeriodicTimeObserver(
            forInterval: interval,
            queue: .main
        ) { [weak self] _ in
            guard let self, let item = self.avPlayer.currentItem else { return }
            let pos = CMTimeGetSeconds(self.avPlayer.currentTime())
            let dur = CMTimeGetSeconds(item.duration)
            guard !pos.isNaN, !dur.isNaN, dur > 0 else { return }
            self.totalMs  = Int64(dur * 1000)
            self.progress = Float(pos / dur)
            self.isPlaying = self.avPlayer.rate != 0 && self.avPlayer.error == nil
        }
    }

    func stopTimer() {
        if let obs = timeObserver {
            avPlayer.removeTimeObserver(obs)
            timeObserver = nil
        }
    }

    deinit { stopTimer() }
}
```

---

## Step 10 — AES-128 Key Delivery (automatic)

**KMP**: no-op on iOS — `setAesKey()` is empty; AVFoundation handles `EXT-X-KEY` natively.

AVFoundation reads the `EXT-X-KEY` line from each variant playlist:

```m3u8
#EXT-X-KEY:METHOD=AES-128,
  URI="http://host:8081/api/v1/manifest/<id>/key?sid=<sid>&t=<token>",
  IV=0x00000000000000000000000000000000
```

- AVFoundation GETs the key URI automatically — **no app code needed**
- The `/key` endpoint is `permitAll()` — the stream token in `?t=` provides auth, no `Authorization` header
- Returns exactly **16 raw bytes**; AVFoundation decrypts every `.ts` segment transparently
- **No equivalent to Android's `setAesKey()` call is needed on iOS**

---

## Step 11 — Session Teardown

**KMP**: `SessionDataSourceImpl → endSession()` / `PlaybackViewModel.onCleared()`

```swift
extension StreamVaultClient {

    // Removed: The backend handles session revocation natively via heartbeat expiration,
    // so explicit DELETE /session/<sessionId> from the client is no longer necessary.
}
```

---

## Full Orchestrator — PlaybackCoordinator

**KMP**: `PlaybackViewModel.initialise() + selectTrack() + onCleared()`

```swift
import AVFoundation

@MainActor
final class PlaybackCoordinator: ObservableObject {

    // ── Configuration ─────────────────────────────────────────────────────────
    private let api = StreamVaultClient(host: "192.168.1.100")  // ← your server IP
    let player      = HLSPlayer()

    // ── Active session state ──────────────────────────────────────────────────
    private var jwt:       String?
    private var sessionId: String?

    // MARK: - Start playback (mirrors PlaybackViewModel.initialise)

    func startPlayback(email: String, password: String, mediaId: String) async {
        do {
            // Step 4: authenticate → JWT
            let jwt  = try await api.login(email: email, password: password)
            self.jwt = jwt

            // Step 5: register device — idempotent, safe on every launch
            try await api.registerDevice(jwt: jwt)

            // Step 7: cert-pin session → sessionId + sessionToken
            let session    = try await api.startSession(jwt: jwt, videoId: mediaId)
            self.sessionId = session.sessionId

            // Step 8: download master manifest with session token → local file://
            let manifestURL = try await api.fetchManifest(
                mediaId:      mediaId,
                sessionToken: session.sessionToken)

            // Step 9: hand file:// URL to AVPlayer, start timer, play
            player.load(manifestURL: manifestURL)
            player.startTimer()
            player.play()

        } catch {
            print("❌ PlaybackCoordinator.startPlayback: \(error.localizedDescription)")
        }
    }

    // MARK: - Track switch (mirrors PlaybackViewModel.selectTrack)

    func switchTrack(mediaId: String) async {
        sessionId = nil
        guard let jwt else { return }

        do {
            let session    = try await api.startSession(jwt: jwt, videoId: mediaId)
            self.sessionId = session.sessionId

            let manifestURL = try await api.fetchManifest(
                mediaId:      mediaId,
                sessionToken: session.sessionToken)

            // Swap item in-place — keeps AVPlayerLayer attached to its UIView surface
            player.pause()
            player.load(manifestURL: manifestURL)
            player.play()

        } catch {
            print("❌ PlaybackCoordinator.switchTrack: \(error.localizedDescription)")
        }
    }

    // MARK: - Controls

    func playPause()             { player.isPlaying ? player.pause() : player.play() }
    func skipForward()           { player.skipForward() }
    func skipBackward()          { player.skipBackward() }
    func seek(fraction: Float)   { player.seek(fraction: fraction); player.play() }

    // MARK: - Teardown (mirrors PlaybackViewModel.onCleared)

    func stop() async {
        player.pause()
        player.stopTimer()
        jwt = nil; sessionId = nil
    }
}
```

---

## SwiftUI View

```swift
import SwiftUI, AVFoundation

struct VideoPlayerView: View {

    let mediaId: String
    @StateObject private var coord = PlaybackCoordinator()

    var body: some View {
        ZStack(alignment: .bottom) {

            // ── Video surface ─────────────────────────────────────────────────
            AVPlayerViewRepresentable(player: coord.player.avPlayer)
                .ignoresSafeArea()

            // ── Controls overlay ──────────────────────────────────────────────
            VStack(spacing: 0) {
                Spacer()

                // Seek bar
                Slider(
                    value: $coord.player.progress,
                    in: 0...1,
                    onEditingChanged: { editing in
                        if !editing { coord.seek(fraction: coord.player.progress) }
                    }
                )
                .accentColor(.green)
                .padding(.horizontal, 20)

                // Playback buttons
                HStack(spacing: 40) {
                    Button { coord.skipBackward() } label: {
                        Image(systemName: "gobackward.10")
                            .font(.system(size: 28))
                            .foregroundColor(.white)
                    }
                    Button { coord.playPause() } label: {
                        Image(systemName: coord.player.isPlaying
                              ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 56))
                            .foregroundColor(.white)
                    }
                    Button { coord.skipForward() } label: {
                        Image(systemName: "goforward.10")
                            .font(.system(size: 28))
                            .foregroundColor(.white)
                    }
                }
                .padding(.bottom, 40)
            }
            .background(
                LinearGradient(
                    colors: [.clear, .black.opacity(0.7)],
                    startPoint: .center,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
            )
        }
        .task {
            await coord.startPlayback(
                email:    "user@example.com",
                password: "secret",
                mediaId:  mediaId
            )
        }
        .onDisappear {
            Task { await coord.stop() }
        }
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
    }
}

// UIKit bridge for AVPlayerLayer
struct AVPlayerViewRepresentable: UIViewRepresentable {

    let player: AVQueuePlayer

    func makeUIView(context: Context) -> UIView {
        let view  = UIView(frame: .zero)
        view.backgroundColor = .black
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspect
        layer.frame        = UIScreen.main.bounds
        view.layer.addSublayer(layer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let layer = uiView.layer.sublayers?.first as? AVPlayerLayer else { return }
        DispatchQueue.main.async { layer.frame = uiView.bounds }
    }
}
```

---

## Orientation Lock During Playback

**KMP**: `iOSApp.swift → OrientationManager + LOCK_CHANGED_NOTIFICATION`

```swift
// AppDelegate.swift
import UIKit

class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        PlaybackOrientationManager.shared.forceLandscape ? .landscape : .portrait
    }
}

// Pure-Swift equivalent of KMP OrientationManager
final class PlaybackOrientationManager {

    static let shared = PlaybackOrientationManager()
    private init() {}

    var forceLandscape = false {
        didSet { requestUpdate() }
    }

    private func requestUpdate() {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first else { return }
        let orientations: UIInterfaceOrientationMask = forceLandscape ? .landscape : .portrait
        if #available(iOS 16, *) {
            scene.requestGeometryUpdate(.iOS(interfaceOrientations: orientations))
            scene.keyWindow?.rootViewController?
                .setNeedsUpdateOfSupportedInterfaceOrientations()
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
}
```

---

## Step Map — KMP ↔ Swift

| # | Step | KMP class | Swift type |
|---|------|-----------|------------|
| 1 | Stable device ID | `TokenStore.ios.kt` | `DeviceStore` |
| 2 | RSA key pair + SPKI export | `DeviceCrypto.ios.kt` | `DeviceCrypto` |
| 3 | JWT Keychain | `TokenStore.ios.kt` | `TokenStore` |
| 4 | Login → JWT | `AuthRepositoryImpl` | `StreamVaultClient.login()` |
| 5 | Device register | `SessionDataSourceImpl.registerDevice()` | `StreamVaultClient.registerDevice()` |
| 6 | Server time (clock-skew guard) | `SessionDataSourceImpl.fetchServerEpochMillis()` | `StreamVaultClient.serverEpochMs()` |
| 7 | Session start (cert-pin) | `SessionDataSourceImpl.startSession()` | `StreamVaultClient.startSession()` |
| 8 | Manifest prefetch → `file://` | `PlaybackStateController.prefetchForPlayback()` | `StreamVaultClient.fetchManifest()` |
| 9 | AVPlayer init + play | `PlaybackStateController.addItem() / play()` | `HLSPlayer.load() / play()` |
| 10 | AES-128 key delivery | no-op (AVFoundation `EXT-X-KEY` native) | no-op |
| 11 | Session teardown | backend self-managed timeout | no-op |
| — | Orchestration | `PlaybackViewModel` | `PlaybackCoordinator` |
| — | UI | `ComposeView` / `UIViewRepresentable` | `VideoPlayerView` + `AVPlayerViewRepresentable` |

---

*Change `StreamVaultClient(host:)` to your production server hostname.*  
*Enable HTTPS: use `https` scheme and `port: 443` for production builds.*  
*Remove `NSExceptionAllowsInsecureHTTPLoads` before App Store submission.*
