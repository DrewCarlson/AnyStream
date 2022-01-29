import AnyStreamCore
import SwiftUI

struct ContentView: View {
    @StateObject var router: Router = Router()
    private let anystreamClient: AnyStreamClient

    init() {
        let iosSessionDataStore = IosSessionDataStore(defaults: UserDefaults(suiteName: "session")!)
        anystreamClient = AnyStreamClient(
            serverUrl: nil,
            http: DarwinHttpClient.shared.create(),
            sessionManager: SessionManager(dataStore: iosSessionDataStore))
    }
    
    var body: some View {
        return AnyView(ZStack(alignment: .center) {
            displayRoute(route: router.stack.last!)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
        }.frame(maxWidth: .infinity, maxHeight: .infinity))
    }

    @ViewBuilder func displayRoute(route: Routes) -> some View {
        switch route {
        case _ as Routes.Login:
            LoginScreen(router: router, anystreamClient: self.anystreamClient)
        default: Text("unknown route")
        }
    }
}
