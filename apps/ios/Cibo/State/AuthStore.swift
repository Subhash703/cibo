import Foundation
import SwiftUI

@MainActor
final class AuthStore: ObservableObject {
    @Published private(set) var token: String?
    @Published private(set) var user: UserPublic?
    @Published private(set) var todaySummary: DailySummary?
    @Published private(set) var todayLogs: [MealLogPublic] = []
    @Published var lastError: String?

    // Free Apple accounts can't create App Groups, so the JWT lives in
    // the main app's own UserDefaults. The Share Extension talks to the
    // unauthenticated /analyze-vision endpoint (auth optional per CLAUDE.md),
    // so it doesn't need to read this.
    private let tokenKey = "cibo.token"
    private var defaults: UserDefaults { .standard }

    var isSignedIn: Bool { token != nil && user != nil }

    init() {
        if let stored = defaults.string(forKey: tokenKey) {
            self.token = stored
            Task { await refreshMe() }
        }
    }

    func register(email: String, password: String, name: String?) async {
        await runAuth { try await AnalyzeClient.shared.register(email: email, password: password, name: name) }
    }

    func login(email: String, password: String) async {
        await runAuth { try await AnalyzeClient.shared.login(email: email, password: password) }
    }

    func signOut() {
        token = nil
        user = nil
        todaySummary = nil
        todayLogs = []
        defaults.removeObject(forKey: tokenKey)
    }

    func refreshMe() async {
        guard let token else { return }
        do {
            let me = try await AnalyzeClient.shared.getMe(token: token)
            self.user = me
            await refreshToday()
        } catch {
            // Stale token — drop it silently.
            signOut()
        }
    }

    func refreshToday() async {
        guard let token else { return }
        async let summary = try? await AnalyzeClient.shared.todaySummary(token: token)
        async let logs    = try? await AnalyzeClient.shared.todayMealLogs(token: token)
        self.todaySummary = await summary
        self.todayLogs    = await logs ?? []
    }

    func updateGoal(kcal: Int) async {
        await updateProfile(ProfileUpdate(dailyKcalTarget: kcal))
    }

    /// Generic profile patch — used by the Edit Info sheet.
    func updateProfile(_ update: ProfileUpdate) async {
        guard let token else { return }
        do {
            let updated = try await AnalyzeClient.shared.updateProfile(
                token: token, update: update
            )
            self.user = updated
        } catch {
            self.lastError = error.localizedDescription
        }
    }

    /// Upload a new avatar image. Updates `user.picture` to the new URL
    /// (server returns it on success).
    func uploadAvatar(jpeg: Data) async {
        guard let token else { return }
        do {
            let updated = try await AnalyzeClient.shared.uploadAvatar(token: token, jpeg: jpeg)
            self.user = updated
        } catch {
            self.lastError = error.localizedDescription
        }
    }

    func deleteAvatar() async {
        guard let token else { return }
        do {
            let updated = try await AnalyzeClient.shared.deleteAvatar(token: token)
            self.user = updated
        } catch {
            self.lastError = error.localizedDescription
        }
    }

    private func runAuth(_ op: () async throws -> AuthResponse) async {
        lastError = nil
        do {
            let resp = try await op()
            token = resp.token
            user = resp.user
            defaults.set(resp.token, forKey: tokenKey)
            await refreshToday()
        } catch {
            lastError = error.localizedDescription
        }
    }
}
