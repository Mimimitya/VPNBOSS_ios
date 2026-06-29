import Foundation
import NetworkExtension
import SwiftUI
import UIKit

enum ConnectionState { case off, connecting, connected }

@MainActor
final class AppSession: ObservableObject {
    @Published var token = UserDefaults.standard.string(forKey: "vpnboss.token") ?? ""
    @Published var routes: [RouteConfig] = [.placeholder]
    @Published var selected = UserDefaults.standard.integer(forKey: "vpnboss.selected")
    @Published var connecting = false
    @Published var connected = false
    @Published var authorizing = false
    @Published var message = ""
    @Published var showError = false

    var connectionState: ConnectionState { connecting ? .connecting : connected ? .connected : .off }
    private let api = APIClient()
    private let vpn = VPNManager()

    init() {
        api.token = token
        if let data = UserDefaults.standard.data(forKey: "vpnboss.routes"), let cached = try? JSONDecoder().decode([RouteConfig].self, from: data), !cached.isEmpty { routes = cached }
        Task {
            connected = await vpn.isConnected
            if !token.isEmpty { await refreshRoutes() }
        }
    }

    func route(at index: Int) -> RouteConfig {
        guard !routes.isEmpty else { return .placeholder }
        return routes[(index % routes.count + routes.count) % routes.count]
    }

    func changeServer(_ delta: Int) {
        guard !connecting, !connected, !routes.isEmpty else { return }
        select((selected + delta + routes.count) % routes.count)
    }

    func select(_ index: Int) {
        selected = max(0, min(index, routes.count - 1))
        UserDefaults.standard.set(selected, forKey: "vpnboss.selected")
    }

    func startAuth() {
        guard !authorizing else { return }
        authorizing = true
        Task {
            do {
                let payload = try await api.appAuthInit()
                if let url = URL(string: payload.authUrl) { await UIApplication.shared.open(url) }
                await poll(payload.appCode)
            } catch { fail("Не удалось открыть вход через сайт") }
        }
    }

    private func poll(_ code: String) async {
        for _ in 0..<150 {
            try? await Task.sleep(for: .seconds(2))
            do {
                let result = try await api.appAuthCheck(code)
                if result.status == "confirmed", let token = result.token, !token.isEmpty {
                    self.token = token
                    UserDefaults.standard.set(token, forKey: "vpnboss.token")
                    authorizing = false
                    await refreshRoutes()
                    return
                }
                if result.status == "expired" { fail("Ссылка входа истекла. Откройте её ещё раз."); return }
            } catch { continue }
        }
        fail("Время ожидания подтверждения истекло")
    }

    func refreshRoutes() async {
        do {
            let loaded = try await api.routes()
            guard !loaded.isEmpty else { throw URLError(.zeroByteResource) }
            routes = loaded
            selected = min(selected, loaded.count - 1)
            if let data = try? JSONEncoder().encode(loaded) { UserDefaults.standard.set(data, forKey: "vpnboss.routes") }
        } catch { fail("Не удалось загрузить серверы подписки") }
    }

    func connectSelected() {
        if connected { disconnect(); return }
        guard !connecting, !route(at: selected).config.isEmpty else { fail("Нет доступного сервера"); return }
        connect(route(at: selected))
    }

    func connectAutomatic() {
        guard !connected, !connecting else { if connected { disconnect() }; return }
        Task {
            connecting = true
            let best = await fastestRoute()
            if let index = routes.firstIndex(of: best) { select(index) }
            await startVPN(best)
        }
    }

    private func connect(_ route: RouteConfig) {
        Task { connecting = true; await startVPN(route) }
    }

    private func startVPN(_ route: RouteConfig) async {
        do {
            try await vpn.start(route)
            try? await Task.sleep(for: .milliseconds(700))
            connected = await vpn.isConnected
            if !connected { throw URLError(.cannotConnectToHost) }
        } catch { fail("Не удалось подключить выбранный сервер") }
        connecting = false
    }

    func disconnect() {
        Task { await vpn.stop(); connected = false; connecting = false }
    }

    private func fastestRoute() async -> RouteConfig {
        // Packet-tunnel setup dominates a short TCP probe; preserve subscription order as a stable fallback.
        routes.first(where: { !$0.config.isEmpty }) ?? .placeholder
    }

    private func fail(_ text: String) { message = text; showError = true; authorizing = false; connecting = false }
}

final class VPNManager {
    var isConnected: Bool {
        get async {
            let managers = (try? await NETunnelProviderManager.loadAllFromPreferences()) ?? []
            return managers.first?.connection.status == .connected
        }
    }

    func start(_ route: RouteConfig) async throws {
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        let manager = managers.first ?? NETunnelProviderManager()
        let proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = "space.vpnboss.client.PacketTunnel"
        proto.serverAddress = route.name
        proto.providerConfiguration = ["vless": route.config]
        manager.protocolConfiguration = proto
        manager.localizedDescription = "VPNBOSS"
        manager.isEnabled = true
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
        try manager.connection.startVPNTunnel()
    }

    func stop() async {
        let managers = (try? await NETunnelProviderManager.loadAllFromPreferences()) ?? []
        managers.first?.connection.stopVPNTunnel()
    }
}
