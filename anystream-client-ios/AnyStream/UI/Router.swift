import Foundation
import Combine
import AnyStreamCore
import SwiftUI

class Router: ObservableObject, CommonRouter {

    @Published private(set) var stack: [Routes] = [Routes.Login.shared]

    private let routeSubject = PassthroughSubject<StackEvent, Never>()
    private var cancellable: AnyCancellable?

    init() {
        cancellable = routeSubject
            .receive(on: DispatchQueue.main)
            .sink(receiveValue: { [unowned self] in
                switch ($0) {
                case .push(route: let route):
                    stack.append(route)
                case .replaceTop(route: let route):
                    stack.removeLast()
                    stack.append(route)
                case .replaceStack(routes: let routes):
                    stack = routes
                case .popCurrent:
                    stack.removeLast()
                }
            })
    }
    
    func pushRoute(route: Routes) {
        routeSubject.send(StackEvent.push(route: route))
    }
    
    func replaceTop(route: Routes) {
        routeSubject.send(StackEvent.replaceTop(route: route))
    }
    
    func replaceStack(routes: [Routes]) {
        routeSubject.send(StackEvent.replaceStack(routes: routes))
    }
    
    func popCurrentRoute() {
        routeSubject.send(StackEvent.popCurrent)
    }
}

private enum StackEvent {
    case push(route: Routes)
    case replaceTop(route: Routes)
    case replaceStack(routes: [Routes])
    case popCurrent
}
