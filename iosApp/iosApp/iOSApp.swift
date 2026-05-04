import SwiftUI
import UIKit
import ComposeApp
import AVFoundation

class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        return OrientationManager.shared.forceLandscape ? .landscape : .portrait
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Failed to set AVAudioSession category: \(error)")
        }

        NotificationCenter.default.addObserver(
            forName: NSNotification.Name(OrientationManager.shared.LOCK_CHANGED_NOTIFICATION),
            object: nil,
            queue: .main
        ) { [weak application] _ in
            guard let windowScene = application?.connectedScenes
                .compactMap({ $0 as? UIWindowScene }).first else { return }
            if #available(iOS 16.0, *) {
                windowScene.requestGeometryUpdate(
                    .iOS(interfaceOrientations: OrientationManager.shared.forceLandscape
                        ? .landscape : .portrait)
                )
                windowScene.keyWindow?.rootViewController?
                    .setNeedsUpdateOfSupportedInterfaceOrientations()
            } else {
                UIViewController.attemptRotationToDeviceOrientation()
            }
        }
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .background:
                // App moved to background — release orientation lock
                OrientationManager.shared.forceLandscape = false
            default:
                break
            }
        }
    }
}