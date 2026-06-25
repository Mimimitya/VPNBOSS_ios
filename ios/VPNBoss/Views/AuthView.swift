import SwiftUI

struct AuthView: View {
    @EnvironmentObject private var session: AppSession
    @Environment(\.openURL) private var openURL
    @State private var isRegister = false
    @State private var login = ""
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var telegramToken: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HeaderBar()
                    .padding(.bottom, 10)

                Text("auth.title")
                    .font(.system(size: 32, weight: .black, design: .rounded))
                    .textCase(.uppercase)

                Picker("", selection: $isRegister) {
                    Text("auth.login").tag(false)
                    Text("auth.register").tag(true)
                }
                .pickerStyle(.segmented)

                if isRegister {
                    BossTextField(title: "auth.name", text: $displayName)
                    BossTextField(title: "auth.email", text: $email, keyboard: .emailAddress)
                }
                BossTextField(title: "auth.login_hint", text: $login)
                BossSecureField(title: "auth.password", text: $password)

                Button {
                    Task {
                        if isRegister {
                            await session.register(login: login, email: email, password: password, displayName: displayName)
                        } else {
                            await session.login(login: login, password: password)
                        }
                    }
                } label: {
                    Text(isRegister ? "auth.register" : "auth.login")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(BossButtonStyle())

                Button {
                    Task {
                        guard let response = await session.beginTelegramAuth(mode: isRegister ? "register" : "login") else { return }
                        telegramToken = response.webToken
                        openURL(response.deepLink ?? response.botLink)
                        await session.pollTelegram(webToken: response.webToken)
                    }
                } label: {
                    Label("auth.telegram", systemImage: "paperplane.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(BossSecondaryButtonStyle())

                if telegramToken != nil {
                    Text("auth.telegram_wait")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(24)
        }
        .overlay {
            if session.isLoading { ProgressView().controlSize(.large).tint(.black) }
        }
    }
}
