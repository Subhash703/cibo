import UIKit
import SwiftUI
import ActivityKit
import UniformTypeIdentifiers

/// Receives an image from the iOS Share Sheet, posts it to /analyze-vision,
/// kicks off a Live Activity, and shows a small in-sheet status view.
class ShareViewController: UIViewController {

    private var hostController: UIHostingController<AnyView>?
    private var activity: Activity<CartVerdictAttributes>?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        installHostView(state: .analyzing)
        Task { await processSharedImage() }
    }

    // MARK: SwiftUI host

    private func installHostView(state: ProcessingView.UIState) {
        let view = ProcessingView(state: state, onClose: { [weak self] in self?.complete() })
        let host = UIHostingController(rootView: AnyView(view))
        host.view.backgroundColor = .clear
        addChild(host)
        self.view.addSubview(host.view)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: self.view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: self.view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: self.view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: self.view.bottomAnchor),
        ])
        host.didMove(toParent: self)
        hostController = host
    }

    private func update(_ state: ProcessingView.UIState) {
        DispatchQueue.main.async {
            let v = ProcessingView(state: state, onClose: { [weak self] in self?.complete() })
            self.hostController?.rootView = AnyView(v)
        }
    }

    // MARK: pipeline

    private func processSharedImage() async {
        guard let image = await loadFirstImage(),
              let jpeg = image.jpegData(compressionQuality: 0.8) else {
            update(.error("No image found in share."))
            return
        }

        if #available(iOS 16.2, *) {
            self.activity = LiveActivityController.startAnalyzing()
        }

        let token = CiboAppGroup.defaults.string(forKey: CiboAppGroup.Keys.token)

        do {
            let response = try await AnalyzeClient.shared.analyzeVision(
                jpeg: jpeg, regionHint: nil, token: token
            )
            let signal = signalFor(score: response.healthScore,
                                   percentDaily: response.percentDailyKcal)
            let swap = response.suggestions.first

            if #available(iOS 16.2, *) {
                await LiveActivityController.update(
                    self.activity,
                    signal: signal,
                    headline: response.healthLabel,
                    kcal: response.macros.kcal,
                    swap: swap?.text,
                    savedKcal: swap.map { abs($0.kcalDelta) }
                )
            }

            update(.ready(
                signal: signal,
                healthLabel: response.healthLabel,
                kcal: response.macros.kcal,
                swap: swap?.text,
                savedKcal: swap.map { abs($0.kcalDelta) }
            ))
        } catch {
            if #available(iOS 16.2, *) {
                await LiveActivityController.fail(self.activity, message: "Couldn't analyze")
            }
            update(.error(error.localizedDescription))
        }
    }

    private func signalFor(score: Int, percentDaily: Int) -> String {
        if score >= 70 && percentDaily < 35 { return "green" }
        if score >= 50 && percentDaily < 55 { return "yellow" }
        return "red"
    }

    private func loadFirstImage() async -> UIImage? {
        for input in (extensionContext?.inputItems as? [NSExtensionItem]) ?? [] {
            for provider in input.attachments ?? [] {
                guard provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else { continue }
                if let img = await loadImage(from: provider) { return img }
            }
        }
        return nil
    }

    private func loadImage(from provider: NSItemProvider) async -> UIImage? {
        await withCheckedContinuation { cont in
            provider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) { item, _ in
                if let url = item as? URL,
                   let data = try? Data(contentsOf: url),
                   let img = UIImage(data: data) {
                    cont.resume(returning: img); return
                }
                if let img = item as? UIImage { cont.resume(returning: img); return }
                if let data = item as? Data, let img = UIImage(data: data) {
                    cont.resume(returning: img); return
                }
                cont.resume(returning: nil)
            }
        }
    }

    private func complete() {
        extensionContext?.completeRequest(returningItems: nil)
    }
}
