import AnyStreamCore
import SwiftUI

let gradient = LinearGradient(
    colors: [
        Color.black.opacity(0.6),
        Color.black.opacity(0.6),
        Color.black.opacity(0.5),
        Color.black.opacity(0.3),
        Color.black.opacity(0.0),
    ],
    startPoint: .top, endPoint: .bottom
)

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = Main_iosKt.MainViewController()
        controller.overrideUserInterfaceStyle = .dark
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeContentView: View {
    var body: some View {
        ZStack {
            ComposeView()
        }.preferredColorScheme(.dark)
    }
}
