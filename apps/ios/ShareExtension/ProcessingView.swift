import SwiftUI

/// Compact UI shown inside the iOS Share Sheet while we hit /analyze-vision.
/// Reuses Cibo theme tokens (Theme.swift compiled into this target).
struct ProcessingView: View {

    enum UIState {
        case analyzing
        case ready(signal: String, healthLabel: String, kcal: Int, swap: String?, savedKcal: Int?)
        case error(String)
    }

    var state: UIState
    var onClose: () -> Void

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                header
                content
                Spacer(minLength: 0)
                PrimaryButton(title: ctaTitle) { onClose() }
            }
            .padding(CiboSpacing.lg)
        }
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        HStack {
            Image(systemName: "sparkles").foregroundStyle(CiboColor.primary)
            Text("Cibo")
                .font(CiboFont.h1)
                .foregroundStyle(CiboColor.primary)
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                    .padding(8)
                    .background(Circle().fill(CiboColor.surfaceContainerHigh))
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .analyzing:
            AnalyzingBlock()
        case .ready(let signal, let label, let kcal, let swap, let saved):
            ReadyBlock(signal: signal, label: label, kcal: kcal, swap: swap, savedKcal: saved)
        case .error(let msg):
            ErrorBlock(message: msg)
        }
    }

    private var ctaTitle: String {
        switch state {
        case .analyzing: return "Hide — keep analyzing"
        case .ready:     return "Done"
        case .error:     return "Close"
        }
    }
}

private struct AnalyzingBlock: View {
    var body: some View {
        GlassCard {
            HStack(spacing: CiboSpacing.md) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(CiboColor.primary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Reading your cart…")
                        .font(CiboFont.h2)
                        .foregroundStyle(CiboColor.onSurface)
                    Text("Verdict will land on your Lock Screen.")
                        .font(CiboFont.bodyMd)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
        }
    }
}

private struct ReadyBlock: View {
    var signal: String
    var label: String
    var kcal: Int
    var swap: String?
    var savedKcal: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: CiboSpacing.md) {
            VerdictBanner(
                signal: bannerSignal,
                headline: label,
                detail: "\(kcal) kcal estimated"
            )
            if let swap {
                GlassCard {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.left.arrow.right")
                                .foregroundStyle(CiboColor.primary)
                            Text("SMARTER SWAP")
                                .font(CiboFont.labelSm).tracking(1.4)
                                .foregroundStyle(CiboColor.primary)
                        }
                        Text(swap)
                            .font(CiboFont.h2)
                            .foregroundStyle(CiboColor.onSurface)
                        if let savedKcal {
                            Text("Saves about \(savedKcal) kcal.")
                                .font(CiboFont.bodyMd)
                                .foregroundStyle(CiboColor.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    private var bannerSignal: VerdictBanner.Signal {
        switch signal {
        case "green": return .green
        case "red":   return .red
        default:      return .yellow
        }
    }
}

private struct ErrorBlock: View {
    var message: String
    var body: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(CiboColor.error)
                    Text("CIBO COULDN'T READ THIS")
                        .font(CiboFont.labelSm).tracking(1.4)
                        .foregroundStyle(CiboColor.error)
                }
                Text(message)
                    .font(CiboFont.bodyMd)
                    .foregroundStyle(CiboColor.onSurface)
            }
        }
    }
}
