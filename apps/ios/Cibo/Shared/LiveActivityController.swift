import Foundation
import ActivityKit

/// Thin wrapper around ActivityKit. Both the main app and the Share
/// Extension use this to start / update the cart-verdict Live Activity.
@available(iOS 16.2, *)
enum LiveActivityController {

    /// Starts a fresh Activity in the "analyzing" state. Returns the
    /// activity so callers can update it as the analysis lands.
    @discardableResult
    static func startAnalyzing() -> Activity<CartVerdictAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return nil }
        do {
            let attrs = CartVerdictAttributes()
            let state = CartVerdictAttributes.State(
                signal: "neutral",
                headline: "Analyzing your cart…",
                kcal: 0,
                status: .analyzing
            )
            let content = ActivityContent(state: state, staleDate: Date().addingTimeInterval(120))
            return try Activity.request(attributes: attrs, content: content, pushType: nil)
        } catch {
            return nil
        }
    }

    /// Updates the running activity with the verdict from the API.
    static func update(_ activity: Activity<CartVerdictAttributes>?,
                       signal: String,
                       headline: String,
                       kcal: Int,
                       swap: String? = nil,
                       savedKcal: Int? = nil) async {
        guard let activity else { return }
        let state = CartVerdictAttributes.State(
            signal: signal, headline: headline, kcal: kcal,
            swap: swap, savedKcal: savedKcal, status: .ready
        )
        let stale = Date().addingTimeInterval(60 * 60 * 8) // 8h Lock-Screen ride-along
        await activity.update(ActivityContent(state: state, staleDate: stale))
    }

    static func fail(_ activity: Activity<CartVerdictAttributes>?, message: String) async {
        guard let activity else { return }
        let state = CartVerdictAttributes.State(
            signal: "red", headline: message, kcal: 0, status: .failed
        )
        await activity.end(ActivityContent(state: state, staleDate: Date().addingTimeInterval(15)),
                           dismissalPolicy: .after(Date().addingTimeInterval(15)))
    }
}
