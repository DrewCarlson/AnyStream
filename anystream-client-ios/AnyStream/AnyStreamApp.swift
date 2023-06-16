import SwiftUI
import AnyStreamCore

@main
struct AnyStreamApp: App {
    
    init() {
        DependencyGraphKt.configure()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
