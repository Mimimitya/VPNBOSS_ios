import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var session: AppSession
    @Environment(\.openURL) private var openURL
    @State private var showDevices = false
    @State private var newDevice = ""
    @State private var supportMessage = ""

    private var isConnectedReady: Bool { session.connect != nil }

    var body: some View {
        NavigationStack {
            ZStack {
                SketchGridBackground()

                ScrollView {
                    VStack(spacing: 18) {
                        HeaderBar()

                        VStack(spacing: 18) {
                            Text(statusText)
                                .font(.system(size: 18, weight: .black, design: .rounded))
                                .foregroundStyle(Color.black.opacity(0.72))

                            Button {
                                if let link = session.connect?.happLink { openURL(link) }
                            } label: {
                                ZStack {
                                    Circle()
                                        .fill(isConnectedReady ? Color.black : Color(red: 0.27, green: 0.05, blue: 0.07))
                                        .frame(width: 172, height: 172)
                                        .shadow(color: .black.opacity(isConnectedReady ? 0.16 : 0.08), radius: 22, y: 14)
                                    Image(systemName: "power")
                                        .font(.system(size: 72, weight: .thin))
                                        .foregroundStyle(.white)
                                }
                            }
                            .disabled(!isConnectedReady)
                            .symbolEffect(.bounce, value: isConnectedReady)

                            if let sub = session.profile?.sub {
                                SubscriptionCard(sub: sub)
                            } else {
                                EmptySubscriptionCard()
                            }
                        }
                        .padding(.vertical, 10)

                        HStack(spacing: 10) {
                            Button {
                                if let url = session.connect?.subUrl {
                                    UIPasteboard.general.string = url.absoluteString
                                }
                            } label: {
                                Label("connect.copy", systemImage: "doc.on.doc.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(BossSecondaryButtonStyle())
                            .disabled(session.connect == nil)

                            Button {
                                showDevices.toggle()
                            } label: {
                                Label("devices.title", systemImage: "iphone.gen3")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(BossSecondaryButtonStyle())
                        }

                        if showDevices {
                            DevicesPanel(newDevice: $newDevice)
                                .transition(.move(edge: .bottom).combined(with: .opacity))
                        }

                        SupportPanel(message: $supportMessage)
                    }
                    .padding(24)
                }
                .refreshable { await session.refresh() }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("auth.logout") { session.logout() }
                }
            }
        }
    }

    private var statusText: LocalizedStringKey {
        isConnectedReady ? "status.ready" : "status.inactive"
    }
}

private struct SubscriptionCard: View {
    let sub: Subscription

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(sub.planName)
                    .font(.system(size: 24, weight: .black, design: .rounded))
                Spacer()
                Text(sub.hasRussia == true ? "EU + RU" : "EU")
                    .font(.system(size: 12, weight: .black, design: .rounded))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(Color.black)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }
            ProgressView(value: usage)
                .tint(.black)
            HStack {
                Text("subscription.traffic")
                Spacer()
                Text(trafficText)
            }
            .font(.system(size: 13, weight: .bold, design: .rounded))
            .foregroundStyle(.secondary)
        }
        .padding(18)
        .background(Color.black.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var usage: Double {
        guard let limit = sub.gbLimit, limit > 0 else { return 0 }
        return min(1, (sub.gbUsed ?? 0) / limit)
    }

    private var trafficText: String {
        guard let limit = sub.gbLimit else { return String(format: "%.1f GB", sub.gbUsed ?? 0) }
        return String(format: "%.1f / %.0f GB", sub.gbUsed ?? 0, limit)
    }
}

private struct EmptySubscriptionCard: View {
    var body: some View {
        Text("subscription.empty")
            .font(.system(size: 14, weight: .bold, design: .rounded))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(Color.black.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
