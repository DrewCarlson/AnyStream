import UIKit
import SwiftUI

struct LoadingScrim: View {

    var body: some View {
        ZStack {
            Color.black.opacity(0.85).edgesIgnoringSafeArea(.all)
            ActivityIndicator(shouldAnimate: State(initialValue: true))
                    .scaleEffect(1.5)
        }.transition(.opacity.animation(.easeInOut(duration: 0.3)))
                .edgesIgnoringSafeArea(.all)
    }
}
