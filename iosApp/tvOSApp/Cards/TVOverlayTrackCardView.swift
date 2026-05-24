import SwiftUI
import VolumeStreamShared

struct TVOverlayTrackCardView: View {
    private enum Layout {
        static let artworkWidth: CGFloat = 130
        static let artworkHeight: CGFloat = 72
    }

    let item: VolumeStreamTVViewModel.MediaItem
    let isFocused: Bool
    let isSelected: Bool
    let accent: Color
    let carouselLabel: Color
    let idleBorder: Color
    let focusedChipBg: Color
    let cardBackground: Color
    let durationText: String?

    var body: some View {
        let titleColor: Color = (isFocused || isSelected) ? accent : carouselLabel
        let borderColor: Color = isSelected ? accent : (isFocused ? accent.opacity(0.80) : idleBorder)
        let background: Color = isSelected ? Color.black.opacity(0.36) : (isFocused ? focusedChipBg : cardBackground)

        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottomLeading) {
                ArtworkImageView(
                    urlString: item.artworkUrl,
                    placeholderColor: Color(red: 26 / 255.0, green: 26 / 255.0, blue: 26 / 255.0)
                )
                .frame(width: Layout.artworkWidth)
                .frame(height: Layout.artworkHeight)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                if isSelected {
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
                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                .foregroundStyle(titleColor)

            if let durationText {
                Text(durationText)
                    .font(.system(size: 10, weight: .regular))
                    .foregroundStyle(carouselLabel)
            }
        }
        .frame(width: Layout.artworkWidth, alignment: .leading)
        .padding(8)
        .background(background)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(borderColor, lineWidth: (isSelected || isFocused) ? 2 : 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .disableSystemFocusEffectIfAvailable()
        .animation(.easeInOut(duration: 0.08), value: isFocused)
    }
}

