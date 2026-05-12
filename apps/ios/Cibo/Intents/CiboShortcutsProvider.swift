import AppIntents

/// Registers Cibo's shortcuts in the system Shortcuts library + Spotlight.
/// Once installed, the user can build a personal automation:
///   "When I take a screenshot in [Swiggy / Zomato / Blinkit] → Run Cibo › Analyze Cart"
@available(iOS 17.0, *)
struct CiboShortcutsProvider: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AnalyzeCartIntent(),
            phrases: [
                "Analyze my cart with \(.applicationName)",
                "Score my order with \(.applicationName)",
                "Run \(.applicationName) on this screenshot",
            ],
            shortTitle: "Analyze cart",
            systemImageName: "sparkles"
        )
    }
}
