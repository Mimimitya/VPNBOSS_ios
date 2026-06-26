const BOT_URL = "https://t.me/Vpnboss_robot";

const screens = [...document.querySelectorAll(".screen")];
const nodes = {
  authStatus: document.getElementById("authStatus"),
  profileLine: document.getElementById("profileLine"),
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
let pendingWebToken = "";
let pollTimer = 0;

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

function cleanName(route) {
  return String(route?.name || "Сервер").replace(route?.flag || "", "").trim() || "Сервер";
}

function flagMarkup(route) {
  const code = flagEmojiToCode(route?.flag);
  if (code) return `<img class="flag-svg" src="flags/4x3/${code}.svg" alt="${cleanName(route)}">`;
  return route?.flag || "🌐";
}

function renderRoutes() {
  if (!routes.length) {
    routes = [
      { flag: "🇩🇰", name: "Дания", detail: "Ожидание подписки" },
      { flag: "🇫🇮", name: "Финляндия", detail: "Ожидание подписки" },
      { flag: "🇳🇱", name: "Нидерланды", detail: "Ожидание подписки" },
    ];
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

function setSelected(next) {
  selectedIndex = (next + routes.length) % routes.length;
  renderRoutes();
  if (bridge?.setSelectedIndex) bridge.setSelectedIndex(selectedIndex);
}

function showPower(node) {
  [nodes.vpnOffAsset, nodes.vpnOnAsset, nodes.onFirstAnimation, nodes.loadingAnimation, nodes.onAnimation]
    .forEach((asset) => asset?.classList.remove("is-visible"));
  node?.classList.add("is-visible");
}

function setVpnState(payload = {}) {
  busy = Boolean(payload.busy);
  connected = Boolean(payload.connected);
  nodes.powerButton?.classList.toggle("connecting", busy);
  nodes.powerButtonOn?.classList.toggle("connecting", busy);
  if (connected) {
    showPower(nodes.vpnOnAsset);
    showScreen("home-on");
  } else {
    showPower(nodes.vpnOffAsset);
    showScreen(routes.length ? "home-off" : "auth");
  }
  renderRoutes();
}

function startSiteAuth() {
  setStatus("Открываю официальный вход VPNBOSS...");
  bridge?.startSiteTelegramAuth?.();
}

function stopPoll() {
  if (pollTimer) window.clearInterval(pollTimer);
  pollTimer = 0;
}

function startPoll(token) {
  pendingWebToken = token || "";
  stopPoll();
  if (!pendingWebToken) return;
  pollTimer = window.setInterval(() => bridge?.checkSiteTelegramAuth?.(pendingWebToken), 2200);
}

function applyAuthSuccess(payload = {}) {
  stopPoll();
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
    setStatus("Подтвердите вход в Telegram или на сайте");
    startPoll(payload.webToken);
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
  if (type === "vpn_status") setVpnState(payload);
  if (type === "vpn_error") setStatus(payload.error || "Ошибка VPN");
}

function bindUi() {
  document.querySelectorAll("[data-next]").forEach((button) => {
    button.addEventListener("click", () => showScreen(button.dataset.next));
  });
  document.querySelectorAll(".tg-open").forEach((button) => {
    button.addEventListener("click", () => bridge?.openTelegramAuth?.() || window.open(BOT_URL, "_blank"));
  });
  document.getElementById("siteAuthButton").addEventListener("click", startSiteAuth);
  document.getElementById("prevServer").addEventListener("click", () => setSelected(selectedIndex - 1));
  document.getElementById("nextServer").addEventListener("click", () => setSelected(selectedIndex + 1));
  document.getElementById("prevServerOn").addEventListener("click", () => setSelected(selectedIndex - 1));
  document.getElementById("nextServerOn").addEventListener("click", () => setSelected(selectedIndex + 1));
  nodes.powerButton.addEventListener("click", () => bridge?.connectVpn?.());
  nodes.powerButtonOn.addEventListener("click", () => bridge?.disconnectVpn?.());
  document.getElementById("autoConnect").addEventListener("click", () => bridge?.connectVpn?.());
  document.getElementById("disconnectButton").addEventListener("click", () => bridge?.disconnectVpn?.());
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
