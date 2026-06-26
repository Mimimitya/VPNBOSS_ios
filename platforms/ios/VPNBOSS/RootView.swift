import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: AppSession

    var body: some View {
        ZStack {
            Color(red: 0.965, green: 0.965, blue: 0.965).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                Image("logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 208, height: 34)
                    .padding(.top, 26)

                Spacer()

                if session.token.isEmpty {
                    authView
                } else {
                    vpnView
                }

                Spacer()
            }
            .padding(.horizontal, 21)
        }
        .font(.system(.body, design: .rounded))
    }

    private var authView: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Перед началом работы авторизуйтесь через сайт VPNBOSS")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .fixedSize(horizontal: false, vertical: true)
            Text("Откроется официальный вход vpnboss.space. После подтверждения приложение автоматически подтянет подписку и серверы.")
                .font(.system(size: 15, weight: .regular, design: .rounded))
                .foregroundStyle(.secondary)
            Button(action: session.startAuth) {
                HStack {
                    Image("telegram").resizable().frame(width: 24, height: 24)
                    Text("Войти через vpnboss.space")
                        .fontWeight(.bold)
                }
                .frame(maxWidth: .infinity, minHeight: 54)
                .foregroundStyle(.white)
                .background(.black, in: Capsule())
            }
            Text(session.message)
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundStyle(.secondary)
        }
    }

    private var vpnView: some View {
        VStack(spacing: 18) {
            Button(action: session.toggleVpn) {
                PowerSymbol(active: session.connected)
                    .frame(width: 217, height: 217)
            }
            Text("Выберите локацию сервера")
                .font(.system(size: 16, weight: .regular, design: .rounded))
            Text(current.flag)
                .font(.system(size: 46))
            Text(current.name)
                .font(.system(size: 24, weight: .regular, design: .rounded))
            Text(session.connected ? "Подключено" : current.detail)
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundStyle(.secondary)
            Button(action: session.toggleVpn) {
                Text(session.connected ? "ОТКЛЮЧИТЬ VPN" : "АВТОПОДКЛЮЧЕНИЕ")
                    .font(.system(size: 16, weight: .bold, design: .rounded))
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .foregroundStyle(.white)
                    .background(.black, in: Capsule())
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var current: RouteConfig {
        session.routes[min(max(session.selected, 0), session.routes.count - 1)]
    }
}

struct PowerSymbol: View {
    let active: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(active ? Color.black : Color(red: 0.17, green: 0.03, blue: 0.06))
            Rectangle()
                .fill(.white)
                .frame(width: 13, height: 60)
                .clipShape(Capsule())
                .offset(y: -28)
            Circle()
                .trim(from: 0.13, to: 0.87)
                .stroke(.white, style: StrokeStyle(lineWidth: 13, lineCap: .round))
                .frame(width: 92, height: 92)
                .rotationEffect(.degrees(37))
                .offset(y: 20)
        }
        .shadow(color: .black.opacity(0.18), radius: 22, y: 14)
    }
}
