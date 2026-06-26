const SITE_URL = "https://vpnboss.space";

const screens = [...document.querySelectorAll(".screen")];
const nodes = {
  authStatus: document.getElementById("authStatus"),
  profileLine: document.getElementById("profileLine"),
  connectionPhase: document.getElementById("connectionPhase"),
  connectionPhaseOn: document.getElementById("connectionPhaseOn"),
  powerButton: document.getElementById("powerButton"),
  powerButtonOn: document.getElementById("powerButtonOn"),
  vpnOffAsset: document.getElementById("vpnOffAsset"),
  vpnOnAsset: document.getElementById("vpnOnAsset"),
  onFirstAnimation: document.getElementById("onFirstAnimation"),
  loadingAnimation: document.getElementById("loadingAnimation"),
  onAnimation: document.getElementById("onAnimation"),
};

let bridge = null;
let routes = [];
let selectedIndex = 0;
let connected = false;
let busy = false;

const COUNTRY_CODE_BY_NAME = {
  "дания": "dk",
  "denmark": "dk",
  "финляндия": "fi",
  "finland": "fi",
  "нидерланды": "nl",
  "netherlands": "nl",
  "германия": "de",
  "germany": "de",
  "франция": "fr",
  "france": "fr",
  "польша": "pl",
  "poland": "pl",
  "швеция": "se",
  "sweden": "se",
  "норвегия": "no",
  "norway": "no",
  "сша": "us",
  "usa": "us",
  "united states": "us",
  "великобритания": "gb",
  "united kingdom": "gb",
  "uk": "gb",
  "турция": "tr",
  "turkey": "tr",
  "япония": "jp",
  "japan": "jp",
};

function showScreen(name) {
  screens.forEach((screen) => screen.classList.toggle("active", screen.dataset.screen === name));
}

function setStatus(text) {
  if (nodes.authStatus) nodes.authStatus.textContent = text;
}

function parseJson(value, fallback = {}) {
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

function flagEmojiToCode(flag) {
  const chars = Array.from(String(flag || "").trim());
  if (chars.length !== 2) return "";
  const points = chars.map((char) => char.codePointAt(0));
  if (points.some((point) => point < 0x1f1e6 || point > 0x1f1ff)) return "";
  return points.map((point) => String.fromCharCode(point - 0x1f1e6 + 65)).join("").toLowerCase();
}

function countryCodeFromRoute(route) {
  const emojiCode = flagEmojiToCode(route?.flag);
  if (emojiCode) return emojiCode;
  const explicitCode = String(route?.countryCode || route?.code || "").trim().toLowerCase();
  if (/^[a-z]{2}$/.test(explicitCode)) return explicitCode;
  const haystack = `${route?.name || ""} ${route?.detail || ""}`.toLowerCase();
  for (const [name, code] of Object.entries(COUNTRY_CODE_BY_NAME)) {
    if (haystack.includes(name)) return code;
  }
  const codeMatch = haystack.match(/(?:^|[^a-z])([a-z]{2})(?:[^a-z]|$)/);
  return codeMatch ? codeMatch[1] : "";
}

function cleanName(route) {
  return String(route?.name || "Сервер").replace(route?.flag || "", "").trim() || "Сервер";
}

function flagMarkup(route) {
  const code = countryCodeFromRoute(route);
  if (code) {
    return `<img class="flag-svg" draggable="false" src="flags/4x3/${code}.svg" alt="${cleanName(route)}" onerror="this.replaceWith(document.createTextNode('🌐'))">`;
  }
  return "🌐";
}

function renderRoutes() {
  if (!routes.length) {
    [
      "leftServer",
      "mainServer",
      "rightServer",
      "leftServerOn",
      "mainServerOn",
      "rightServerOn",
    ].forEach((id) => {
      const node = document.getElementById(id);
      if (node) node.textContent = "🌐";
    });
    [
      ["serverName", "VPNBOSS"],
      ["serverNameOn", "VPNBOSS"],
      ["serverIp", "Ожидание реальной подписки"],
      ["serverIpOn", "Ожидание реальной подписки"],
    ].forEach(([id, text]) => {
      const node = document.getElementById(id);
      if (node) node.textContent = text;
    });
    return;
  }

  selectedIndex = Math.max(0, Math.min(selectedIndex, routes.length - 1));
  const left = routes[(selectedIndex - 1 + routes.length) % routes.length];
  const current = routes[selectedIndex];
  const right = routes[(selectedIndex + 1) % routes.length];

  [
    ["leftServer", left],
    ["mainServer", current],
    ["rightServer", right],
    ["leftServerOn", left],
    ["mainServerOn", current],
    ["rightServerOn", right],
  ].forEach(([id, route]) => {
    const node = document.getElementById(id);
    if (node) node.innerHTML = flagMarkup(route);
  });

  [
    ["serverName", cleanName(current)],
    ["serverNameOn", cleanName(current)],
    ["serverIp", current.detail || "маршрут скрыт"],
    ["serverIpOn", connected ? "Подключено" : current.detail || "маршрут скрыт"],
  ].forEach(([id, text]) => {
    const node = document.getElementById(id);
    if (node) node.textContent = text;
  });
}

function updatePhase(text) {
  if (nodes.connectionPhase) nodes.connectionPhase.textContent = text;
  if (nodes.connectionPhaseOn) nodes.connectionPhaseOn.textContent = text;
}

function setSelected(next) {
  if (!routes.length) return;
  selectedIndex = (next + routes.length) % routes.length;
  renderRoutes();
  if (bridge?.setSelectedIndex) bridge.setSelectedIndex(selectedIndex);
}

function startAutoConnect() {
  if (bridge?.autoConnectVpn) {
    bridge.autoConnectVpn();
    return;
  }
  bridge?.connectVpn?.();
}

function showPower(node) {
  [nodes.vpnOffAsset, nodes.vpnOnAsset, nodes.onFirstAnimation, nodes.loadingAnimation, nodes.onAnimation]
    .forEach((asset) => asset?.classList.remove("is-visible"));
  node?.classList.add("is-visible");
}

function setVpnState(payload = {}) {
  busy = Boolean(payload.busy);
  connected = Boolean(payload.connected);
  const externalIp = String(payload.externalIp || "").trim();
  nodes.powerButton?.classList.toggle("connecting", busy);
  nodes.powerButtonOn?.classList.toggle("connecting", busy);
  if (busy) {
    showPower(payload.phase === "ping" ? nodes.loadingAnimation : nodes.onFirstAnimation);
    showScreen("home-off");
    updatePhase(payload.phase === "ping" ? "Пингуем серверы..." : "Подключаемся...");
    renderRoutes();
    return;
  }
  if (connected) {
    showPower(nodes.vpnOnAsset);
    showScreen("home-on");
    updatePhase(externalIp ? `IP: ${externalIp}` : "Подключено");
  } else {
    showPower(nodes.vpnOffAsset);
    showScreen(routes.length ? "home-off" : "auth");
    updatePhase(routes.length ? "Готов к подключению" : "Ожидание доступа");
  }
  renderRoutes();
}

function startSiteAuth() {
  setStatus("Открываю официальный вход VPNBOSS...");
  if (bridge?.startSiteAuth) {
    bridge.startSiteAuth();
    return;
  }
  window.open(SITE_URL, "_blank");
}

function applyAuthSuccess(payload = {}) {
  const profile = payload.siteProfile || payload.profile || {};
  const name = profile.displayName || profile.username || profile.name || "VPNBOSS";
  nodes.profileLine.textContent = `${name}: доступ синхронизирован`;
  routes = payload.routes || routes;
  selectedIndex = payload.selectedIndex || 0;
  renderRoutes();
  showScreen(routes.length ? "ready" : "auth");
  setStatus("Доступ подтверждён");
}

function onBridgeEvent(type, payloadText) {
  const payload = parseJson(payloadText);
  if (type === "site_auth_init") {
    setStatus("Войдите на сайте VPNBOSS в открытом окне");
  }
  if (type === "site_auth_poll") {
    setStatus(payload.status === "confirmed" ? "Подтверждено" : "Ожидание подтверждения");
  }
  if (type === "site_auth_success" || type === "site_session_restored") applyAuthSuccess(payload);
  if (type === "site_auth_error") setStatus(payload.error || "Не удалось авторизоваться");
  if (type === "subscription_import_success") {
    routes = payload.routes || [];
    selectedIndex = payload.selectedIndex || 0;
    renderRoutes();
    showScreen("ready");
  }
  if (type === "routes_latency") {
    routes = payload.routes || routes;
    selectedIndex = Number.isInteger(payload.selectedIndex) ? payload.selectedIndex : selectedIndex;
    renderRoutes();
    updatePhase(payload.latencyMs >= 9999 ? "Нет ответа от серверов" : `Лучший сервер: ${payload.latencyMs} ms`);
  }
  if (type === "vpn_status") setVpnState(payload);
  if (type === "vpn_error") {
    const error = payload.error || "Ошибка VPN";
    setStatus(error);
    updatePhase(error);
  }
}

function bindUi() {
  document.querySelectorAll("[data-next]").forEach((button) => {
    button.addEventListener("click", () => showScreen(button.dataset.next));
  });
  document.querySelectorAll(".site-open").forEach((button) => {
    button.addEventListener("click", () => {
      if (bridge?.openSite) {
        bridge.openSite();
        return;
      }
      window.open(SITE_URL, "_blank");
    });
  });
  document.getElementById("siteAuthButton").addEventListener("click", startSiteAuth);
  document.getElementById("prevServer").addEventListener("click", () => setSelected(selectedIndex - 1));
  document.getElementById("nextServer").addEventListener("click", () => setSelected(selectedIndex + 1));
  document.getElementById("prevServerOn").addEventListener("click", () => setSelected(selectedIndex - 1));
  document.getElementById("nextServerOn").addEventListener("click", () => setSelected(selectedIndex + 1));
  nodes.powerButton.addEventListener("click", () => bridge?.connectVpn?.());
  nodes.powerButtonOn.addEventListener("click", () => bridge?.disconnectVpn?.());
  document.getElementById("autoConnect").addEventListener("click", startAutoConnect);
  document.getElementById("disconnectButton").addEventListener("click", () => bridge?.disconnectVpn?.());
  document.querySelectorAll("img").forEach((image) => {
    image.draggable = false;
  });
}

function initBridge() {
  if (!window.qt?.webChannelTransport) {
    renderRoutes();
    bindUi();
    return;
  }
  new QWebChannel(window.qt.webChannelTransport, (channel) => {
    bridge = channel.objects.bridge;
    bridge.eventEmitted.connect(onBridgeEvent);
    const initial = parseJson(bridge.loadInitialState?.() || "{}");
    routes = initial.routes || [];
    selectedIndex = initial.selectedIndex || 0;
    connected = Boolean(initial.connected);
    renderRoutes();
    bindUi();
    if (initial.siteLoggedIn) bridge.restoreSiteSession?.();
    showScreen(initial.siteLoggedIn ? (routes.length ? "home-off" : "ready") : "welcome");
  });
}

initBridge();
