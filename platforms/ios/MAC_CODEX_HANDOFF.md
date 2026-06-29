# VPNBOSS iOS: передача проекта Codex на Mac

Дата передачи: 30 июня 2026 года

Ветка: `main`
Последний iOS-коммит: `a30c256`

## Цель

Довести VPNBOSS для iOS до собираемого и проверенного приложения:

- авторизация только через сайт `vpnboss.space`;
- обязательное заполнение email и пароля для нового пользователя;
- автоматическая выдача бесплатной подписки на 5 дней;
- загрузка персональных VLESS Reality-конфигураций;
- настоящий системный VPN через Packet Tunnel, а не HTTP-прокси;
- выбор сервера кнопками и свайпом;
- публикация тестовой сборки в TestFlight.

## Инфраструктура VPNBOSS

### Основной сервер

- IP: `92.113.147.193`
- SSH user: `ElValleChat`
- Рабочая папка: `/home/ElValleChat/vpn-boss-bot`
- Сайт и API: `/home/ElValleChat/vpn-boss-bot/site.js`
- Telegram-бот: `/home/ElValleChat/vpn-boss-bot/index.js`
- SQLite: `/home/ElValleChat/vpn-boss-bot/vpn.db`
- PM2-процесс сайта: `site`
- PM2-процесс бота: `vpnbot`
- Внутренний порт сайта: `6760`

SSH-ключ и пароль нельзя добавлять в GitHub или вставлять в этот документ. На Windows доступ хранится отдельно в:

`ops/vps-ssh.json`

Этот файл нужно перенести на Mac отдельно защищённым способом и сохранить вне репозитория, например:

`~/.config/vpnboss/vps-ssh.json`

После переноса ограничить права:

```bash
chmod 600 ~/.config/vpnboss/vps-ssh.json
```

### Сайт

- Публичный адрес: `https://vpnboss.space`
- API base URL: `https://vpnboss.space/api`
- Кабинет: `https://vpnboss.space/cabinet`
- Авторизация приложения открывает `https://vpnboss.space/auth?appCode=...`

Основные endpoint приложения:

- `POST /api/app-auth/init`
- `GET /api/app-auth/check/:code`
- `GET /api/auth/me`
- `POST /api/auth/complete-profile`
- `POST /api/trial/activate`
- `GET /api/connect/configs`

`POST /api/trial/activate` уже развёрнут на сервере. Он проверяет профиль и `trial_used`, затем атомарно создаёт trial на 5 дней, 75 ГБ и 3 устройства. Повторная выдача одному пользователю запрещена сервером.

### Telegram-бот

- Username: `@Vpnboss_robot`
- Ссылка: `https://t.me/Vpnboss_robot`
- PM2-процесс: `vpnbot`
- Entry point: `/home/ElValleChat/vpn-boss-bot/index.js`

Бот и сайт используют одну SQLite-базу. Изменения подписок должны оставаться совместимыми между `site.js`, `index.js`, `database.js` и `config.js`.

### Subscription service

- Публичная база subscription links: `https://sekretnik1.vps.webdock.cloud`
- Приложение получает конкретный `subUrl` только от авторизованного `/api/connect/configs`.
- Нельзя встраивать общий subscription token или VLESS-ключ в исходный код приложения.

### Подключение по SSH на Mac

Из JSON нужно извлечь `privateKey` в локальный файл, не печатая ключ в терминал или логи. После этого:

```bash
chmod 600 ~/.ssh/vpnboss_denmark
ssh -i ~/.ssh/vpnboss_denmark ElValleChat@92.113.147.193
```

Диагностика процессов:

```bash
cd /home/ElValleChat/vpn-boss-bot
pm2 list
pm2 logs site --lines 100
pm2 logs vpnbot --lines 100
curl -fsS http://127.0.0.1:6760/api/plans
```

Безопасная выкладка `site.js`:

```bash
cd /home/ElValleChat/vpn-boss-bot
cp site.js "site.js.bak-$(date +%Y%m%d-%H%M%S)"
node --check site.js
pm2 restart site --update-env
pm2 show site
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:6760/api/plans
```

Для бота аналогично использовать резервную копию, `node --check index.js` и `pm2 restart vpnbot --update-env`. Не перезапускать `index`, `backhome` или другие процессы: они не относятся к этому приложению.

## Что уже реализовано

- SwiftUI-интерфейс в стиле Android-версии.
- Новый круглый логотип и App Icon.
- Авторизация через `/api/app-auth/init` и `/api/app-auth/check/:code`.
- Сохранение серверного токена между запусками.
- Проверка профиля через `/api/auth/me`.
- Форма email, пароля и подтверждения пароля внутри приложения.
- Вызов `/api/auth/complete-profile`.
- Вызов `/api/trial/activate` после завершения профиля.
- Серверный endpoint trial уже развёрнут на основном сервере.
- Trial выдаётся один раз: 5 дней, 75 ГБ, 3 устройства.
- Загрузка subscription URL и разбор списка VLESS-серверов.
- Карусель серверов со стрелками и горизонтальным свайпом.
- Packet Tunnel extension.
- `LibXray 26.6.1` и `Tun2SocksKit 5.15.0` через Swift Package Manager.
- Генерация Xray JSON из VLESS Reality-ссылки.

## Важное состояние проекта

Код написан на Windows и ещё не компилировался Xcode. Нельзя считать VPN готовым, пока он не прошёл сборку и сетевой тест на физическом iPhone.

Известные места, которые нужно проверить или доделать:

1. Исправить все ошибки Swift/XcodeGen/Swift Package Manager, обнаруженные первой сборкой.
2. Проверить API `LibXray` фактической версии `26.6.1` и сигнатуры в `PacketTunnelProvider.swift`.
3. Проверить запуск `Tun2SocksKit`, DNS, IPv4, IPv6 и UDP.
4. Убедиться, что после подключения появляется системный значок VPN и внешний IP меняется.
5. `fastestRoute()` пока выбирает первый доступный маршрут. Нужно реализовать реальное измерение задержки и выбор минимального ping.
6. Токен сейчас хранится в `UserDefaults`. Перед релизом перенести его в Keychain.
7. Проверить возврат из Safari после web-авторизации. При необходимости добавить universal link/deep link и обновление по `scenePhase`.
8. Добавить полноценные локализации русского, английского и испанского текста.
9. Проверить отображение всех стран: сейчас собственные флаги нарисованы для DK, DE, RU и ES, остальные показывают глобус.
10. Проверить лицензии библиотек, Privacy Manifest и требования App Store перед отправкой.

## Открытие на Mac

```bash
git clone https://github.com/Mimimitya/VPNBOSS_ios.git
cd VPNBOSS_ios/platforms/ios
chmod +x OPEN_IN_XCODE.command
./OPEN_IN_XCODE.command
```

Скрипт устанавливает XcodeGen через Homebrew, создаёт `VPNBOSS.xcodeproj`, загружает Swift Packages и открывает Xcode.

Если скрипт не сработал:

```bash
brew install xcodegen
cd platforms/ios
xcodegen generate --spec project.yml
xcodebuild -resolvePackageDependencies -project VPNBOSS.xcodeproj -scheme VPNBOSS
open VPNBOSS.xcodeproj
```

## Настройка подписи

В Xcode выбрать одну Apple Team для обеих целей:

- `VPNBOSS`;
- `PacketTunnel`.

Проверить идентификаторы:

- приложение: `space.vpnboss.client`;
- расширение: `space.vpnboss.client.PacketTunnel`.

Для обоих App ID должна быть доступна capability Network Extensions с `packet-tunnel-provider`. Нужен платный Apple Developer аккаунт. Если эти Bundle ID заняты другой Team, заменить оба согласованно в `project.yml`, затем заново выполнить `xcodegen generate`.

## Порядок работы Codex на Mac

1. Выполнить `git pull` и убедиться, что рабочее дерево не содержит чужих изменений.
2. Запустить `OPEN_IN_XCODE.command`.
3. Собрать схему `VPNBOSS` без подписи для проверки компиляции.
4. Исправлять ошибки минимально, не менять дизайн без визуальной причины.
5. Настроить Team и capabilities для обеих целей.
6. Запустить приложение на физическом iPhone.
7. Пройти полный сценарий нового пользователя.
8. Проверить VPN через внешний IP и DNS leak test.
9. Перезапустить приложение и убедиться, что сессия и серверы сохранились.
10. Сделать Archive только после прохождения проверок ниже.

## Проверка нового пользователя

1. Нажать `ВОЙТИ ЧЕРЕЗ САЙТ`.
2. Авторизоваться на `vpnboss.space`.
3. Вернуться в приложение.
4. Должно появиться окно завершения регистрации.
5. Ввести email, пароль и подтверждение.
6. Приложение должно вызвать `/api/auth/complete-profile`.
7. Затем приложение вызывает `/api/trial/activate`.
8. Сервер должен создать trial на 5 дней, 75 ГБ и 3 устройства.
9. После этого должны загрузиться персональные серверы.
10. Повторный вызов trial не должен создавать новую подписку.

## Проверка VPN

- Выбор стрелкой подключает выбранный сервер.
- Свайп влево выбирает следующий сервер.
- Свайп вправо выбирает предыдущий сервер.
- Нажатие большой кнопки подключает именно выбранный сервер.
- `НАЙТИ ЛУЧШИЙ СЕРВЕР` измеряет ping и выбирает минимальный.
- Во время подключения кнопка вращается без перезагрузки экрана.
- После подключения кнопка становится серо-зелёной.
- В статусной строке iOS появляется VPN.
- Внешний IP соответствует выбранной стране.
- DNS проходит через туннель.
- После отключения сеть продолжает работать нормально.

## Основные файлы

- `VPNBOSS/RootView.swift` — интерфейс, форма профиля и свайп серверов.
- `VPNBOSS/AppSession.swift` — авторизация, onboarding, trial и состояние VPN.
- `VPNBOSS/APIClient.swift` — API сайта и подписки.
- `VPNBOSS/XrayConfigurationBuilder.swift` — VLESS Reality в Xray JSON.
- `PacketTunnel/PacketTunnelProvider.swift` — системный туннель Xray/tun2socks.
- `project.yml` — цели, пакеты, подпись и Bundle ID.
- `OPEN_IN_XCODE.command` — генерация проекта.

## TestFlight

После успешного теста на iPhone:

1. Увеличить `CURRENT_PROJECT_VERSION` в `project.yml`.
2. Перегенерировать проект через XcodeGen.
3. Выбрать `Any iOS Device (arm64)`.
4. Выполнить `Product` → `Archive`.
5. В Organizer выбрать `Distribute App` → `App Store Connect` → `Upload`.
6. Проверить обработанную сборку в TestFlight.

## Готовый промпт для Codex на Mac

```text
Продолжи разработку VPNBOSS iOS из текущего репозитория. Сначала полностью прочитай platforms/ios/MAC_CODEX_HANDOFF.md, включая раздел инфраструктуры с сервером 92.113.147.193, сайтом vpnboss.space и ботом @Vpnboss_robot. Затем проверь git status и открой проект через platforms/ios/OPEN_IN_XCODE.command. Собери схему VPNBOSS и исправь все реальные ошибки компиляции, Swift Package Manager, подписи и Packet Tunnel extension. Не меняй существующий дизайн без необходимости. После успешной сборки запусти приложение на физическом iPhone и проверь полный сценарий: web-авторизация, обязательные email/пароль, автоматический trial 5 дней, загрузка всех VLESS Reality-серверов, свайп карусели, подключение выбранного сервера, настоящий системный VPN, изменение внешнего IP, DNS и отключение. Реализуй настоящий ping для кнопки лучшего сервера, перенеси токен в Keychain и добавь RU/EN/ES локализации. Для SSH используй только отдельно переданный ~/.config/vpnboss/vps-ssh.json, никогда не добавляй его в Git. Перед изменением site.js или index.js обязательно делай резервную копию и node --check. Не считай работу завершённой без фактической проверки на устройстве.
```
