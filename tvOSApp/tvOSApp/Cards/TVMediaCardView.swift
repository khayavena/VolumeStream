import SwiftUI
import VolumeStreamShared

enum TVCardLayout {
    static let mediaWidth: CGFloat = 320
    static let mediaHeight: CGFloat = 180
    static let cornerRadius: CGFloat = 12
    static let titleScrimHeight: CGFloat = 74
    static let horizontalSpacing: CGFloat = 16
    static let verticalSpacing: CGFloat = 14
    static let totalHeight: CGFloat = mediaHeight
}

struct TVMediaCardView: View {
    let item: VolumeStreamTVViewModel.MediaItem
    let isFocused: Bool
    let accent: Color
    let placeholderColor: Color

    private let titleScrim = LinearGradient(
        colors: [Color.clear, Color.black.opacity(0.78)],
        startPoint: .top,
        endPoint: .bottom
    )

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            ArtworkImageView(
                urlString: item.artworkUrl,
                placeholderColor: placeholderColor
            )
            .frame(width: TVCardLayout.mediaWidth, height: TVCardLayout.totalHeight)

            titleScrim
                .frame(height: TVCardLayout.titleScrimHeight)

            Text(item.title)
                .foregroundStyle(isFocused ? accent : accent.opacity(0.82))
                .font(.system(size: 18, weight: isFocused ? .bold : .regular))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
        }
        .frame(width: TVCardLayout.mediaWidth, height: TVCardLayout.totalHeight, alignment: .bottomLeading)
        .clipShape(RoundedRectangle(cornerRadius: TVCardLayout.cornerRadius))

        .scaleEffect(isFocused ? 1.06 : 1.0)
        .shadow(color: isFocused ? accent.opacity(0.7) : .clear, radius: 12)
        .animation(.easeInOut(duration: 0.12), value: isFocused)
    }
}

