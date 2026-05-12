import SwiftUI

struct OnboardingFlow: View {
    @AppStorage("cibo.onboarded") private var onboarded: Bool = false
    @State private var page: Int = 0
    var onFinish: () -> Void

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            VStack(spacing: 0) {
                TabView(selection: $page) {
                    WelcomeStep().tag(0)
                    HowItWorksStep().tag(1)
                    OneTapStep().tag(2)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                pager
                    .padding(.bottom, CiboSpacing.md)

                PrimaryButton(
                    title: page < 2 ? "Get started" : "Sign in to continue",
                    icon: "arrow.right"
                ) {
                    if page < 2 {
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { page += 1 }
                    } else {
                        onboarded = true
                        onFinish()
                    }
                }
                .screenPadding()
                .padding(.bottom, CiboSpacing.lg)
            }
        }
        .toolbar {
            if page > 0 {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip") {
                        onboarded = true
                        onFinish()
                    }
                    .font(CiboFont.body(14, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                }
            }
        }
    }

    private var pager: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                Capsule()
                    .fill(i == page ? CiboColor.primary : CiboColor.outlineVariant)
                    .frame(width: i == page ? 22 : 8, height: 4)
                    .animation(.spring(response: 0.3, dampingFraction: 0.7), value: page)
            }
        }
    }
}

// MARK: Step 1 — Welcome (welcome_to_cibo_2)

private struct WelcomeStep: View {
    var body: some View {
        VStack(spacing: CiboSpacing.lg) {
            Spacer(minLength: 0)
            CiboMark()
            Text("Cibo")
                .font(CiboFont.display(36, weight: .semibold))
                .foregroundStyle(CiboColor.primary)
            Text("See what your meal will do — before you order.")
                .font(CiboFont.display(28, weight: .semibold))
                .multilineTextAlignment(.center)
                .foregroundStyle(CiboColor.onSurface)
                .lineSpacing(4)
                .padding(.horizontal, CiboSpacing.md)

            VStack(spacing: CiboSpacing.gutter) {
                FeatureRow(icon: "cart.fill", text: "Reads any cart on Swiggy, Zomato, Domino's")
                FeatureRow(icon: "sparkles", text: "AI tells you the calorie + health impact in seconds")
                FeatureRow(icon: "arrow.left.arrow.right", text: "Suggests lighter swaps you'll actually want")
            }

            Text("Only reads food apps. Nothing stored without your tap.")
                .font(CiboFont.labelSm)
                .tracking(1.2)
                .foregroundStyle(CiboColor.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.top, CiboSpacing.sm)

            Spacer(minLength: 0)
        }
        .screenPadding()
    }
}

// MARK: Step 2 — How it works (how_cibo_works)

private struct HowItWorksStep: View {
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                HStack { Text("Cibo").font(CiboFont.h2).foregroundStyle(CiboColor.primary); Spacer() }
                Text("How it works")
                    .font(CiboFont.display(32, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
                Text("Cibo lives where you order food, providing effortless nutritional intelligence.")
                    .font(CiboFont.bodyMd)
                    .foregroundStyle(CiboColor.onSurfaceVariant)

                Step(
                    number: 1,
                    icon: "fork.knife",
                    title: "Open a food app",
                    body: "Browse Swiggy, Zomato or Blinkit as you normally would."
                )
                Step(
                    number: 2,
                    icon: "camera.viewfinder",
                    title: "Screenshot the cart",
                    body: "Take a screenshot, then tap Share \u{2192} Cibo. (We'll show you a one-tap shortcut on Home.)"
                )
                Step(
                    number: 3,
                    icon: "sparkles",
                    title: "Get an instant verdict",
                    body: "Cibo scores the order, shows macros, and suggests a lighter swap pinned to your Lock Screen."
                )
            }
            .screenPadding()
            .padding(.top, CiboSpacing.lg)
        }
    }

    struct Step: View {
        var number: Int
        var icon: String
        var title: String
        var body_: String
        init(number: Int, icon: String, title: String, body: String) {
            self.number = number; self.icon = icon; self.title = title; self.body_ = body
        }
        var body: some View {
            HStack(alignment: .top, spacing: CiboSpacing.md) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(CiboColor.primary.opacity(0.12))
                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(CiboColor.primary)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(CiboFont.h2).foregroundStyle(CiboColor.onSurface)
                    Text(body_).font(CiboFont.bodyMd).foregroundStyle(CiboColor.onSurfaceVariant)
                }
            }
        }
    }
}

// MARK: Step 3 — Make Cibo 1-tap (iOS-specific permissions/automation card)

private struct OneTapStep: View {
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                Text("One last thing")
                    .font(CiboFont.display(32, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
                Text("Pick the experience you want. You can change this any time from Profile.")
                    .font(CiboFont.bodyMd)
                    .foregroundStyle(CiboColor.onSurfaceVariant)

                GlassCard(tone: .glow) {
                    VStack(alignment: .leading, spacing: CiboSpacing.md) {
                        HStack {
                            Image(systemName: "sparkles")
                                .foregroundStyle(CiboColor.primary)
                            Text("MAKE CIBO 1-TAP")
                                .font(CiboFont.labelSm).tracking(1.4)
                                .foregroundStyle(CiboColor.primary)
                            Spacer()
                            StatusPill(text: "Recommended", tone: .positive)
                        }
                        Text("Right now: screenshot \u{2192} share \u{2192} Cibo (3 taps).\nAfter setup: screenshot \u{2192} done.")
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.onSurface)
                        SecondaryButton(title: "Set it up — 30s", icon: "bolt.fill") { }
                    }
                }

                GlassCard {
                    VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                        HStack {
                            Image(systemName: "bell.badge.fill")
                                .foregroundStyle(CiboColor.primary)
                            Text("Lock-screen verdicts")
                                .font(CiboFont.h2)
                                .foregroundStyle(CiboColor.onSurface)
                        }
                        Text("Cibo pins the green/yellow/red verdict to your Dynamic Island so you can finish ordering without switching back.")
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.onSurfaceVariant)
                        SecondaryButton(title: "Allow notifications", icon: "checkmark") { }
                    }
                }
            }
            .screenPadding()
            .padding(.top, CiboSpacing.lg)
        }
    }
}

// MARK: Bits

private struct FeatureRow: View {
    var icon: String; var text: String
    var body: some View {
        HStack(spacing: CiboSpacing.md) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(CiboColor.primary.opacity(0.14))
                Image(systemName: icon).foregroundStyle(CiboColor.primary)
            }
            .frame(width: 40, height: 40)
            Text(text)
                .font(CiboFont.bodyMd.weight(.semibold))
                .foregroundStyle(CiboColor.onSurface)
            Spacer(minLength: 0)
        }
        .padding(CiboSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: CiboRadius.md, style: .continuous)
                .fill(CiboColor.surfaceContainer)
        )
    }
}

struct CiboMark: View {
    var size: CGFloat = 64
    var body: some View {
        Image("CiboLogo")
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
            .shadow(color: CiboColor.primary.opacity(0.35), radius: size * 0.25, y: size * 0.05)
    }
}
