import SwiftUI

/// Small uppercase pill — used for "CIBO IS RUNNING", "GRANTED", "PENDING".
struct StatusPill: View {
    enum Tone { case neutral, positive, warning, danger }

    var text: String
    var tone: Tone = .positive
    var icon: String? = nil
    var pulses: Bool = false

    @State private var pulseScale: CGFloat = 1

    var body: some View {
        HStack(spacing: 6) {
            if let icon {
                Image(systemName: icon).font(.system(size: 10, weight: .bold))
            } else {
                Circle()
                    .fill(dotColor)
                    .frame(width: 8, height: 8)
                    .scaleEffect(pulseScale)
            }
            Text(text.uppercased())
                .font(CiboFont.labelSm)
                .tracking(1.4)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .foregroundStyle(textColor)
        .background(Capsule().fill(bg))
        .overlay(Capsule().strokeBorder(border, lineWidth: 1))
        .onAppear {
            guard pulses else { return }
            withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                pulseScale = 1.6
            }
        }
    }

    private var dotColor: Color {
        switch tone {
        case .positive: return CiboColor.primary
        case .warning:  return CiboColor.warning
        case .danger:   return CiboColor.error
        case .neutral:  return CiboColor.outline
        }
    }
    private var textColor: Color {
        switch tone {
        case .positive: return CiboColor.primary
        case .warning:  return CiboColor.warning
        case .danger:   return CiboColor.error
        case .neutral:  return CiboColor.onSurfaceVariant
        }
    }
    private var bg: Color {
        switch tone {
        case .positive: return CiboColor.primary.opacity(0.12)
        case .warning:  return CiboColor.warning.opacity(0.12)
        case .danger:   return CiboColor.error.opacity(0.16)
        case .neutral:  return CiboColor.surfaceContainerHigh
        }
    }
    private var border: Color {
        switch tone {
        case .positive: return CiboColor.primary.opacity(0.3)
        case .warning:  return CiboColor.warning.opacity(0.3)
        case .danger:   return CiboColor.error.opacity(0.3)
        case .neutral:  return Color.clear
        }
    }
}
