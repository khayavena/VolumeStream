import SwiftUI
import VolumeStreamShared


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
    private let carouselLabel = Color(red: 116 / 255.0, green: 186 / 255.0, blue: 152 / 255.0) // #74BA98
    private let carouselIdleBorder = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.28) // #4700E676
    private let neutralStroke = Color(red: 0 / 255.0, green: 230 / 255.0, blue: 118 / 255.0, opacity: 0.24)
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
    @State private var navHasFocus = false
    @State private var navCollapseTask: Task<Void, Never>?
    @State private var lastFocusedContentByRoute: [String: String] = [:]
    @FocusState private var focusedRoute: String?
    @FocusState private var focusedMediaId: String?
    @FocusState private var focusedOverlayControl: OverlayControl?

    private let qualityPresets: [QualityPreset] = [
        .init(id: "auto", label: "HD", peakBitRate: 0),
        .init(id: "1080p", label: "1080p", peakBitRate: 6_500_000),
        .init(id: "720p", label: "720p", peakBitRate: 3_500_000),
        .init(id: "480p", label: "480p", peakBitRate: 1_500_000)
    ]

    private var navExpanded: Bool { navHasFocus }
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
                .onMoveCommand { direction in
                    handleShellMove(direction)
                }

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
        .onChange(of: focusedRoute) { route in
            updateNavFocusState(route)
        }
        .onChange(of: focusedMediaId) { mediaId in
            guard let mediaId else { return }
            lastFocusedContentByRoute[selectedRoute] = mediaId
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
            navCollapseTask?.cancel()
            navCollapseTask = nil
        }
    }

    private var sideNav: some View {
        TVSideNavView(
            items: visibleNavItems,
            selectedRoute: selectedRoute,
            navExpanded: navExpanded,
            focusedRoute: $focusedRoute,
            accent: accent,
            carouselLabel: carouselLabel,
            selectedNavBg: selectedNavBg,
            focusedNavBg: focusedNavBg,
            sideNavBg: sideNavBg,
            sideNavExpandedBg: sideNavExpandedBg,
            navRailCollapsed: navRailCollapsed,
            navRailExpanded: navRailExpanded,
            onSelect: { route in
                selectRoute(route)
            }
        )
        .focusSection()
     }

     private var mainContent: some View {
         Group {
             switch selectedRoute {
             case RouteKey.home:
                 TVHomeScreenView(
                     accent: accent,
                     cardPlaceholderColor: tvCardBg,
                     homeSections: viewModel.homeSections,
                     errorMessage: viewModel.errorMessage,
                     isLoading: viewModel.isLoading,
                     focusedMediaId: $focusedMediaId,
                     onReload: {
                         viewModel.refreshHome()
                     },
                     onPlay: { item in
                         playMedia(item)
                     },
                     onRetry: {
                         viewModel.refreshHome()
                     }
                 )
             case RouteKey.search:
                 TVSearchScreenView(
                     accent: accent,
                     cardPlaceholderColor: tvCardBg,
                     searchQuery: $searchQuery,
                     canSubmitSearch: canSubmitSearch,
                     searchResults: viewModel.searchResults,
                     focusedMediaId: $focusedMediaId,
                     errorMessage: viewModel.errorMessage,
                     isLoading: viewModel.isLoading,
                     onSubmitSearch: {
                         triggerSearch()
                     },
                     onPlay: { item in
                         playMedia(item)
                     },
                     onRetry: {
                         viewModel.search(query: searchQuery)
                     }
                 )
             case RouteKey.profile:
                 TVProfileScreenView(
                     accent: accent,
                     isLoggedIn: viewModel.isLoggedIn,
                     currentUserEmail: viewModel.currentUserEmail,
                     profile: viewModel.profile,
                     loginEmail: $loginEmail,
                     loginPassword: $loginPassword,
                     canSubmitLogin: canSubmitLogin,
                     isLoginSubmitting: viewModel.isLoginSubmitting,
                     errorMessage: viewModel.errorMessage,
                     isLoading: viewModel.isLoading,
                     onRefreshProfile: {
                         viewModel.refreshProfile()
                     },
                     onLogout: {
                         viewModel.logout()
                     },
                     onLogin: {
                         triggerLogin()
                     },
                     onRetry: {
                         if viewModel.isLoggedIn {
                             viewModel.refreshProfile()
                         } else {
                             viewModel.refreshShell(downloadsEnabled: false)
                         }
                     }
                 )
             case RouteKey.settings:
                 TVSettingsScreenView(
                     accent: accent,
                     onPlayFallback: {
                         revealOverlay()
                         viewModel.play(using: config.playbackItem)
                     }
                 )
             default:
                 TVHomeScreenView(
                     accent: accent,
                     cardPlaceholderColor: tvCardBg,
                     homeSections: viewModel.homeSections,
                     errorMessage: viewModel.errorMessage,
                     isLoading: viewModel.isLoading,
                     focusedMediaId: $focusedMediaId,
                     onReload: {
                         viewModel.refreshHome()
                     },
                     onPlay: { item in
                         playMedia(item)
                     },
                     onRetry: {
                         viewModel.refreshHome()
                     }
                 )
             }
         }
         .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
         .padding(32)
          .focusSection()
     }

      @ViewBuilder
      private var overlayStatus: some View {
          switch viewModel.phase {
          case .idle:
              EmptyView()
          case .buffering:
              TVStatusPillView(text: viewModel.statusText, isError: false)
          case .playing:
              EmptyView()
          case .failed(let message):
              TVStatusPillView(text: message, isError: true)
          }
      }

      private func routeError(text: String, onRetry: @escaping () -> Void) -> some View {
          TVRouteErrorView(text: text, isLoading: viewModel.isLoading, onRetry: onRetry)
      }

      private var loadingIndicator: some View {
          TVLoadingIndicatorView(background: controlsBarBg)
      }


      private func playMedia(_ item: VolumeStreamTVViewModel.MediaItem) {
          revealOverlay()
          viewModel.play(mediaItem: item)
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
                      TVOverlayChipView(title: "Back", selected: false, isFocused: focusedOverlayControl == .back, accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                          revealOverlay(autoHideDelay: 4)
                          viewModel.closePlayer()
                      }
                      .focused($focusedOverlayControl, equals: .back)

                      Spacer()

                      HStack(spacing: 10) {
                          TVOverlayChipView(title: isZoomed ? "FILL" : "FIT", selected: isZoomed, isFocused: focusedOverlayControl == .zoom, accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                              revealOverlay(autoHideDelay: 4)
                              isZoomed.toggle()
                          }
                          .focused($focusedOverlayControl, equals: .zoom)

                          TVOverlayChipView(title: selectedQualityLabel, selected: showQualityPanel, isFocused: focusedOverlayControl == .quality, accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                              revealOverlay(autoHideDelay: 4)
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
                          TVOverlayChipView(title: "-\(seekButtonLabelSeconds)s", selected: false, isFocused: focusedOverlayControl == .rewind, accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                              revealOverlay(autoHideDelay: 4)
                              viewModel.seek(by: -config.playbackSeekStepSeconds)
                          }
                          .focused($focusedOverlayControl, equals: .rewind)

                          TVOverlayChipView(title: viewModel.isPlaying ? "Pause" : "Play", selected: viewModel.isPlaying, isFocused: focusedOverlayControl == .playPause, accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                              revealOverlay(autoHideDelay: 4)
                              viewModel.togglePlayPause()
                          }
                          .focused($focusedOverlayControl, equals: .playPause)

                          TVOverlayChipView(title: "+\(seekButtonLabelSeconds)s", selected: false, isFocused: focusedOverlayControl == .forward, accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                              revealOverlay(autoHideDelay: 4)
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
                                      TVOverlayTrackCardView(
                                          item: item,
                                          isFocused: focusedOverlayControl == .track(item.id),
                                          isSelected: viewModel.currentMediaId == item.id,
                                          accent: accent,
                                          carouselLabel: carouselLabel,
                                          idleBorder: carouselIdleBorder,
                                          focusedChipBg: focusedChipBg,
                                          cardBackground: tvCardBg,
                                          durationText: item.durationMs > 0 ? formatMillis(item.durationMs) : nil
                                      )
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
                          TVOverlayChipView(title: preset.label, selected: preset.id == selectedQualityId, isFocused: focusedOverlayControl == .qualityOption(preset.id), accent: accent, focusedChipBackground: focusedChipBg, chipBackground: chipBg, neutralStroke: neutralStroke) {
                              revealOverlay(autoHideDelay: 4)
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
          let s = seconds % 60
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

      private var firstMediaIdForCurrentRoute: String? {
          switch selectedRoute {
          case RouteKey.home:
              return viewModel.homeSections.first?.items.first?.id
          case RouteKey.search:
              return viewModel.searchResults.first?.id
          default:
              return nil
          }
      }

      private func updateNavFocusState(_ route: String?) {
          if route != nil {
              navCollapseTask?.cancel()
              navCollapseTask = nil
              navHasFocus = true
              return
          }

          navCollapseTask?.cancel()
          navCollapseTask = Task { @MainActor in
              try? await Task.sleep(nanoseconds: 120_000_000)
              if !Task.isCancelled && focusedRoute == nil {
                  navHasFocus = false
              }
          }
      }

      private func focusPreferredContentForCurrentRoute() {
          guard selectedRoute == RouteKey.home || selectedRoute == RouteKey.search else { return }
          let target = lastFocusedContentByRoute[selectedRoute] ?? firstMediaIdForCurrentRoute
          guard let target else { return }
          focusedMediaId = target
          focusedRoute = nil
      }

      private func handleShellMove(_ direction: MoveCommandDirection) {
          guard !viewModel.isPlayerVisible else { return }

          switch direction {
          case .left:
              // Only hand off to nav when content focus is already at its leading edge.
              if focusedMediaId != nil && isAtLeadingContentBoundary() {
                  focusedMediaId = nil
                  focusedRoute = selectedRoute
              }
          case .right:
              if focusedRoute != nil {
                  focusPreferredContentForCurrentRoute()
              }
          default:
              break
          }
      }

      private func isAtLeadingContentBoundary() -> Bool {
          guard let focusedId = focusedMediaId else {
              return false
          }

          switch selectedRoute {
          case RouteKey.home:
              guard let homePosition = homeFocusPosition(for: focusedId) else { return false }
              return homePosition.itemIndex == 0
          case RouteKey.search:
              return searchFocusIndex(for: focusedId) == 0
          default:
              return false
          }
      }

      private func searchFocusIndex(for mediaId: String) -> Int? {
          viewModel.searchResults.firstIndex(where: { $0.id == mediaId })
      }

      private func homeFocusPosition(for mediaId: String) -> (sectionIndex: Int, itemIndex: Int)? {
          for (sectionIndex, section) in viewModel.homeSections.enumerated() {
              if let itemIndex = section.items.firstIndex(where: { $0.id == mediaId }) {
                  return (sectionIndex, itemIndex)
              }
          }
          return nil
      }

      private func selectRoute(_ route: String) {
          let normalizedRoute = isDownloadsRoute(route) ? RouteKey.home : route
          guard selectedRoute != normalizedRoute else { return }
          selectedRoute = normalizedRoute
          focusedRoute = normalizedRoute
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



