import SwiftUI
import AnyStreamCore

@main
struct AnyStreamApp: App {

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .background(Color("dark1"))
                .preferredColorScheme(.dark)
        }
    }
}
