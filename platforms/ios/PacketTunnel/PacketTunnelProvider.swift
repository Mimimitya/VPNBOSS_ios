import LibXray
import NetworkExtension
import Tun2SocksKit

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let mtu = 8500

    override func startTunnel(options: [String: NSObject]?) async throws {
        let stored = (protocolConfiguration as? NETunnelProviderProtocol)?.providerConfiguration
        guard let json = (options?["xrayJSON"] as? String) ?? (stored?["xrayJSON"] as? String) else {
            throw tunnelError("Отсутствует конфигурация Xray")
        }
        let port = (options?["socks5Port"] as? NSNumber)?.intValue ?? (stored?["socks5Port"] as? NSNumber)?.intValue ?? 10808

        let dataDirectory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        try FileManager.default.createDirectory(at: dataDirectory, withIntermediateDirectories: true)
        var requestError: NSError?
        let request = LibXrayNewXrayRunFromJSONRequest(dataDirectory.path, "", json, &requestError)
        if let requestError { throw requestError }
        guard !request.isEmpty else { throw tunnelError("Xray не принял конфигурацию") }

        LibXrayRunXrayFromJSON(request)
        try await applyNetworkSettings()
        try startTun2Socks(port: port)
    }

    override func stopTunnel(with reason: NEProviderStopReason) async {
        Socks5Tunnel.quit()
        LibXrayStopXray()
    }

    private func applyNetworkSettings() async throws {
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.mtu = NSNumber(value: mtu)

        let ipv4 = NEIPv4Settings(addresses: ["198.18.0.1"], subnetMasks: ["255.255.255.0"])
        ipv4.includedRoutes = [.default()]
        ipv4.excludedRoutes = [
            NEIPv4Route(destinationAddress: "10.0.0.0", subnetMask: "255.0.0.0"),
            NEIPv4Route(destinationAddress: "172.16.0.0", subnetMask: "255.240.0.0"),
            NEIPv4Route(destinationAddress: "192.168.0.0", subnetMask: "255.255.0.0"),
        ]
        settings.ipv4Settings = ipv4

        let ipv6 = NEIPv6Settings(addresses: ["fd6e:a81b:704f:1211::1"], networkPrefixLengths: [64])
        ipv6.includedRoutes = [.default()]
        settings.ipv6Settings = ipv6

        let dns = NEDNSSettings(servers: ["1.1.1.1", "8.8.8.8", "2606:4700:4700::1111"])
        dns.matchDomains = [""]
        settings.dnsSettings = dns
        try await setTunnelNetworkSettings(settings)
    }

    private func startTun2Socks(port: Int) throws {
        let config = """
        tunnel:
          mtu: \(mtu)
        socks5:
          port: \(port)
          address: "::"
          udp: 'udp'
        misc:
          task-stack-size: 20480
          connect-timeout: 5000
          read-write-timeout: 60000
          log-file: stderr
          log-level: warn
          limit-nofile: 65535
        """
        Socks5Tunnel.run(withConfig: .string(content: config)) { _ in }
    }

    private func tunnelError(_ message: String) -> NSError {
        NSError(domain: "VPNBOSS.PacketTunnel", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
