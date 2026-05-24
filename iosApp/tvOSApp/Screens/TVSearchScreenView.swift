import SwiftUI
import VolumeStreamShared

struct TVSearchScreenView: View {
    let accent: Color
    let cardPlaceholderColor: Color
    @Binding var searchQuery: String
    let canSubmitSearch: Bool
    let searchResults: [VolumeStreamTVViewModel.MediaItem]
    let focusedMediaId: FocusState<String?>.Binding
    let errorMessage: String
    let isLoading: Bool
    let onSubmitSearch: () -> Void
    let onPlay: (VolumeStreamTVViewModel.MediaItem) -> Void
    let onRetry: () -> Void

    @FocusState private var isSearchFieldFocused: Bool

    private let searchFieldBackground = Color.black.opacity(0.78)

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Search")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)

            HStack(spacing: 14) {
                TextField("Search title or description", text: $searchQuery)
                    .textFieldStyle(.plain)
                    .frame(width: 520)
                    .foregroundStyle(.white)
                    .tint(accent)
                    .focused($isSearchFieldFocused)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(searchFieldBackground)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(accent.opacity(isSearchFieldFocused ? 0.95 : 0.42), lineWidth: isSearchFieldFocused ? 2.0 : 1.2)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .onSubmit {
                        onSubmitSearch()
                    }
                Button("Find") {
                    onSubmitSearch()
                }
                .buttonStyle(TVPrimaryButtonStyle(accent: accent))
                .disabled(!canSubmitSearch)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: TVCardLayout.verticalSpacing) {
                    ForEach(searchResults) { item in
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

            if !errorMessage.isEmpty {
                TVRouteErrorView(text: errorMessage, isLoading: isLoading, onRetry: onRetry)
            }

            Spacer()
        }
    }
}

