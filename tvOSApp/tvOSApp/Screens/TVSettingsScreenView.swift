import SwiftUI
import VolumeStreamShared

struct TVSettingsScreenView: View {
    let accent: Color
    let onPlayFallback: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Settings")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.white)

            Button("Play configured fallback media") {
                onPlayFallback()
            }
            .buttonStyle(TVPrimaryButtonStyle(accent: accent))

            Text("Use Menu on the tvOS remote to exit full-screen playback.")
                .font(.system(size: 18))
                .foregroundStyle(.white.opacity(0.7))

            Spacer()
        }
    }
}

