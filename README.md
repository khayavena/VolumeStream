This is a Kotlin Multiplatform project targeting Android, iOS, and tvOS (Apple TV).

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## Apple TV (tvOS) enablement

The shared modules now expose tvOS targets (`tvosArm64`, `tvosSimulatorArm64`, `tvosX64`) and reuse `iosMain`
implementations through source-set dependency (`tvosMain -> iosMain`) to bootstrap Apple TV support.

At the moment, Compose UI dependencies still do not resolve for tvOS in this project, so the practical Apple TV
path is: **SwiftUI tvOS host app + shared KMP data framework**.

Production tvOS app sources now live under `iosApp/tvOSApp`.

Build the shared data framework for Apple TV simulator/device from Gradle:

```bash
cd /Users/khayavena/Documents/auth-pulse-service/software
./gradlew :data:linkDebugFrameworkTvosSimulatorArm64
./gradlew :data:linkDebugFrameworkTvosArm64
```

The framework is generated as `VolumeStreamShared.framework`.

Initialize shared services from Swift/tvOS before using repositories:

```swift
import VolumeStreamShared

_ = AppleDataBootstrapKt.initializeAppleDataLayer(
    apiHost: "your-api-host",
    authHost: "your-auth-host",
    apiPort: 8081,
    authPort: 8080,
    apiUseHttps: true,
    authUseHttps: true,
    artworkProfile: "TV"
)
```

To keep behavior aligned with your existing Kotlin iOS player implementation, use
`ApplePlaybackBridge` helpers from Swift:

```swift
import VolumeStreamShared

let normalized = ApplePlaybackBridge.shared.normalizeManifestUrl(url: rawManifestUrl)
ApplePlaybackBridge.shared.createPlaybackHeaders(mediaId: mediaId) { headers, error in
    // headers contains Authorization + X-Session-Token when session bootstrap succeeds.
}
```

ComposeApp framework commands (for future use once tvOS Compose dependencies are resolved):

```bash
cd /Users/khayavena/Documents/auth-pulse-service/software
./gradlew :composeApp:linkDebugFrameworkTvosSimulatorArm64
./gradlew :composeApp:linkDebugFrameworkTvosArm64
```

Next step in Xcode is to add a tvOS app target (or a dedicated tvOS project) and point it at the generated
`ComposeApp.framework` similarly to how the existing iOS target is wired.

## Auth Service (Login/Register/Profile)

The app reads auth/API host and ports from `software/local.properties`.

```properties
API_HOST=https://localhost
AUTH_PORT=8080
API_PORT=8081
USE_HTTPS=true
```

Notes:

- `API_HOST` may include `https://`; the app normalizes host/scheme internally.
- `USE_HTTPS=true` forces TLS even on non-443 ports (for example `8080`).
- Login/Register call `/api/*`; profile calls `/profile/*` on the same auth service host/port.

## iOS Simulator TLS (-1202) Troubleshooting

If iOS shows `NSURLErrorDomain Code=-1202` for `https://localhost:8080/...`,
the simulator does not trust the local TLS certificate chain yet.

1) Generate a trusted local certificate (mkcert):

```bash
brew install mkcert
mkcert -install

cd /Users/khayavena/Documents/auth-pulse-service
mkdir -p certs
mkcert -cert-file certs/auth-pulse-local.crt -key-file certs/auth-pulse-local.key localhost 127.0.0.1 ::1
openssl pkcs12 -export -out certs/auth-pulse-local.p12 -in certs/auth-pulse-local.crt -inkey certs/auth-pulse-local.key -name auth-pulse-local -passout pass:changeit123
```

2) Restart backend with HTTPS + local compose override:

```bash
cd /Users/khayavena/Documents/auth-pulse-service
USE_LOCAL_COMPOSE_OVERRIDE='true' \
JWT_SECRET='local-dev-jwt-secret-change-me-please-32-bytes-min' \
MONGODB_URI='mongodb://host.docker.internal:27017/trust_pulse_auth' \
REDIS_HOST='host.docker.internal' \
REDIS_PORT='6379' \
CORS_ALLOWED_ORIGINS='https://localhost:8080' \
APP_SECURITY_API_KEY_PEPPER='local-dev-api-key-pepper-change-me' \
APP_SECURITY_RESET_TOKEN_PEPPER='local-dev-reset-token-pepper-change-me' \
SERVER_SSL_ENABLED='true' \
SSL_KEY_STORE_HOST_PATH='./certs/auth-pulse-local.p12' \
SSL_KEY_STORE_CONTAINER_PATH='/run/secrets/auth-pulse-local.p12' \
SSL_KEY_STORE_PATH='file:/run/secrets/auth-pulse-local.p12' \
SSL_KEY_STORE_PASSWORD='changeit123' \
SSL_KEY_ALIAS='auth-pulse-local' \
PORT='8080' \
MANAGEMENT_PORT='18081' \
./scripts/run-prod-docker.sh start
```

3) Verify backend over TLS:

```bash
curl -k -i https://localhost:8080/v3/api-docs
curl -k -i https://localhost:8080/swagger-ui/index.html
```

4) If the app still fails with `-1202`, trust the mkcert root CA in simulator:

```bash
mkcert -CAROOT
```

Then drag `<CAROOT>/rootCA.pem` into the iOS simulator and enable it under:
`Settings -> General -> About -> Certificate Trust Settings`.

