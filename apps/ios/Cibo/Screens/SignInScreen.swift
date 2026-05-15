import SwiftUI

struct SignInScreen: View {
    enum Mode { case signIn, register }

    @EnvironmentObject private var auth: AuthStore
    @State private var mode: Mode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var name = ""
    @State private var isWorking = false
    @FocusState private var focused: Field?

    enum Field { case name, email, password }

    var body: some View {
        ZStack {
            CiboColor.background.ignoresSafeArea()
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: CiboSpacing.lg) {
                    header

                    GlassCard {
                        VStack(spacing: CiboSpacing.md) {
                            Picker("", selection: $mode) {
                                Text("Sign in").tag(Mode.signIn)
                                Text("Create account").tag(Mode.register)
                            }
                            .pickerStyle(.segmented)

                            if mode == .register {
                                CiboField(label: "Name", text: $name, icon: "person")
                                    .focused($focused, equals: .name)
                            }
                            CiboField(label: "Email", text: $email, icon: "envelope",
                                      contentType: .emailAddress, keyboard: .emailAddress)
                                .focused($focused, equals: .email)
                            CiboField(label: "Password", text: $password, icon: "lock",
                                      contentType: .password, secure: true)
                                .focused($focused, equals: .password)

                            PrimaryButton(
                                title: mode == .signIn ? "Sign in" : "Create account",
                                isLoading: isWorking,
                                isEnabled: canSubmit
                            ) { Task { await submit() } }
                        }
                    }

                    if let err = auth.lastError {
                        Text(err)
                            .font(CiboFont.body(13, weight: .semibold))
                            .foregroundStyle(CiboColor.error)
                            .padding(.horizontal, CiboSpacing.sm)
                    }

                    Text("By continuing you agree to Cibo's Terms and Privacy.")
                        .font(CiboFont.labelSm)
                        .foregroundStyle(CiboColor.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, CiboSpacing.md)
                }
                .screenPadding()
                .padding(.top, CiboSpacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .task {
            // Belt-and-braces wake — by the time the user types credentials,
            // the Render dyno is up.
            await AnalyzeClient.shared.wake()
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: CiboSpacing.sm) {
            CiboMark(size: 44)
            Text(mode == .signIn ? "Welcome back" : "Create your Cibo")
                .font(CiboFont.display(34, weight: .semibold))
                .foregroundStyle(CiboColor.onSurface)
            Text(mode == .signIn
                 ? "Pick up where you left off — your goal, logs, and history are waiting."
                 : "Tell us a name and we'll personalise every verdict to your daily target.")
                .font(CiboFont.bodyLg)
                .foregroundStyle(CiboColor.onSurfaceVariant)
        }
    }

    private var canSubmit: Bool {
        guard email.contains("@"), password.count >= 6 else { return false }
        if mode == .register, name.trimmingCharacters(in: .whitespaces).isEmpty { return false }
        return true
    }

    private func submit() async {
        focused = nil
        isWorking = true
        defer { isWorking = false }
        switch mode {
        case .signIn:
            await auth.login(email: email, password: password)
        case .register:
            await auth.register(email: email, password: password, name: name)
        }
    }
}

private struct CiboField: View {
    var label: String
    @Binding var text: String
    var icon: String
    var contentType: UITextContentType? = nil
    var keyboard: UIKeyboardType = .default
    var secure: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label.uppercased())
                .font(CiboFont.labelSm).tracking(1.2)
                .foregroundStyle(CiboColor.onSurfaceVariant)
            HStack(spacing: CiboSpacing.sm) {
                Image(systemName: icon)
                    .foregroundStyle(CiboColor.onSurfaceVariant)
                Group {
                    if secure {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }
                .textContentType(contentType)
                .keyboardType(keyboard)
                .foregroundStyle(CiboColor.onSurface)
                .font(CiboFont.bodyMd)
            }
            .padding(.horizontal, CiboSpacing.md)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: CiboRadius.md, style: .continuous)
                    .fill(CiboColor.surfaceContainerHigh)
            )
            .overlay(
                RoundedRectangle(cornerRadius: CiboRadius.md, style: .continuous)
                    .strokeBorder(CiboColor.outline.opacity(0.2), lineWidth: 1)
            )
        }
    }
}
