import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var session: AppSession
    @State private var page = 0

    private let pages = [
        OnboardingPage(title: "onboarding.welcome.title", body: "onboarding.welcome.body", symbol: "bolt.shield.fill"),
        OnboardingPage(title: "onboarding.telegram.title", body: "onboarding.telegram.body", symbol: "paperplane.fill"),
        OnboardingPage(title: "onboarding.trial.title", body: "onboarding.trial.body", symbol: "sparkles"),
        OnboardingPage(title: "onboarding.autoconnect.title", body: "onboarding.autoconnect.body", symbol: "power")
    ]

    var body: some View {
        VStack(spacing: 0) {
            HeaderBar()
            TabView(selection: $page) {
                ForEach(pages.indices, id: \.self) { index in
                    VStack(alignment: .leading, spacing: 24) {
                        Spacer()
                        ZStack {
                            Circle()
                                .fill(index == 3 ? Color.black : Color.clear)
                                .frame(width: 150, height: 150)
                            Image(systemName: pages[index].symbol)
                                .font(.system(size: index == 3 ? 58 : 42, weight: .black))
                                .foregroundStyle(index == 3 ? .white : .black)
                                .symbolEffect(.pulse, options: .repeating, value: page)
                        }
                        Text(LocalizedStringKey(pages[index].title))
                            .font(.system(size: 27, weight: .black, design: .rounded))
                            .textCase(.uppercase)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(LocalizedStringKey(pages[index].body))
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundStyle(.secondary)
                            .lineSpacing(4)
                        Spacer()
                    }
                    .padding(.horizontal, 28)
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            Button {
                if page == pages.count - 1 { session.finishOnboarding() }
                else { withAnimation(.spring(response: 0.5, dampingFraction: 0.82)) { page += 1 } }
            } label: {
                Text(page == pages.count - 1 ? "button.start" : "button.next")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(BossButtonStyle())
            .padding(24)
        }
        .background(Color.white)
    }
}

private struct OnboardingPage {
    let title: String
    let body: String
    let symbol: String
}
