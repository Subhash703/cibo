import SwiftUI

struct RootView: View {
    @EnvironmentObject private var auth: AuthStore
    @AppStorage("cibo.onboarded") private var onboarded: Bool = false
    @State private var showAuthSheet = false

    var body: some View {
        Group {
            if !onboarded {
                NavigationStack {
                    OnboardingFlow { /* falls through to next branch on @AppStorage flip */ }
                }
            } else if !auth.isSignedIn {
                NavigationStack { SignInScreen() }
            } else {
                MainTabs()
            }
        }
        .background(CiboColor.background.ignoresSafeArea())
    }
}

private struct MainTabs: View {
    @State private var tab: Tab = .home
    enum Tab: Hashable { case home, plate, logs, profile }

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { DashboardScreen() }
                .tabItem { Label("Home",  systemImage: "square.grid.2x2.fill") }
                .tag(Tab.home)

            NavigationStack { PlateScreen() }
                .tabItem { Label("Plate", systemImage: "sparkles") }
                .tag(Tab.plate)

            NavigationStack { LogsScreen() }
                .tabItem { Label("Logs",  systemImage: "doc.text") }
                .tag(Tab.logs)

            NavigationStack { ProfileScreen() }
                .tabItem { Label("Profile", systemImage: "person.fill") }
                .tag(Tab.profile)
        }
        .tint(CiboColor.primary)
    }
}
