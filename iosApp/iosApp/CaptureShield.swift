import UIKit

/// The neutral colour behind private content: the app-switcher cover and what a capture shows instead of the app.
enum PrivacyCover {
    static let color = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.098, green: 0.118, blue: 0.094, alpha: 1)
            : UIColor(red: 0.961, green: 0.953, blue: 0.922, alpha: 1)
    }
}

/// Keeps the app out of screenshots, screen recordings and mirroring, the iOS counterpart of Android's FLAG_SECURE.
///
/// iOS has no public switch for this. The content is hosted in the canvas of a secure text field, which the system
/// leaves out of every capture while it stays visible on the device itself, so a capture shows only the neutral cover
/// behind it. If a future iOS stops providing that canvas, the shield falls back to covering the app whenever the
/// screen is being recorded or mirrored; screenshots are then not prevented.
final class CaptureShieldController: UIViewController {
    private let content: UIViewController
    private var secureCanvas: UIView?
    private var captureCover: UIView?
    private var captureObserver: NSObjectProtocol?

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    deinit {
        captureObserver.map(NotificationCenter.default.removeObserver)
    }

    override func loadView() {
        let backdrop = UIView()
        backdrop.backgroundColor = PrivacyCover.color
        view = backdrop
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        secureCanvas = Self.makeSecureCanvas()
        let host = secureCanvas ?? UIView()
        pin(host, in: view)
        addChild(content)
        pin(content.view, in: host)
        content.didMove(toParent: self)
        if secureCanvas == nil { watchCapture() }
    }

    override var childForStatusBarStyle: UIViewController? { content }

    override var childForStatusBarHidden: UIViewController? { content }

    override var childForHomeIndicatorAutoHidden: UIViewController? { content }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if secureCanvas == nil { updateCaptureCover() }
    }

    /// The secure text field's canvas, emptied and reused as a plain container. It keeps its capture exclusion after
    /// leaving the field. Nil when this iOS version lays the field out differently.
    private static func makeSecureCanvas() -> UIView? {
        let field = UITextField()
        field.isSecureTextEntry = true
        field.layoutIfNeeded()
        guard let canvas = field.subviews.first else { return nil }
        canvas.removeFromSuperview()
        canvas.subviews.forEach { $0.removeFromSuperview() }
        canvas.isUserInteractionEnabled = true
        canvas.backgroundColor = .clear
        return canvas
    }

    private func watchCapture() {
        if #available(iOS 17.0, *) {
            registerForTraitChanges([UITraitSceneCaptureState.self]) { (self: Self, _: UITraitCollection) in
                self.updateCaptureCover()
            }
        } else {
            captureObserver = NotificationCenter.default.addObserver(
                forName: UIScreen.capturedDidChangeNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.updateCaptureCover()
            }
        }
    }

    private var isCaptured: Bool {
        if #available(iOS 17.0, *) {
            return traitCollection.sceneCaptureState == .active
        }
        return view.window?.windowScene?.screen.isCaptured ?? false
    }

    private func updateCaptureCover() {
        if isCaptured {
            guard captureCover == nil else { return }
            let cover = UIView()
            cover.backgroundColor = PrivacyCover.color
            pin(cover, in: view)
            captureCover = cover
        } else {
            captureCover?.removeFromSuperview()
            captureCover = nil
        }
    }

    private func pin(_ child: UIView, in parent: UIView) {
        child.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(child)
        NSLayoutConstraint.activate([
            child.topAnchor.constraint(equalTo: parent.topAnchor),
            child.bottomAnchor.constraint(equalTo: parent.bottomAnchor),
            child.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            child.trailingAnchor.constraint(equalTo: parent.trailingAnchor),
        ])
    }
}
