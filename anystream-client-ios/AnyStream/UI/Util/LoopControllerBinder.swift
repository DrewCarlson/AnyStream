import AnyStreamCore
import Foundation
import SwiftUI

extension View {
    public func bindController<M>(
            loopController: MobiusLoopController,
            modelBinding: Binding<M>,
            consumerBinding: Binding<Consumer?>
    ) -> some View {
        onAppear {
            // Typical initialization event
            // At this point we've navigated to this view/screen for the first time.
            // If necessary, connect the view and start the loop.
            if !loopController.isRunning {
                if consumerBinding.wrappedValue == nil {
                    try! loopController.connect(view: UIConnectable<M>(
                            modelBinding: modelBinding,
                            consumerBinding: consumerBinding
                    ))
                }
                try! loopController.start()
            }
        }
        .onDisappear {
            // Typical finalization event:
            // At this point we're navigating away from this view/screen for the last time.
            // Stop the running loop and disconnect the view.
            if loopController.isRunning {
                try! loopController.stop()
            }
            try! loopController.disconnect()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { output in
            // Application entered the background, stop the loop if it is running and disconnect the view
            if loopController.isRunning {
                try! loopController.stop()
            }
            try! loopController.disconnect()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { output in
            // Application is entering the foreground, connect the view if necessary and start the loop.
            if !loopController.isRunning {
                if consumerBinding.wrappedValue == nil {
                    try! loopController.connect(view: UIConnectable<M>(
                            modelBinding: modelBinding,
                            consumerBinding: consumerBinding
                    ))
                }
                try! loopController.start()
            }
        }
    }
}
