# VPNBOSS Platform Clients

Три отдельные папки под платформы:

- `windows` — рабочий PySide6/Xray клиент на базе исходного Windows-приложения.
- `android` — нативный Kotlin клиент с `VpnService`.
- `ios` — SwiftUI клиент с подготовкой под `NetworkExtension`.

Во всех клиентах вход не дублирует сайт: авторизация запускается через официальный `vpnboss.space` / Telegram OAuth flow, после чего приложение подтягивает профиль и конфиги через API.
