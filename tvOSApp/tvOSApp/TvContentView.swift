import UIKit
import SwiftUI
import ComposeApp

struct TvComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        TvMainViewControllerKt.TvMainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct TvContentView: View {
    var body: some View {
        TvComposeView()
            .ignoresSafeArea(.all)
            .preferredColorScheme(.dark)
    }
}

