import AppIntents
import ActivityKit
import Foundation

/// The Shortcuts-facing intent that powers the "Make Cibo 1-tap" flow:
/// Shortcuts personal automation triggers on `Screenshot Taken` (restricted
/// to Swiggy / Zomato / Blinkit), passes the image to this intent, which
/// posts it to /analyze-vision and starts a Live Activity with the verdict.
@available(iOS 17.0, *)
struct AnalyzeCartIntent: AppIntent {
    static var title: LocalizedStringResource = "Analyze cart screenshot"
    static var description = IntentDescription(
        "Score this Swiggy / Zomato / Blinkit cart and pin a verdict to the Lock Screen.",
        categoryName: "Cibo"
    )
    static var openAppWhenRun: Bool = false
    static var isDiscoverable: Bool = true

    @Parameter(
        title: "Cart screenshot",
        description: "The screenshot of your delivery-app cart.",
        supportedTypeIdentifiers: ["public.image"]
    )
    var image: IntentFile

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let token = CiboAppGroup.defaults.string(forKey: CiboAppGroup.Keys.token)

        var activity: Activity<CartVerdictAttributes>?
        if #available(iOS 16.2, *) {
            activity = LiveActivityController.startAnalyzing()
        }

        do {
            let response = try await AnalyzeClient.shared.analyzeVision(
                jpeg: image.data, regionHint: nil, token: token
            )
            let signal = signal(score: response.healthScore,
                                percentDaily: response.percentDailyKcal)
            let swap = response.suggestions.first

            if #available(iOS 16.2, *) {
                await LiveActivityController.update(
                    activity,
                    signal: signal,
                    headline: response.healthLabel,
                    kcal: response.macros.kcal,
                    swap: swap?.text,
                    savedKcal: swap.map { abs($0.kcalDelta) }
                )
            }

            let dialog: IntentDialog
            if let swap {
                dialog = IntentDialog("\(response.healthLabel) — \(response.macros.kcal) kcal. Tip: \(swap.text).")
            } else {
                dialog = IntentDialog("\(response.healthLabel) — \(response.macros.kcal) kcal. Verdict pinned to your Lock Screen.")
            }
            return .result(dialog: dialog)
        } catch {
            if #available(iOS 16.2, *) {
                await LiveActivityController.fail(activity, message: "Couldn't analyze")
            }
            throw error
        }
    }

    private func signal(score: Int, percentDaily: Int) -> String {
        if score >= 70 && percentDaily < 35 { return "green" }
        if score >= 50 && percentDaily < 55 { return "yellow" }
        return "red"
    }
}
