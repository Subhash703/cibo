import SwiftUI

/// Tonal-elevated card. Mirrors Stitch "Level 1" surface with a faint
/// outline + the option to glow teal for AI / hero containers.
struct GlassCard<Content: View>: View {
    enum Tone { case standard, glow, raised }

    var tone: Tone = .standard
    var corner: CGFloat = CiboRadius.xl
    var padding: CGFloat = CiboSpacing.lg
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(fill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .strokeBorder(stroke, lineWidth: 1)
            )
            .shadow(color: shadow, radius: 24, y: 8)
    }

    private var fill: Color {
        switch tone {
        case .standard: return CiboColor.surfaceContainer
        case .glow:     return CiboColor.surfaceContainerLow
        case .raised:   return CiboColor.surfaceContainerHigh
        }
    }

    private var stroke: Color {
        switch tone {
        case .glow: return CiboColor.primary.opacity(0.35)
        default:    return CiboColor.onSurface.opacity(0.04)
        }
    }

    private var shadow: Color {
        switch tone {
        case .glow: return CiboColor.primary.opacity(0.12)
        default:    return Color.black.opacity(0.25)
        }
    }
}
