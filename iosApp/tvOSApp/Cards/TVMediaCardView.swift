import SwiftUI
import VolumeStreamShared

struct TVMediaCardView: View {
    let item: VolumeStreamTVViewModel.MediaItem
    let isFocused: Bool
    let accent: Color
    let placeholderColor: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ArtworkImageView(
                urlString: item.artworkUrl,
                placeholderColor: placeholderColor
            )
            .frame(width: 280, height: 160)
            .clipShape(RoundedRectangle(cornerRadius: 10))

            Text(item.title)
                .foregroundStyle(.white)
                .font(.system(size: 18, weight: isFocused ? .bold : .regular))
                .lineLimit(1)
        }
        .scaleEffect(isFocused ? 1.06 : 1.0)
        .shadow(color: isFocused ? accent.opacity(0.7) : .clear, radius: 12)
        .animation(.easeInOut(duration: 0.12), value: isFocused)
    }
}

