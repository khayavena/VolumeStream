import Foundation
import AVFoundation
import VolumeStreamShared
import CoreMedia

@MainActor
final class VolumeStreamTVViewModel: ObservableObject {
    struct NavItem: Identifiable, Decodable, Equatable {
        let route: String
        let label: String
        let iconToken: String
        let sfSymbol: String

        var id: String { route }
    }

    struct MediaItem: Identifiable, Decodable, Hashable {
        let id: String
        let title: String
        let streamUrl: String
        let hlsStreamUrl: String
        let artworkUrl: String
        let durationMs: Int64
        let description: String

        var playableManifestUrl: String {
            hlsStreamUrl.isEmpty ? streamUrl : hlsStreamUrl
        }
    }

    struct MediaSection: Identifiable, Decodable {
        let title: String
        let items: [MediaItem]

        var id: String { title }
    }

    struct ProfileSummary: Decodable {
        let fullName: String
        let phone: String
        let address: String
    }

    struct LoginResult: Decodable {
        let success: Bool
        let message: String
    }

    struct ShellSnapshot: Decodable {
        let navItems: [NavItem]
        let homeSections: [MediaSection]
        let isLoggedIn: Bool
        let currentUserEmail: String
        let profile: ProfileSummary?
    }

    enum PlaybackPhase {
        case idle
        case buffering
        case playing
        case failed(String)
    }

    @Published var phase: PlaybackPhase = .idle
    @Published var player = AVPlayer()
    @Published var statusText = "Ready"
    @Published var isPlayerVisible = false
    @Published var isPlaying = false
    @Published var currentTimeSeconds: Double = 0
    @Published var durationSeconds: Double = 0
    @Published var playbackErrorMessage: String = ""
    @Published var nowPlayingTitle: String = ""
    @Published var nowPlayingDescription: String = ""
    @Published var currentMediaId: String = ""

    @Published var navItems: [NavItem] = []
    @Published var homeSections: [MediaSection] = []
    @Published var searchResults: [MediaItem] = []
    @Published var profile: ProfileSummary?
    @Published var currentUserEmail: String = ""
    @Published var isLoggedIn: Bool = false
    @Published var isLoading: Bool = false
    @Published var isLoginSubmitting: Bool = false
    @Published var errorMessage: String = ""

    private let decoder = JSONDecoder()
    private var downloadsEnabled = true
    private var timeObserverToken: Any?
    private var endObserver: NSObjectProtocol?
    private var itemStatusObservation: NSKeyValueObservation?
    private var searchDebounceTask: Task<Void, Never>?
    private var searchRequestToken: Int = 0
    private var shellRequestToken: Int = 0
    private var activeSearchQuery: String = ""
    private var pendingRequestCount: Int = 0
    private var preferredPeakBitRate: Double = 0

    func bootstrap(using config: VolumeStreamTVBootstrapConfig) {
        let started = AppleDataBootstrapKt.initializeAppleDataLayer(
            apiHost: config.apiHost,
            authHost: config.authHost,
            apiPort: config.apiPort,
            authPort: config.authPort,
            apiUseHttps: config.apiUseHttps,
            authUseHttps: config.authUseHttps,
            artworkProfile: "TV"
        )
        let ready = AppleDataBootstrapKt.isAppleDataLayerReady()
        if ready {
            statusText = started ? "Ready" : "Reusing existing session"
        } else {
            phase = .failed(AppleDataBootstrapKt.appleDataLayerReadinessMessage())
            statusText = "Data layer wiring failed"
        }
    }

    func refreshShell(downloadsEnabled: Bool) {
        self.downloadsEnabled = downloadsEnabled
        shellRequestToken += 1
        let requestToken = shellRequestToken
        performBridgeJsonRequest(
            call: { completion in
                AppleTvContentBridge.shared.fetchShellSnapshotJson(downloadsEnabled: downloadsEnabled, completionHandler: completion)
            },
            acceptResult: { [weak self] in
                guard let self else { return false }
                return requestToken == self.shellRequestToken
            },
            onSuccess: { [weak self] payload in
                guard let self else { return }
                if let snapshot = self.decode(ShellSnapshot.self, from: payload, fallback: "{\"navItems\":[],\"homeSections\":[],\"isLoggedIn\":false,\"currentUserEmail\":\"\",\"profile\":null}") {
                    self.apply(snapshot: snapshot)
                }
            }
        )
    }

    func play(using item: VolumeStreamTVPlaybackItemConfig) {
        guard !item.mediaId.isEmpty, !item.manifestUrl.isEmpty else {
            phase = .failed("Set TV_MEDIA_ID and TV_MANIFEST_URL in tvOS config")
            return
        }

        phase = .buffering
        statusText = "Preparing playback..."
        isPlayerVisible = true
        if currentMediaId.isEmpty {
            currentMediaId = item.mediaId
        }

        let normalized = ApplePlaybackBridge.shared.normalizeManifestUrl(url: item.manifestUrl)
        ApplePlaybackBridge.shared.createPlaybackHeaders(mediaId: item.mediaId) { [weak self] headers, error in
            guard let self else { return }
            self.onMain {
                if let error {
                    self.phase = .failed("Header creation failed: \(error.localizedDescription)")
                    self.statusText = "Playback failed"
                    return
                }

                guard let url = URL(string: normalized) else {
                    self.phase = .failed("Invalid manifest URL")
                    self.statusText = "Playback failed"
                    return
                }

                var assetOptions: [String: Any] = [:]
                if let map = headers as? [String: Any], !map.isEmpty {
                    assetOptions["AVURLAssetHTTPHeaderFieldsKey"] = map
                }

                let asset = AVURLAsset(url: url, options: assetOptions)
                let playerItem = AVPlayerItem(asset: asset)
                self.player.replaceCurrentItem(with: playerItem)
                self.bindPlayerObservers(playerItem)
                self.player.play()
                self.phase = .playing
                self.statusText = "Playing"
                self.isPlaying = true
                self.playbackErrorMessage = ""
            }
        }
    }

    func play(mediaItem: MediaItem) {
        currentMediaId = mediaItem.id
        nowPlayingTitle = mediaItem.title
        nowPlayingDescription = mediaItem.description
        play(
            using: VolumeStreamTVPlaybackItemConfig(
                mediaId: mediaItem.id,
                manifestUrl: mediaItem.playableManifestUrl
            )
        )
    }

    func closePlayer() {
        unbindPlayerObservers()
        player.pause()
        player.replaceCurrentItem(with: nil)
        phase = .idle
        statusText = "Ready"
        isPlayerVisible = false
        isPlaying = false
        currentTimeSeconds = 0
        durationSeconds = 0
        playbackErrorMessage = ""
        nowPlayingTitle = ""
        nowPlayingDescription = ""
        currentMediaId = ""
    }

    func togglePlayPause() {
        if isPlaying {
            player.pause()
            isPlaying = false
            statusText = "Paused"
        } else {
            player.play()
            isPlaying = true
            statusText = "Playing"
        }
    }

    func seek(by seconds: Double) {
        let target = max(0, min(durationSeconds, currentTimeSeconds + seconds))
        seek(toProgress: durationSeconds > 0 ? target / durationSeconds : 0)
    }

    func seek(toProgress progress: Double) {
        guard durationSeconds > 0 else { return }
        let clamped = max(0, min(1, progress))
        let targetSeconds = durationSeconds * clamped
        let time = CMTime(seconds: targetSeconds, preferredTimescale: 600)
        player.seek(to: time) { [weak self] _ in
            self?.onMain {
                self?.currentTimeSeconds = targetSeconds
            }
        }
    }

    func refreshHome() { refreshShell(downloadsEnabled: downloadsEnabled) }

    func search(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            searchResults = []
            activeSearchQuery = ""
            return
        }

        activeSearchQuery = trimmed
        searchRequestToken += 1
        let requestToken = searchRequestToken

        performBridgeJsonRequest(
            call: { completion in
                AppleTvContentBridge.shared.searchJson(query: trimmed, completionHandler: completion)
            },
            onSuccess: { [weak self] payload in
                guard let self else { return }
                guard requestToken == self.searchRequestToken, self.activeSearchQuery == trimmed else {
                    return
                }
                self.searchResults = self.decodeArray(MediaItem.self, from: payload, fallback: "[]")
            }
        )
    }

    func searchDebounced(query: String, delayMillis: UInt64 = 350) {
        searchDebounceTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            searchResults = []
            return
        }

        searchDebounceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: delayMillis * 1_000_000)
            guard !Task.isCancelled else { return }
            self?.search(query: trimmed)
        }
    }

    func cancelPendingSearch() {
        searchDebounceTask?.cancel()
        searchDebounceTask = nil
        searchRequestToken += 1
    }

    func resetSearchState() {
        cancelPendingSearch()
        activeSearchQuery = ""
        searchResults = []
    }

    func clearErrorMessage() {
        errorMessage = ""
    }

    func setPreferredPeakBitRate(_ bitsPerSecond: Double) {
        preferredPeakBitRate = max(0, bitsPerSecond)
        player.currentItem?.preferredPeakBitRate = preferredPeakBitRate
    }

    func refreshProfile() { refreshShell(downloadsEnabled: downloadsEnabled) }

    func login(email: String, password: String, completion: @escaping (Bool) -> Void) {
        guard !isLoginSubmitting else { return }
        isLoginSubmitting = true
        performBridgeJsonRequest(
            call: { callback in
                AppleTvContentBridge.shared.loginJson(email: email, password: password, completionHandler: callback)
            },
            onSuccess: { [weak self] payload in
                guard let self else {
                    completion(false)
                    return
                }
                self.isLoginSubmitting = false
                let result = self.decode(LoginResult.self, from: payload, fallback: "{\"success\":false,\"message\":\"Unknown error\"}")
                if result?.success == true {
                    // Apply optimistic auth state immediately, then hydrate full shell snapshot.
                    self.errorMessage = ""
                    self.isLoggedIn = true
                    self.currentUserEmail = email
                    self.refreshShell(downloadsEnabled: self.downloadsEnabled)
                    completion(true)
                } else if self.reconcileLoginState(fallbackEmail: email) {
                    completion(true)
                } else {
                    self.errorMessage = result?.message ?? "Login failed"
                    completion(false)
                }
            },
            onFailure: { [weak self] _ in
                guard let self else {
                    completion(false)
                    return
                }
                self.isLoginSubmitting = false
                if self.reconcileLoginState(fallbackEmail: email) {
                    completion(true)
                } else {
                    completion(false)
                }
            }
        )
    }

    private func reconcileLoginState(fallbackEmail: String) -> Bool {
        guard AppleTvContentBridge.shared.isLoggedIn() else {
            return false
        }

        errorMessage = ""
        isLoggedIn = true
        let bridgedEmail = AppleTvContentBridge.shared.currentUserEmail()
        currentUserEmail = bridgedEmail.isEmpty ? fallbackEmail : bridgedEmail
        refreshShell(downloadsEnabled: downloadsEnabled)
        return true
    }

    func logout() {
        AppleTvContentBridge.shared.logout()
        refreshShell(downloadsEnabled: downloadsEnabled)
    }

    private func apply(snapshot: ShellSnapshot) {
        navItems = snapshot.navItems
        homeSections = snapshot.homeSections
        isLoggedIn = snapshot.isLoggedIn
        currentUserEmail = snapshot.currentUserEmail
        profile = snapshot.profile
    }

    private func bindPlayerObservers(_ item: AVPlayerItem) {
        unbindPlayerObservers()
        item.preferredPeakBitRate = preferredPeakBitRate

        itemStatusObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] observedItem, _ in
            self?.onMain {
                guard let self else { return }
                switch observedItem.status {
                case .readyToPlay:
                    let seconds = CMTimeGetSeconds(observedItem.duration)
                    self.durationSeconds = seconds.isFinite && seconds > 0 ? seconds : 0
                    self.playbackErrorMessage = ""
                case .failed:
                    let message = observedItem.error?.localizedDescription ?? "Playback item failed"
                    self.phase = .failed(message)
                    self.statusText = "Playback failed"
                    self.playbackErrorMessage = message
                    self.isPlaying = false
                default:
                    break
                }
            }
        }

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.onMain {
                guard let self else { return }
                self.isPlaying = false
                self.statusText = "Ended"
                self.phase = .idle
                self.currentTimeSeconds = self.durationSeconds
            }
        }

        timeObserverToken = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            self?.onMain {
                guard let self else { return }
                let seconds = CMTimeGetSeconds(time)
                if seconds.isFinite {
                    self.currentTimeSeconds = max(0, seconds)
                }
                self.isPlaying = self.player.rate > 0
                switch self.phase {
                case .failed:
                    break
                default:
                    self.phase = self.isPlaying ? .playing : .buffering
                }
            }
        }
    }

    private func unbindPlayerObservers() {
        if let token = timeObserverToken {
            player.removeTimeObserver(token)
            timeObserverToken = nil
        }
        if let observer = endObserver {
            NotificationCenter.default.removeObserver(observer)
            endObserver = nil
        }
        itemStatusObservation = nil
    }

    deinit {
        searchDebounceTask?.cancel()
    }

    private func performBridgeJsonRequest(
        call: (@escaping (String?, Error?) -> Void) -> Void,
        acceptResult: (() -> Bool)? = nil,
        onSuccess: @escaping (String) -> Void,
        onFailure: ((Error) -> Void)? = nil
    ) {
        updateLoading(delta: +1)
        errorMessage = ""
        call { [weak self] json, error in
            self?.onMain {
                guard let self else { return }
                self.updateLoading(delta: -1)
                if !(acceptResult?() ?? true) {
                    return
                }
                if let error {
                    self.errorMessage = error.localizedDescription
                    onFailure?(error)
                    return
                }
                onSuccess(json ?? "")
            }
        }
    }

    private func onMain(_ work: @escaping @MainActor () -> Void) {
        Task { @MainActor in
            work()
        }
    }

    private func updateLoading(delta: Int) {
        pendingRequestCount = max(0, pendingRequestCount + delta)
        isLoading = pendingRequestCount > 0
    }

    private func decodeArray<T: Decodable>(_ type: T.Type, from json: String, fallback: String) -> [T] {
        guard let data = (json.isEmpty ? fallback : json).data(using: .utf8) else { return [] }
        return (try? decoder.decode([T].self, from: data)) ?? []
    }

    private func decode<T: Decodable>(_ type: T.Type, from json: String, fallback: String? = nil) -> T? {
        let payload = json.isEmpty ? (fallback ?? "") : json
        guard let data = payload.data(using: .utf8) else { return nil }
        return try? decoder.decode(T.self, from: data)
    }
}

