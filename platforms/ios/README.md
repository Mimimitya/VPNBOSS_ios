# VPNBOSS for iOS

## Самый простой запуск на Mac

1. Установите Xcode из App Store и один раз запустите его.
2. Откройте папку `platforms/ios` в Finder.
3. Дважды нажмите `OPEN_IN_XCODE.command`.
4. В Xcode откройте `VPNBOSS` -> `Signing & Capabilities` и выберите свою Apple Team.
5. Подключите iPhone, выберите его сверху и нажмите кнопку Run.

Скрипт сам создаёт `VPNBOSS.xcodeproj`, загружает нативные VPN-библиотеки и открывает проект. Повторный запуск безопасен.

В проект уже включены Packet Tunnel extension, LibXray и Tun2SocksKit. Подключение использует системный VPN iOS, а не HTTP-прокси.

## TestFlight

1. В Xcode выберите `Any iOS Device (arm64)`.
2. Откройте `Product` -> `Archive`.
3. В Organizer нажмите `Distribute App` -> `App Store Connect` -> `Upload`.
4. После обработки сборки добавьте её в TestFlight в App Store Connect.

Для установки VPN-профиля нужен платный Apple Developer аккаунт и разрешение Network Extensions для App ID `space.vpnboss.client`.

## Структура

- `VPNBOSS/RootView.swift` - интерфейс приложения.
- `VPNBOSS/APIClient.swift` - авторизация через сайт и загрузка подписки.
- `VPNBOSS/AppSession.swift` - сессия, серверы и Network Extension manager.
- `VPNBOSS/XrayConfigurationBuilder.swift` - преобразование VLESS Reality в конфигурацию Xray.
- `PacketTunnel/` - системное VPN-расширение iOS.
- `project.yml` - воспроизводимая конфигурация Xcode.
- `OPEN_IN_XCODE.command` - создание и открытие проекта одним запуском.
