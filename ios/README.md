# VPNBOSS iOS

SwiftUI-клиент под текущий backend `vpn-boss-bot`.

## Что уже подключено

- API: `/api/auth/login`, `/api/auth/register`, Telegram OAuth, `/api/auth/me`, `/api/connect`, `/api/connect/configs`, `/api/devices`, `/api/tickets`.
- Языки: English, Russian, Spanish через `Localizable.strings`.
- UI по эскизам: onboarding, Telegram gate, trial/pro screen, connect dashboard, device/config list, support entry.
- Deep link: `happ://add/<encoded subscription URL>`, fallback copy subscription URL.

## Сборка

1. Установить XcodeGen: `brew install xcodegen`.
2. В папке `ios`: `xcodegen generate`.
3. Открыть `VPNBoss.xcodeproj`.
4. В `VPNBoss/Support/AppConfig.swift` проверить `apiBaseURL`.
5. Указать `DEVELOPMENT_TEAM` в `project.yml` или в Xcode Signing.

## Логотип

Оригинальный логотип добавлен как asset `VPNBossLogo` из файла:

`C:\Users\Usuario\Downloads\Group 2 (3) 1.png`

Он используется в шапке интерфейса через `Image("VPNBossLogo")`.

## TestFlight

1. В Apple Developer создать App ID для `com.vpnboss.client`.
2. В App Store Connect создать приложение с тем же Bundle ID.
3. В Xcode открыть `VPNBoss.xcodeproj`, выбрать свой Team и устройство `Any iOS Device`.
4. `Product > Archive`.
5. В Organizer нажать `Distribute App > App Store Connect > Upload`.
6. Дождаться обработки билда в App Store Connect.
7. Вкладка `TestFlight`: добавить билд, заполнить `What to Test`, добавить internal testers.
8. Установить приложение TestFlight на iPhone и принять инвайт.

Если нужен внешний тест, Apple сначала сделает короткий Beta App Review.

## Про встроенный VPN

Сейчас приложение работает как клиент кабинета: авторизация, подписка, устройства, ссылка подключения и deep link в HAPP. Это можно быстро тестировать через TestFlight.

Чтобы приложение само поднимало VPN внутри iOS, нужен отдельный Network Extension target, entitlement `com.apple.developer.networking.networkextension`, одобрение Apple и встроенный VPN-движок под ваш протокол. Для VLESS/Xray-подобных конфигов обычно используют отдельный core вроде sing-box/Xray wrapper, но это уже следующий слой разработки, не просто UI.

Важно: платежи в iOS нужно проводить по правилам Apple. В текущем клиенте нет скрытых покупок или маскировки пополнений; для App Review лучше использовать понятный экран статуса подписки и легальный способ управления аккаунтом.
