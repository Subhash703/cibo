import SwiftUI

@main
struct CiboApp: App {
    @StateObject private var auth = AuthStore()

    init() {
        // Wake Render free-tier dyno before the user taps anything.
        Task.detached(priority: .background) { await AnalyzeClient.shared.wake() }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(auth)
                .preferredColorScheme(.dark)
                .tint(CiboColor.primary)
        }
    }
}
