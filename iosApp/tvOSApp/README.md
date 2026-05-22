# VolumeStream tvOS App Sources

This directory contains production Apple TV app sources (no sample naming).

## Files

- `VolumeStreamTVApp.swift`
- `VolumeStreamTVShellView.swift`
- `VolumeStreamTVViewModel.swift`
- `VolumeStreamTVConfig.swift`

## Runtime Config Keys

Set these in your tvOS target `Info.plist` (or environment for debug runs):

- `TV_API_HOST`
- `TV_AUTH_HOST`
- `TV_API_PORT`
- `TV_AUTH_PORT`
- `TV_API_USE_HTTPS`
- `TV_AUTH_USE_HTTPS`
- `TV_MEDIA_ID`
- `TV_MANIFEST_URL`
- `TV_DOWNLOADS_ENABLED`
- `TV_OVERLAY_AUTO_HIDE_SECONDS`
- `TV_SEEK_STEP_SECONDS`
- `TV_SEEK_FAST_STEP_SECONDS`

## Kotlin Framework

Build the framework used by this app:

```bash
cd /Users/khayavena/Documents/auth-pulse-service/software
./gradlew :data:linkDebugFrameworkTvosSimulatorArm64
```

Link `VolumeStreamShared.framework` to your tvOS target.

## Shared Bridges (Data Module)

After calling `AppleDataBootstrapKt.initializeAppleDataLayer(...)`, tvOS can use:

- `ApplePlaybackBridge` for playback headers/session bootstrap.
- `AppleTvContentBridge` for auth/content/profile calls from Swift UI.
  - `fetchShellSnapshotJson(downloadsEnabled)` for nav + home + auth/profile in one payload.
  - `searchJson(query)`
  - `loginJson(email,password)`

This keeps Apple TV UI in `iosApp/tvOSApp` while repository/business logic remains in the data module.

