import Foundation

struct AuthResponse: Codable {
    let token: String
    let telegramId: Int64
    let authProvider: String?
    let hasPassword: Bool?
    let email: String?
    let displayName: String?
}

struct LoginBody: Codable {
    let login: String
    let password: String
}

struct RegisterBody: Codable {
    let login: String
    let email: String
    let password: String
    let displayName: String
}

struct TelegramInitBody: Codable { let mode: String }

struct TelegramInitResponse: Codable {
    let webToken: String
    let mode: String
    let expiresAt: String
    let botLink: URL
    let deepLink: URL?
}

struct TelegramCheckResponse: Codable {
    let status: String
    let token: String?
    let telegramId: Int64?
    let isNewUser: Bool?
}

struct UserProfile: Codable {
    let telegramId: Int64
    let name: String?
    let username: String?
    let email: String?
    let displayName: String?
    let needsProfileCompletion: Bool?
    let balance: Double?
    let discount: Double?
    let trialUsed: Bool?
    let sub: Subscription?
    let devices: [Device]
}

struct Subscription: Codable {
    let id: Int
    let plan: String
    let planName: String
    let devicesLimit: Int
    let gbLimit: Double?
    let gbUsed: Double?
    let expiresAt: String?
    let hasRussia: Bool?
    let hasExtraFast: Bool?
}

struct Device: Codable, Identifiable {
    let id: Int
    let name: String
    let server: String
    let location: String?
    let createdAt: String?
}

struct ConnectPayload: Codable {
    let subUrl: URL
    let happLink: URL
}

struct ConnectConfigsResponse: Codable {
    let subscription: ConfigSubscription
    let connect: ConnectPayload
    let configs: [DeviceConfig]
}

struct ConfigSubscription: Codable {
    let plan: String
    let expiresAt: String?
    let devicesLimit: Int
    let gbLimit: Double?
    let gbUsed: Double?
    let hasRussia: Bool?
}

struct DeviceConfig: Codable, Identifiable {
    let id: Int
    let name: String
    let uuid: String
    let vlessKey: String?
    let createdAt: String?
    let server: ServerInfo?
}

struct ServerInfo: Codable {
    let id: Int
    let name: String
    let location: String
    let ip: String
}

struct AddDeviceBody: Codable { let name: String }

struct Ticket: Codable, Identifiable {
    let id: Int
    let subject: String?
    let status: String?
    let messages: [TicketMessage]?
}

struct TicketMessage: Codable, Identifiable {
    let id: Int
    let message: String
    let is_admin: Int?
    let created_at: String?
}

struct TicketBody: Codable { let message: String }
struct TicketCreateResponse: Codable { let id: Int }
