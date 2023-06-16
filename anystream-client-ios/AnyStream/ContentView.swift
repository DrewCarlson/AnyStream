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
