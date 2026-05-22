import SwiftUI
import VolumeStreamShared
import AVKit
import UIKit

struct VolumeStreamTVShellView: View {
    private enum OverlayLane {
        case top
        case transport
        case tracks
        case qualityPanel
        case unknown
    }

    private enum RouteKey {
        static let home = "home"
        static let search = "search"
        static let profile = "profile"
        static let settings = "settings"
    }

    private enum OverlayControl: Hashable {
        case back
        case zoom
        case quality
        case playPause
        case rewind
        case forward
        case qualityOption(String)
        case track(String)
    }

    private struct QualityPreset: Identifiable, Hashable {
        let id: String
        let label: String
        let peakBitRate: Double
    }

    private let config: VolumeStreamTVConfig

    init(config: VolumeStreamTVConfig = .load()) {
        self.config = config
        _loginEmail = State(initialValue: config.defaultLoginEmail)
        _loginPassword = State(initialValue: config.defaultLoginPassword)
    }

    // Android TV palette parity (TvNavigationShell + PlaybackOverlayTheme).
    private let accent = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0) // #00E676
    private let focusedChipBg = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.40) // #6600E676
    private let sideNavBg = Color(red: 13 / 255.0, green: 13 / 255.0, blue: 13 / 255.0) // #0D0D0D
    private let sideNavExpandedBg = Color(red: 20 / 255.0, green: 20 / 255.0, blue: 20 / 255.0) // #141414
    private let selectedNavBg = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.27) // #4400E676
    private let focusedNavBg = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.13) // #2200E676
    private let controlsBarBg = Color.black.opacity(0.55) // #8C000000
    private let chipBg = Color.black.opacity(0.70) // #B3000000
    private let tvCardBg = Color(red: 17 / 255.0, green: 17 / 255.0, blue: 17 / 255.0, opacity: 0.80) // #CC111111
    private let carouselLabel = Color(red: 170 / 255.0, green: 170 / 255.0, blue: 170 / 255.0) // #AAAAAA
    private let carouselIdleBorder = Color(red: 51 / 255.0, green: 51 / 255.0, blue: 51 / 255.0) // #333333
    private let neutralStroke = Color.white.opacity(0.20)
    private let topScrim = LinearGradient(
        colors: [Color.black.opacity(0.72), Color.black.opacity(0.30), Color.clear],
        startPoint: .top,
        endPoint: .bottom
    )
    private let bottomScrim = LinearGradient(
        colors: [Color.clear, Color.black.opacity(0.40), Color.black.opacity(0.78)],
        startPoint: .top,
        endPoint: .bottom
    )
    private let navRailCollapsed: CGFloat = 72
    private let navRailExpanded: CGFloat = 220

    @StateObject private var viewModel = VolumeStreamTVViewModel()
    @State private var selectedRoute: String = RouteKey.home
    @State private var searchQuery: String = ""
    @State private var loginEmail: String
    @State private var loginPassword: String
    @State private var seekProgress: Double = 0
    @State private var overlayVisible = true
    @State private var showQualityPanel = false
    @State private var isZoomed = true
    @State private var selectedQualityId = "auto"
    @State private var overlayHideTask: Task<Void, Never>?
    @State private var lastSeekCommandAt: Date?
    @State private var consecutiveSeekCount: Int = 0
    @FocusState private var focusedRoute: String?
    @FocusState private var focusedMediaId: String?
    @FocusState private var focusedOverlayControl: OverlayControl?

    private let qualityPresets: [QualityPreset] = [
        .init(id: "auto", label: "HD", peakBitRate: 0),
        .init(id: "1080p", label: "1080p", peakBitRate: 6_500_000),
        .init(id: "720p", label: "720p", peakBitRate: 3_500_000),
        .init(id: "480p", label: "480p", peakBitRate: 1_500_000)
    ]

    private var navExpanded: Bool { focusedRoute != nil }
    private var canSubmitSearch: Bool {
        !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    private var canSubmitLogin: Bool {
        !loginEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !loginPassword.isEmpty &&
            !viewModel.isLoginSubmitting
    }
    private func isDownloadsRoute(_ route: String) -> Bool {
        route == "downloads"
    }
    private var visibleNavItems: [VolumeStreamTVViewModel.NavItem] {
        viewModel.navItems.filter { !isDownloadsRoute($0.route) }
    }

    var body: some View {
        ZStack(alignment: .leading) {
            if viewModel.isPlayerVisible {
                PlayerHostView(
                    player: viewModel.player,
                    videoGravity: isZoomed ? .resizeAspectFill : .resizeAspect
                )
                    .ignoresSafeArea()
                    .onPlayPauseCommand {
                        revealOverlay()
                        viewModel.togglePlayPause()
                    }
                    .onMoveCommand { direction in
                        if overlayVisible {
                            touchOverlay(autoHideDelay: 4)
                            handleOverlayMove(direction)
                            return
                        }
                        switch direction {
                        case .left:
                            revealOverlay(autoHideDelay: 4)
                            viewModel.seek(by: -currentSeekStep())
                        case .right:
                            revealOverlay(autoHideDelay: 4)
                            viewModel.seek(by: currentSeekStep())
                        default:
                            revealOverlay()
                        }
                    }
                    .onExitCommand {
                        if overlayVisible && showQualityPanel {
                            showQualityPanel = false
                            focusedOverlayControl = .quality
                            revealOverlay()
                        } else if overlayVisible {
                            hideOverlay()
                        } else {
                            resetSeekAcceleration()
                            viewModel.closePlayer()
                        }
                    }
                if overlayVisible {
                    playbackOverlay
                }
            } else {
                HStack(spacing: 0) {
                    sideNav
                    mainContent
                }
                .ignoresSafeArea()
                .background(Color.black)

                if viewModel.isLoading {
                    loadingIndicator
                }
            }

            overlayStatus
        }
        .onAppear {
            viewModel.bootstrap(using: config.bootstrap)
            // tvOS intentionally hides Downloads for parity and simpler nav flow.
            viewModel.refreshShell(downloadsEnabled: false)
        }
        .onChange(of: viewModel.isPlayerVisible) { visible in
            if visible {
                revealOverlay()
            } else {
                hideOverlay()
                showQualityPanel = false
                resetSeekAcceleration()
            }
        }
        .onChange(of: viewModel.isPlaying) { playing in
            if playing {
                scheduleOverlayAutoHide()
            } else {
                overlayVisible = true
                overlayHideTask?.cancel()
                overlayHideTask = nil
                if viewModel.isPlayerVisible {
                    focusedOverlayControl = .playPause
                }
            }
        }
        .onChange(of: showQualityPanel) { shown in
            if shown {
                overlayHideTask?.cancel()
                focusedOverlayControl = .qualityOption(selectedQualityId)
            } else if viewModel.isPlayerVisible {
                focusedOverlayControl = .quality
                scheduleOverlayAutoHide()
            }
            normalizeOverlayFocus()
        }
        .onChange(of: viewModel.currentTimeSeconds) { seconds in
            guard viewModel.durationSeconds > 0 else {
                seekProgress = 0
                return
            }
            seekProgress = max(0, min(1, seconds / viewModel.durationSeconds))
        }
        .onChange(of: viewModel.navItems) { items in
            let supported = items.filter { !isDownloadsRoute($0.route) }
            guard !supported.isEmpty else { return }
            if !supported.contains(where: { $0.route == selectedRoute }) {
                selectRoute(supported.first?.route ?? RouteKey.home)
            }
        }
        .onChange(of: selectedRoute) { route in
            handleRouteChange(route)
        }
        .onChange(of: searchQuery) { value in
            if selectedRoute == RouteKey.search {
                viewModel.searchDebounced(query: value)
            }
        }
        .onChange(of: focusedOverlayControl) { control in
            guard control != nil, viewModel.isPlayerVisible else { return }
            scheduleOverlayAutoHide(afterSeconds: 4)
        }
        .onChange(of: overlayTrackFocusSignature) { _ in
            normalizeOverlayFocus()
        }
        .onDisappear {
            overlayHideTask?.cancel()
            overlayHideTask = nil
        }
    }

    private var sideNav: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(visibleNavItems) { item in
                let isFocused = focusedRoute == item.route
                Button {
                    selectRoute(item.route)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: item.sfSymbol)
                        if navExpanded {
                            Text(item.label)
                        }
                    }
                    .foregroundStyle(selectedRoute == item.route ? accent : Color.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 10)
                    .padding(.horizontal, 12)
                    .background(
                        selectedRoute == item.route
                            ? selectedNavBg
                            : (isFocused ? focusedNavBg : Color.clear)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .focused($focusedRoute, equals: item.route)
            }

            Spacer()
        }
        .frame(width: navExpanded ? navRailExpanded : navRailCollapsed)
        .padding(.top, 44)
        .padding(.horizontal, 8)
        .background(navExpanded ? sideNavExpandedBg : sideNavBg)
        .animation(.easeInOut(duration: 0.2), value: navExpanded)
    }

    private var mainContent: some View {
        Group {
            switch selectedRoute {
            case RouteKey.home:
                homeView
            case RouteKey.search:
                searchView
            case RouteKey.profile:
                profileView
            case RouteKey.settings:
                settingsView
            default:
                homeView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(32)
    }

    private var homeView: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Home")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)
            Text("Continue watching and featured content")
                .font(.system(size: 24, weight: .regular))
                .foregroundStyle(.white.opacity(0.8))

            if viewModel.homeSections.isEmpty {
                Button("Reload Home") {
                    viewModel.refreshHome()
                }
                .buttonStyle(TVPrimaryButtonStyle(accent: accent))
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(viewModel.homeSections) { section in
                            VStack(alignment: .leading, spacing: 10) {
                                Text(section.title)
                                    .font(.title3)
                                    .foregroundStyle(.white)

                                ScrollView(.horizontal) {
                                    HStack(spacing: 12) {
                                        ForEach(section.items) { item in
                                            mediaCard(item)
                                            .contentShape(RoundedRectangle(cornerRadius: 10))
                                            .focusable(true)
                                             .disableSystemFocusEffectIfAvailable()
                                             .focused($focusedMediaId, equals: item.id)
                                            .onTapGesture {
                                                playMedia(item)
                                            }
                                         }
                                     }
                                 }
                            }
                        }
                    }
                }
            }

            if !viewModel.errorMessage.isEmpty {
                routeError(text: viewModel.errorMessage) {
                    viewModel.refreshHome()
                }
            }

            Spacer()
        }
    }

    private var searchView: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Search")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)

            HStack {
                TextField("Search title or description", text: $searchQuery)
                    .textFieldStyle(.plain)
                    .frame(width: 520)
                    .onSubmit {
                        triggerSearch()
                    }
                Button("Find") {
                    triggerSearch()
                }
                .buttonStyle(TVPrimaryButtonStyle(accent: accent))
                .disabled(!canSubmitSearch)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(viewModel.searchResults) { item in
                        mediaCard(item)
                        .contentShape(RoundedRectangle(cornerRadius: 10))
                        .focusable(true)
                         .disableSystemFocusEffectIfAvailable()
                         .focused($focusedMediaId, equals: item.id)
                        .onTapGesture {
                            playMedia(item)
                        }
                    }
                }
            }

            if !viewModel.errorMessage.isEmpty {
                routeError(text: viewModel.errorMessage) {
                    viewModel.search(query: searchQuery)
                }
            }

            Spacer()
        }
    }

    private var profileView: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Profile")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)

            if viewModel.isLoggedIn {
                Text("Email: \(viewModel.currentUserEmail)")
                    .foregroundStyle(.white)
                if let profile = viewModel.profile {
                    Text("Name: \(profile.fullName)")
                        .foregroundStyle(.white)
                    Text("Phone: \(profile.phone)")
                        .foregroundStyle(.white)
                    Text("Address: \(profile.address)")
                        .foregroundStyle(.white)
                }
                HStack(spacing: 12) {
                    Button("Refresh") {
                        viewModel.refreshProfile()
                    }
                    .buttonStyle(.bordered)
                    .tint(accent)
                    Button("Logout") {
                        viewModel.logout()
                    }
                    .buttonStyle(.bordered)
                }
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Welcome back")
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundStyle(.white)
                    Text("Sign in to sync your profile and continue playback")
                        .font(.system(size: 16))
                        .foregroundStyle(.white.opacity(0.75))

                    TextField("Email", text: $loginEmail)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14)
                        .frame(width: 520, height: 48)
                        .background(Color.white.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 10))

                    SecureField("Password", text: $loginPassword)
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14)
                        .frame(width: 520, height: 48)
                        .background(Color.white.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .onSubmit {
                            triggerLogin()
                        }

                    Button("Login") {
                        triggerLogin()
                    }
                    .buttonStyle(TVPrimaryButtonStyle(accent: accent))
                    .frame(width: 200)
                    .disabled(!canSubmitLogin)

                    if viewModel.isLoginSubmitting {
                        ProgressView("Signing in...")
                            .tint(.white)
                    }
                }
                .padding(18)
                .background(Color.white.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }

            if !viewModel.errorMessage.isEmpty {
                routeError(text: viewModel.errorMessage) {
                    if viewModel.isLoggedIn {
                        viewModel.refreshProfile()
                    } else {
                        viewModel.refreshShell(downloadsEnabled: false)
                    }
                }
            }

            Spacer()
        }
    }

    private var settingsView: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Settings")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)
            Button("Play configured fallback media") {
                revealOverlay()
                viewModel.play(using: config.playbackItem)
            }
            .buttonStyle(TVPrimaryButtonStyle(accent: accent))

            Text("Use Menu on Apple TV remote to exit full-screen playback.")
                .font(.system(size: 18))
                .foregroundStyle(.white.opacity(0.7))
            Spacer()
        }
    }

    @ViewBuilder
    private var overlayStatus: some View {
        switch viewModel.phase {
        case .idle:
            EmptyView()
        case .buffering:
            statusPill(text: viewModel.statusText)
        case .playing:
            EmptyView()
        case .failed(let message):
            statusPill(text: message, isError: true)
        }
    }

    private func statusPill(text: String, isError: Bool = false) -> some View {
        VStack {
            HStack {
                Spacer()
                Text(text)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
                    .background((isError ? Color.red : Color.black).opacity(0.8))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundStyle(.white)
            }
            Spacer()
        }
        .padding(24)
    }

    private func routeError(text: String, onRetry: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            Text(text)
                .font(.system(size: 18))
                .foregroundStyle(.red)
            Button("Retry") {
                onRetry()
            }
            .buttonStyle(.bordered)
            .disabled(viewModel.isLoading)
        }
    }

    private var loadingIndicator: some View {
        VStack {
            HStack {
                Spacer()
                ProgressView()
                    .padding(10)
                    .background(controlsBarBg)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            Spacer()
        }
        .padding(24)
    }


    private func playMedia(_ item: VolumeStreamTVViewModel.MediaItem) {
        revealOverlay()
        viewModel.play(mediaItem: item)
    }

    private func mediaCard(_ item: VolumeStreamTVViewModel.MediaItem) -> some View {
        let focused = focusedMediaId == item.id
        return VStack(alignment: .leading, spacing: 8) {
            ArtworkImageView(
                urlString: item.artworkUrl,
                placeholderColor: tvCardBg
            )
            .frame(width: 280, height: 160)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            Text(item.title)
                .foregroundStyle(.white)
                .font(.system(size: 18, weight: focused ? .bold : .regular))
                .lineLimit(1)
        }
        .scaleEffect(focused ? 1.06 : 1.0)
        .shadow(color: focused ? accent.opacity(0.7) : .clear, radius: 12)
        .animation(.easeInOut(duration: 0.12), value: focused)
    }

    private var playbackOverlay: some View {
        ZStack {
            VStack(spacing: 0) {
                topScrim
                    .frame(height: 220)
                Spacer(minLength: 0)
                bottomScrim
                    .frame(height: 360)
            }
            .ignoresSafeArea()

            VStack {
                HStack {
                    overlayChip("Back", selected: false, isFocused: focusedOverlayControl == .back) {
                        viewModel.closePlayer()
                    }
                    .focused($focusedOverlayControl, equals: .back)

                    Spacer()

                    HStack(spacing: 10) {
                        overlayChip(isZoomed ? "FILL" : "FIT", selected: isZoomed, isFocused: focusedOverlayControl == .zoom) {
                            isZoomed.toggle()
                        }
                        .focused($focusedOverlayControl, equals: .zoom)

                        overlayChip(selectedQualityLabel, selected: showQualityPanel, isFocused: focusedOverlayControl == .quality) {
                            showQualityPanel.toggle()
                        }
                        .focused($focusedOverlayControl, equals: .quality)
                    }
                }
                .padding(.horizontal, 34)
                .padding(.top, 22)

                Spacer()

                VStack(alignment: .leading, spacing: 12) {
                    if !viewModel.nowPlayingTitle.isEmpty {
                        Text(viewModel.nowPlayingTitle)
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }

                    HStack(spacing: 12) {
                        overlayChip("-\(seekButtonLabelSeconds)s", selected: false, isFocused: focusedOverlayControl == .rewind) {
                            viewModel.seek(by: -config.playbackSeekStepSeconds)
                        }
                        .focused($focusedOverlayControl, equals: .rewind)

                        overlayChip(viewModel.isPlaying ? "Pause" : "Play", selected: viewModel.isPlaying, isFocused: focusedOverlayControl == .playPause) {
                            viewModel.togglePlayPause()
                        }
                        .focused($focusedOverlayControl, equals: .playPause)

                        overlayChip("+\(seekButtonLabelSeconds)s", selected: false, isFocused: focusedOverlayControl == .forward) {
                            viewModel.seek(by: config.playbackSeekStepSeconds)
                        }
                        .focused($focusedOverlayControl, equals: .forward)
                    }
                    .frame(maxWidth: .infinity, alignment: .center)

                    ProgressView(value: seekProgress)
                        .tint(accent)
                        .scaleEffect(x: 1, y: 1.12, anchor: .center)

                    HStack {
                        Text(formatTime(viewModel.currentTimeSeconds))
                            .foregroundStyle(.white)
                        Spacer()
                        Text(formatTime(viewModel.durationSeconds))
                            .foregroundStyle(carouselLabel)
                    }
                    .font(.system(size: 14, weight: .semibold))

                    if !overlayTracks.isEmpty {
                        Text("TRACKS")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(carouselLabel)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(overlayTracks) { item in
                                    trackOverlayCard(item)
                                    .contentShape(RoundedRectangle(cornerRadius: 10))
                                    .focusable(true)
                                     .focused($focusedOverlayControl, equals: .track(item.id))
                                     .disableSystemFocusEffectIfAvailable()
                                    .onTapGesture {
                                        playMedia(item)
                                    }
                                }
                            }
                            .padding(.horizontal, 2)
                        }
                    }

                    if !viewModel.playbackErrorMessage.isEmpty {
                        Text(viewModel.playbackErrorMessage)
                            .foregroundStyle(.red)
                            .font(.system(size: 14))
                    }
                }
                .padding(18)
                .frame(maxWidth: .infinity)
                .background(controlsBarBg)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .padding(.horizontal, 30)
                .padding(.bottom, 26)
            }

            if showQualityPanel {
                VStack {
                    Spacer()
                    qualityPanel
                        .padding(.bottom, 260)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private var qualityPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("QUALITY")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(accent)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(qualityPresets) { preset in
                        overlayChip(
                            preset.label,
                            selected: preset.id == selectedQualityId,
                            isFocused: focusedOverlayControl == .qualityOption(preset.id)
                        ) {
                            applyQualityPreset(preset)
                            showQualityPanel = false
                        }
                        .focused($focusedOverlayControl, equals: .qualityOption(preset.id))
                    }
                }
            }
        }
        .padding(14)
        .background(chipBg)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .padding(.horizontal, 34)
    }

    private func overlayChip(_ title: String, selected: Bool, isFocused: Bool, action: @escaping () -> Void) -> some View {
        let active = selected || isFocused
        return Button(title) {
            revealOverlay(autoHideDelay: 4)
            action()
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            isFocused ? focusedChipBg : chipBg
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .inset(by: isFocused ? 1.0 : 0.6)
                .stroke(
                    isFocused ? accent.opacity(0.92) : (selected ? accent.opacity(0.85) : neutralStroke),
                    lineWidth: isFocused ? 1.8 : (selected ? 1.5 : 1.2)
                )
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .foregroundStyle(active ? accent : .white)
        .font(.system(size: 24, weight: .semibold))
        .animation(.easeInOut(duration: 0.08), value: isFocused)
    }

    private var selectedQualityLabel: String {
        qualityPresets.first(where: { $0.id == selectedQualityId })?.label ?? "HD"
    }

    private var overlayTracks: [VolumeStreamTVViewModel.MediaItem] {
        var seen = Set<String>()
        return viewModel.homeSections
            .flatMap(\.items)
            .filter { seen.insert($0.id).inserted }
            .prefix(14)
            .map { $0 }
    }

    private var overlayTrackFocusSignature: String {
        overlayTracks.map(\.id).joined(separator: "|")
    }

    private func trackOverlayCard(_ item: VolumeStreamTVViewModel.MediaItem) -> some View {
        let focused = focusedOverlayControl == .track(item.id)
        let selected = viewModel.currentMediaId == item.id
        let active = focused || selected
        let borderColor: Color = selected
            ? accent
            : (focused ? accent.opacity(0.80) : carouselIdleBorder)
        let cardBackground: Color = selected
            ? Color.black.opacity(0.36)
            : (focused ? focusedChipBg : tvCardBg)

        return VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottomLeading) {
                ArtworkImageView(
                    urlString: item.artworkUrl,
                    placeholderColor: Color(red: 26 / 255.0, green: 26 / 255.0, blue: 26 / 255.0)
                )
                .frame(width: 130, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                if selected {
                    Text("PLAYING")
                        .font(.system(size: 8, weight: .bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .foregroundStyle(.black)
                        .padding(4)
                }
            }

            Text(item.title)
                .lineLimit(2)
                .font(.system(size: 11, weight: selected ? .bold : .medium))
                .foregroundStyle(active ? accent : .white)

            if item.durationMs > 0 {
                Text(formatMillis(item.durationMs))
                    .font(.system(size: 10, weight: .regular))
                    .foregroundStyle(carouselLabel)
            }
        }
        .frame(width: 130, alignment: .leading)
        .padding(8)
        .background(cardBackground)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(borderColor, lineWidth: (selected || focused) ? 2 : 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .disableSystemFocusEffectIfAvailable()
        .animation(.easeInOut(duration: 0.08), value: focused)
    }

    private func revealOverlay(autoHideDelay: UInt64? = nil) {
        overlayVisible = true
        if viewModel.isPlayerVisible {
            if focusedOverlayControl == nil {
                focusedOverlayControl = showQualityPanel ? .qualityOption(selectedQualityId) : .playPause
            }
            normalizeOverlayFocus()
        }
        scheduleOverlayAutoHide(afterSeconds: autoHideDelay)
    }

    private func touchOverlay(autoHideDelay: UInt64? = nil) {
        overlayVisible = true
        scheduleOverlayAutoHide(afterSeconds: autoHideDelay)
    }

    private func handleOverlayMove(_ direction: MoveCommandDirection) {
        switch direction {
        case .left:
            moveOverlayFocusLeft()
        case .right:
            moveOverlayFocusRight()
        case .up:
            moveOverlayFocusUp()
        case .down:
            moveOverlayFocusDown()
        @unknown default:
            break
        }
    }

    private func moveOverlayFocusLeft() {
        let current = focusedOverlayControl ?? .playPause
        if lane(of: current) == .top {
            focusedOverlayControl = shiftedControl(in: topLaneControls, current: current, delta: -1)
            return
        }
        moveInCurrentLane(delta: -1)
    }

    private func moveOverlayFocusRight() {
        let current = focusedOverlayControl ?? .playPause
        if lane(of: current) == .top {
            focusedOverlayControl = shiftedControl(in: topLaneControls, current: current, delta: 1)
            return
        }
        moveInCurrentLane(delta: 1)
    }

    private func moveOverlayFocusUp() {
        let current = focusedOverlayControl ?? .playPause
        switch lane(of: current) {
        case .qualityPanel:
            showQualityPanel = false
            focusedOverlayControl = .quality
        case .tracks:
            focusedOverlayControl = .playPause
        case .transport:
            // Keep Back reachable in one move from the primary transport control.
            if current == .playPause {
                focusedOverlayControl = .back
            } else if current == .rewind {
                focusedOverlayControl = .zoom
            } else {
                focusedOverlayControl = .quality
            }
        case .top, .unknown:
            break
        }
    }

    private func moveOverlayFocusDown() {
        let current = focusedOverlayControl ?? .playPause
        switch lane(of: current) {
        case .qualityPanel:
            break
        case .top:
            if current == .back || current == .zoom {
                focusedOverlayControl = .rewind
            } else {
                focusedOverlayControl = .playPause
            }
        case .transport:
            focusedOverlayControl = preferredTrackFocusControl() ?? current
        case .tracks, .unknown:
            break
        }
    }

    private func moveInCurrentLane(delta: Int) {
        let current = focusedOverlayControl ?? .playPause
        switch lane(of: current) {
        case .top:
            focusedOverlayControl = shiftedControl(in: topLaneControls, current: current, delta: delta)
        case .transport:
            focusedOverlayControl = shiftedControl(in: transportLaneControls, current: current, delta: delta)
        case .tracks:
            moveFocusInTracks(delta: delta)
        case .qualityPanel:
            moveFocusInQuality(delta: delta)
        case .unknown:
            focusedOverlayControl = .rewind
        }
    }

    private var topLaneControls: [OverlayControl] {
        [.back, .zoom, .quality]
    }

    private var transportLaneControls: [OverlayControl] {
        [.rewind, .playPause, .forward]
    }

    private func lane(of control: OverlayControl) -> OverlayLane {
        switch control {
        case .back, .zoom, .quality:
            return .top
        case .rewind, .playPause, .forward:
            return .transport
        case .track:
            return .tracks
        case .qualityOption:
            return .qualityPanel
        }
    }

    private func moveFocusInQuality(delta: Int) {
        let controls = qualityPresets.map { OverlayControl.qualityOption($0.id) }
        guard !controls.isEmpty else { return }
        let current = focusedOverlayControl ?? .qualityOption(selectedQualityId)
        focusedOverlayControl = shiftedControl(in: controls, current: current, delta: delta)
    }

    private func moveFocusInTracks(delta: Int) {
        let controls = overlayTracks.map { OverlayControl.track($0.id) }
        guard !controls.isEmpty else { return }
        let current = focusedOverlayControl ?? preferredTrackFocusControl() ?? controls[0]
        focusedOverlayControl = shiftedControl(in: controls, current: current, delta: delta)
    }

    private func shiftedControl(in controls: [OverlayControl], current: OverlayControl, delta: Int) -> OverlayControl {
        guard let index = controls.firstIndex(of: current) else {
            return controls[0]
        }
        let bounded = max(0, min(controls.count - 1, index + delta))
        return controls[bounded]
    }

    private func preferredTrackFocusControl() -> OverlayControl? {
        if overlayTracks.isEmpty {
            return nil
        }
        if overlayTracks.contains(where: { $0.id == viewModel.currentMediaId }) {
            return .track(viewModel.currentMediaId)
        }
        return .track(overlayTracks[0].id)
    }

    private func hideOverlay() {
        overlayVisible = false
        focusedOverlayControl = nil
        overlayHideTask?.cancel()
        overlayHideTask = nil
    }

    private func normalizeOverlayFocus() {
        guard viewModel.isPlayerVisible, overlayVisible else {
            focusedOverlayControl = nil
            return
        }

        if showQualityPanel {
            if case .qualityOption(let id) = focusedOverlayControl,
               qualityPresets.contains(where: { $0.id == id }) {
                return
            }
            focusedOverlayControl = .qualityOption(selectedQualityId)
            return
        }

        if case .qualityOption = focusedOverlayControl {
            focusedOverlayControl = .quality
            return
        }

        if case .track(let id) = focusedOverlayControl,
           !overlayTracks.contains(where: { $0.id == id }) {
            focusedOverlayControl = preferredTrackFocusControl() ?? .playPause
            return
        }

        if focusedOverlayControl == nil {
            focusedOverlayControl = .playPause
        }
    }

    private func scheduleOverlayAutoHide(afterSeconds: UInt64? = nil) {
        overlayHideTask?.cancel()
        guard viewModel.isPlayerVisible, viewModel.isPlaying, !showQualityPanel else { return }
        let delay = afterSeconds ?? config.playbackOverlayAutoHideSeconds
        overlayHideTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: delay * 1_000_000_000)
            if !Task.isCancelled && viewModel.isPlayerVisible && viewModel.isPlaying && !showQualityPanel {
                overlayVisible = false
                focusedOverlayControl = nil
            }
        }
    }

    private func applyQualityPreset(_ preset: QualityPreset) {
        selectedQualityId = preset.id
        viewModel.setPreferredPeakBitRate(preset.peakBitRate)
        revealOverlay(autoHideDelay: 4)
    }

    private func formatMillis(_ millis: Int64) -> String {
        formatTime(Double(millis) / 1000.0)
    }

    private func formatTime(_ totalSeconds: Double) -> String {
        guard totalSeconds.isFinite, totalSeconds >= 0 else { return "00:00" }
        let seconds = Int(totalSeconds)
        let h = seconds / 3600
        let m = (seconds % 3600) / 60
        let s = seconds % 66
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }

    private func currentSeekStep() -> Double {
        if !viewModel.isPlaying {
            return min(config.playbackSeekStepSeconds, 5.0)
        }

        let now = Date()
        defer {
            lastSeekCommandAt = now
        }

        if let previous = lastSeekCommandAt, now.timeIntervalSince(previous) < 0.8 {
            consecutiveSeekCount += 1
        } else {
            consecutiveSeekCount = 1
        }

        return consecutiveSeekCount >= 3
            ? config.playbackSeekFastStepSeconds
            : config.playbackSeekStepSeconds
    }

    private func resetSeekAcceleration() {
        lastSeekCommandAt = nil
        consecutiveSeekCount = 0
    }

    private func selectRoute(_ route: String) {
        let normalizedRoute = isDownloadsRoute(route) ? RouteKey.home : route
        guard selectedRoute != normalizedRoute else { return }
        selectedRoute = normalizedRoute
    }

    private func handleRouteChange(_ route: String) {
        viewModel.clearErrorMessage()
        focusedMediaId = nil
        if route != RouteKey.search {
            searchQuery = ""
            viewModel.resetSearchState()
        }
    }

    private func triggerSearch() {
        guard canSubmitSearch else { return }
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        viewModel.cancelPendingSearch()
        viewModel.search(query: query)
    }

    private func triggerLogin() {
        guard canSubmitLogin else { return }
        let email = loginEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        viewModel.login(email: email, password: loginPassword) { success in
            if success {
                loginPassword = ""
            }
        }
    }

    private var seekButtonLabelSeconds: Int {
        max(1, Int(config.playbackSeekStepSeconds.rounded()))
    }
}

private struct TVPrimaryButtonStyle: ButtonStyle {
    let accent: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(.black)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(accent.opacity(configuration.isPressed ? 0.82 : 1.0))
            )
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeInOut(duration: 0.08), value: configuration.isPressed)
    }
}

private struct PlayerHostView: UIViewControllerRepresentable {
    let player: AVPlayer
    let videoGravity: AVLayerVideoGravity

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = false
        controller.videoGravity = videoGravity
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        if uiViewController.player !== player {
            uiViewController.player = player
        }
        if uiViewController.videoGravity != videoGravity {
            uiViewController.videoGravity = videoGravity
        }
    }
}

private extension View {
    @ViewBuilder
    func disableSystemFocusEffectIfAvailable() -> some View {
        if #available(tvOS 17.0, *) {
            self.focusEffectDisabled()
        } else {
            self
        }
    }
}

private struct ArtworkImageView: View {
    let urlString: String
    let placeholderColor: Color

    @StateObject private var loader = LocalTLSArtworkLoader()

    var body: some View {
        ZStack {
            // Keep a stable dark base so transparent artwork never reveals white.
            Rectangle().fill(placeholderColor)

            if let image = loader.image {
                // Preserve intrinsic image ratio and avoid stretch/crop artifacts.
                 Image(uiImage: image)
                     .resizable()
                     .aspectRatio(image.size, contentMode: .fill)
                     .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                     .scaleEffect(1.06, anchor: .top)
                     .clipped()
             } else {
                Image(systemName: "film")
                    .foregroundStyle(.white.opacity(0.8))
             }
         }
         .background(placeholderColor)
         .clipped()
         .onAppear {
             loader.load(urlString: urlString)
         }
         .onChange(of: urlString) { value in
             loader.load(urlString: value)
         }
     }
 }

@MainActor
private final class LocalTLSArtworkLoader: NSObject, ObservableObject, URLSessionDelegate {
    @Published var image: UIImage?

    private var currentURL: URL?
    private var task: URLSessionDataTask?
    private lazy var session = URLSession(
        configuration: .ephemeral,
        delegate: self,
        delegateQueue: nil
    )

    func load(urlString: String) {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), !trimmed.isEmpty else {
            image = nil
            currentURL = nil
            task?.cancel()
            task = nil
            return
        }
        guard currentURL != url else { return }

        currentURL = url
        image = nil
        task?.cancel()
        task = session.dataTask(with: url) { [weak self] data, _, _ in
            guard let self else { return }
            guard let data, let decoded = UIImage(data: data) else { return }
            Task { @MainActor in
                if self.currentURL == url {
                    self.image = decoded
                }
            }
        }
        task?.resume()
    }

    nonisolated func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        let host = challenge.protectionSpace.host.lowercased()
        if host == "localhost" || host == "127.0.0.1" || host == "::1" {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}

