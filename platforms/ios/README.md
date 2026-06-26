# VPNBOSS iOS

На Mac:

```bash
cd platforms/ios
xcodegen generate
open VPNBOSS.xcodeproj
```

В проекте:

- `RootView.swift` — SwiftUI интерфейс в стиле Windows-клиента.
- `APIClient.swift` — Telegram OAuth и загрузка `/api/connect/configs`.
- `AppSession.swift` — состояние приложения и `NetworkExtension` manager.

Для реального VPN на iOS нужен Apple Developer аккаунт с Network Extension entitlement и отдельный Packet Tunnel provider target. Интерфейс и API-авторизация уже подготовлены без оплат, балансов, паролей и личного кабинета внутри приложения.
