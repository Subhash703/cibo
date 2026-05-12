import Foundation
import ActivityKit

/// ActivityKit attributes for the cart-verdict Live Activity.
/// Static `attributes` are immutable for the life of the Activity;
/// `ContentState` is what we mutate as the analysis progresses.
struct CartVerdictAttributes: ActivityAttributes {
    public typealias ContentState = State

    public struct State: Codable, Hashable {
        public enum Status: String, Codable { case analyzing, ready, logged, failed }

        public var signal: String       // "green" | "yellow" | "red" | "neutral"
        public var headline: String     // "Fair choice", "Looks good", "Heads up"
        public var kcal: Int
        public var swap: String?        // "Swap fries → grilled corn"
        public var savedKcal: Int?      // 220
        public var status: Status

        public init(signal: String, headline: String, kcal: Int,
                    swap: String? = nil, savedKcal: Int? = nil, status: Status) {
            self.signal = signal; self.headline = headline; self.kcal = kcal
            self.swap = swap; self.savedKcal = savedKcal; self.status = status
        }
    }

    public var startedAt: Date
    public init(startedAt: Date = Date()) { self.startedAt = startedAt }
}
