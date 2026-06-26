# VPNBOSS Windows

Запуск из исходников:

```powershell
pip install PySide6 PySide6-Addons PySide6-Essentials requests
python main.py
```

Сборка:

```powershell
pyinstaller "VPN BOSS Windows.spec"
```

UI лежит в `web/`. Вход в приложении выполняется только через официальный flow `vpnboss.space`. Поля пароля, баланс, оплаты и личный кабинет из приложения убраны.
