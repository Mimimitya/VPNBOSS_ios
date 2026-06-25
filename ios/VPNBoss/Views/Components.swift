import SwiftUI

struct HeaderBar: View {
    var body: some View {
        HStack {
            Image("VPNBossLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 86, height: 18, alignment: .leading)
                .accessibilityLabel("VPNBOSS")
            Spacer()
            Image(systemName: "paperplane.fill")
                .font(.system(size: 12, weight: .black))
        }
    }
}

struct SketchGridBackground: View {
    var body: some View {
        Canvas { context, size in
            let step: CGFloat = 8
            var path = Path()
            var x: CGFloat = 0
            while x <= size.width {
                path.move(to: CGPoint(x: x, y: 0))
                path.addLine(to: CGPoint(x: x, y: size.height))
                x += step
            }
            var y: CGFloat = 0
            while y <= size.height {
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: size.width, y: y))
                y += step
            }
            context.stroke(path, with: .color(Color(red: 0.95, green: 0.61, blue: 0.61).opacity(0.28)), lineWidth: 0.7)
        }
        .background(Color(red: 1.0, green: 0.94, blue: 0.94))
        .ignoresSafeArea()
    }
}

struct BossTextField: View {
    let title: LocalizedStringKey
    @Binding var text: String
    var keyboard: UIKeyboardType = .default

    var body: some View {
        TextField(title, text: $text)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .padding(15)
            .background(Color.black.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct BossSecureField: View {
    let title: LocalizedStringKey
    @Binding var text: String

    var body: some View {
        SecureField(title, text: $text)
            .padding(15)
            .background(Color.black.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct BossButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .black, design: .rounded))
            .textCase(.uppercase)
            .padding(.vertical, 16)
            .padding(.horizontal, 18)
            .background(Color.black)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.spring(response: 0.22, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

struct BossSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 12, weight: .black, design: .rounded))
            .textCase(.uppercase)
            .padding(.vertical, 14)
            .padding(.horizontal, 12)
            .background(Color.white)
            .foregroundStyle(Color.black)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.black, lineWidth: 1.5))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct BossIconButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 18, weight: .black))
            .background(Color.black)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
    }
}
