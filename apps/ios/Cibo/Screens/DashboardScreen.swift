import SwiftUI

struct DashboardScreen: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var showShortcutSheet = false

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                    topBar
                    StatusPill(text: "Cibo is running", tone: .positive, pulses: true)
                    greeting
                    ringCard
                    shortcutCard
                    todayMealsSection
                    insightCard
                }
                .screenPadding()
                .padding(.top, CiboSpacing.md)
                .padding(.bottom, CiboSpacing.xl)
            }
            .refreshable { await auth.refreshToday() }
        }
        .navigationBarHidden(true)
        .task { await auth.refreshToday() }
        .sheet(isPresented: $showShortcutSheet) { ShortcutSetupSheet() }
    }

    // MARK: bits

    private var topBar: some View {
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
            avatar
        }
    }

    private var avatar: some View {
        CiboAvatar(user: auth.user, size: 36)
    }

    private func resolveFirstName() -> String {
        if let name = auth.user?.name {
            let parts = name.split(separator: " ")
            if let first = parts.first { return String(first) }
        }
        if let email = auth.user?.email {
            let parts = email.split(separator: "@")
            if let local = parts.first { return String(local) }
        }
        return "there"
    }

    private var greeting: some View {
        let hour = Calendar.current.component(.hour, from: Date())
        let part: String = hour < 12 ? "morning" : (hour < 17 ? "afternoon" : "evening")
        let firstName = resolveFirstName()
        let used = auth.todaySummary?.consumedKcal ?? 0
        let target = auth.todaySummary?.dailyKcalTarget ?? auth.user?.dailyKcalTarget ?? 2000
        return VStack(alignment: .leading, spacing: 6) {
            Text("Good \(part),")
                .font(CiboFont.display(36, weight: .semibold))
                .foregroundStyle(CiboColor.onSurface)
            Text(firstName.capitalized)
                .font(CiboFont.display(36, weight: .semibold))
                .foregroundStyle(CiboColor.onSurface)
            Text("You've used \(used.formatted()) of \(target.formatted()) kcal today.")
                .font(CiboFont.bodyMd)
                .foregroundStyle(CiboColor.onSurfaceVariant)
        }
    }

    private var ringCard: some View {
        let summary = auth.todaySummary
        let consumed = summary?.consumedKcal ?? 0
        let target   = summary?.dailyKcalTarget ?? auth.user?.dailyKcalTarget ?? 2000
        return GlassCard {
            VStack(spacing: CiboSpacing.lg) {
                KcalRing(consumed: consumed, target: target)
                HStack(spacing: CiboSpacing.lg) {
                    MacroBar(label: "Protein", grams: summary?.consumedProteinG ?? 0)
                    MacroBar(label: "Fats",    grams: summary?.consumedFatG ?? 0, target: 80)
                    MacroBar(label: "Carbs",   grams: summary?.consumedCarbsG ?? 0, target: 250)
                }
            }
        }
    }

    @ViewBuilder
    private var shortcutCard: some View {
        // Conditional — same pattern as the Android battery-tip card.
        // Hide once the user has set the Shortcut up (flag-driven for now).
        if !UserDefaults.standard.bool(forKey: "cibo.shortcut.setup") {
            GlassCard(tone: .glow) {
                VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkles").foregroundStyle(CiboColor.primary)
                        Text("MAKE CIBO 1-TAP")
                            .font(CiboFont.labelSm).tracking(1.4)
                            .foregroundStyle(CiboColor.primary)
                    }
                    Text("Right now: screenshot \u{2192} share \u{2192} Cibo (3 taps).\nAfter setup: screenshot \u{2192} done.")
                        .font(CiboFont.bodyMd)
                        .foregroundStyle(CiboColor.onSurface)
                    PrimaryButton(title: "Set it up — 30s") { showShortcutSheet = true }
                }
            }
        }
    }

    @ViewBuilder
    private var todayMealsSection: some View {
        HStack {
            Text("Today's meals")
                .font(CiboFont.h1)
                .foregroundStyle(CiboColor.onSurface)
            Spacer()
            Button("View Log") { }
                .font(CiboFont.body(14, weight: .semibold))
                .foregroundStyle(CiboColor.primary)
        }
        if auth.todayLogs.isEmpty {
            GlassCard {
                HStack(spacing: CiboSpacing.md) {
                    Image(systemName: "fork.knife")
                        .font(.system(size: 22))
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("No meals logged yet")
                            .font(CiboFont.h2)
                            .foregroundStyle(CiboColor.onSurface)
                        Text("Snap a plate or share a cart screenshot to start.")
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.onSurfaceVariant)
                    }
                }
            }
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: CiboSpacing.md) {
                    ForEach(auth.todayLogs) { MealCard(log: $0) }
                }
            }
        }
    }

    private var insightCard: some View {
        let used   = auth.todaySummary?.consumedKcal ?? 0
        let target = auth.todaySummary?.dailyKcalTarget ?? 2000
        let remaining = max(0, target - used)
        let body: String = remaining > 600
            ? "You're well under target — a balanced dinner with lean protein keeps the rhythm going."
            : remaining > 200
                ? "You're tracking smoothly. Lean toward fibre + protein for the rest of the day."
                : "You're near your goal. A light, vegetable-forward meal will land softly."
        return InsightCard(text: body)
    }
}

private struct MealCard: View {
    var log: MealLogPublic
    var body: some View {
        VStack(alignment: .leading, spacing: CiboSpacing.sm) {
            ZStack {
                RoundedRectangle(cornerRadius: CiboRadius.lg, style: .continuous)
                    .fill(CiboColor.surfaceContainerHigh)
                    .frame(height: 120)
                Image(systemName: "fork.knife.circle.fill")
                    .font(.system(size: 38))
                    .foregroundStyle(CiboColor.primary.opacity(0.6))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(timeLabel)
                    .font(CiboFont.labelSm).tracking(1.2)
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                Text(title)
                    .font(CiboFont.body(15, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
                    .lineLimit(1)
                Text("\(log.kcal) kcal")
                    .font(CiboFont.body(13, weight: .semibold))
                    .foregroundStyle(CiboColor.primary)
            }
        }
        .frame(width: 180)
    }

    private var title: String {
        log.items.first?.name ?? "Logged meal"
    }

    private var timeLabel: String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let d = f.date(from: log.loggedAt + "Z")
            ?? f.date(from: log.loggedAt)
            ?? Date()
        let df = DateFormatter()
        df.dateFormat = "hh:mm a"
        return df.string(from: d)
    }
}

private struct ShortcutSetupSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var step = 0
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                    Text("Make Cibo 1-tap")
                        .font(CiboFont.display(30, weight: .semibold))
                        .foregroundStyle(CiboColor.onSurface)
                    Text("iOS lets you run Cibo automatically when you screenshot inside Swiggy or Zomato. Setup is one Shortcut, ~30 seconds.")
                        .font(CiboFont.bodyMd)
                        .foregroundStyle(CiboColor.onSurfaceVariant)

                    InstructionStep(n: 1, text: "Tap **Open Shortcuts**. We'll deep-link you into the right screen.")
                    InstructionStep(n: 2, text: "Choose trigger **Screenshot Taken**, restrict to Swiggy / Zomato / Blinkit.")
                    InstructionStep(n: 3, text: "Pick **Run Cibo › Analyze Cart** as the action.")
                    InstructionStep(n: 4, text: "Toggle **Run Immediately** on so iOS doesn't ask each time.")

                    PrimaryButton(title: "Open Shortcuts", icon: "arrow.up.right") {
                        if let url = URL(string: "shortcuts://") {
                            UIApplication.shared.open(url)
                        }
                    }
                    SecondaryButton(title: "I'll do this later") { dismiss() }
                }
                .screenPadding()
                .padding(.top, CiboSpacing.md)
            }
            .background(CiboColor.background.ignoresSafeArea())
            .navigationTitle("Setup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }.tint(CiboColor.primary)
                }
            }
        }
    }
}

private struct InstructionStep: View {
    var n: Int; var text: String
    var body: some View {
        HStack(alignment: .top, spacing: CiboSpacing.md) {
            ZStack {
                Circle().fill(CiboColor.primary.opacity(0.16))
                Text("\(n)").font(CiboFont.body(15, weight: .bold))
                    .foregroundStyle(CiboColor.primary)
            }
            .frame(width: 32, height: 32)
            Text(.init(text))
                .font(CiboFont.bodyMd)
                .foregroundStyle(CiboColor.onSurface)
        }
    }
}

struct LogsScreen: View {
    @EnvironmentObject private var auth: AuthStore
    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: CiboSpacing.md) {
                    Text("Today's log")
                        .font(CiboFont.display(32, weight: .semibold))
                        .foregroundStyle(CiboColor.onSurface)
                    if auth.todayLogs.isEmpty {
                        Text("Nothing logged yet.")
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.onSurfaceVariant)
                    } else {
                        ForEach(auth.todayLogs) { log in
                            GlassCard {
                                VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                                    HStack {
                                        Text(log.items.first?.name ?? "Meal")
                                            .font(CiboFont.h2)
                                            .foregroundStyle(CiboColor.onSurface)
                                        Spacer()
                                        Text("\(log.kcal) kcal")
                                            .font(CiboFont.body(15, weight: .semibold))
                                            .foregroundStyle(CiboColor.primary)
                                    }
                                    Text("P \(log.proteinG)g · F \(log.fatG)g · C \(log.carbsG)g")
                                        .font(CiboFont.bodyMd)
                                        .foregroundStyle(CiboColor.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                .screenPadding()
                .padding(.top, CiboSpacing.md)
            }
        }
        .navigationTitle("Logs")
        .navigationBarTitleDisplayMode(.inline)
    }
}
