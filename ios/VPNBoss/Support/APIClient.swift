import Foundation

enum APIError: LocalizedError {
    case server(String)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .server(let message): return message
        case .invalidResponse: return String(localized: "error.invalid_response")
        }
    }
}

struct APIClient {
    var token: String?
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    func request<T: Decodable>(_ method: String, _ path: String) async throws -> T {
        try await send(method, path, bodyData: nil)
    }

    func request<T: Decodable, Body: Encodable>(_ method: String, _ path: String, body: Body) async throws -> T {
        try await send(method, path, bodyData: encoder.encode(body))
    }

    private func send<T: Decodable>(_ method: String, _ path: String, bodyData: Data?) async throws -> T {
        var request = URLRequest(url: AppConfig.apiBaseURL.appending(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.httpBody = bodyData

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }

        if !(200..<300).contains(http.statusCode) {
            let payload = try? decoder.decode(ErrorPayload.self, from: data)
            throw APIError.server(payload?.error ?? "HTTP \(http.statusCode)")
        }

        if T.self == EmptyResponse.self, data.isEmpty {
            return EmptyResponse() as! T
        }
        return try decoder.decode(T.self, from: data)
    }
}

struct EmptyResponse: Codable {}
struct ErrorPayload: Codable { let error: String? }

extension URL {
    func appending(path: String) -> URL {
        URL(string: path, relativeTo: self)!.absoluteURL
    }
}
