import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: AppSession

    var body: some View {
        ZStack {
            if !session.onboardingDone {
                OnboardingView()
            } else if session.isAuthenticated {
                DashboardView()
                    .task { await session.refresh() }
            } else {
                AuthView()
            }
        }
        .alert(String(localized: "error.title"), isPresented: Binding(
            get: { session.errorMessage != nil },
            set: { if !$0 { session.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(session.errorMessage ?? "")
        }
    }
}
