import SwiftUI
import UIKit
import ComposeApp
import AVFoundation

class AppDelegate: NSObject, UIApplicationDelegate {

    private func applyOrientationLock(_ application: UIApplication?) {
        let mask: UIInterfaceOrientationMask = OrientationManager.shared.forceLandscape ? .landscape : .portrait

        let scenes = application?.connectedScenes.compactMap { $0 as? UIWindowScene } ?? []
        guard let windowScene = scenes.first else { return }

        if #available(iOS 16.0, *) {
            windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { error in
                print("Geometry update error: \(error)")
            }
            let keyWindow = windowScene.windows.first(where: { $0.isKeyWindow })
            keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            UIViewController.attemptRotationToDeviceOrientation()
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

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
            self.applyOrientationLock(application)
        }

        applyOrientationLock(application)
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