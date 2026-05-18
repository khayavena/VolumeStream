This is a Kotlin Multiplatform project targeting Android, iOS.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## Auth Service (Login/Register/Profile)

The app reads auth/API host and ports from `software/local.properties`.

```properties
API_HOST=https://localhost
AUTH_PORT=18443
API_PORT=18443
USE_HTTPS=true
```

Notes:

- `API_HOST` may include `https://`; the app normalizes host/scheme internally.
- `USE_HTTPS=true` forces TLS even on non-443 ports (for example `18443`).
- Login/Register call `/api/*`; profile calls `/profile/*` on the same auth service host/port.

## iOS Simulator TLS (-1202) Troubleshooting

If iOS shows `NSURLErrorDomain Code=-1202` for `https://localhost:18443/...`,
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
CORS_ALLOWED_ORIGINS='https://localhost:18443' \
APP_SECURITY_API_KEY_PEPPER='local-dev-api-key-pepper-change-me' \
APP_SECURITY_RESET_TOKEN_PEPPER='local-dev-reset-token-pepper-change-me' \
SERVER_SSL_ENABLED='true' \
SSL_KEY_STORE_HOST_PATH='./certs/auth-pulse-local.p12' \
SSL_KEY_STORE_CONTAINER_PATH='/run/secrets/auth-pulse-local.p12' \
SSL_KEY_STORE_PATH='file:/run/secrets/auth-pulse-local.p12' \
SSL_KEY_STORE_PASSWORD='changeit123' \
SSL_KEY_ALIAS='auth-pulse-local' \
PORT='18443' \
MANAGEMENT_PORT='18081' \
./scripts/run-prod-docker.sh start
```

3) Verify backend over TLS:

```bash
curl -k -i https://localhost:18443/v3/api-docs
curl -k -i https://localhost:18443/swagger-ui/index.html
```

4) If the app still fails with `-1202`, trust the mkcert root CA in simulator:

```bash
mkcert -CAROOT
```

Then drag `<CAROOT>/rootCA.pem` into the iOS simulator and enable it under:
`Settings -> General -> About -> Certificate Trust Settings`.

