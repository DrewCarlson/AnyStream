import AnyStreamCore
import SwiftUI

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return WrappedUIViewController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

class WrappedUIViewController : UIViewController, SystemBarController {
    private var _prefersHomeIndicatorAutoHidden: Bool = false
    private var _prefersStatusBarHidden: Bool = false
    
    func setSystemBars(hidden: Bool) {
        _prefersHomeIndicatorAutoHidden = hidden
        _prefersStatusBarHidden = hidden
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        setNeedsStatusBarAppearanceUpdate()
    }
    
    override var prefersHomeIndicatorAutoHidden: Bool {
        get { _prefersHomeIndicatorAutoHidden }
    }
    
    override var prefersStatusBarHidden: Bool {
        get { _prefersStatusBarHidden }
    }
    
    override var overrideUserInterfaceStyle: UIUserInterfaceStyle {
        get { .dark }
        set { }
    }

    init() {
        super.init(nibName: nil, bundle: nil)
        let controller = Main_iosKt.MainViewController(statusBarController: self)
        controller.view.backgroundColor = UIColor(named: "dark1")
        self.addChild(controller)
        
        view.addSubview(controller.view)

        controller.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            controller.view.topAnchor.constraint(equalTo: view.topAnchor),
            controller.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            controller.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            controller.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
        ])

        controller.didMove(toParent: self)
    }
    
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) not implemented")
    }
    
}
