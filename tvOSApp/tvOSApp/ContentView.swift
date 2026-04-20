import UIKit
import SwiftUI
import ComposeApp

// Bridges the Kotlin/Compose TV root into a SwiftUI-compatible UIViewController.
// TvMainViewController() (Kotlin) wraps VolumeStreamTvApp which uses the
// platform-agnostic TvNavigationShell composable with a persistent side nav.
struct ComposeTvView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        TvMainViewControllerKt.TvMainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// Root SwiftUI view for tvOS.
// • Fills the full screen including safe areas for an immersive experience.
// • Enforces dark colour scheme to match the black / green design.
struct ContentView: View {
    var body: some View {
        ComposeTvView()
            .ignoresSafeArea(.all)
            .preferredColorScheme(.dark)
    }
}

