import WidgetKit
import SwiftUI
import ActivityKit

@main
struct CiboWidgetBundle: WidgetBundle {
    var body: some Widget {
        CartVerdictLiveActivity()
    }
}

// MARK: Live Activity

@available(iOS 16.2, *)
struct CartVerdictLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CartVerdictAttributes.self) { context in
            // Lock-screen / banner presentation.
            LockScreenView(state: context.state)
                .activityBackgroundTint(CiboColor.background)
                .activitySystemActionForegroundColor(CiboColor.primary)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    SignalChip(signal: context.state.signal)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(context.state.kcal) kcal")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(CiboColor.primary)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.state.headline)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(CiboColor.onSurface)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if let swap = context.state.swap {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.left.arrow.right")
                                .foregroundStyle(CiboColor.primary)
                            Text(swap)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(CiboColor.onSurface)
                                .lineLimit(1)
                            if let saved = context.state.savedKcal {
                                Spacer(minLength: 0)
                                Text("-\(saved) kcal")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(CiboColor.primary)
                            }
                        }
                    } else if context.state.status == .analyzing {
                        HStack(spacing: 6) {
                            ProgressView().tint(CiboColor.primary)
                            Text("Reading cart…")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(CiboColor.onSurfaceVariant)
                        }
                    }
                }
            } compactLeading: {
                Image(systemName: SignalIcon.name(context.state.signal))
                    .foregroundStyle(SignalIcon.color(context.state.signal))
            } compactTrailing: {
                Text("\(context.state.kcal)")
                    .font(.caption2.bold())
                    .foregroundStyle(SignalIcon.color(context.state.signal))
                    .monospacedDigit()
            } minimal: {
                Image(systemName: SignalIcon.name(context.state.signal))
                    .foregroundStyle(SignalIcon.color(context.state.signal))
            }
            .keylineTint(CiboColor.primary)
        }
    }
}

// MARK: Lock screen

@available(iOS 16.2, *)
private struct LockScreenView: View {
    var state: CartVerdictAttributes.ContentState

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(SignalIcon.color(state.signal).opacity(0.18))
                Image(systemName: SignalIcon.name(state.signal))
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(SignalIcon.color(state.signal))
            }
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 4) {
                Text(state.headline)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(CiboColor.onSurface)
                if let swap = state.swap {
                    Text(swap)
                        .font(.system(size: 13))
                        .foregroundStyle(CiboColor.primary)
                        .lineLimit(2)
                } else if state.status == .analyzing {
                    Text("Cibo is analyzing your order…")
                        .font(.system(size: 13))
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                } else {
                    Text("\(state.kcal) kcal estimated")
                        .font(.system(size: 13))
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }
            }
            Spacer(minLength: 0)
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(state.kcal)")
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .foregroundStyle(CiboColor.primary)
                    .monospacedDigit()
                Text("kcal")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurfaceVariant)
            }
        }
        .padding(16)
    }
}

// MARK: Helpers

private struct SignalChip: View {
    var signal: String
    var body: some View {
        ZStack {
            Circle().fill(SignalIcon.color(signal).opacity(0.2))
            Image(systemName: SignalIcon.name(signal))
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(SignalIcon.color(signal))
        }
        .frame(width: 28, height: 28)
    }
}

enum SignalIcon {
    static func name(_ signal: String) -> String {
        switch signal {
        case "green":  return "checkmark.seal.fill"
        case "yellow": return "exclamationmark.triangle.fill"
        case "red":    return "xmark.octagon.fill"
        default:       return "sparkles"
        }
    }
    static func color(_ signal: String) -> Color {
        switch signal {
        case "green":  return CiboColor.primary
        case "yellow": return CiboColor.warning
        case "red":    return CiboColor.error
        default:       return CiboColor.primary
        }
    }
}
