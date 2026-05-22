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

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Search")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)

            HStack {
                TextField("Search title or description", text: $searchQuery)
                    .textFieldStyle(.plain)
                    .frame(width: 520)
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
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(searchResults) { item in
                        TVMediaCardView(
                            item: item,
                            isFocused: focusedMediaId.wrappedValue == item.id,
                            accent: accent,
                            placeholderColor: cardPlaceholderColor
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 10))
                        .focusable(true)
                        .disableSystemFocusEffectIfAvailable()
                        .focused(focusedMediaId, equals: item.id)
                        .onTapGesture {
                            onPlay(item)
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

