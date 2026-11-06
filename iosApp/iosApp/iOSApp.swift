import SwiftUI

@main
struct iOSApp: App {
    init() { DriveAuthorization.shared.install() }
    var body: some Scene {
        WindowGroup { ContentView().ignoresSafeArea() }
    }
}
