import SwiftUI
import PhotosUI
import UIKit
import ActivityKit

struct PlateScreen: View {
    @EnvironmentObject private var auth: AuthStore
    @State private var state: PlateState = .idle
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false

    enum PlateState: Equatable {
        case idle
        case analyzing(UIImage)
        case result(UIImage, PlateAnalyzeResponse)
        case error(String)

        static func == (l: PlateState, r: PlateState) -> Bool {
            switch (l, r) {
            case (.idle, .idle): return true
            case (.analyzing, .analyzing): return true
            case (.result, .result): return true
            case (.error, .error): return true
            default: return false
            }
        }
    }

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            switch state {
            case .idle:                IdleView(pickerItem: $pickerItem, showCamera: $showCamera)
            case .analyzing(let img):  AnalyzingView(image: img)
            case .result(_, let res):  ResultView(response: res, onDiscard: { state = .idle }, onLog: { Task { await log(res) } })
            case .error(let m):        ErrorView(message: m, retry: { state = .idle })
            }
        }
        .navigationBarHidden(true)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task { await loadFromPicker(item) }
        }
        .sheet(isPresented: $showCamera) {
            CameraPicker { img in
                showCamera = false
                if let img { Task { await analyze(img) } }
            }
            .ignoresSafeArea()
        }
    }

    private func loadFromPicker(_ item: PhotosPickerItem) async {
        if let data = try? await item.loadTransferable(type: Data.self),
           let img = UIImage(data: data) {
            await analyze(img)
        }
        pickerItem = nil
    }

    private func analyze(_ img: UIImage) async {
        guard let token = auth.token else { state = .error("Sign in first."); return }
        state = .analyzing(img)
        guard let jpeg = img.jpegData(compressionQuality: 0.8) else {
            state = .error("Could not encode photo.")
            return
        }
        var activity: Activity<CartVerdictAttributes>?
        if #available(iOS 16.2, *) {
            activity = LiveActivityController.startAnalyzing()
        }
        do {
            let res = try await AnalyzeClient.shared.analyzePlate(token: token, jpeg: jpeg)
            if #available(iOS 16.2, *) {
                await LiveActivityController.update(
                    activity,
                    signal: res.verdict.signal,
                    headline: res.dishName,
                    kcal: res.macros.kcal,
                    swap: res.verdict.oneLiner,
                    savedKcal: nil
                )
            }
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                state = .result(img, res)
            }
        } catch {
            if #available(iOS 16.2, *) {
                await LiveActivityController.fail(activity, message: "Couldn't analyze")
            }
            state = .error(error.localizedDescription)
        }
    }

    private func log(_ res: PlateAnalyzeResponse) async {
        guard let token = auth.token else { return }
        let req = MealLogRequest(
            macros: res.macros,
            items: [MatchedItem(name: res.dishName, qty: 1, kcal: res.macros.kcal)],
            healthScore: res.healthScore
        )
        _ = try? await AnalyzeClient.shared.logMeal(token: token, request: req)
        await auth.refreshToday()
        state = .idle
    }
}

// MARK: idle (cibo_plate_analysis)

private struct IdleView: View {
    @Binding var pickerItem: PhotosPickerItem?
    @Binding var showCamera: Bool

    var body: some View {
        VStack(spacing: CiboSpacing.lg) {
            HStack {
                Text("Plate")
                    .font(CiboFont.display(34, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
                Spacer()
                StatusPill(text: "Ready", tone: .positive)
            }

            ZStack {
                RoundedRectangle(cornerRadius: CiboRadius.xl, style: .continuous)
                    .fill(CiboColor.surfaceContainerHigh)
                    .aspectRatio(1, contentMode: .fit)
                    .overlay(
                        RoundedRectangle(cornerRadius: CiboRadius.xl, style: .continuous)
                            .strokeBorder(CiboColor.primary.opacity(0.5),
                                          style: StrokeStyle(lineWidth: 2, dash: [10, 8]))
                    )
                VStack(spacing: CiboSpacing.sm) {
                    Image(systemName: "camera.viewfinder")
                        .font(.system(size: 48, weight: .light))
                        .foregroundStyle(CiboColor.primary)
                    Text("Ready to scan")
                        .font(CiboFont.h2)
                        .foregroundStyle(CiboColor.onSurface)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Snap your meal.")
                    .font(CiboFont.display(28, weight: .semibold))
                    .foregroundStyle(CiboColor.onSurface)
                Text("Cibo coaches you against today's goal.")
                    .font(CiboFont.bodyMd)
                    .foregroundStyle(CiboColor.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: CiboSpacing.gutter) {
                PrimaryButton(title: "Camera", icon: "camera.fill") { showCamera = true }
                PhotosPicker(selection: $pickerItem, matching: .images) {
                    HStack(spacing: CiboSpacing.sm) {
                        Image(systemName: "photo.on.rectangle")
                        Text("From library").font(CiboFont.body(15, weight: .semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .foregroundStyle(CiboColor.primary)
                    .overlay(Capsule().strokeBorder(CiboColor.primary.opacity(0.6), lineWidth: 1))
                }
            }

            InsightCard(label: "AI INSIGHT",
                        text: "Scanning a photo helps me track macros more accurately than text alone. Give it a try!")

            Spacer(minLength: 0)
        }
        .screenPadding()
        .padding(.top, CiboSpacing.md)
    }
}

// MARK: analyzing (analyzing_plate)

private struct AnalyzingView: View {
    var image: UIImage
    @State private var spin: Double = 0

    var body: some View {
        VStack(spacing: CiboSpacing.lg) {
            HStack {
                Text("Cibo")
                    .font(CiboFont.display(28, weight: .semibold))
                    .foregroundStyle(CiboColor.primary)
                Spacer()
            }

            ZStack {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 240, height: 240)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .trim(from: 0, to: 0.25)
                            .stroke(CiboColor.primary, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                            .rotationEffect(.degrees(spin))
                    )
                    .shadow(color: CiboColor.primary.opacity(0.4), radius: 30)
            }
            .padding(.top, CiboSpacing.lg)

            VStack(spacing: CiboSpacing.sm) {
                Text("Looking at your plate…")
                    .font(CiboFont.display(28, weight: .semibold))
                    .foregroundStyle(CiboColor.primary)
                    .multilineTextAlignment(.center)
                Text("AI is identifying ingredients and estimating portion sizes.")
                    .font(CiboFont.bodyMd)
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, CiboSpacing.md)

            GlassCard {
                VStack(alignment: .leading, spacing: CiboSpacing.sm) {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkles").foregroundStyle(CiboColor.primary)
                        Text("LIVE RECOGNITION")
                            .font(CiboFont.labelSm).tracking(1.4)
                            .foregroundStyle(CiboColor.primary)
                    }
                    Text("Working out what's on your plate…")
                        .font(CiboFont.h2)
                        .foregroundStyle(CiboColor.onSurface)
                    HStack(spacing: 6) {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(CiboColor.primary)
                        Text("Doing the math…")
                            .font(CiboFont.bodyMd)
                            .foregroundStyle(CiboColor.onSurfaceVariant)
                    }
                }
            }

            Spacer()
        }
        .screenPadding()
        .onAppear {
            withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) {
                spin = 360
            }
        }
    }
}

// MARK: result (plate_result)

private struct ResultView: View {
    var response: PlateAnalyzeResponse
    var onDiscard: () -> Void
    var onLog: () -> Void

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                HStack {
                    Text("Cibo")
                        .font(CiboFont.display(28, weight: .semibold))
                        .foregroundStyle(CiboColor.primary)
                    Spacer()
                }

                VerdictBanner(
                    signal: signal,
                    headline: signal.headlineLabel,
                    detail: response.verdict.oneLiner
                )

                VStack(alignment: .leading, spacing: CiboSpacing.xs) {
                    Text(response.dishName)
                        .font(CiboFont.display(32, weight: .semibold))
                        .foregroundStyle(CiboColor.onSurface)
                    Text(response.dishDescription)
                        .font(CiboFont.bodyMd)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: CiboSpacing.md) {
                    Stat(label: "KCAL",    value: "\(response.macros.kcal)")
                    Stat(label: "PROTEIN", value: "\(response.macros.proteinG)g")
                    Stat(label: "FAT",     value: "\(response.macros.fatG)g")
                    Stat(label: "CARBS",   value: "\(response.macros.carbsG)g")
                }

                if !response.verdict.reason.isEmpty {
                    InsightCard(label: "NUTRITIONIST INSIGHT", text: response.verdict.reason)
                }

                HStack(spacing: CiboSpacing.gutter) {
                    SecondaryButton(title: "Discard") { onDiscard() }
                    PrimaryButton(title: "I ate this", icon: "checkmark") { onLog() }
                }
                .padding(.top, CiboSpacing.sm)
            }
            .screenPadding()
            .padding(.top, CiboSpacing.md)
            .padding(.bottom, CiboSpacing.xl)
        }
    }

    private var signal: VerdictBanner.Signal {
        switch response.verdict.signal.lowercased() {
        case "green":  return .green
        case "yellow": return .yellow
        default:       return .red
        }
    }
}

private extension VerdictBanner.Signal {
    var headlineLabel: String {
        switch self {
        case .green:  return "Go for it"
        case .yellow: return "Fair choice"
        case .red:    return "Heads up"
        }
    }
}

private struct Stat: View {
    var label: String; var value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(CiboFont.labelSm).tracking(1.4)
                .foregroundStyle(CiboColor.onSurfaceVariant)
            Text(value).font(CiboFont.display(24, weight: .semibold))
                .foregroundStyle(CiboColor.onSurface)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CiboSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: CiboRadius.md, style: .continuous)
                .fill(CiboColor.surfaceContainer)
        )
    }
}

private struct ErrorView: View {
    var message: String
    var retry: () -> Void
    var body: some View {
        VStack(spacing: CiboSpacing.lg) {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundStyle(CiboColor.error)
            Text(message)
                .font(CiboFont.bodyLg)
                .foregroundStyle(CiboColor.onSurface)
                .multilineTextAlignment(.center)
            PrimaryButton(title: "Try again") { retry() }
                .frame(maxWidth: 280)
            Spacer()
        }
        .screenPadding()
    }
}

// MARK: tiny UIImagePickerController wrapper

struct CameraPicker: UIViewControllerRepresentable {
    var onImage: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = .camera
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coord { Coord(onImage: onImage) }

    final class Coord: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onImage: (UIImage?) -> Void
        init(onImage: @escaping (UIImage?) -> Void) { self.onImage = onImage }
        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            onImage(info[.originalImage] as? UIImage)
        }
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onImage(nil)
        }
    }
}
