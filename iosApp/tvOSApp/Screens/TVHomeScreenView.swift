import SwiftUI
import VolumeStreamShared

struct TVHomeScreenView: View {
    let accent: Color
    let cardPlaceholderColor: Color
    let homeSections: [VolumeStreamTVViewModel.MediaSection]
    let errorMessage: String
    let isLoading: Bool
    let focusedMediaId: FocusState<String?>.Binding
    let onReload: () -> Void
    let onPlay: (VolumeStreamTVViewModel.MediaItem) -> Void
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Home")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)
            Text("Continue watching and featured content")
                .font(.system(size: 24, weight: .regular))
                .foregroundStyle(.white.opacity(0.8))

            if homeSections.isEmpty {
                Button("Reload Home") {
                    onReload()
                }
                .buttonStyle(TVPrimaryButtonStyle(accent: accent))
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(homeSections) { section in
                            VStack(alignment: .leading, spacing: 10) {
                                Text(section.title)
                                    .font(.title3)
                                    .foregroundStyle(.white)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: TVCardLayout.horizontalSpacing) {
                                        ForEach(section.items) { item in
                                            TVMediaCardView(
                                                item: item,
                                                isFocused: focusedMediaId.wrappedValue == item.id,
                                                accent: accent,
                                                placeholderColor: cardPlaceholderColor
                                            )
                                            .frame(width: TVCardLayout.mediaWidth, height: TVCardLayout.totalHeight, alignment: .topLeading)
                                            .contentShape(RoundedRectangle(cornerRadius: TVCardLayout.cornerRadius))
                                            .focusable(true)
                                            .disableSystemFocusEffectIfAvailable()
                                            .focused(focusedMediaId, equals: item.id)
                                            .onTapGesture {
                                                onPlay(item)
                                            }
                                        }
                                    }
                                    .padding(.vertical, 6)
                                }
                                .frame(height: TVCardLayout.totalHeight + 12)
                            }
                        }
                    }
                }
            }

            if !errorMessage.isEmpty {
                TVRouteErrorView(text: errorMessage, isLoading: isLoading, onRetry: onRetry)
            }

            Spacer()
        }
    }
}

