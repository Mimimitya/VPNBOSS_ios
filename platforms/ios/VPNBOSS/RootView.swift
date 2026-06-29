import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: AppSession
    @State private var showCabinet = false
    @State private var showServers = false
    @State private var onboardingEmail = ""
    @State private var onboardingPassword = ""
    @State private var onboardingConfirmation = ""
    @GestureState private var carouselDrag: CGFloat = 0

    private let paper = Color(red: 246 / 255, green: 246 / 255, blue: 244 / 255)

    var body: some View {
        ZStack {
            paper.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                if session.token.isEmpty { authView } else { homeView }
            }
            .padding(.horizontal, 24)
            .padding(.top, 10)
            .padding(.bottom, 16)

            if showCabinet { cabinetOverlay }
            if showServers { serverOverlay }
            if session.needsProfileCompletion { profileCompletionOverlay }
        }
        .tint(.black)
        .alert("VPNBOSS", isPresented: $session.showError) {
            Button("Закрыть", role: .cancel) {}
        } message: { Text(session.message) }
    }

    private var header: some View {
        HStack {
            Image("logo")
                .resizable()
                .scaledToFit()
                .frame(width: 44, height: 44)
                .clipShape(Circle())
            Spacer()
            Circle()
                .fill(session.connected ? .black : Color(white: 0.73))
                .frame(width: 7, height: 7)
            Text(session.connected ? "Подключено" : "VPNBOSS")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Color(white: 0.54))
        }
        .frame(height: 44)
    }

    private var authView: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer()
            Text("ПЕРВЫЙ ЗАПУСК")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color(white: 0.54))
            Text("Войдите через\nvpnboss.space")
                .font(.system(size: 34, weight: .bold))
                .lineSpacing(-2)
                .padding(.top, 10)
            Text("Откроется официальный сайт. После подтверждения приложение само загрузит вашу подписку и доступные серверы.")
                .font(.system(size: 15))
                .foregroundStyle(Color(white: 0.32))
                .lineSpacing(3)
                .padding(.top, 20)
            FilledButton(title: session.authorizing ? "ОЖИДАНИЕ ПОДТВЕРЖДЕНИЯ" : "ВОЙТИ ЧЕРЕЗ САЙТ") {
                session.startAuth()
            }
            .disabled(session.authorizing)
            .padding(.top, 34)
            Text("Сессия сохраняется на этом устройстве")
                .font(.system(size: 12))
                .foregroundStyle(Color(white: 0.54))
                .frame(maxWidth: .infinity)
                .padding(.top, 14)
            Spacer().frame(height: 55)
        }
    }

    private var homeView: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 20)
            PowerButton(state: session.connectionState) { session.connectSelected() }
                .frame(width: 234, height: 234)
            Text(connectionTitle)
                .font(.system(size: 15, weight: .bold))
                .padding(.top, 6)

            HStack(spacing: 0) {
                Button { session.changeServer(-1) } label: { Image(systemName: "chevron.left").font(.system(size: 25, weight: .light)).frame(width: 46, height: 92) }
                FlagBubble(code: previous.flag, size: 58, prominent: false)
                Spacer().frame(width: 17)
                Button { showServers = true } label: { FlagBubble(code: current.flag, size: 92, prominent: true) }
                Spacer().frame(width: 17)
                FlagBubble(code: next.flag, size: 58, prominent: false)
                Button { session.changeServer(1) } label: { Image(systemName: "chevron.right").font(.system(size: 25, weight: .light)).frame(width: 46, height: 92) }
            }
            .frame(height: 92)
            .padding(.top, 16)
            .contentShape(Rectangle())
            .offset(x: carouselDrag * 0.35)
            .gesture(
                DragGesture(minimumDistance: 18)
                    .updating($carouselDrag) { value, state, _ in state = value.translation.width }
                    .onEnded { value in
                        guard abs(value.translation.width) > 36 else { return }
                        session.changeServer(value.translation.width < 0 ? 1 : -1)
                    }
            )
            .animation(.spring(response: 0.28, dampingFraction: 0.82), value: session.selected)

            Text(current.name)
                .font(.system(size: 22, weight: .bold))
                .lineLimit(1)
                .minimumScaleFactor(0.72)
                .padding(.top, 12)
            Text(connectionDetail)
                .font(.system(size: 13))
                .foregroundStyle(Color(white: 0.54))
                .lineLimit(1)
                .padding(.top, 7)

            FilledButton(title: session.connected ? "ОТКЛЮЧИТЬ" : "НАЙТИ ЛУЧШИЙ СЕРВЕР") {
                session.connected ? session.disconnect() : session.connectAutomatic()
            }
            .padding(.top, 26)
            OutlineButton(title: "ЛИЧНЫЙ КАБИНЕТ") { showCabinet = true }
                .padding(.top, 9)
            Spacer(minLength: 4)
        }
        .multilineTextAlignment(.center)
    }

    private var connectionTitle: String {
        if session.connecting { return "Устанавливаем соединение" }
        if session.connected { return "VPNBOSS включён" }
        return "Выберите локацию сервера"
    }

    private var connectionDetail: String {
        if session.connecting { return "Устанавливаем защищённый маршрут" }
        if session.connected { return "Подключено · \(current.detail)" }
        return current.detail
    }

    private var current: RouteConfig { session.route(at: session.selected) }
    private var previous: RouteConfig { session.route(at: session.selected - 1) }
    private var next: RouteConfig { session.route(at: session.selected + 1) }

    private var cabinetOverlay: some View {
        ModalBackdrop {
            Text("Личный кабинет").font(.system(size: 24, weight: .bold)).frame(maxWidth: .infinity, alignment: .leading)
            Text("Управление подпиской и аккаунтом находится на официальном сайте VPNBOSS.")
                .font(.system(size: 14)).foregroundStyle(Color(white: 0.32)).lineSpacing(3).padding(.top, 11)
            Text("vpnboss.space").font(.system(size: 17, weight: .bold)).padding(.top, 22).frame(maxWidth: .infinity, alignment: .leading)
            FilledButton(title: "ОТКРЫТЬ САЙТ") {
                UIApplication.shared.open(URL(string: "https://vpnboss.space/cabinet")!)
                showCabinet = false
            }.padding(.top, 22)
            OutlineButton(title: "ЗАКРЫТЬ") { showCabinet = false }.padding(.top, 8)
        }
    }

    private var serverOverlay: some View {
        ModalBackdrop {
            Text("Выберите сервер").font(.system(size: 23, weight: .bold)).frame(maxWidth: .infinity, alignment: .leading)
            Text("Подключение пойдёт именно через выбранную локацию")
                .font(.system(size: 13)).foregroundStyle(Color(white: 0.32)).padding(.top, 8)
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(Array(session.routes.enumerated()), id: \.element.id) { index, route in
                        Button {
                            session.select(index)
                            showServers = false
                        } label: {
                            HStack(spacing: 12) {
                                FlagBubble(code: route.flag, size: 42, prominent: false)
                                Text(route.name).font(.system(size: 15, weight: .bold)).foregroundStyle(.black)
                                Spacer()
                                Image(systemName: index == session.selected ? "circle.inset.filled" : "circle")
                            }
                            .padding(.horizontal, 14).frame(height: 62)
                            .background(index == session.selected ? Color(white: 0.92) : .white, in: RoundedRectangle(cornerRadius: 7))
                        }
                    }
                }
            }.frame(maxHeight: 330).padding(.top, 18)
            OutlineButton(title: "ЗАКРЫТЬ") { showServers = false }.padding(.top, 12)
        }
    }

    private var profileCompletionOverlay: some View {
        ModalBackdrop {
            Text("Завершите регистрацию")
                .font(.system(size: 24, weight: .bold))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("Укажите email и придумайте пароль. После этого мы сразу подарим вам 5 дней VPNBOSS.")
                .font(.system(size: 14))
                .foregroundStyle(Color(white: 0.32))
                .lineSpacing(3)
                .padding(.top, 10)

            TextField("Email", text: $onboardingEmail)
                .textInputAutocapitalization(.never)
                .keyboardType(.emailAddress)
                .autocorrectionDisabled()
                .textContentType(.emailAddress)
                .modifier(ProfileFieldStyle())
                .padding(.top, 20)
            SecureField("Пароль", text: $onboardingPassword)
                .textContentType(.newPassword)
                .modifier(ProfileFieldStyle())
                .padding(.top, 9)
            SecureField("Повторите пароль", text: $onboardingConfirmation)
                .textContentType(.newPassword)
                .modifier(ProfileFieldStyle())
                .padding(.top, 9)

            FilledButton(title: session.completingProfile ? "СОЗДАЁМ ДОСТУП..." : "ПОЛУЧИТЬ 5 ДНЕЙ БЕСПЛАТНО") {
                session.completeProfile(email: onboardingEmail, password: onboardingPassword, confirmation: onboardingConfirmation)
            }
            .disabled(session.completingProfile)
            .padding(.top, 18)
            Text("75 ГБ · 3 устройства · без оплаты")
                .font(.system(size: 12))
                .foregroundStyle(Color(white: 0.54))
                .frame(maxWidth: .infinity)
                .padding(.top, 11)
        }
        .onAppear {
            if onboardingEmail.isEmpty { onboardingEmail = session.profileEmail }
        }
    }
}

private struct ProfileFieldStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.system(size: 16))
            .padding(.horizontal, 15)
            .frame(height: 52)
            .background(.white, in: RoundedRectangle(cornerRadius: 7))
            .overlay(RoundedRectangle(cornerRadius: 7).stroke(Color.black.opacity(0.14)))
    }
}

private struct PowerButton: View {
    let state: ConnectionState
    let action: () -> Void
    @State private var spin = false

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(state == .connected ? Color.black.opacity(0.08) : Color.black.opacity(0.04)).frame(width: 234, height: 234)
                Circle()
                    .fill(state == .off ? Color(red: 43 / 255, green: 7 / 255, blue: 16 / 255) : state == .connecting ? Color(red: 23 / 255, green: 25 / 255, blue: 24 / 255) : Color(red: 112 / 255, green: 128 / 255, blue: 120 / 255))
                    .frame(width: 210, height: 210)
                    .shadow(color: state == .connected ? .black.opacity(0.18) : .clear, radius: 16, y: 10)
                if state == .connecting {
                    Circle().trim(from: 0, to: 0.69).stroke(.white, style: StrokeStyle(lineWidth: 13, lineCap: .round)).frame(width: 158, height: 158).rotationEffect(.degrees(spin ? 360 : 0))
                } else {
                    Image(systemName: "power").font(.system(size: 91, weight: .light)).foregroundStyle(.white)
                }
            }
        }
        .buttonStyle(PressButtonStyle())
        .onAppear { spin = state == .connecting }
        .onChange(of: state) { spin = $0 == .connecting }
        .animation(state == .connecting ? .linear(duration: 1.05).repeatForever(autoreverses: false) : .easeOut(duration: 0.2), value: spin)
    }
}

private struct FlagBubble: View {
    let code: String
    let size: CGFloat
    let prominent: Bool

    var body: some View {
        ZStack {
            Circle().fill(.white).shadow(color: .black.opacity(prominent ? 0.13 : 0.07), radius: prominent ? 8 : 3, y: 3)
            FlagView(code: code).clipShape(Circle()).padding(size * 0.08)
        }
        .frame(width: size, height: size)
        .opacity(prominent ? 1 : 0.56)
    }
}

private struct FlagView: View {
    let code: String
    var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width, h = proxy.size.height
            ZStack {
                switch code {
                case "🇩🇰":
                    Color(red: 0.76, green: 0.05, blue: 0.12); Rectangle().fill(.white).frame(width: w * 0.1); Rectangle().fill(.white).frame(height: h * 0.1).offset(x: -w * 0.15)
                case "🇩🇪":
                    VStack(spacing: 0) { Color.black; Color(red: 0.82, green: 0.02, blue: 0.08); Color(red: 1, green: 0.8, blue: 0) }
                case "🇷🇺":
                    VStack(spacing: 0) { Color.white; Color(red: 0.04, green: 0.31, blue: 0.68); Color(red: 0.84, green: 0.07, blue: 0.12) }
                case "🇪🇸":
                    VStack(spacing: 0) { Color(red: 0.69, green: 0.03, blue: 0.12).frame(height: h * 0.25); Color(red: 1, green: 0.77, blue: 0.05); Color(red: 0.69, green: 0.03, blue: 0.12).frame(height: h * 0.25) }
                default:
                    Color.white; Image(systemName: "globe").font(.system(size: sizeFor(w), weight: .medium)).foregroundStyle(.black)
                }
            }
        }
    }
    private func sizeFor(_ width: CGFloat) -> CGFloat { width * 0.42 }
}

private struct ModalBackdrop<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        ZStack {
            Color.black.opacity(0.28).ignoresSafeArea()
            VStack(spacing: 0) { content }
                .padding(.horizontal, 24).padding(.vertical, 22)
                .background(Color(red: 246 / 255, green: 246 / 255, blue: 244 / 255), in: RoundedRectangle(cornerRadius: 8))
                .padding(.horizontal, 20)
        }.transition(.opacity.combined(with: .scale(scale: 0.97)))
    }
}

private struct FilledButton: View {
    let title: String; let action: () -> Void
    var body: some View { Button(action: action) { Text(title).font(.system(size: 14, weight: .bold)).foregroundStyle(.white).frame(maxWidth: .infinity, minHeight: 56).background(.black, in: Capsule()) }.buttonStyle(PressButtonStyle()) }
}

private struct OutlineButton: View {
    let title: String; let action: () -> Void
    var body: some View { Button(action: action) { Text(title).font(.system(size: 13, weight: .bold)).foregroundStyle(.black).frame(maxWidth: .infinity, minHeight: 48).background(RoundedRectangle(cornerRadius: 7).stroke(Color.black.opacity(0.19))) }.buttonStyle(PressButtonStyle()) }
}

private struct PressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.96 : 1).animation(.easeOut(duration: 0.14), value: configuration.isPressed) }
}
