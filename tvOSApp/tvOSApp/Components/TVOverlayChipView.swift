import SwiftUI

struct TVOverlayChipView: View {
    let title: String
    let selected: Bool
    let isFocused: Bool
    let accent: Color
    let focusedChipBackground: Color
    let chipBackground: Color
    let neutralStroke: Color
    let onPress: () -> Void

    var body: some View {
        Button(title) {
            onPress()
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(isFocused ? focusedChipBackground : chipBackground)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .inset(by: isFocused ? 1.0 : 0.6)
                .stroke(
                    isFocused ? accent.opacity(0.92) : (selected ? accent.opacity(0.85) : neutralStroke),
                    lineWidth: isFocused ? 1.8 : (selected ? 1.5 : 1.2)
                )
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .foregroundStyle(.white)
        .font(.system(size: 24, weight: .semibold))
        .animation(.easeInOut(duration: 0.08), value: isFocused)
    }
}

