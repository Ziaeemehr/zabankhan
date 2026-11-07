import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase
    init() { DriveAuthorization.shared.install() }
    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea()
                .onChange(of: scenePhase) { phase in
                    IosBackupLifecycle.shared.onScenePhaseChanged(active: phase == .active, backgrounded: phase == .background)
                }
        }
    }
}
