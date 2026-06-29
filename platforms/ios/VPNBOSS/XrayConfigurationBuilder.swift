import Foundation

enum XrayConfigurationBuilder {
    static func build(from link: String) throws -> String {
        guard let url = URLComponents(string: link), url.scheme?.lowercased() == "vless",
              let host = url.host, let uuid = url.user, !uuid.isEmpty else {
            throw NSError(domain: "VPNBOSS.Xray", code: 1, userInfo: [NSLocalizedDescriptionKey: "Некорректный VLESS-ключ"])
        }

        let query = (url.queryItems ?? []).reduce(into: [String: String]()) { $0[$1.name.lowercased()] = $1.value ?? "" }
        let security = query["security"] ?? "reality"
        let network = query["type"] ?? "tcp"
        var stream: [String: Any] = ["network": network, "security": security]

        if security == "reality" {
            stream["realitySettings"] = [
                "serverName": query["sni"] ?? host,
                "fingerprint": query["fp"] ?? "chrome",
                "publicKey": query["pbk"] ?? "",
                "shortId": query["sid"] ?? "",
                "spiderX": query["spx"] ?? "/",
                "show": false,
            ]
        } else if security == "tls" {
            stream["tlsSettings"] = ["serverName": query["sni"] ?? host, "fingerprint": query["fp"] ?? "chrome"]
        }

        if network == "ws" {
            stream["wsSettings"] = ["path": query["path"] ?? "/", "headers": ["Host": query["host"] ?? ""]]
        } else if network == "grpc" {
            stream["grpcSettings"] = ["serviceName": query["servicename"] ?? ""]
        } else if network == "xhttp" {
            stream["xhttpSettings"] = ["path": query["path"] ?? "/", "host": query["host"] ?? ""]
        }

        let user: [String: Any] = [
            "id": uuid,
            "encryption": query["encryption"] ?? "none",
            "flow": query["flow"] ?? "xtls-rprx-vision",
            "level": 8,
        ]
        let proxy: [String: Any] = [
            "tag": "proxy",
            "protocol": "vless",
            "settings": ["vnext": [["address": host, "port": url.port ?? 443, "users": [user]]]],
            "streamSettings": stream,
        ]
        let config: [String: Any] = [
            "log": ["loglevel": "warning"],
            "dns": ["servers": ["1.1.1.1", "8.8.8.8"]],
            "inbounds": [[
                "tag": "socks",
                "listen": "0.0.0.0",
                "port": 10808,
                "protocol": "socks",
                "settings": ["udp": true],
                "sniffing": ["enabled": true, "destOverride": ["http", "tls", "quic"]],
            ]],
            "outbounds": [
                proxy,
                ["tag": "direct", "protocol": "freedom", "settings": ["domainStrategy": "UseIP"]],
                ["tag": "block", "protocol": "blackhole"],
            ],
            "routing": [
                "domainStrategy": "IPIfNonMatch",
                "rules": [["type": "field", "ip": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8"], "outboundTag": "direct"]],
            ],
        ]
        let data = try JSONSerialization.data(withJSONObject: config)
        guard let json = String(data: data, encoding: .utf8) else { throw NSError(domain: "VPNBOSS.Xray", code: 2) }
        return json
    }
}
