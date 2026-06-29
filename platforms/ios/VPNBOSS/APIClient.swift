import Foundation

struct AppAuthInit: Decodable { let appCode: String; let authUrl: String }
struct AppAuthCheck: Decodable { let status: String; let token: String? }
struct UserProfile: Decodable {
    let email: String?
    let hasPassword: Bool?
    let hasEmail: Bool?
    let needsProfileCompletion: Bool?
    let trialUsed: Bool?
    let sub: ActiveSubscription?
}
struct ActiveSubscription: Decodable { let id: Int?; let expiresAt: String? }
struct TrialActivation: Decodable { let ok: Bool; let trial: Bool?; let alreadyActive: Bool?; let expiresAt: String? }
struct ProfileCompletion: Decodable { let ok: Bool }
private struct ConfigBundle: Decodable { let connect: ConnectInfo?; let configs: [ServerConfig]? }
private struct ConnectInfo: Decodable { let subUrl: String? }
private struct ServerConfig: Decodable { let id: Int?; let vlessKey: String?; let name: String?; let server: ServerInfo? }
private struct ServerInfo: Decodable { let name: String?; let location: String? }

struct RouteConfig: Codable, Identifiable, Equatable {
    let id: String
    let flag: String
    let name: String
    let location: String
    let detail: String
    let config: String

    static let placeholder = RouteConfig(id: "placeholder", flag: "🌐", name: "Серверы загружаются", location: "", detail: "Ожидание подписки", config: "")
}

final class APIClient {
    private let base = URL(string: "https://vpnboss.space")!
    var token = ""

    func appAuthInit() async throws -> AppAuthInit { try await request("POST", "/api/app-auth/init", body: [String: String](), authenticated: false) }

    func appAuthCheck(_ code: String) async throws -> AppAuthCheck {
        let result: AppAuthCheck = try await request("GET", "/api/app-auth/check/\(code)", authenticated: false)
        if let token = result.token, !token.isEmpty { self.token = token }
        return result
    }

    func routes() async throws -> [RouteConfig] {
        let bundle: ConfigBundle = try await request("GET", "/api/connect/configs")
        if let raw = bundle.connect?.subUrl, !raw.isEmpty, let routes = try? await subscription(raw), !routes.isEmpty { return routes }
        return (bundle.configs ?? []).compactMap { item in
            guard let link = item.vlessKey, link.lowercased().hasPrefix("vless://") else { return nil }
            let rawName = item.server?.name ?? item.name ?? "VPNBOSS"
            let location = item.server?.location ?? rawName
            let flag = flag(for: location + " " + rawName)
            return RouteConfig(id: String(item.id ?? rawName.hashValue), flag: flag, name: clean(rawName, flag: flag), location: location, detail: "VLESS Reality", config: link)
        }
    }

    func profile() async throws -> UserProfile { try await request("GET", "/api/auth/me") }

    func completeProfile(email: String, password: String) async throws -> ProfileCompletion {
        try await request("POST", "/api/auth/complete-profile", body: ["email": email, "password": password])
    }

    func activateTrial() async throws -> TrialActivation {
        try await request("POST", "/api/trial/activate", body: [String: String]())
    }

    private func subscription(_ raw: String) async throws -> [RouteConfig] {
        let value = raw.hasPrefix("https://") ? raw : "https://sekretnik1.vps.webdock.cloud/sub/\(raw.replacingOccurrences(of: "/sub/", with: "").trimmingCharacters(in: CharacterSet(charactersIn: "/")))"
        let (data, response) = try await URLSession.shared.data(from: URL(string: value)!)
        guard (response as? HTTPURLResponse)?.statusCode ?? 500 < 300 else { throw URLError(.badServerResponse) }
        let source = String(decoding: data, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        let decoded: String
        if source.localizedCaseInsensitiveContains("vless://") { decoded = source }
        else {
            var normalized = source.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/").filter { !$0.isWhitespace }
            normalized += String(repeating: "=", count: (4 - normalized.count % 4) % 4)
            decoded = Data(base64Encoded: normalized).map { String(decoding: $0, as: UTF8.self) } ?? source
        }
        return decoded.split(whereSeparator: \.isNewline).enumerated().compactMap { index, line in route(from: String(line), index: index) }
    }

    private func route(from link: String, index: Int) -> RouteConfig? {
        guard link.lowercased().hasPrefix("vless://"), let components = URLComponents(string: link) else { return nil }
        let fragment = components.fragment?.removingPercentEncoding ?? "VPNBOSS"
        let routeFlag = extractFlag(fragment) ?? flag(for: fragment + " " + (components.host ?? ""))
        var name = clean(fragment, flag: routeFlag).replacingOccurrences(of: "| iPhone", with: "", options: .caseInsensitive).trimmingCharacters(in: .whitespaces)
        if name.isEmpty { name = "VPNBOSS" }
        let query = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        let detail = "\((query["security"] ?? "reality").uppercased()) · \((query["type"] ?? "tcp").uppercased())"
        return RouteConfig(id: "sub-\(index)-\(link.hashValue)", flag: routeFlag, name: name, location: name, detail: detail, config: link)
    }

    private func extractFlag(_ text: String) -> String? {
        let scalars = Array(text.unicodeScalars)
        for i in 0..<(max(0, scalars.count - 1)) where (0x1F1E6...0x1F1FF).contains(Int(scalars[i].value)) && (0x1F1E6...0x1F1FF).contains(Int(scalars[i + 1].value)) {
            return String(String.UnicodeScalarView([scalars[i], scalars[i + 1]]))
        }
        return nil
    }

    private func clean(_ text: String, flag: String) -> String { text.replacingOccurrences(of: flag, with: "").trimmingCharacters(in: .whitespacesAndNewlines) }
    private func flag(for text: String) -> String {
        let value = text.lowercased()
        if ["denmark", "дания", "copenhagen", "копенгаген", " dk"].contains(where: value.contains) { return "🇩🇰" }
        if ["germany", "германия", "frankfurt", "берлин", " de"].contains(where: value.contains) { return "🇩🇪" }
        if ["spain", "испания", "madrid", "мадрид", " es"].contains(where: value.contains) { return "🇪🇸" }
        if ["russia", "россия", "moscow", "москва", " ru"].contains(where: value.contains) { return "🇷🇺" }
        return "🌐"
    }

    private func request<T: Decodable>(_ method: String, _ path: String, body: [String: String]? = nil, authenticated: Bool = true) async throws -> T {
        var request = URLRequest(url: URL(string: path, relativeTo: base)!.absoluteURL)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if authenticated && !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { request.httpBody = try JSONEncoder().encode(body) }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw URLError(.badServerResponse) }
        return try JSONDecoder().decode(T.self, from: data)
    }
}
