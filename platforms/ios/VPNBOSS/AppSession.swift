import Foundation
import NetworkExtension
import SwiftUI
import UIKit

@MainActor
final class AppSession: ObservableObject {
    @Published var token = UserDefaults.standard.string(forKey: "vpnboss.token") ?? ""
    @Published var routes = [RouteConfig(flag: "🇩🇰", name: "Дания", detail: "Ожидание подписки")]
    @Published var selected = 0
    @Published var connected = false
    @Published var message = "Ожидание авторизации"

    private let api = APIClient()
    private let vpn = VPNManager()

    init() {
        api.token = token
        if !token.isEmpty { Task { await refreshRoutes() } }
    }

    func startAuth() {
        message = "Открываю официальный вход..."
        Task {
            do {
                let initPayload = try await api.tgInit()
                if let link = initPayload.deepLink ?? initPayload.botLink, let url = URL(string: link) {
                    await UIApplication.shared.open(url)
                }
                if let webToken = initPayload.webToken {
                    await poll(webToken)
                }
            } catch {
                message = "Ошибка авторизации"
            }
        }
    }

    func poll(_ webToken: String) async {
        for _ in 0..<120 {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            do {
                let check = try await api.tgCheck(webToken)
                if check.status == "confirmed", let token = check.token {
                    self.token = token
                    UserDefaults.standard.set(token, forKey: "vpnboss.token")
                    message = "Доступ подтверждён"
                    await refreshRoutes()
                    return
                }
            } catch {
                message = "Ожидание подтверждения"
            }
        }
    }

    func refreshRoutes() async {
        do {
            routes = try await api.routes()
        } catch {
            message = "Не удалось загрузить подписку"
        }
    }

    func toggleVpn() {
        Task {
            do {
                if connected {
                    try await vpn.stop()
                    connected = false
                } else {
                    try await vpn.start()
                    connected = true
                }
            } catch {
                message = "VPN требует Network Extension entitlement"
            }
        }
    }
}

final class VPNManager {
    func start() async throws {
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        let manager = managers.first ?? NETunnelProviderManager()
        let proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = "space.vpnboss.client.PacketTunnel"
        proto.serverAddress = "VPNBOSS"
        manager.protocolConfiguration = proto
        manager.localizedDescription = "VPNBOSS"
        manager.isEnabled = true
        try await manager.saveToPreferences()
        try manager.connection.startVPNTunnel()
    }

    func stop() async throws {
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        managers.first?.connection.stopVPNTunnel()
    }
}
