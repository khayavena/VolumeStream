import SwiftUI

extension View {
    @ViewBuilder
    func disableSystemFocusEffectIfAvailable() -> some View {
        if #available(tvOS 17.0, *) {
            self.focusEffectDisabled()
        } else {
            self
        }
    }
}

