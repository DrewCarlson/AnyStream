import AnyStreamCore
import SwiftUI

struct LoginScreen: View {
    @State private var model: LoginScreenModel
    @State private var eventConsumer: Consumer? = nil
    private var loopController: MobiusLoopController

    @State private var serverUrl: String = ""
    @State private var username: String = ""
    @State private var password: String = ""

    init(router: Router, anystreamClient: AnyStreamClient) {
        let startModel = LoginScreenModel.companion.create()
        model = startModel
        let handler = LoginScreenHandler.shared.create(client: anystreamClient) {
            router.replaceTop(route: Routes.Home.shared)
        }
        let loopFactory = FlowMobius.shared.loop(update: LoginScreenUpdate.shared, effectHandler: handler)
                .logger(logger: SimpleLogger<AnyObject, AnyObject, AnyObject>(tag: "LoginScreen"))
        loopController = Mobius.shared.controller(
            loopFactory: loopFactory,
            defaultModel: startModel,
            modelRunner: DispatchQueueWorkRunner(dispatchQueue: DispatchQueue.main))
    }

    var body: some View {
        NavigationView {
            ZStack {
                VStack {
                    Text("AnyStream").font(.largeTitle)
                    Spacer()
                    VStack(alignment: .center, spacing: 16) {
                        Text("Login").font(.title)

                        TextField("Server URL",
                            text: Binding(
                                get: { serverUrl },
                                set: { value in
                                    eventConsumer?.accept(value: LoginScreenEvent.OnServerUrlChanged(serverUrl: value))
                                    serverUrl = value
                                }))
                                .textContentType(.URL)
                                .disableAutocorrection(true)
                                .textFieldStyle(.roundedBorder)

                        TextField("Username",
                            text: Binding(
                                get: { username },
                                set: { value in
                                    eventConsumer?.accept(value: LoginScreenEvent.OnUsernameChanged(username: value))
                                    username = value
                                }))
                                .textContentType(.username)
                                .disableAutocorrection(true)
                                .textFieldStyle(.roundedBorder)

                        SecureField("Password",
                            text: Binding(
                                get: { password },
                                set: { value in
                                    eventConsumer?.accept(value: LoginScreenEvent.OnPasswordChanged(password: value))
                                    password = value
                                }))
                                .textContentType(.password)
                                .disableAutocorrection(true)
                                .textFieldStyle(.roundedBorder)

                        Button("Submit", action: {
                            eventConsumer?.accept(value: LoginScreenEvent.OnLoginSubmit.shared)
                        }).padding(8)
                                .background(Color.blue.opacity(0.1))
                                .clipShape(Capsule(style: .continuous))

                        Text((model.loginError?.description() ?? " "))
                                .foregroundColor(Color.red)
                    }.padding(.horizontal, 12)

                    Spacer()
                }

                if model.isInputLocked() {
                    LoadingScrim()
                }
            }
        }.bindController(
            loopController: loopController,
            modelBinding: $model,
            consumerBinding: $eventConsumer)
    }
}
