import AnyStreamCore
import SwiftUI

struct ContentView: View {
    @StateObject var router: Router = Router()
    
    var body: some View {
        return AnyView(ZStack(alignment: .center) {
            displayRoute(route: router.stack.last!)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }.frame(maxWidth: .infinity, maxHeight: .infinity))
    }
    
    @ViewBuilder func displayRoute(route: Routes) -> some View {
        switch route {
        case _ as Routes.Login:
            LoginScreen(router: router, anystreamClient: DependencyGraphKt.getClient())
        default: Text("unknown route")
        }
    }
}

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
        controller.overrideUserInterfaceStyle = .light
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeContentView: View {
    var body: some View {
        ZStack {
            ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
            VStack {
                gradient.ignoresSafeArea(edges: .top).frame(height: 0)
                Spacer()
            }
        }.preferredColorScheme(.dark)
    }
}
