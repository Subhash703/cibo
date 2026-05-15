import SwiftUI

/// Edit metrics & biology — what powers the personalised verdict.
struct ProfileEditSheet: View {
    @EnvironmentObject private var auth: AuthStore
    @Environment(\.dismiss) private var dismiss

    @State private var sex: String = "male"
    @State private var birthYear: String = ""
    @State private var weightKg: Double = 70
    @State private var heightCm: Double = 170
    @State private var activity: String = "moderate"
    @State private var goal: String = "stay_healthy"
    @State private var saving = false

    private let activityLevels: [(id: String, label: String)] = [
        ("sedentary",  "Sedentary"),
        ("light",      "Light"),
        ("moderate",   "Moderate"),
        ("active",     "Active"),
        ("very_active", "Very active"),
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                CiboColor.background.ignoresSafeArea()
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                        sectionHeader("Sex")
                        Picker("", selection: $sex) {
                            Text("Male").tag("male")
                            Text("Female").tag("female")
                            Text("Other").tag("other")
                        }
                        .pickerStyle(.segmented)

                        sectionHeader("Birth year")
                        TextField("e.g. 1992", text: $birthYear)
                            .keyboardType(.numberPad)
                            .padding(.horizontal, CiboSpacing.md)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: CiboRadius.md, style: .continuous)
                                    .fill(CiboColor.surfaceContainerHigh)
                            )
                            .foregroundStyle(CiboColor.onSurface)
                            .font(CiboFont.bodyMd)

                        sliderBlock(
                            title: "Weight",
                            value: $weightKg,
                            range: 30...200,
                            step: 0.5,
                            unit: "kg",
                            display: { String(format: "%.1f", $0) }
                        )

                        sliderBlock(
                            title: "Height",
                            value: $heightCm,
                            range: 120...220,
                            step: 1,
                            unit: "cm",
                            display: { String(Int($0)) }
                        )

                        sectionHeader("Activity level")
                        FlowLayout(spacing: 8) {
                            ForEach(activityLevels, id: \.id) { level in
                                ActivityChip(
                                    label: level.label,
                                    selected: activity == level.id
                                ) { activity = level.id }
                            }
                        }

                        sectionHeader("Your goal")
                        FlowLayout(spacing: 8) {
                            ForEach(CiboGoals, id: \.id) { g in
                                ActivityChip(
                                    label: g.label,
                                    selected: goal == g.id
                                ) { goal = g.id }
                            }
                        }

                        InsightCard(
                            label: "WHY THIS MATTERS",
                            text: "Cibo uses your body stats and chosen goal to generate a personal daily insight you'll see on the Goal card."
                        )

                        if let err = auth.lastError {
                            Text(err)
                                .font(CiboFont.body(13, weight: .semibold))
                                .foregroundStyle(CiboColor.error)
                        }

                        PrimaryButton(
                            title: "Save",
                            isLoading: saving,
                            isEnabled: validBirthYear
                        ) {
                            Task { await save() }
                        }
                    }
                    .screenPadding()
                    .padding(.top, CiboSpacing.md)
                    .padding(.bottom, CiboSpacing.xl)
                }
            }
            .navigationTitle("Edit info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }
            }
        }
        .onAppear { hydrate() }
    }

    // MARK: helpers

    private func sectionHeader(_ text: String) -> some View {
        Text(text.uppercased())
            .font(CiboFont.labelSm).tracking(1.4)
            .foregroundStyle(CiboColor.onSurfaceVariant)
    }

    private func sliderBlock(
        title: String,
        value: Binding<Double>,
        range: ClosedRange<Double>,
        step: Double,
        unit: String,
        display: @escaping (Double) -> String
    ) -> some View {
        VStack(alignment: .leading, spacing: CiboSpacing.sm) {
            sectionHeader(title)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(display(value.wrappedValue))
                    .font(CiboFont.display(34, weight: .semibold))
                    .foregroundStyle(CiboColor.primary)
                    .monospacedDigit()
                Text(unit)
                    .font(CiboFont.bodyLg)
                    .foregroundStyle(CiboColor.onSurfaceVariant)
            }
            Slider(value: value, in: range, step: step).tint(CiboColor.primary)
        }
    }

    private var validBirthYear: Bool {
        guard let y = Int(birthYear) else { return false }
        let now = Calendar.current.component(.year, from: Date())
        return (now - 100)...(now - 5) ~= y
    }

    private func hydrate() {
        if let s = auth.user?.sex { sex = s }
        if let y = auth.user?.birthYear { birthYear = "\(y)" }
        if let w = auth.user?.weightKg { weightKg = w }
        if let h = auth.user?.heightCm { heightCm = h }
        if let a = auth.user?.activityLevel { activity = a }
        if let g = auth.user?.goal { goal = g }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let update = ProfileUpdate(
            dailyKcalTarget: nil,
            birthYear: Int(birthYear),
            sex: sex,
            weightKg: weightKg,
            heightCm: heightCm,
            activityLevel: activity,
            goal: goal
        )
        await auth.updateProfile(update)
        if auth.lastError == nil { dismiss() }
    }
}

private struct ActivityChip: View {
    var label: String
    var selected: Bool
    var onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(CiboFont.body(13, weight: .semibold))
                .foregroundStyle(selected ? CiboColor.onPrimary : CiboColor.onSurface)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule().fill(selected ? CiboColor.primary : CiboColor.surfaceContainerHigh)
                )
                .overlay(
                    Capsule().strokeBorder(
                        selected ? Color.clear : CiboColor.outline.opacity(0.2),
                        lineWidth: 1
                    )
                )
        }
        .buttonStyle(.plain)
    }
}
