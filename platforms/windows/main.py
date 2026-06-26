from __future__ import annotations

import base64
import ctypes
import io
import json
import os
import re
import socket
import subprocess
import sys
import tempfile
import time
import zipfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

import requests
from PySide6.QtCore import QObject, QRunnable, QThreadPool, QUrl, Signal, Slot
from PySide6.QtGui import QDesktopServices
from PySide6.QtWebChannel import QWebChannel
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWidgets import QApplication, QMainWindow

try:
    import winreg
except ImportError:  # pragma: no cover
    winreg = None


APP_NAME = "VPN BOSS"
BOT_PUBLIC_URL = "https://sekretnik1.vps.webdock.cloud"
SITE_BASE_URL = "https://vpnboss.space"
SITE_AUTH_URL = f"{SITE_BASE_URL}/login"
APP_DIR = Path(os.getenv("APPDATA", Path.home())) / "VPN BOSS Windows"
STATE_FILE = APP_DIR / "state.bin"
CORE_DIR = APP_DIR / "core"
XRAY_EXE = CORE_DIR / "xray.exe"
XRAY_LOG = CORE_DIR / "runtime.log"
DEBUG_LOG = APP_DIR / "debug.log"
SYSTEM_PROXY_HOST = "127.0.0.1"
SYSTEM_PROXY_PORT = 10809
SUBSCRIPTION_TIMEOUT = 20
SITE_TIMEOUT = 20
PROXY_TEST_URLS = (
    "https://api.ipify.org?format=json",
    "https://ifconfig.me/ip",
    "https://icanhazip.com",
)


def resource_base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent))
    return Path(__file__).parent


def ensure_dirs() -> None:
    APP_DIR.mkdir(parents=True, exist_ok=True)
    CORE_DIR.mkdir(parents=True, exist_ok=True)


def debug_log(message: str) -> None:
    try:
        ensure_dirs()
        timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
        DEBUG_LOG.open("a", encoding="utf-8").write(f"[{timestamp}] {message}\n")
    except Exception:
        pass


def is_windows_admin() -> bool:
    if os.name != "nt":
        return True
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def relaunch_as_admin() -> bool:
    if os.name != "nt":
        return False
    try:
        if getattr(sys, "frozen", False):
            executable = sys.executable
            params = subprocess.list2cmdline(sys.argv[1:])
        else:
            executable = sys.executable
            params = subprocess.list2cmdline([str(Path(__file__).resolve()), *sys.argv[1:]])
        result = ctypes.windll.shell32.ShellExecuteW(None, "runas", executable, params, None, 1)
        return result > 32
    except Exception as exc:
        debug_log(f"Failed to relaunch as admin: {exc}")
        return False


def show_message_box(title: str, text: str) -> None:
    if os.name != "nt":
        return
    try:
        ctypes.windll.user32.MessageBoxW(0, text, title, 0x10)
    except Exception:
        pass


def pad_base64(raw: str) -> str:
    return raw + "=" * ((4 - len(raw) % 4) % 4)


def try_decode_subscription(payload: str) -> str:
    cleaned = payload.strip()
    if "vless://" in cleaned.lower():
        return cleaned
    candidates = [cleaned, cleaned.replace("-", "+").replace("_", "/"), "".join(cleaned.splitlines())]
    for candidate in candidates:
        try:
            decoded = base64.b64decode(pad_base64(candidate), validate=False)
            text = decoded.decode("utf-8", errors="ignore")
            if "vless://" in text.lower():
                return text
        except Exception:
            continue
    return cleaned


def is_flag_sequence(text: str) -> bool:
    if len(text) != 2:
        return False
    return all(0x1F1E6 <= ord(ch) <= 0x1F1FF for ch in text)


def strip_variation(text: str) -> str:
    return text.replace("\ufe0f", "").strip()


def extract_name_badges(name: str) -> tuple[str, str | None, bool]:
    text = strip_variation(name)
    flag = None
    has_lightning = False
    pieces: list[str] = []
    index = 0
    while index < len(text):
        current = text[index]
        pair = text[index:index + 2]
        if len(pair) == 2 and is_flag_sequence(pair):
            if flag is None:
                flag = pair
            index += 2
            continue
        if current in {"\u26A1", "\u2607"}:
            has_lightning = True
            index += 1
            continue
        pieces.append(current)
        index += 1
    cleaned = re.sub(r"\s{2,}", " ", "".join(pieces)).strip(" -|_")
    return cleaned or "Config", flag, has_lightning


def bool_from_value(value: str | None) -> bool:
    if not value:
        return False
    return value.lower() in {"1", "true", "yes", "on"}


def split_csv(value: str | None) -> list[str]:
    if not value:
        return []
    return [item.strip() for item in value.split(",") if item.strip()]


@dataclass
class ConfigEntry:
    raw_uri: str
    display_name: str
    original_name: str
    flag: str | None
    has_lightning: bool
    host: str
    port: int
    uuid: str
    query: dict[str, str]
    country_code: str | None = None
    api_name: str | None = None
    api_detail: str | None = None


class SubscriptionError(RuntimeError):
    pass


class CoreError(RuntimeError):
    pass


class SiteAuthError(RuntimeError):
    pass


def parse_vless_uri(uri: str) -> ConfigEntry:
    parsed = urlparse(uri.strip())
    if parsed.scheme.lower() != "vless":
        raise SubscriptionError("Only VLESS items are supported.")
    if not parsed.hostname or not parsed.port or not parsed.username:
        raise SubscriptionError("Broken VLESS config.")
    params = {key: values[-1] for key, values in parse_qs(parsed.query, keep_blank_values=True).items()}
    fragment = unquote(parsed.fragment) if parsed.fragment else parsed.hostname
    display_name, flag, has_lightning = extract_name_badges(fragment)
    return ConfigEntry(
        raw_uri=uri.strip(),
        display_name=display_name,
        original_name=fragment,
        flag=flag,
        has_lightning=has_lightning,
        host=parsed.hostname,
        port=parsed.port,
        uuid=parsed.username,
        query=params,
    )


def flag_to_country_code(flag: str | None) -> str:
    chars = list(str(flag or "").strip())
    if len(chars) != 2 or not is_flag_sequence("".join(chars)):
        return ""
    return "".join(chr(ord(ch) - 0x1F1E6 + ord("A")) for ch in chars).lower()


def first_text_value(payload: dict[str, Any], keys: tuple[str, ...]) -> str:
    for key in keys:
        value = str(payload.get(key) or "").strip()
        if value:
            return value
    return ""


def enrich_config_from_api(entry: ConfigEntry, payload: dict[str, Any]) -> ConfigEntry:
    flag = first_text_value(payload, ("flag", "emoji", "countryFlag")) or entry.flag or ""
    name = first_text_value(payload, ("name", "displayName", "title", "server", "location", "country", "region"))
    country_code = first_text_value(payload, ("countryCode", "country_code", "code", "iso", "iso2")).lower()
    detail = first_text_value(payload, ("detail", "description", "protocol"))
    if name:
        entry.api_name = name.replace(flag, "").strip() or name
        entry.display_name = entry.api_name
    if flag and is_flag_sequence(flag):
        entry.flag = flag
    if not country_code:
        country_code = flag_to_country_code(entry.flag)
    if country_code:
        entry.country_code = country_code
    if detail:
        entry.api_detail = detail
    return entry


def normalize_subscription_input(raw_input: str) -> str:
    value = raw_input.strip()
    if value.lower().startswith("https://"):
        return value
    cleaned = value.removeprefix("/sub/").removeprefix("sub/").strip("/")
    return f"{BOT_PUBLIC_URL}/sub/{cleaned}"


def site_api_request(method: str, path: str, token: str = "", body: dict[str, Any] | None = None) -> dict[str, Any]:
    url = f"{SITE_BASE_URL}{path}"
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    debug_log(f"site_api_request {method} {url} token={'yes' if token else 'no'} body_keys={sorted((body or {}).keys())}")
    try:
        response = requests.request(method, url, headers=headers, json=body, timeout=SITE_TIMEOUT)
    except requests.RequestException as exc:
        debug_log(f"site_api_request error {method} {url}: {exc}")
        raise SiteAuthError(f"Site request failed: {exc}") from exc

    try:
        payload = response.json()
    except ValueError:
        payload = {}

    if not response.ok:
        raw_error = payload.get("error")
        if not raw_error:
            try:
                raw_error = response.content.decode("utf-8")
            except UnicodeDecodeError:
                raw_error = response.text
        error = str(raw_error or f"HTTP {response.status_code}").strip()
        debug_log(f"site_api_request failed {method} {url}: status={response.status_code} error={error[:200]}")
        raise SiteAuthError(error)

    if isinstance(payload, dict):
        debug_log(f"site_api_request ok {method} {url}: keys={sorted(payload.keys())}")
        return payload
    debug_log(f"site_api_request unexpected payload {method} {url}")
    raise SiteAuthError("Unexpected site response.")


def fetch_subscription(url: str) -> list[ConfigEntry]:
    normalized = normalize_subscription_input(url)
    if not normalized.lower().startswith("https://"):
        raise SubscriptionError("Only HTTPS subscription links are allowed.")
    try:
        response = requests.get(normalized, timeout=SUBSCRIPTION_TIMEOUT)
        response.raise_for_status()
    except requests.RequestException as exc:
        raise SubscriptionError(f"Subscription request failed: {exc}") from exc
    content = try_decode_subscription(response.text)
    configs: list[ConfigEntry] = []
    for line in content.splitlines():
        line = line.strip()
        if line and line.lower().startswith("vless://"):
            try:
                configs.append(parse_vless_uri(line))
            except SubscriptionError:
                continue
    if not configs:
        raise SubscriptionError("No VLESS configs were found in this subscription.")
    return configs


class DataBlob(ctypes.Structure):
    _fields_ = [("cbData", ctypes.c_uint), ("pbData", ctypes.POINTER(ctypes.c_byte))]


def _blob_from_bytes(data: bytes) -> DataBlob:
    if not data:
        return DataBlob(0, None)
    buffer = ctypes.create_string_buffer(data)
    return DataBlob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_byte)))


def dpapi_protect(data: bytes) -> bytes:
    if os.name != "nt":
        return data
    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    in_blob = _blob_from_bytes(data)
    out_blob = DataBlob()
    if not crypt32.CryptProtectData(ctypes.byref(in_blob), None, None, None, None, 0, ctypes.byref(out_blob)):
        raise OSError("CryptProtectData failed")
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        kernel32.LocalFree(out_blob.pbData)


def dpapi_unprotect(data: bytes) -> bytes:
    if os.name != "nt":
        return data
    crypt32 = ctypes.windll.crypt32
    kernel32 = ctypes.windll.kernel32
    in_blob = _blob_from_bytes(data)
    out_blob = DataBlob()
    if not crypt32.CryptUnprotectData(ctypes.byref(in_blob), None, None, None, None, 0, ctypes.byref(out_blob)):
        raise OSError("CryptUnprotectData failed")
    try:
        return ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        kernel32.LocalFree(out_blob.pbData)


class SecureStateStore:
    def __init__(self) -> None:
        ensure_dirs()
        self.state = self._load()

    def _default(self) -> dict[str, Any]:
        return {
            "onboarding_done": False,
            "subscription_url": "",
            "configs": [],
            "selected_index": 0,
            "site_session_token": "",
            "site_profile": {},
            "api_base_url": SITE_BASE_URL,
        }

    def _load(self) -> dict[str, Any]:
        if not STATE_FILE.exists():
            return self._default()
        try:
            encrypted = STATE_FILE.read_bytes()
            raw = dpapi_unprotect(encrypted)
            data = json.loads(raw.decode("utf-8"))
        except Exception:
            data = {}
        default = self._default()
        default.update(data)
        return default

    def save(self) -> None:
        payload = json.dumps(self.state, ensure_ascii=False, indent=2).encode("utf-8")
        STATE_FILE.write_bytes(dpapi_protect(payload))

    def configs(self) -> list[ConfigEntry]:
        result: list[ConfigEntry] = []
        for item in self.state.get("configs", []):
            try:
                result.append(ConfigEntry(**item))
            except TypeError:
                continue
        return result

    def set_subscription(self, url: str, configs: list[ConfigEntry]) -> None:
        self.state["subscription_url"] = normalize_subscription_input(url)
        self.state["configs"] = [asdict(item) for item in configs]
        self.state["onboarding_done"] = True
        self.state["selected_index"] = 0
        self.save()

    def set_onboarding_done(self, value: bool) -> None:
        self.state["onboarding_done"] = bool(value)
        self.save()

    def set_selected_index(self, index: int) -> None:
        self.state["selected_index"] = max(0, int(index))
        self.save()

    def set_site_session(self, token: str, profile: dict[str, Any] | None = None) -> None:
        self.state["site_session_token"] = str(token or "")
        self.state["site_profile"] = profile or {}
        self.save()

    def clear_site_session(self) -> None:
        self.state["site_session_token"] = ""
        self.state["site_profile"] = {}
        self.save()


def build_routes(configs: list[ConfigEntry]) -> list[dict[str, Any]]:
    routes: list[dict[str, Any]] = []
    seen: set[str] = set()
    for idx, config in enumerate(configs):
        key = f"{config.host}:{config.port}"
        if key in seen:
            continue
        seen.add(key)
        security = config.query.get("security", "none").upper()
        network = config.query.get("type", "tcp").upper()
        routes.append(
            {
                "flag": config.flag or "",
                "countryCode": config.country_code or flag_to_country_code(config.flag),
                "name": (config.api_name or config.display_name or "VPNBOSS").strip(),
                "detail": config.api_detail or f"{security} • {network} • маршрут скрыт",
                "config_index": idx,
            }
        )
    return routes


class ProxyController:
    INTERNET_OPTION_SETTINGS_CHANGED = 39
    INTERNET_OPTION_REFRESH = 37

    def __init__(self) -> None:
        self.previous_state: dict[str, Any] | None = None

    def _open_key(self):
        if winreg is None:
            raise CoreError("Windows proxy control is unavailable.")
        return winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\CurrentVersion\Internet Settings",
            0,
            winreg.KEY_READ | winreg.KEY_WRITE,
        )

    def _read_state(self) -> dict[str, Any]:
        with self._open_key() as key:
            def get_value(name: str, default: Any = "") -> Any:
                try:
                    value, _ = winreg.QueryValueEx(key, name)
                    return value
                except OSError:
                    return default

            return {
                "ProxyEnable": int(get_value("ProxyEnable", 0)),
                "ProxyServer": str(get_value("ProxyServer", "")),
                "ProxyOverride": str(get_value("ProxyOverride", "")),
                "AutoConfigURL": str(get_value("AutoConfigURL", "")),
                "AutoDetect": int(get_value("AutoDetect", 0)),
            }

    def _apply_state(self, state: dict[str, Any]) -> None:
        with self._open_key() as key:
            winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, int(state.get("ProxyEnable", 0)))
            winreg.SetValueEx(key, "ProxyServer", 0, winreg.REG_SZ, str(state.get("ProxyServer", "")))
            winreg.SetValueEx(key, "ProxyOverride", 0, winreg.REG_SZ, str(state.get("ProxyOverride", "")))
            winreg.SetValueEx(key, "AutoDetect", 0, winreg.REG_DWORD, int(state.get("AutoDetect", 0)))
            auto_config = str(state.get("AutoConfigURL", ""))
            if auto_config:
                winreg.SetValueEx(key, "AutoConfigURL", 0, winreg.REG_SZ, auto_config)
            else:
                try:
                    winreg.DeleteValue(key, "AutoConfigURL")
                except OSError:
                    pass
        ctypes.windll.Wininet.InternetSetOptionW(0, self.INTERNET_OPTION_SETTINGS_CHANGED, 0, 0)
        ctypes.windll.Wininet.InternetSetOptionW(0, self.INTERNET_OPTION_REFRESH, 0, 0)

    def _apply_winhttp_proxy(self, host: str, http_port: int) -> None:
        try:
            result = subprocess.run(
                ["netsh", "winhttp", "set", "proxy", f"{host}:{http_port}"],
                capture_output=True,
                text=True,
                timeout=8,
                check=False,
            )
            debug_log(f"winhttp set proxy rc={result.returncode} out={result.stdout[-160:]} err={result.stderr[-160:]}")
        except Exception as exc:
            debug_log(f"winhttp set proxy failed: {exc}")

    def _reset_winhttp_proxy(self) -> None:
        try:
            result = subprocess.run(
                ["netsh", "winhttp", "reset", "proxy"],
                capture_output=True,
                text=True,
                timeout=8,
                check=False,
            )
            debug_log(f"winhttp reset proxy rc={result.returncode} out={result.stdout[-160:]} err={result.stderr[-160:]}")
        except Exception as exc:
            debug_log(f"winhttp reset proxy failed: {exc}")

    def enable(self, host: str, http_port: int, socks_port: int) -> None:
        if self.previous_state is None:
            self.previous_state = self._read_state()
        proxy_server = f"http={host}:{http_port};https={host}:{http_port};socks={host}:{socks_port}"
        self._apply_state(
            {
                "ProxyEnable": 1,
                "ProxyServer": proxy_server,
                "ProxyOverride": "",
                "AutoConfigURL": "",
                "AutoDetect": 0,
            }
        )
        self._apply_winhttp_proxy(host, http_port)

    def restore(self) -> None:
        if self.previous_state is not None:
            self._apply_state(self.previous_state)
            self._reset_winhttp_proxy()
            self.previous_state = None


@dataclass
class NetworkBinding:
    interface_alias: str
    interface_index: int
    ip_address: str
    gateway: str


@dataclass
class TunBinding:
    interface_index: int
    ip_address: str


class TunRouteController:
    def __init__(self, tun_name: str = "xray0") -> None:
        self.tun_name = tun_name
        self.active_route: tuple[str, int] | None = None

    def _run_powershell_json(self, script: str) -> dict[str, Any]:
        command = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script]
        result = subprocess.run(command, capture_output=True, text=True, timeout=12, check=False)
        if result.returncode != 0:
            raise CoreError((result.stderr or result.stdout or "PowerShell failed").strip())
        output = (result.stdout or "").strip()
        if not output:
            raise CoreError("PowerShell returned empty output.")
        try:
            parsed = json.loads(output)
        except ValueError as exc:
            raise CoreError(f"Failed to parse PowerShell output: {output[:200]}") from exc
        if not isinstance(parsed, dict):
            raise CoreError("Unexpected PowerShell payload.")
        return parsed

    def detect_primary_network(self) -> NetworkBinding:
        script = (
            "$route = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' | "
            "Where-Object { $_.NextHop -ne '0.0.0.0' -and $_.InterfaceAlias -ne 'Loopback Pseudo-Interface 1' } | "
            "Sort-Object RouteMetric, InterfaceMetric | Select-Object -First 1; "
            "if (-not $route) { throw 'No active IPv4 default route found.' }; "
            "$ip = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $route.InterfaceIndex | "
            "Where-Object { $_.IPAddress -notlike '169.254*' -and $_.IPAddress -notlike '127.*' } | "
            "Select-Object -First 1; "
            "if (-not $ip) { throw 'No IPv4 address found for primary interface.' }; "
            "[pscustomobject]@{"
            "interface_alias=$route.InterfaceAlias;"
            "interface_index=[int]$route.InterfaceIndex;"
            "ip_address=$ip.IPAddress;"
            "gateway=$route.NextHop"
            "} | ConvertTo-Json -Compress"
        )
        data = self._run_powershell_json(script)
        return NetworkBinding(
            interface_alias=str(data["interface_alias"]),
            interface_index=int(data["interface_index"]),
            ip_address=str(data["ip_address"]),
            gateway=str(data["gateway"]),
        )

    def wait_for_tun(self, timeout_seconds: float = 8.0) -> TunBinding:
        deadline = time.time() + timeout_seconds
        last_error = "TUN interface not found."
        while time.time() < deadline:
            try:
                script = (
                    f"$ip = Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias '{self.tun_name}' -ErrorAction Stop | "
                    "Select-Object -First 1; "
                    f"$iface = Get-NetIPInterface -AddressFamily IPv4 -InterfaceAlias '{self.tun_name}' -ErrorAction Stop | "
                    "Select-Object -First 1; "
                    "if (-not $ip -or -not $iface) { throw 'TUN interface not ready.' }; "
                    "[pscustomobject]@{"
                    "interface_index=[int]$iface.InterfaceIndex;"
                    "ip_address=$ip.IPAddress"
                    "} | ConvertTo-Json -Compress"
                )
                data = self._run_powershell_json(script)
                return TunBinding(interface_index=int(data["interface_index"]), ip_address=str(data["ip_address"]))
            except Exception as exc:
                last_error = str(exc)
                time.sleep(0.6)
        raise CoreError(last_error)

    def apply_default_route(self, tun: TunBinding) -> None:
        self.clear_default_route()
        delete_command = ["route", "delete", "0.0.0.0", "mask", "0.0.0.0", tun.ip_address]
        subprocess.run(delete_command, capture_output=True, text=True, timeout=8, check=False)
        add_command = [
            "route",
            "add",
            "0.0.0.0",
            "mask",
            "0.0.0.0",
            tun.ip_address,
            "if",
            str(tun.interface_index),
            "metric",
            "3",
        ]
        result = subprocess.run(add_command, capture_output=True, text=True, timeout=8, check=False)
        if result.returncode != 0:
            raise CoreError((result.stderr or result.stdout or "Failed to add default route.").strip())
        self.active_route = (tun.ip_address, tun.interface_index)
        debug_log(f"Applied TUN default route via {tun.ip_address} if {tun.interface_index}")

    def clear_default_route(self) -> None:
        if not self.active_route:
            return
        gateway, interface_index = self.active_route
        delete_command = [
            "route",
            "delete",
            "0.0.0.0",
            "mask",
            "0.0.0.0",
            gateway,
            "if",
            str(interface_index),
        ]
        result = subprocess.run(delete_command, capture_output=True, text=True, timeout=8, check=False)
        debug_log(
            f"Cleared TUN route rc={result.returncode} out={(result.stdout or '')[-120:]} err={(result.stderr or '')[-120:]}"
        )
        self.active_route = None


class XrayManager:
    def __init__(self) -> None:
        ensure_dirs()
        self.process: subprocess.Popen[str] | None = None
        self.proxy = ProxyController()
        self.route_controller = TunRouteController()
        self.http_port = SYSTEM_PROXY_PORT
        self.socks_port = 10808
        self.runtime_config_path: Path | None = None
        self.log_handle: Any = None
        self.primary_binding: NetworkBinding | None = None
        self.tun_name = "xray0"

    def _terminate_managed_processes(self) -> None:
        exe_path = str(XRAY_EXE).lower()
        script = (
            "$target = [string]::Join('', @'\n"
            f"{exe_path}\n"
            "'@).Trim().ToLower(); "
            "$procs = Get-CimInstance Win32_Process -Filter \"Name = 'xray.exe'\"; "
            "foreach ($proc in $procs) { "
            "  $path = ''; "
            "  try { $path = ($proc.ExecutablePath | Out-String).Trim().ToLower() } catch {}; "
            "  if ($path -eq $target) { "
            "    try { Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop } catch {} "
            "  } "
            "}"
        )
        try:
            result = subprocess.run(
                ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
                capture_output=True,
                text=True,
                timeout=12,
                check=False,
            )
            debug_log(
                f"terminate_managed_processes rc={result.returncode} "
                f"out={(result.stdout or '')[-160:]} err={(result.stderr or '')[-160:]}"
            )
        except Exception as exc:
            debug_log(f"terminate_managed_processes failed: {exc}")

    def _get_free_port(self) -> int:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.bind((SYSTEM_PROXY_HOST, 0))
            sock.listen(1)
            return int(sock.getsockname()[1])

    def _allocate_ports(self) -> None:
        self.socks_port = self._get_free_port()
        self.http_port = self._get_free_port()

    def _download_latest_core(self) -> None:
        api_url = "https://api.github.com/repos/XTLS/Xray-core/releases/latest"
        try:
            response = requests.get(api_url, timeout=SUBSCRIPTION_TIMEOUT)
            response.raise_for_status()
            release = response.json()
        except requests.RequestException as exc:
            raise CoreError(f"Failed to check core release: {exc}") from exc

        assets = release.get("assets", [])
        asset = next((item for item in assets if "windows-64.zip" in item.get("name", "").lower()), None)
        if not asset or not asset.get("browser_download_url"):
            raise CoreError("Windows core package was not found.")

        archive = io.BytesIO()
        try:
            stream = requests.get(asset["browser_download_url"], timeout=SUBSCRIPTION_TIMEOUT, stream=True)
            stream.raise_for_status()
            for chunk in stream.iter_content(chunk_size=1024 * 512):
                if chunk:
                    archive.write(chunk)
        except requests.RequestException as exc:
            raise CoreError(f"Failed to download core package: {exc}") from exc

        archive.seek(0)
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(CORE_DIR)

    def ensure_core(self) -> None:
        if XRAY_EXE.exists():
            return
        self._download_latest_core()
        if not XRAY_EXE.exists():
            raise CoreError("Core download completed, but xray.exe was not found.")

    def _build_stream_settings(self, entry: ConfigEntry) -> dict[str, Any]:
        network = entry.query.get("type", "tcp")
        security = entry.query.get("security", "none")
        settings: dict[str, Any] = {"network": network, "security": security}

        if security == "tls":
            tls_settings: dict[str, Any] = {
                "serverName": entry.query.get("sni") or entry.query.get("host") or entry.host,
                "allowInsecure": bool_from_value(entry.query.get("allowInsecure")),
            }
            if entry.query.get("fp"):
                tls_settings["fingerprint"] = entry.query["fp"]
            alpn = split_csv(entry.query.get("alpn"))
            if alpn:
                tls_settings["alpn"] = alpn
            settings["tlsSettings"] = tls_settings
        elif security == "reality":
            reality_settings: dict[str, Any] = {
                "serverName": entry.query.get("sni") or entry.host,
                "publicKey": entry.query.get("pbk", ""),
                "shortId": entry.query.get("sid", ""),
                "show": False,
            }
            if entry.query.get("fp"):
                reality_settings["fingerprint"] = entry.query["fp"]
            if entry.query.get("spx"):
                reality_settings["spiderX"] = entry.query["spx"]
            settings["realitySettings"] = reality_settings

        if network == "ws":
            headers: dict[str, str] = {}
            if entry.query.get("host"):
                headers["Host"] = entry.query["host"]
            settings["wsSettings"] = {"path": entry.query.get("path", "/"), "headers": headers}
        elif network == "grpc":
            settings["grpcSettings"] = {
                "serviceName": entry.query.get("serviceName", ""),
                "authority": entry.query.get("authority", ""),
                "multiMode": entry.query.get("mode", "") == "multi",
            }
        elif network == "tcp":
            header_type = entry.query.get("headerType")
            if header_type:
                settings["tcpSettings"] = {"header": {"type": header_type}}
        elif network == "httpupgrade":
            settings["httpupgradeSettings"] = {"host": entry.query.get("host", ""), "path": entry.query.get("path", "/")}
        elif network == "xhttp":
            settings["xhttpSettings"] = {
                "path": entry.query.get("path", "/"),
                "host": entry.query.get("host", ""),
                "mode": entry.query.get("mode", "auto"),
            }
        if self.primary_binding:
            settings["sockopt"] = {"interface": self.primary_binding.interface_alias}
        return settings

    def _build_outbound(self, tag: str, protocol: str, settings: dict[str, Any] | None = None, stream: dict[str, Any] | None = None) -> dict[str, Any]:
        outbound: dict[str, Any] = {"tag": tag, "protocol": protocol}
        if settings is not None:
            outbound["settings"] = settings
        if stream is not None:
            outbound["streamSettings"] = stream
        if self.primary_binding:
            outbound["sendThrough"] = self.primary_binding.ip_address
        return outbound

    def _build_runtime_config(self, entry: ConfigEntry) -> dict[str, Any]:
        user: dict[str, Any] = {"id": entry.uuid, "encryption": entry.query.get("encryption", "none")}
        if entry.query.get("flow"):
            user["flow"] = entry.query["flow"]
        proxy_outbound = self._build_outbound(
            "proxy",
            "vless",
            {"vnext": [{"address": entry.host, "port": entry.port, "users": [user]}]},
            self._build_stream_settings(entry),
        )
        return {
            "log": {"loglevel": "warning"},
            "inbounds": [
                {
                    "tag": "tun-in",
                    "protocol": "tun",
                    "settings": {"name": self.tun_name, "MTU": 1500},
                    "sniffing": {"enabled": True, "destOverride": ["http", "tls", "quic"]},
                },
                {"listen": SYSTEM_PROXY_HOST, "port": self.socks_port, "protocol": "socks", "settings": {"udp": True}},
                {"listen": SYSTEM_PROXY_HOST, "port": self.http_port, "protocol": "http", "settings": {"timeout": 0}},
            ],
            "outbounds": [
                proxy_outbound,
                self._build_outbound("direct", "freedom", {"domainStrategy": "UseIP"}),
                self._build_outbound("block", "blackhole"),
            ],
            "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                    {"type": "field", "ip": ["geoip:private", "127.0.0.0/8"], "outboundTag": "direct"},
                    {"type": "field", "network": "udp", "port": "443", "outboundTag": "block"},
                ],
            },
        }

    def _verify_proxy(self) -> str:
        proxy_variants = [
            {},
            {
                "http": f"http://{SYSTEM_PROXY_HOST}:{self.http_port}",
                "https": f"http://{SYSTEM_PROXY_HOST}:{self.http_port}",
            },
        ]
        for url in PROXY_TEST_URLS:
            for proxies in proxy_variants:
                try:
                    response = requests.get(url, proxies=proxies or None, timeout=6)
                    response.raise_for_status()
                except requests.RequestException:
                    continue
                if "json" in response.headers.get("Content-Type", "").lower():
                    try:
                        payload = response.json()
                        ip = str(payload.get("ip", "")).strip()
                    except ValueError:
                        ip = ""
                else:
                    ip = response.text.strip()
                if ip:
                    return ip
        return ""

    def start(self, entry: ConfigEntry) -> str:
        self.stop()
        self._terminate_managed_processes()
        self.ensure_core()
        before_ip = self._verify_proxy()
        self.primary_binding = self.route_controller.detect_primary_network()
        self._allocate_ports()
        fd, temp_path = tempfile.mkstemp(prefix="vpnboss_runtime_", suffix=".json", dir=str(APP_DIR))
        os.close(fd)
        self.runtime_config_path = Path(temp_path)
        self.runtime_config_path.write_text(
            json.dumps(self._build_runtime_config(entry), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        self.log_handle = open(XRAY_LOG, "w", encoding="utf-8")
        for attempt in range(2):
            self.process = subprocess.Popen(
                [str(XRAY_EXE), "run", "-c", str(self.runtime_config_path)],
                stdout=self.log_handle,
                stderr=subprocess.STDOUT,
                cwd=str(CORE_DIR),
                text=True,
            )
            time.sleep(1.3)
            if self.process.poll() is None:
                break
            output = XRAY_LOG.read_text(encoding="utf-8", errors="ignore")[-1400:] if XRAY_LOG.exists() else ""
            self.process = None
            if attempt == 0 and ("Failed to register rings" in output or "initialization has already been completed" in output):
                debug_log("Detected stale xray/tun state, retrying start once.")
                self._terminate_managed_processes()
                time.sleep(1.0)
                continue
            self._cleanup_runtime_files()
            raise CoreError(output or "Core terminated immediately.")
        tun_binding = self.route_controller.wait_for_tun()
        self.route_controller.apply_default_route(tun_binding)
        external_ip = self._verify_proxy()
        if not external_ip:
            raise CoreError("VPN tunnel started, but external IP check failed.")
        if before_ip and external_ip == before_ip:
            raise CoreError("VPN tunnel started, but external IP did not change.")
        return external_ip

    def _cleanup_runtime_files(self) -> None:
        if self.log_handle is not None:
            try:
                self.log_handle.close()
            except Exception:
                pass
            self.log_handle = None
        if self.runtime_config_path and self.runtime_config_path.exists():
            try:
                self.runtime_config_path.unlink()
            except Exception:
                pass
        self.runtime_config_path = None

    def stop(self) -> None:
        self.route_controller.clear_default_route()
        self.proxy.restore()
        if self.process is not None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
        self._terminate_managed_processes()
        self._cleanup_runtime_files()
        self.primary_binding = None


class WorkerSignals(QObject):
    finished = Signal(object)
    failed = Signal(str)


class Task(QRunnable):
    def __init__(self, fn, *args, **kwargs) -> None:
        super().__init__()
        self.fn = fn
        self.args = args
        self.kwargs = kwargs
        self.signals = WorkerSignals()
        self.setAutoDelete(False)

    def run(self) -> None:
        try:
            result = self.fn(*self.args, **self.kwargs)
        except Exception as exc:
            try:
                self.signals.failed.emit(str(exc))
            except RuntimeError:
                debug_log(f"Task failed after signal source deletion: {exc}")
            return
        try:
            self.signals.finished.emit(result)
        except RuntimeError:
            debug_log("Task finished after signal source deletion.")


class DesktopBridge(QObject):
    eventEmitted = Signal(str, str)
    mainThreadInvoke = Signal(object)

    def __init__(self) -> None:
        super().__init__()
        self.pool = QThreadPool.globalInstance()
        self.store = SecureStateStore()
        self.configs = self.store.configs()
        self.routes = build_routes(self.configs)
        self.selected_index = min(int(self.store.state.get("selected_index", 0)), max(len(self.routes) - 1, 0)) if self.routes else 0
        self.xray = XrayManager()
        self.connected = False
        self.busy = False
        self.external_ip = ""
        self.site_session_token = str(self.store.state.get("site_session_token", ""))
        self.site_profile = self.store.state.get("site_profile", {}) or {}
        self._tasks: set[Task] = set()
        self.mainThreadInvoke.connect(self._run_main_thread_callback)

    def _emit(self, event_type: str, payload: dict[str, Any]) -> None:
        self.eventEmitted.emit(event_type, json.dumps(payload, ensure_ascii=False))

    @Slot(object)
    def _run_main_thread_callback(self, callback_payload: object) -> None:
        callback, args = callback_payload
        callback(*args)

    def _invoke_on_main(self, callback, *args) -> None:
        self.mainThreadInvoke.emit((callback, args))

    def _track_task(self, task: Task) -> None:
        self._tasks.add(task)

        def cleanup(*_args) -> None:
            self._tasks.discard(task)

        task.signals.finished.connect(cleanup)
        task.signals.failed.connect(cleanup)
        self.pool.start(task)

    def _emit_site_auth_error(self, message: str) -> None:
        self._emit("site_auth_error", {"error": message})

    def _emit_subscription_import_failed(self, message: str) -> None:
        self._emit("subscription_import_failed", {"error": message})

    def _current_entry(self) -> ConfigEntry | None:
        if not self.routes or not self.configs:
            return None
        route = self.routes[min(self.selected_index, len(self.routes) - 1)]
        idx = int(route.get("config_index", 0))
        if idx < 0 or idx >= len(self.configs):
            return None
        return self.configs[idx]

    def _routes_payload(self) -> list[dict[str, Any]]:
        return [{k: v for k, v in route.items() if k != "config_index"} for route in self.routes]

    def _measure_config_latency(self, entry: ConfigEntry, timeout: float = 2.4) -> int:
        started = time.perf_counter()
        try:
            with socket.create_connection((entry.host, entry.port), timeout=timeout):
                return max(1, int((time.perf_counter() - started) * 1000))
        except OSError:
            return 9999

    def _select_fastest_route_worker(self) -> dict[str, Any]:
        if not self.configs or not self.routes:
            raise SubscriptionError("Нет серверов для проверки.")
        latency_by_config: dict[int, int] = {}
        for idx, entry in enumerate(self.configs):
            latency_by_config[idx] = self._measure_config_latency(entry)
        best_route_index = 0
        best_latency = 9999
        for route_index, route in enumerate(self.routes):
            config_index = int(route.get("config_index", 0))
            latency = latency_by_config.get(config_index, 9999)
            route["latencyMs"] = latency
            route["detail"] = "нет ответа" if latency >= 9999 else f"{latency} ms • маршрут скрыт"
            if latency < best_latency:
                best_latency = latency
                best_route_index = route_index
        self.selected_index = best_route_index
        self.store.set_selected_index(best_route_index)
        return {
            "routes": self._routes_payload(),
            "selectedIndex": best_route_index,
            "latencyMs": best_latency,
        }

    @Slot(result=str)
    def loadInitialState(self) -> str:
        payload = {
            "onboardingDone": bool(self.store.state.get("onboarding_done", False)),
            "subscriptionUrl": self.store.state.get("subscription_url", ""),
            "routes": self._routes_payload(),
            "selectedIndex": self.selected_index,
            "connected": self.connected,
            "busy": self.busy,
            "externalIp": self.external_ip,
            "siteLoggedIn": bool(self.site_session_token),
            "siteProfile": self.site_profile,
            "siteBaseUrl": SITE_BASE_URL,
        }
        return json.dumps(payload, ensure_ascii=False)

    @Slot()
    def markOnboardingDone(self) -> None:
        self.store.set_onboarding_done(True)

    def _profile_summary(self, profile: dict[str, Any]) -> dict[str, Any]:
        sub = profile.get("sub") or {}
        return {
            "telegramId": profile.get("telegramId"),
            "username": profile.get("username", ""),
            "name": profile.get("name", ""),
            "email": profile.get("email", ""),
            "displayName": profile.get("displayName", ""),
            "authProvider": profile.get("authProvider", ""),
            "hasPassword": bool(profile.get("hasPassword", False)),
            "hasEmail": bool(profile.get("hasEmail", False)),
            "needsProfileCompletion": bool(profile.get("needsProfileCompletion", False)),
            "balance": profile.get("balance", 0),
            "subToken": profile.get("subToken", ""),
            "hasSubscription": bool(sub),
            "plan": sub.get("plan", ""),
            "planName": sub.get("planName", ""),
            "expiresAt": sub.get("expiresAt", ""),
            "devicesLimit": sub.get("devicesLimit", 0),
            "gbLimit": sub.get("gbLimit"),
            "gbUsed": sub.get("gbUsed", 0),
            "hasBypassBs": bool(sub.get("hasBypassBs", False)),
            "hasExtraFast": bool(sub.get("hasExtraFast", False)),
            "devicesCount": len(profile.get("devices") or []),
        }

    def _profile_needs_completion(self, profile: dict[str, Any]) -> bool:
        return bool(profile.get("needsProfileCompletion"))

    def _ensure_device_for_profile(self, profile: dict[str, Any], device_name: str) -> dict[str, Any]:
        if not profile.get("sub"):
            return profile
        devices = profile.get("devices") or []
        if devices:
            return profile
        site_api_request("POST", "/api/devices", token=self.site_session_token, body={"name": device_name})
        return site_api_request("GET", "/api/auth/me", token=self.site_session_token)

    def _legacy_restore_from_profile(self, profile: dict[str, Any]) -> dict[str, Any]:
        result: dict[str, Any] = {"profile": profile}
        if self._profile_needs_completion(profile):
            result["profile_incomplete"] = True
            result["message"] = "Завершите регистрацию: укажите email и пароль для web-входа."
            return result
        sub_token = str(profile.get("subToken") or "").strip()
        has_sub = bool(profile.get("sub"))
        if has_sub and sub_token:
            profile = self._ensure_device_for_profile(profile, "Windows PC")
            result["profile"] = profile
            sub_token = str(profile.get("subToken") or "").strip()
            try:
                configs = fetch_subscription(sub_token)
            except Exception as exc:
                result["subscription_error"] = str(exc)
                debug_log(f"Legacy subscription fetch failed: {exc}")
            else:
                result["configs"] = configs
                result["subscription_url"] = normalize_subscription_input(sub_token)
        return result

    def _restore_from_connect_configs(self) -> dict[str, Any]:
        profile = site_api_request("GET", "/api/auth/me", token=self.site_session_token)
        if self._profile_needs_completion(profile):
            return {
                "profile": profile,
                "profile_incomplete": True,
                "message": "Завершите регистрацию: укажите email и пароль для web-входа.",
            }
        profile = self._ensure_device_for_profile(profile, "Windows PC")
        bundle = site_api_request("GET", "/api/connect/configs", token=self.site_session_token)
        result: dict[str, Any] = {"profile": profile}

        connect = bundle.get("connect") or {}
        configs_payload = bundle.get("configs") or []
        raw_items: list[str] = []
        for item in configs_payload:
            item_payload = item if isinstance(item, dict) else {}
            vless = first_text_value(item_payload, ("vlessKey", "url", "link", "subscriptionUrl")) if item_payload else str(item or "").strip()
            if vless.lower().startswith("vless://"):
                raw_items.append(vless)
        if raw_items:
            parsed = []
            for index, raw_item in enumerate(raw_items):
                entry = parse_vless_uri(raw_item)
                meta = configs_payload[index] if index < len(configs_payload) and isinstance(configs_payload[index], dict) else {}
                parsed.append(enrich_config_from_api(entry, meta))
            result["configs"] = parsed
        if connect.get("subUrl"):
            subscription_url = str(connect.get("subUrl"))
            result["subscription_url"] = subscription_url
            if not result.get("configs"):
                result["configs"] = fetch_subscription(subscription_url)
        return result

    def _site_restore_worker(self) -> dict[str, Any]:
        if not self.site_session_token:
            raise SiteAuthError("No saved site session.")
        try:
            debug_log("Trying /api/connect/configs flow.")
            return self._restore_from_connect_configs()
        except Exception as exc:
            debug_log(f"/api/connect/configs flow unavailable, falling back to legacy flow: {exc}")
            profile = site_api_request("GET", "/api/auth/me", token=self.site_session_token)
            return self._legacy_restore_from_profile(profile)

    def _apply_site_restore(self, result: dict[str, Any], event_type: str) -> None:
        profile = result.get("profile") or {}
        self.site_profile = profile
        self.store.set_site_session(self.site_session_token, profile)

        payload = {
            "siteLoggedIn": bool(self.site_session_token),
            "siteProfile": self._profile_summary(profile),
            "hasSubscription": False,
        }

        configs = result.get("configs")
        if isinstance(configs, list) and configs:
            self.configs = configs
            self.routes = build_routes(configs)
            self.selected_index = 0
            self.store.set_subscription(result.get("subscription_url", ""), configs)
            payload.update(
                {
                    "subscriptionUrl": self.store.state.get("subscription_url", ""),
                    "routes": self._routes_payload(),
                    "selectedIndex": self.selected_index,
                    "hasSubscription": True,
                }
            )
        elif result.get("profile_incomplete"):
            payload["message"] = str(result.get("message") or "Завершите регистрацию: укажите email и пароль для web-входа.")
        elif not profile.get("sub"):
            payload["message"] = "Вход выполнен. Активной подписки пока нет."
        else:
            payload["message"] = str(result.get("subscription_error") or "Вход выполнен, но конфиги еще не готовы.")

        self._emit(event_type, payload)

    def _finish_site_session_from_auth(self, result: object) -> None:
        payload = dict(result or {})
        self.site_session_token = str(payload.get("token") or "")
        debug_log(f"Auth finished, token received={'yes' if self.site_session_token else 'no'} payload_keys={sorted(payload.keys())}")
        if not self.site_session_token:
            self._emit("site_auth_error", {"error": "Сайт не вернул токен сессии."})
            return
        task = Task(self._site_restore_worker)
        task.signals.finished.connect(lambda restore_result: self._invoke_on_main(self._apply_site_restore, restore_result, "site_auth_success"))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_site_auth_error, message))
        self._track_task(task)

    def _clear_site_session_state(self, message: str = "") -> None:
        self.site_session_token = ""
        self.site_profile = {}
        self.store.clear_site_session()
        self._emit("site_auth_cleared", {"message": message})

    @Slot(int)
    def setSelectedIndex(self, index: int) -> None:
        self.selected_index = max(0, index)
        self.store.set_selected_index(self.selected_index)

    @Slot()
    def restoreSiteSession(self) -> None:
        if not self.site_session_token:
            return
        task = Task(self._site_restore_worker)
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._apply_site_restore, result, "site_session_restored"))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._clear_site_session_state, message))
        self._track_task(task)

    @Slot(str, str)
    def sitePasswordLogin(self, login: str, password: str) -> None:
        debug_log(f"sitePasswordLogin called login={login!r}")
        task = Task(site_api_request, "POST", "/api/auth/login", "", {"login": login.strip(), "password": password})
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._finish_site_session_from_auth, result))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_site_auth_error, message))
        self._track_task(task)

    @Slot(str, str, str)
    def siteRegister(self, login: str, email: str, password: str) -> None:
        debug_log(f"siteRegister called login={login!r} email={email!r}")
        task = Task(
            site_api_request,
            "POST",
            "/api/auth/register",
            "",
            {"login": login.strip(), "email": email.strip(), "password": password, "displayName": login.strip().lstrip("@")},
        )
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._finish_site_session_from_auth, result))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_site_auth_error, message))
        self._track_task(task)

    @Slot()
    def startSiteAuth(self) -> None:
        debug_log("startSiteAuth called")
        task = Task(site_api_request, "POST", "/api/auth/tg-init", "", {"mode": "login"})
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._on_site_auth_init, result))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_site_auth_error, message))
        self._track_task(task)

    def _on_site_auth_init(self, result: object) -> None:
        payload = dict(result or {})
        web_link = (
            str(payload.get("webLink") or "")
            or str(payload.get("siteLink") or "")
            or str(payload.get("authUrl") or "")
            or str(payload.get("url") or "")
        )
        candidates = [web_link, SITE_AUTH_URL, SITE_BASE_URL]
        link = next((item for item in candidates if item.startswith("http://") or item.startswith("https://")), "")
        debug_log(f"Site auth init result keys={sorted(payload.keys())} web_link={web_link} opened={link}")
        if link:
            QDesktopServices.openUrl(QUrl(link))
        payload["openedLink"] = link
        self._emit("site_auth_init", payload)

    @Slot(str)
    def checkSiteAuth(self, web_token: str) -> None:
        if not web_token.strip():
            return
        task = Task(site_api_request, "GET", f"/api/auth/tg-check/{web_token.strip()}")
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._on_site_auth_check, result))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_site_auth_error, message))
        self._track_task(task)

    def _on_site_auth_check(self, result: object) -> None:
        payload = dict(result or {})
        status = str(payload.get("status") or "pending")
        if status != "confirmed":
            self._emit("site_auth_poll", {"status": status})
            return
        self.site_session_token = str(payload.get("token") or "")
        if not self.site_session_token:
            self._emit("site_auth_error", {"error": "Сайт не вернул сессию после подтверждения."})
            return
        self._emit("site_auth_poll", {"status": "confirmed"})
        task = Task(self._site_restore_worker)
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._apply_site_restore, result, "site_auth_success"))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_site_auth_error, message))
        self._track_task(task)

    @Slot(str)
    def importSubscription(self, raw_input: str) -> None:
        self._emit("subscription_import_started", {})
        task = Task(fetch_subscription, raw_input)
        task.signals.finished.connect(lambda configs: self._invoke_on_main(self._on_subscription_loaded, raw_input, configs))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._emit_subscription_import_failed, message))
        self._track_task(task)

    def _on_subscription_loaded(self, raw_input: str, configs: list[ConfigEntry]) -> None:
        self.configs = configs
        self.routes = build_routes(configs)
        self.selected_index = 0
        self.store.set_subscription(raw_input, configs)
        self._emit(
            "subscription_import_success",
            {
                "subscriptionUrl": self.store.state.get("subscription_url", ""),
                "routes": self._routes_payload(),
                "selectedIndex": self.selected_index,
            },
        )

    @Slot()
    def autoConnectVpn(self) -> None:
        if self.busy:
            return
        self.busy = True
        self._emit("vpn_status", {"busy": True, "connected": self.connected, "phase": "ping"})
        task = Task(self._select_fastest_route_worker)
        task.signals.finished.connect(lambda result: self._invoke_on_main(self._on_fastest_route_selected, result))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._on_connect_failed, message))
        self._track_task(task)

    def _on_fastest_route_selected(self, result: object) -> None:
        payload = dict(result or {})
        self._emit("routes_latency", payload)
        self.busy = False
        self.connectVpn()

    @Slot()
    def connectVpn(self) -> None:
        if self.busy:
            return
        entry = self._current_entry()
        if entry is None:
            self._emit("vpn_error", {"error": "Сначала импортируйте подписку и выберите маршрут."})
            return
        self.busy = True
        self._emit("vpn_status", {"busy": True, "connected": self.connected, "phase": "connect"})
        task = Task(self.xray.start, entry)
        task.signals.finished.connect(lambda external_ip: self._invoke_on_main(self._on_connected, external_ip))
        task.signals.failed.connect(lambda message: self._invoke_on_main(self._on_connect_failed, message))
        self._track_task(task)

    def _on_connected(self, external_ip: object) -> None:
        self.busy = False
        self.connected = True
        self.external_ip = str(external_ip or "")
        self._emit("vpn_status", {"busy": False, "connected": True, "externalIp": self.external_ip})

    def _on_connect_failed(self, message: str) -> None:
        try:
            self.xray.stop()
        except Exception as exc:
            debug_log(f"cleanup after connect failure failed: {exc}")
        self.busy = False
        self.connected = False
        self.external_ip = ""
        self._emit("vpn_status", {"busy": False, "connected": False})
        self._emit("vpn_error", {"error": message})

    @Slot()
    def disconnectVpn(self) -> None:
        if self.busy:
            return
        self.busy = True
        self._emit("vpn_status", {"busy": True, "connected": self.connected})
        task = Task(self.xray.stop)
        task.signals.finished.connect(lambda _: self._invoke_on_main(self._on_disconnected))
        task.signals.failed.connect(lambda _: self._invoke_on_main(self._on_disconnected))
        self._track_task(task)

    def _on_disconnected(self) -> None:
        self.busy = False
        self.connected = False
        self.external_ip = ""
        self._emit("vpn_status", {"busy": False, "connected": False})

    @Slot()
    def openSite(self) -> None:
        QDesktopServices.openUrl(QUrl(SITE_AUTH_URL))

    @Slot()
    def openBotTopUp(self) -> None:
        QDesktopServices.openUrl(QUrl(SITE_BASE_URL))

    @Slot()
    def openSupport(self) -> None:
        QDesktopServices.openUrl(QUrl(SITE_BASE_URL))

    @Slot()
    def openSubscriptions(self) -> None:
        QDesktopServices.openUrl(QUrl(SITE_BASE_URL))


class MainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        ensure_dirs()
        self.setWindowTitle(APP_NAME)
        self.setFixedSize(430, 852)
        self.view = QWebEngineView(self)
        self.setCentralWidget(self.view)
        self.bridge = DesktopBridge()
        self.channel = QWebChannel(self.view.page())
        self.channel.registerObject("bridge", self.bridge)
        self.view.page().setWebChannel(self.channel)
        self.view.setUrl(QUrl.fromLocalFile(str((resource_base_dir() / "web" / "index.html").resolve())))

    def closeEvent(self, event) -> None:
        try:
            self.bridge.xray.stop()
        finally:
            super().closeEvent(event)


def main() -> int:
    ensure_dirs()
    if os.name == "nt" and not is_windows_admin():
        debug_log("Application is not elevated. Requesting administrator rights for TUN mode.")
        if relaunch_as_admin():
            return 0
        show_message_box(APP_NAME, "Для настоящего VPN-подключения Windows-клиент нужно запускать с правами администратора.")
        return 1
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
