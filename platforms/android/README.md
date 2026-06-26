# VPNBOSS Android

Открыть папку `platforms/android` в Android Studio и собрать `:app`.

В проекте:

- `MainActivity.kt` — нативный интерфейс и авторизация через `vpnboss.space`.
- `ApiClient.kt` — Telegram OAuth и загрузка `/api/connect/configs`.
- `VpnBossVpnService.kt` — системная точка входа Android VPN.

Для полного VLESS/Xray-туннеля нужно подключить Android xray/tun2socks runtime к `VpnBossVpnService`; UI и API-контур уже отделены от сайта и не содержат оплат/балансов/паролей.
