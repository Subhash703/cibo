import Foundation

/// Shared container so the Share Extension + Widget can read the JWT and
/// any cached profile written by the main app.
enum CiboAppGroup {
    static let id = "group.com.foodlens.cibo"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: id) ?? .standard
    }

    enum Keys {
        static let token       = "cibo.token"
        static let dailyTarget = "cibo.dailyTarget"
        static let lastSummary = "cibo.lastSummary"
    }
}
