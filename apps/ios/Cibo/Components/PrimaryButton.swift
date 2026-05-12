import SwiftUI

struct PrimaryButton: View {
    enum Variant { case solid, ghost }

    var title: String
    var icon: String? = nil
    var variant: Variant = .solid
    var isLoading: Bool = false
    var isEnabled: Bool = true
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: CiboSpacing.sm) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(variant == .solid ? CiboColor.onPrimary : CiboColor.primary)
                } else {
                    Text(title).font(CiboFont.body(17, weight: .semibold))
                    if let icon { Image(systemName: icon) }
                }
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .foregroundStyle(variant == .solid ? CiboColor.onPrimary : CiboColor.primary)
            .background(background)
            .overlay(border)
            .clipShape(Capsule())
            .opacity(isEnabled ? 1 : 0.4)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled || isLoading)
        .sensoryFeedback(.impact(weight: .light), trigger: isLoading)
    }

    @ViewBuilder private var background: some View {
        if variant == .solid {
            Capsule().fill(CiboColor.primary)
        } else {
            Capsule().fill(Color.clear)
        }
    }

    @ViewBuilder private var border: some View {
        if variant == .ghost {
            Capsule().strokeBorder(CiboColor.primary.opacity(0.6), lineWidth: 1)
        }
    }
}

struct SecondaryButton: View {
    var title: String
    var icon: String? = nil
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: CiboSpacing.sm) {
                if let icon { Image(systemName: icon) }
                Text(title).font(CiboFont.body(15, weight: .semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(CiboColor.onSurface)
            .background(Capsule().fill(CiboColor.surfaceContainerHigh))
        }
        .buttonStyle(.plain)
    }
}
