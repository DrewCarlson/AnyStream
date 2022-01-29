import UIKit
import SwiftUI

struct ActivityIndicator: UIViewRepresentable {

    @State(initialValue: false) var shouldAnimate: Bool
    var style: UIActivityIndicatorView.Style = .large
    var color: UIColor = .white

    func makeUIView(context: Context) -> UIActivityIndicatorView {
        let view = UIActivityIndicatorView(style: style)
        view.color = color
        return view
    }

    func updateUIView(_ uiView: UIActivityIndicatorView,
                      context: Context) {
        if shouldAnimate {
            uiView.startAnimating()
        } else {
            uiView.stopAnimating()
        }
    }
}
