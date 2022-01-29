import AnyStreamCore
import SwiftUI

class UIConnectable<T> : Connectable {
    private let modelBinding: Binding<T>
    private let consumerBinding: Binding<Consumer?>
    init(modelBinding: Binding<T>, consumerBinding: Binding<Consumer?>) {
        self.modelBinding = modelBinding
        self.consumerBinding = consumerBinding
    }

    class SimpleConnection : Connection {
        private let modelBinding: Binding<T>
        private let consumerBinding: Binding<Consumer?>
        init(modelBinding: Binding<T>, consumerBinding: Binding<Consumer?>) {
            self.modelBinding = modelBinding
            self.consumerBinding = consumerBinding
        }

        func accept(value: Any?) {
            guard let model: T = value as? T else { return }
            modelBinding.wrappedValue = model
        }

        func dispose() {
            consumerBinding.wrappedValue = nil
        }
    }

    func connect(output: Consumer) throws -> Connection {
        let connection = SimpleConnection(modelBinding: modelBinding, consumerBinding: consumerBinding)
        consumerBinding.wrappedValue = output
        return connection
    }
}
