import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Koin starts once per process, before any scene or view controller exists.
        KoinIosKt.doInitKoin()
        ScenePrivacy.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Keeps the vault private across scene changes. Each scene installs an opaque cover before the system takes its
/// app-switcher snapshot; backgrounding any scene locks the one shared vault.
final class ScenePrivacy {
    static let shared = ScenePrivacy()

    private let coordinator = KoinIosKt.vaultLifecycleCoordinator()
    private var covers: [String: UIWindow] = [:]
    private var observers: [NSObjectProtocol] = []

    func start() {
        let center = NotificationCenter.default
        observers = [
            center.addObserver(forName: UIScene.willDeactivateNotification, object: nil, queue: .main) { [weak self] note in
                self?.willDeactivate(note.object as? UIWindowScene)
            },
            center.addObserver(forName: UIScene.didActivateNotification, object: nil, queue: .main) { [weak self] note in
                self?.didActivate(note.object as? UIWindowScene)
            },
            center.addObserver(forName: UIScene.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
                self?.didEnterBackground()
            },
            center.addObserver(forName: UIApplication.protectedDataWillBecomeUnavailableNotification, object: nil, queue: .main) { [weak self] _ in
                self?.protectedDataWillBecomeUnavailable()
            },
        ]
    }

    /// Synchronous and nonanimated: the cover is in place before UIKit captures the snapshot.
    private func willDeactivate(_ scene: UIWindowScene?) {
        guard let scene = scene else { return }
        let identifier = scene.session.persistentIdentifier
        coordinator.obscure(surface: identifier)
        guard covers[identifier] == nil else { return }
        let cover = UIWindow(windowScene: scene)
        cover.windowLevel = .alert + 1
        let controller = UIViewController()
        controller.view.backgroundColor = UIColor { trait in
            trait.userInterfaceStyle == .dark
                ? UIColor(red: 0.098, green: 0.118, blue: 0.094, alpha: 1)
                : UIColor(red: 0.961, green: 0.953, blue: 0.922, alpha: 1)
        }
        cover.rootViewController = controller
        cover.isHidden = false
        covers[identifier] = cover
    }

    /// Lifts the cover only when the coordinator says the vault is not obscured or locking, and only after the
    /// underlying scene has laid out and committed a frame.
    private func didActivate(_ scene: UIWindowScene?) {
        guard let scene = scene else { return }
        let pending = coordinator.reveal(surface: scene.session.persistentIdentifier)
        if let pending = pending {
            pending.invokeOnCompletion(handler: { _ in
                DispatchQueue.main.async { self.removeCoversIfRevealed() }
            })
        } else {
            DispatchQueue.main.async { self.removeCoversIfRevealed() }
        }
    }

    private func removeCoversIfRevealed() {
        guard !coordinator.isCovered else { return }
        CATransaction.begin()
        CATransaction.setCompletionBlock { [weak self] in
            guard let self = self, !self.coordinator.isCovered else { return }
            self.covers.values.forEach { $0.isHidden = true }
            self.covers.removeAll()
        }
        CATransaction.commit()
    }

    /// The lock runs under a bounded background task. Expiration releases the task; the lock keeps running.
    private func didEnterBackground() {
        let task = BackgroundTask()
        task.begin(name: "daftar.vault-lock")
        coordinator.enteredBackground().invokeOnCompletion(handler: { _ in
            task.end()
        })
    }

    private func protectedDataWillBecomeUnavailable() {
        _ = coordinator.enteredBackground()
    }
}

/// One UIKit background task, ended exactly once: on lock completion or on expiration.
private final class BackgroundTask {
    private var identifier: UIBackgroundTaskIdentifier = .invalid

    func begin(name: String) {
        identifier = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            self?.end()
        }
    }

    func end() {
        guard identifier != .invalid else { return }
        UIApplication.shared.endBackgroundTask(identifier)
        identifier = .invalid
    }
}
