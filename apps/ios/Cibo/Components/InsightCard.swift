import SwiftUI

/// Glass-style "AI Insight" card with the Fraunces italic quote.
struct InsightCard: View {
    var label: String = "CIBO INSIGHT"
    var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: CiboSpacing.sm) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .font(.system(size: 14, weight: .bold))
                Text(label)
                    .font(CiboFont.labelSm)
                    .tracking(1.4)
            }
            .foregroundStyle(CiboColor.primary)

            Text("\u{201C}\(text)\u{201D}")
                .font(CiboFont.display(17, weight: .medium).italic())
                .foregroundStyle(CiboColor.onSurface)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(CiboSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: CiboRadius.lg, style: .continuous)
                .fill(CiboColor.surfaceContainerLow)
        )
        .overlay(
            RoundedRectangle(cornerRadius: CiboRadius.lg, style: .continuous)
                .strokeBorder(CiboColor.primary.opacity(0.35), lineWidth: 1)
        )
    }
}

/// Small chip used for tags ("Keto", "Omega-3 Rich").
struct CiboChip: View {
    var label: String
    var body: some View {
        Text(label)
            .font(CiboFont.body(12, weight: .semibold))
            .foregroundStyle(CiboColor.primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(CiboColor.primary.opacity(0.12)))
    }
}
