import SwiftUI

/// Large progress ring with rounded caps. Centre slot shows the percent
/// or "0 / -- KCAL" placeholder for signed-out state.
struct KcalRing: View {
    var consumed: Int
    var target: Int
    var lineWidth: CGFloat = 14
    var diameter: CGFloat = 200

    @State private var animatedFraction: CGFloat = 0

    private var fraction: CGFloat {
        guard target > 0 else { return 0 }
        return min(1, CGFloat(consumed) / CGFloat(target))
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(CiboColor.surfaceContainerHigh, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: animatedFraction)
                .stroke(
                    AngularGradient(
                        colors: [CiboColor.primary, CiboColor.primaryFixedDim],
                        center: .center
                    ),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
                .shadow(color: CiboColor.primary.opacity(0.4), radius: 12)

            VStack(spacing: 2) {
                if target > 0 {
                    Text("\(Int(fraction * 100))%")
                        .font(CiboFont.display(40, weight: .semibold))
                        .foregroundStyle(CiboColor.onSurface)
                    Text("DAILY GOAL")
                        .font(CiboFont.labelSm)
                        .tracking(1.6)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                } else {
                    Text("0").font(CiboFont.display(40, weight: .semibold))
                        .foregroundStyle(CiboColor.onSurface)
                    Text("/ -- KCAL")
                        .font(CiboFont.labelSm)
                        .tracking(1.6)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }
            }
        }
        .frame(width: diameter, height: diameter)
        .onAppear {
            withAnimation(.spring(response: 1.0, dampingFraction: 0.85)) {
                animatedFraction = fraction
            }
        }
        .onChange(of: fraction) { _, new in
            withAnimation(.spring(response: 0.6, dampingFraction: 0.85)) {
                animatedFraction = new
            }
        }
    }
}

/// Slim macro progress bar (Protein / Fat / Carbs).
struct MacroBar: View {
    var label: String
    var grams: Int
    var target: Int = 120  // visual ceiling only

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label)
                    .font(CiboFont.body(13, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                Spacer()
                Text("\(grams)g")
                    .font(CiboFont.body(13, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(CiboColor.surfaceContainerHigh)
                    Capsule()
                        .fill(CiboColor.primary)
                        .frame(width: geo.size.width * min(1, CGFloat(grams) / CGFloat(target)))
                }
            }
            .frame(height: 6)
        }
    }
}
