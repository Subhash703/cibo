import SwiftUI
import Charts

struct HistoryScreen: View {
    @EnvironmentObject private var auth: AuthStore

    @State private var history: HistoryResponse?
    @State private var range: Range = .week
    @State private var loading = true
    @State private var error: String?

    enum Range: Int, CaseIterable, Identifiable {
        case week = 7
        case month = 30
        var id: Int { rawValue }
        var label: String { self == .week ? "7 days" : "30 days" }
    }

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                    header
                    rangePicker
                    streakCard
                    kcalChartCard
                    macroChartCard
                    if let error {
                        Text(error)
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.error)
                    }
                }
                .screenPadding()
                .padding(.top, CiboSpacing.md)
                .padding(.bottom, CiboSpacing.xl)
            }
        }
        .navigationTitle("Your history")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: range) { await reload() }
    }

    // MARK: pieces

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Your history")
                .font(CiboFont.display(32, weight: .semibold))
                .foregroundStyle(CiboColor.onSurface)
            Text("How the last \(range.rawValue) days went.")
                .font(CiboFont.bodyMd)
                .foregroundStyle(CiboColor.onSurfaceVariant)
        }
    }

    private var rangePicker: some View {
        Picker("", selection: $range) {
            ForEach(Range.allCases) { Text($0.label).tag($0) }
        }
        .pickerStyle(.segmented)
    }

    @ViewBuilder
    private var streakCard: some View {
        if let h = history {
            GlassCard(tone: .glow) {
                HStack(spacing: CiboSpacing.lg) {
                    StreakStat(value: "\(h.streakDays)", label: "DAY STREAK")
                    Divider().frame(width: 1, height: 40).overlay(CiboColor.outlineVariant)
                    StreakStat(value: "\(h.goalHits)", label: "GOAL HITS")
                    Divider().frame(width: 1, height: 40).overlay(CiboColor.outlineVariant)
                    StreakStat(value: "\(loggedDays(h))", label: "DAYS LOGGED")
                }
            }
        } else if loading {
            GlassCard {
                HStack { ProgressView().tint(CiboColor.primary); Text("Pulling your history…")
                    .font(CiboFont.bodyMd).foregroundStyle(CiboColor.onSurfaceVariant) }
            }
        }
    }

    @ViewBuilder
    private var kcalChartCard: some View {
        if let h = history {
            GlassCard {
                VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                    Text("KCAL TREND")
                        .font(CiboFont.labelSm).tracking(1.4)
                        .foregroundStyle(CiboColor.primary)
                    Chart {
                        ForEach(h.days) { day in
                            LineMark(
                                x: .value("Day", short(day.date)),
                                y: .value("kcal", day.kcal)
                            )
                            .interpolationMethod(.catmullRom)
                            .foregroundStyle(CiboColor.primary)
                            .lineStyle(StrokeStyle(lineWidth: 3))
                            PointMark(
                                x: .value("Day", short(day.date)),
                                y: .value("kcal", day.kcal)
                            )
                            .foregroundStyle(day.kcal > h.dailyKcalTarget ? CiboColor.warning : CiboColor.primary)
                        }
                        RuleMark(y: .value("Goal", h.dailyKcalTarget))
                            .foregroundStyle(CiboColor.outlineVariant)
                            .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                            .annotation(position: .topLeading) {
                                Text("Goal \(h.dailyKcalTarget)")
                                    .font(CiboFont.labelSm)
                                    .foregroundStyle(CiboColor.onSurfaceVariant)
                            }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading) { _ in
                            AxisGridLine().foregroundStyle(CiboColor.outlineVariant)
                            AxisValueLabel().foregroundStyle(CiboColor.onSurfaceVariant)
                        }
                    }
                    .chartXAxis {
                        AxisMarks { _ in
                            AxisValueLabel().foregroundStyle(CiboColor.onSurfaceVariant)
                        }
                    }
                    .frame(height: 180)
                }
            }
        }
    }

    @ViewBuilder
    private var macroChartCard: some View {
        if let h = history {
            GlassCard {
                VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                    Text("MACROS PER DAY")
                        .font(CiboFont.labelSm).tracking(1.4)
                        .foregroundStyle(CiboColor.primary)
                    Chart {
                        ForEach(h.days) { day in
                            BarMark(
                                x: .value("Day", short(day.date)),
                                y: .value("Protein", day.proteinG)
                            ).foregroundStyle(by: .value("Macro", "Protein"))
                            BarMark(
                                x: .value("Day", short(day.date)),
                                y: .value("Fat", day.fatG)
                            ).foregroundStyle(by: .value("Macro", "Fat"))
                            BarMark(
                                x: .value("Day", short(day.date)),
                                y: .value("Carbs", day.carbsG)
                            ).foregroundStyle(by: .value("Macro", "Carbs"))
                        }
                    }
                    .chartForegroundStyleScale([
                        "Protein": CiboColor.primary,
                        "Fat":     CiboColor.warning,
                        "Carbs":   CiboColor.secondary,
                    ])
                    .frame(height: 180)
                }
            }
        }
    }

    // MARK: data

    private func loggedDays(_ h: HistoryResponse) -> Int {
        h.days.filter { $0.logCount > 0 }.count
    }

    private func short(_ iso: String) -> String {
        // "2026-05-15" → "May 15" → "15" for the chart axis (compact).
        guard let last = iso.split(separator: "-").last else { return iso }
        return String(last)
    }

    private func reload() async {
        guard let token = auth.token else { return }
        loading = true; error = nil
        do {
            history = try await AnalyzeClient.shared.dailySummaries(token: token, days: range.rawValue)
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private struct StreakStat: View {
    var value: String
    var label: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(CiboFont.display(28, weight: .semibold))
                .foregroundStyle(CiboColor.primary)
                .monospacedDigit()
            Text(label)
                .font(CiboFont.labelSm).tracking(1.2)
                .foregroundStyle(CiboColor.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
