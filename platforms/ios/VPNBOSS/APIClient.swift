import Foundation

struct AuthInit: Decodable {
    let webToken: String?
    let deepLink: String?
    let botLink: String?
}

struct AuthCheck: Decodable {
    let status: String
    let token: String?
}

struct ConfigBundle: Decodable {
    let configs: [RouteConfig]?
}

struct RouteConfig: Decodable, Identifiable {
    var id: String { "\(flag)-\(name)-\(detail)" }
    let flag: String
    let name: String
    let detail: String
}

final class APIClient {
    private let base = URL(string: "https://vpnboss.space")!
    var token = ""

    func tgInit() async throws -> AuthInit {
        try await request("POST", "/api/auth/tg-init", body: ["mode": "login"])
    }

    func tgCheck(_ webToken: String) async throws -> AuthCheck {
        let result: AuthCheck = try await request("GET", "/api/auth/tg-check/\(webToken)")
        if let token = result.token, !token.isEmpty { self.token = token }
        return result
    }

    func routes() async throws -> [RouteConfig] {
        let bundle: ConfigBundle = try await request("GET", "/api/connect/configs")
        let routes = bundle.configs ?? []
        return routes.isEmpty ? [RouteConfig(flag: "🇩🇰", name: "Дания", detail: "Ожидание подписки")] : routes
    }

    private func request<T: Decodable>(_ method: String, _ path: String, body: [String: String]? = nil) async throws -> T {
        var request = URLRequest(url: URL(string: path, relativeTo: base)!.absoluteURL)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { request.httpBody = try JSONEncoder().encode(body) }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
}
