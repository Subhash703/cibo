import SwiftUI

struct ProfileScreen: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var goal: Double = 2000
    @State private var personalizeOpen = false
    @State private var showEditSheet = false

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                    headerCard
                    goalCard
                    personalizeCard
                    accountCard
                    Text("Cibo v0.1 — proudly AI crafted")
                        .font(CiboFont.labelSm)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                        .frame(maxWidth: .infinity)
                }
                .screenPadding()
                .padding(.top, CiboSpacing.md)
                .padding(.bottom, CiboSpacing.xl)
            }
        }
        .navigationBarHidden(true)
        .onAppear { goal = Double(auth.user?.dailyKcalTarget ?? 2000) }
        .sheet(isPresented: $showEditSheet) { ProfileEditSheet() }
    }

    private var headerCard: some View {
        VStack(spacing: CiboSpacing.md) {
            HStack {
                Text("Cibo")
                    .font(CiboFont.display(28, weight: .semibold))
                    .foregroundStyle(CiboColor.primary)
                Spacer()
                Image(systemName: "bell")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
                    .padding(10)
                    .background(Circle().fill(CiboColor.surfaceContainerHigh))
            }
            ZStack {
                Circle()
                    .stroke(CiboColor.primary, lineWidth: 3)
                    .frame(width: 110, height: 110)
                Image(systemName: "person.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(CiboColor.primary)
            }
            Text(auth.user?.name ?? auth.user?.email ?? "—")
                .font(CiboFont.display(26, weight: .semibold))
                .foregroundStyle(CiboColor.onSurface)
            Text(auth.user?.email ?? "")
                .font(CiboFont.bodyMd)
                .foregroundStyle(CiboColor.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
    }

    private var goalCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: CiboSpacing.md) {
                HStack {
                    Text("Daily Goal")
                        .font(CiboFont.h1)
                        .foregroundStyle(CiboColor.onSurface)
                    Spacer()
                    StatusPill(text: "Active", tone: .positive)
                }
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("\(Int(goal))")
                        .font(CiboFont.display(44, weight: .semibold))
                        .foregroundStyle(CiboColor.primary)
                    Text("kcal / day")
                        .font(CiboFont.bodyLg)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }
                Slider(value: $goal, in: 1200...3500, step: 50)
                    .tint(CiboColor.primary)
                    .onChange(of: goal) { _, new in
                        Task { await auth.updateGoal(kcal: Int(new)) }
                    }
                HStack {
                    Text("1,200").font(CiboFont.labelSm).foregroundStyle(CiboColor.onSurfaceVariant)
                    Spacer()
                    Text("3,500").font(CiboFont.labelSm).foregroundStyle(CiboColor.onSurfaceVariant)
                }
                InsightCard(label: "AI INSIGHT",
                            text: "This goal is optimized for consistent energy and metabolic health based on your activity levels.")
            }
        }
    }

    private var personalizeCard: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: CiboSpacing.md) {
                Button { withAnimation { personalizeOpen.toggle() } } label: {
                    HStack {
                        Image(systemName: "slider.horizontal.3")
                            .foregroundStyle(CiboColor.primary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Personalize")
                                .font(CiboFont.h1)
                                .foregroundStyle(CiboColor.onSurface)
                            Text("METRICS & BIOLOGY")
                                .font(CiboFont.labelSm).tracking(1.2)
                                .foregroundStyle(CiboColor.onSurfaceVariant)
                        }
                        Spacer()
                        Image(systemName: personalizeOpen ? "chevron.up" : "chevron.down")
                            .foregroundStyle(CiboColor.onSurfaceVariant)
                    }
                }
                .buttonStyle(.plain)

                if personalizeOpen {
                    if hasAnyPersonalization {
                        FlowLayout(spacing: 8) {
                            if let s = auth.user?.sex { CiboChip(label: s.capitalized) }
                            if let y = auth.user?.birthYear { CiboChip(label: "\(y)") }
                            if let w = auth.user?.weightKg { CiboChip(label: "\(Int(w)) kg") }
                            if let h = auth.user?.heightCm { CiboChip(label: "\(Int(h)) cm") }
                            if let a = auth.user?.activityLevel { CiboChip(label: a.capitalized) }
                        }
                    } else {
                        Text("No metrics yet. Add them so Cibo can personalise every verdict to your body.")
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.onSurfaceVariant)
                    }

                    SecondaryButton(
                        title: hasAnyPersonalization ? "Edit info" : "Add my info",
                        icon: "pencil"
                    ) { showEditSheet = true }
                }
            }
        }
    }

    private var hasAnyPersonalization: Bool {
        auth.user?.sex != nil
            || auth.user?.birthYear != nil
            || auth.user?.weightKg != nil
            || auth.user?.heightCm != nil
            || auth.user?.activityLevel != nil
    }

    private var accountCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("ACCOUNT SETTINGS")
                .font(CiboFont.labelSm).tracking(1.4)
                .foregroundStyle(CiboColor.onSurfaceVariant)
                .padding(.bottom, CiboSpacing.sm)

            Row(icon: "square.and.arrow.down", title: "Data export") { }
            Divider().background(CiboColor.outlineVariant)
            Row(icon: "rectangle.portrait.and.arrow.right", title: "Sign out") {
                auth.signOut()
            }
            Divider().background(CiboColor.outlineVariant)
            Row(icon: "trash", title: "Delete account", destructive: true) { }
        }
        .padding(.vertical, CiboSpacing.sm)
        .padding(.horizontal, CiboSpacing.sm)
        .background(
            RoundedRectangle(cornerRadius: CiboRadius.lg, style: .continuous)
                .fill(CiboColor.surfaceContainer)
        )
    }

    struct Row: View {
        var icon: String
        var title: String
        var destructive: Bool = false
        var action: () -> Void
        var body: some View {
            Button(action: action) {
                HStack {
                    Image(systemName: icon)
                        .frame(width: 28)
                        .foregroundStyle(destructive ? CiboColor.error : CiboColor.onSurface)
                    Text(title)
                        .font(CiboFont.bodyLg)
                        .foregroundStyle(destructive ? CiboColor.error : CiboColor.onSurface)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }
                .padding(.vertical, 14)
                .padding(.horizontal, CiboSpacing.sm)
            }
            .buttonStyle(.plain)
        }
    }
}

/// Tiny flow layout — wraps chips onto multiple lines.
struct FlexibleHStack<Content: View>: View {
    var spacing: CGFloat = 8
    @ViewBuilder var content: () -> Content
    var body: some View {
        // SwiftUI 16+: Layout protocol. For simplicity use HStack with wrap via FlowLayout.
        FlowLayout(spacing: spacing) { content() }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0; var y: CGFloat = 0; var rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > maxWidth { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
        return CGSize(width: maxWidth.isFinite ? maxWidth : x, height: y + rowH)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX; var y = bounds.minY; var rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}
