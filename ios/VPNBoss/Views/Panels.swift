import SwiftUI

struct DevicesPanel: View {
    @EnvironmentObject private var session: AppSession
    @Binding var newDevice: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("devices.title")
                .font(.system(size: 19, weight: .black, design: .rounded))

            ForEach(session.configs) { config in
                VStack(alignment: .leading, spacing: 5) {
                    Text(config.name)
                        .font(.system(size: 15, weight: .black, design: .rounded))
                    Text(config.server?.name ?? "VPNBOSS")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color.white)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }

            HStack {
                TextField(String(localized: "devices.name"), text: $newDevice)
                    .textInputAutocapitalization(.words)
                    .padding(12)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                Button {
                    Task {
                        await session.addDevice(name: newDevice)
                        newDevice = ""
                    }
                } label: {
                    Image(systemName: "plus")
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(BossIconButtonStyle())
            }
        }
        .padding(16)
        .background(Color.black.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct SupportPanel: View {
    @EnvironmentObject private var session: AppSession
    @Binding var message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("support.title")
                .font(.system(size: 19, weight: .black, design: .rounded))

            TextField(String(localized: "support.placeholder"), text: $message, axis: .vertical)
                .lineLimit(3...5)
                .padding(12)
                .background(Color.black.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            Button {
                Task {
                    await session.createTicket(message: message)
                    message = ""
                }
            } label: {
                Text("support.send")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(BossButtonStyle())
        }
    }
}
