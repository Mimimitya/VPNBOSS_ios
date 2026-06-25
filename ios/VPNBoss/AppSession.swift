import Foundation
import SwiftUI

@MainActor
final class AppSession: ObservableObject {
    @AppStorage("authToken") private var storedToken = ""

    @Published var profile: UserProfile?
    @Published var connect: ConnectPayload?
    @Published var configs: [DeviceConfig] = []
    @Published var tickets: [Ticket] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var onboardingDone = UserDefaults.standard.bool(forKey: "onboardingDone")

    var isAuthenticated: Bool { !storedToken.isEmpty }
    var api: APIClient { APIClient(token: storedToken.isEmpty ? nil : storedToken) }

    func finishOnboarding() {
        onboardingDone = true
        UserDefaults.standard.set(true, forKey: "onboardingDone")
    }

    func login(login: String, password: String) async {
        await run {
            let response: AuthResponse = try await api.request("POST", "/api/auth/login", body: LoginBody(login: login, password: password))
            storedToken = response.token
            await refresh()
        }
    }

    func register(login: String, email: String, password: String, displayName: String) async {
        await run {
            let response: AuthResponse = try await api.request("POST", "/api/auth/register", body: RegisterBody(login: login, email: email, password: password, displayName: displayName))
            storedToken = response.token
            await refresh()
        }
    }

    func beginTelegramAuth(mode: String) async -> TelegramInitResponse? {
        var result: TelegramInitResponse?
        await run {
            result = try await api.request("POST", "/api/auth/tg-init", body: TelegramInitBody(mode: mode))
        }
        return result
    }

    func pollTelegram(webToken: String) async {
        for _ in 0..<180 {
            do {
                let check: TelegramCheckResponse = try await api.request("GET", "/api/auth/tg-check/\(webToken)")
                if check.status == "confirmed", let token = check.token {
                    storedToken = token
                    await refresh()
                    return
                }
                if check.status == "expired" || check.status == "not_found" {
                    errorMessage = String(localized: "auth.telegram_expired")
                    return
                }
                try await Task.sleep(nanoseconds: 2_000_000_000)
            } catch {
                errorMessage = error.localizedDescription
                return
            }
        }
    }

    func refresh() async {
        await run {
            profile = try await api.request("GET", "/api/auth/me")
            connect = try? await api.request("GET", "/api/connect")
            if let response: ConnectConfigsResponse = try? await api.request("GET", "/api/connect/configs") {
                configs = response.configs
            }
            tickets = (try? await api.request("GET", "/api/tickets")) ?? []
        }
    }

    func addDevice(name: String) async {
        await run {
            let _: EmptyResponse = try await api.request("POST", "/api/devices", body: AddDeviceBody(name: name))
            await refresh()
        }
    }

    func createTicket(message: String) async {
        await run {
            let _: TicketCreateResponse = try await api.request("POST", "/api/tickets", body: TicketBody(message: message))
            await refresh()
        }
    }

    func logout() {
        storedToken = ""
        profile = nil
        connect = nil
        configs = []
        tickets = []
    }

    private func run(_ operation: () async throws -> Void) async {
        isLoading = true
        errorMessage = nil
        do { try await operation() } catch { errorMessage = error.localizedDescription }
        isLoading = false
    }
}
