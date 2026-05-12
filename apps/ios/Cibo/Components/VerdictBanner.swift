import SwiftUI

/// Big colour-coded verdict tile shown on plate/cart result.
struct VerdictBanner: View {
    enum Signal: String { case green, yellow, red }

    var signal: Signal
    var headline: String
    var detail: String
    var icon: String? = nil

    @State private var appear = false

    var body: some View {
        HStack(alignment: .top, spacing: CiboSpacing.md) {
            ZStack {
                Circle().fill(accent.opacity(0.18))
                Image(systemName: icon ?? defaultIcon)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(accent)
            }
            .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 4) {
                Text(headline)
                    .font(CiboFont.body(17, weight: .bold))
                    .foregroundStyle(accent)
                Text(detail)
                    .font(CiboFont.bodyMd)
                    .foregroundStyle(CiboColor.onSurface)
            }
            Spacer(minLength: 0)
        }
        .padding(CiboSpacing.lg)
        .background(
            RoundedRectangle(cornerRadius: CiboRadius.lg, style: .continuous)
                .fill(accent.opacity(0.10))
        )
        .overlay(
            RoundedRectangle(cornerRadius: CiboRadius.lg, style: .continuous)
                .strokeBorder(accent.opacity(0.4), lineWidth: 1)
        )
        .scaleEffect(appear ? 1 : 0.94)
        .opacity(appear ? 1 : 0)
        .onAppear {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) { appear = true }
        }
    }

    private var accent: Color {
        switch signal {
        case .green:  return CiboColor.primary
        case .yellow: return CiboColor.warning
        case .red:    return CiboColor.error
        }
    }
    private var defaultIcon: String {
        switch signal {
        case .green:  return "checkmark.seal.fill"
        case .yellow: return "exclamationmark.triangle.fill"
        case .red:    return "xmark.octagon.fill"
        }
    }
}
