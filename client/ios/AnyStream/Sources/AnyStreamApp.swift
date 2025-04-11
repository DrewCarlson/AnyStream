import SwiftUI
import AnyStreamCore

@main
struct AnyStreamApp: App {
    
    init() {
        DependencyGraphKt.configure()
    }
    
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .background(Color("dark1"))
                .preferredColorScheme(.dark)
        }
    }
}
