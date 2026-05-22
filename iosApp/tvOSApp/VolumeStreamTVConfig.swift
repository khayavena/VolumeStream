import Foundation

struct VolumeStreamTVBootstrapConfig {
    let apiHost: String
    let authHost: String
    let apiPort: Int32
    let authPort: Int32
    let apiUseHttps: Bool
    let authUseHttps: Bool
}

struct VolumeStreamTVPlaybackItemConfig {
    let mediaId: String
    let manifestUrl: String
}

struct VolumeStreamTVConfig {
    let bootstrap: VolumeStreamTVBootstrapConfig
    let playbackItem: VolumeStreamTVPlaybackItemConfig
    let downloadsEnabled: Bool
    let defaultLoginEmail: String
    let defaultLoginPassword: String
    let playbackOverlayAutoHideSeconds: UInt64
    let playbackSeekStepSeconds: Double
    let playbackSeekFastStepSeconds: Double

    static func load() -> VolumeStreamTVConfig {
        let info = Bundle.main.infoDictionary ?? [:]

        func string(_ key: String, default defaultValue: String) -> String {
            if let env = ProcessInfo.processInfo.environment[key], !env.isEmpty {
                return env
            }
            if let value = info[key] as? String, !value.isEmpty {
                return value
            }
            return defaultValue
        }

        func int32(_ key: String, default defaultValue: Int32) -> Int32 {
            let raw = string(key, default: String(defaultValue))
            return Int32(raw) ?? defaultValue
        }

        func uint64(_ key: String, default defaultValue: UInt64) -> UInt64 {
            let raw = string(key, default: String(defaultValue))
            return UInt64(raw) ?? defaultValue
        }

        func double(_ key: String, default defaultValue: Double) -> Double {
            let raw = string(key, default: String(defaultValue))
            return Double(raw) ?? defaultValue
        }

        func bool(_ key: String, default defaultValue: Bool) -> Bool {
            let raw = string(key, default: defaultValue ? "true" : "false").lowercased()
            switch raw {
            case "1", "true", "yes", "on": return true
            case "0", "false", "no", "off": return false
            default: return defaultValue
            }
        }

        let apiHost = string("TV_API_HOST", default: "localhost")
        return VolumeStreamTVConfig(
            bootstrap: VolumeStreamTVBootstrapConfig(
                apiHost: apiHost,
                authHost: string("TV_AUTH_HOST", default: apiHost),
                apiPort: int32("TV_API_PORT", default: 8081),
                authPort: int32("TV_AUTH_PORT", default: 18443),
                apiUseHttps: bool("TV_API_USE_HTTPS", default: true),
                authUseHttps: bool("TV_AUTH_USE_HTTPS", default: true)
            ),
            playbackItem: VolumeStreamTVPlaybackItemConfig(
                mediaId: string("TV_MEDIA_ID", default: ""),
                manifestUrl: string("TV_MANIFEST_URL", default: "")
            ),
            downloadsEnabled: bool("TV_DOWNLOADS_ENABLED", default: true),
            defaultLoginEmail: string("TV_LOGIN_EMAIL", default: "testuser100@example.com"),
            defaultLoginPassword: string("TV_LOGIN_PASSWORD", default: "TestPassword!123"),
            playbackOverlayAutoHideSeconds: uint64("TV_OVERLAY_AUTO_HIDE_SECONDS", default: 6),
            playbackSeekStepSeconds: double("TV_SEEK_STEP_SECONDS", default: 10.0),
            playbackSeekFastStepSeconds: double("TV_SEEK_FAST_STEP_SECONDS", default: 30.0)
        )
    }
}

