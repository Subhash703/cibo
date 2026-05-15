import SwiftUI

/// Friendly "you've hit the free-tier cap" sheet. v1 just announces that
/// premium is coming — no email capture, no pricing. Keeps things honest.
struct PaywallSheet: View {

    /// Identifiable wrapper so callers can drive the sheet via `sheet(item:)`.
    /// Don't rename this nested type back to `Body` — it shadows SwiftUI's
    /// `View.Body` associated type and the parent stops conforming to View.
    struct Item: Identifiable {
        let text: String
        var id: String { text }
    }

    var message: String
    var onClose: () -> Void

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            VStack(spacing: CiboSpacing.lg) {
                Spacer(minLength: 0)
                ZStack {
                    Circle()
                        .fill(CiboColor.primary.opacity(0.15))
                        .frame(width: 96, height: 96)
                    Image(systemName: "sparkles")
                        .font(.system(size: 40, weight: .bold))
                        .foregroundStyle(CiboColor.primary)
                }
                Text("Premium is coming soon")
                    .font(CiboFont.display(28, weight: .semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(CiboColor.onSurface)
                Text(message)
                    .font(CiboFont.bodyMd)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, CiboSpacing.md)
                Spacer(minLength: 0)
                PrimaryButton(title: "Got it") { onClose() }
            }
            .padding(CiboSpacing.lg)
        }
    }
}
