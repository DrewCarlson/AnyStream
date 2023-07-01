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

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        Main_iosKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
    }
}
