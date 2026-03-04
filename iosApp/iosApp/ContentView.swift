import UIKit
import SwiftUI
import ComposeApp

// Bridges the Kotlin/Compose root into a SwiftUI-compatible UIViewController
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// Root SwiftUI view
// • Fills the full screen including safe areas (video player)
// • Forces dark colour scheme to match the black/green design
// • Hides the status bar for an immersive experience
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)           // full-screen video surface
            .preferredColorScheme(.dark)     // always dark to match black/green theme
            .statusBarHidden(true)           // immersive — no status bar over video
    }
}



