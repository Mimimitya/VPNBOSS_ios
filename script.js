const API_BASE = new URLSearchParams(window.location.search).get("api") || "https://sekretnik1.vps.webdock.cloud";

const STORAGE = {
  token: "vpnboss_client_token",
  lang: "vpnboss_client_lang",
  screen: "vpnboss_client_screen",
  access: "vpnboss_client_access",
};

const I18N = {
  en: {
    welcomeTitle: "Welcome to VPNBOSS",
    welcomeBody: "",
    next: "Next",
    telegramTitle: "Link Telegram before you start.",
    telegramBody: "Open Telegram, confirm access, then return here.",
    telegramButton: "Telegram",
    trialTitle: "You received PRO access for 5 days.",
    trialBody: "",
    chooseTitle: "Choose the access activated for you.",
    chooseBody: "Confirm the option already issued through Telegram or the website.",
    confirm: "Confirm",
    startPlan: "START",
    proPlan: "PRO",
    autoTitle: "Auto-connect",
    autoBody: "Keep the client ready and connect when access is available.",
    statusOn: "Enabled",
    statusOff: "Disabled",
    statusTitle: "Status",
    menuTitle: "Menu",
    activeAccess: "Active access",
    connectionKey: "Connection key",
    autoConnect: "Auto-connect",
    copyButton: "Copy key",
    openButton: "Open client",
    copied: "Copied",
    unavailable: "Not available yet",
    linked: "Telegram linked",
    waitingTelegram: "Waiting for Telegram",
    error: "Error",
  },
  ru: {
    welcomeTitle: "Добро пожаловать в VPNBOSS",
    welcomeBody: "",
    next: "Далее",
    telegramTitle: "Перед началом работы привяжи Telegram-аккаунт.",
    telegramBody: "Открой Telegram, подтверди доступ и вернись обратно.",
    telegramButton: "Telegram",
    trialTitle: "Поздравляем! Вы получили PRO на 5 дней.",
    trialBody: "",
    chooseTitle: "Выберите доступ, активированный для аккаунта.",
    chooseBody: "Подтвердите вариант, полученный через Telegram или сайт.",
    confirm: "Подтвердить",
    startPlan: "СТАРТ",
    proPlan: "PRO",
    autoTitle: "Автоподключение",
    autoBody: "Клиент будет готов подключиться, когда доступ активен.",
    statusOn: "Включено",
    statusOff: "Выключено",
    statusTitle: "Статус",
    menuTitle: "Меню",
    activeAccess: "Активный доступ",
    connectionKey: "Ключ подключения",
    autoConnect: "Автоподключение",
    copyButton: "Скопировать ключ",
    openButton: "Открыть клиент",
    copied: "Скопировано",
    unavailable: "Пока недоступно",
    linked: "Telegram привязан",
    waitingTelegram: "Ждём Telegram",
    error: "Ошибка",
  },
  es: {
    welcomeTitle: "Bienvenido a VPNBOSS",
    welcomeBody: "",
    next: "Siguiente",
    telegramTitle: "Vincula tu Telegram antes de empezar.",
    telegramBody: "Abre Telegram, confirma el acceso y vuelve aquí.",
    telegramButton: "Telegram",
    trialTitle: "Tienes PRO por 5 días.",
    trialBody: "",
    chooseTitle: "Elige el acceso activado para tu cuenta.",
    chooseBody: "Confirma la opción emitida por Telegram o el sitio web.",
    confirm: "Confirmar",
    startPlan: "START",
    proPlan: "PRO",
    autoTitle: "Autoconexión",
    autoBody: "Mantén el cliente listo cuando el acceso esté activo.",
    statusOn: "Activado",
    statusOff: "Desactivado",
    statusTitle: "Estado",
    menuTitle: "Menú",
    activeAccess: "Acceso activo",
    connectionKey: "Clave de conexión",
    autoConnect: "Autoconexión",
    copyButton: "Copiar clave",
    openButton: "Abrir cliente",
    copied: "Copiado",
    unavailable: "Aún no disponible",
    linked: "Telegram vinculado",
    waitingTelegram: "Esperando Telegram",
    error: "Error",
  },
};

const state = {
  token: localStorage.getItem(STORAGE.token) || "",
  lang: localStorage.getItem(STORAGE.lang) || normalizeLang(navigator.language),
  screen: Number(localStorage.getItem(STORAGE.screen) || 0),
  access: localStorage.getItem(STORAGE.access) || "pro",
  user: null,
  connect: null,
  configs: [],
  telegramPoll: null,
};

const screens = [
  { key: "welcome", kind: "welcome" },
  { key: "telegram", kind: "telegram" },
  { key: "trial", kind: "trial" },
  { key: "choose", kind: "choose" },
  { key: "auto", kind: "auto" },
  { key: "status", kind: "status" },
  { key: "menu", kind: "menu" },
];

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function normalizeLang(lang) {
  const value = String(lang || "en").slice(0, 2).toLowerCase();
  return ["en", "ru", "es"].includes(value) ? value : "en";
}

function t(key) {
  if (Object.prototype.hasOwnProperty.call(I18N[state.lang] || {}, key)) {
    return I18N[state.lang][key];
  }
  if (Object.prototype.hasOwnProperty.call(I18N.en, key)) {
    return I18N.en[key];
  }
  return key;
}

function logo() {
  return `<img class="phone-logo" src="./assets/vpnboss-logo.png" alt="VPNBOSS" />`;
}

function head(label = "") {
  return `
    <div class="phone-head">
      ${logo()}
      <span>${label}</span>
      <span class="mini-mark" aria-hidden="true"></span>
    </div>
  `;
}

function screenTemplate(content, options = {}) {
  const grid = options.grid ? " screen-grid" : "";
  return `<div class="screen-inner${grid}">${content}</div>`;
}

function renderWelcome() {
  return screenTemplate(`
    ${head("")}
    <div class="copy">
      <h1 class="title">${t("welcomeTitle")}</h1>
      ${t("welcomeBody") ? `<p class="body">${t("welcomeBody")}</p>` : ""}
    </div>
    <button class="pill ghost" data-action="next" type="button">${t("next")} →</button>
  `);
}

function renderTelegram() {
  return screenTemplate(`
    ${head("")}
    <div class="copy">
      <h1 class="title">${t("telegramTitle")}</h1>
      <button class="telegram-card" data-action="telegram" type="button">
        <span class="telegram-dot">›</span>
        <span>${t("telegramButton")}</span>
      </button>
      <p class="small">${t("telegramBody")}</p>
    </div>
    <button class="pill ghost" data-action="next" type="button">${t("next")} →</button>
  `);
}

function renderTrial() {
  return screenTemplate(`
    ${head("")}
    <div class="copy">
      <h1 class="title">${t("trialTitle")}</h1>
      ${t("trialBody") ? `<p class="body">${t("trialBody")}</p>` : ""}
    </div>
    <button class="pill ghost" data-action="next" type="button">${t("next")} →</button>
  `);
}

function renderChoose() {
  return screenTemplate(`
    ${head(t("menuTitle"))}
    <div class="copy">
      <h1 class="title">${t("chooseTitle")}</h1>
      <p class="small">${t("chooseBody")}</p>
      <div class="access-list">
        ${accessOption("start", t("startPlan"))}
        ${accessOption("pro", t("proPlan"))}
      </div>
    </div>
    <button class="pill" data-action="confirm" type="button">${t("confirm")}</button>
  `);
}

function accessOption(key, label) {
  const active = state.access === key ? " active" : "";
  return `
    <button class="access-option${active}" data-access="${key}" type="button">
      <span class="option-dot">✓</span>
      <span>${label}</span>
    </button>
  `;
}

function renderAuto() {
  return screenTemplate(`
    ${head(t("autoTitle"))}
    <div>
      <div class="question">?</div>
      <div class="copy" style="margin: 18px auto 0; text-align: center">
        <h1 class="title">${t("autoTitle")}</h1>
        <p class="small">${t("autoBody")}</p>
      </div>
    </div>
    <button class="pill" data-action="next" type="button">${t("autoConnect")}</button>
  `);
}

function renderStatus() {
  const connected = Boolean(state.connect?.subUrl || state.configs.length);
  return screenTemplate(`
    ${head(t("statusTitle"))}
    <div class="power-zone">
      <button class="power-btn" data-action="copy" type="button" aria-label="${t("copyButton")}"></button>
      <div class="status-text">${connected ? t("statusOn") : t("statusOff")}</div>
    </div>
    <button class="pill ghost" data-action="copy" type="button">${t("copyButton")}</button>
  `, { grid: true });
}

function renderMenu() {
  const connected = Boolean(state.connect?.subUrl || state.configs.length);
  return screenTemplate(`
    ${head(t("menuTitle"))}
    <div class="menu-sheet">
      <div class="menu-items">
        <button class="menu-item" data-action="telegram" type="button">
          <span class="option-dot">›</span>
          <span>${t("telegramButton")}</span>
        </button>
        <button class="menu-item" data-action="copy" type="button">
          <span class="option-dot">✓</span>
          <span>${connected ? t("connectionKey") : t("unavailable")}</span>
        </button>
        <button class="menu-item" data-action="next" type="button">
          <span class="option-dot">↻</span>
          <span>${t("autoConnect")}</span>
        </button>
      </div>
    </div>
    <button class="pill ghost" data-action="open" type="button">${t("openButton")}</button>
  `, { grid: true });
}

function renderScreen() {
  const current = screens[state.screen] || screens[0];
  const renderers = {
    welcome: renderWelcome,
    telegram: renderTelegram,
    trial: renderTrial,
    choose: renderChoose,
    auto: renderAuto,
    status: renderStatus,
    menu: renderMenu,
  };
  $("#phoneScreen").innerHTML = renderers[current.kind]();
  renderStrip();
  renderDots();
  applyStaticText();
}

function renderStrip() {
  $("#screenStrip").innerHTML = screens.map((screen, index) => {
    const active = index === state.screen ? " active" : "";
    const titleKey = {
      welcome: "welcomeTitle",
      telegram: "telegramButton",
      trial: "proPlan",
      choose: "confirm",
      auto: "autoConnect",
      status: "statusTitle",
      menu: "menuTitle",
    }[screen.kind];
    return `
      <button class="thumb${active}" data-screen="${index}" type="button">
        <strong>${t(titleKey)}</strong>
        <span>VPNBOSS</span>
      </button>
    `;
  }).join("");
}

function renderDots() {
  $("#dots").innerHTML = screens.map((_, index) => {
    const active = index === state.screen ? " active" : "";
    return `<button class="dot${active}" data-screen="${index}" type="button" aria-label="Screen ${index + 1}"></button>`;
  }).join("");
}

function applyStaticText() {
  document.documentElement.lang = state.lang;
  $$("[data-i18n]").forEach((node) => {
    node.textContent = t(node.dataset.i18n);
  });
  $$(".lang-btn").forEach((button) => {
    button.classList.toggle("active", button.dataset.lang === state.lang);
  });
}

function setScreen(index) {
  state.screen = (index + screens.length) % screens.length;
  localStorage.setItem(STORAGE.screen, String(state.screen));
  renderScreen();
}

function setLang(lang) {
  state.lang = normalizeLang(lang);
  localStorage.setItem(STORAGE.lang, state.lang);
  renderScreen();
}

function toast(message, type = "ok") {
  const node = document.createElement("div");
  node.className = `toast ${type === "bad" ? "bad" : ""}`;
  node.textContent = message;
  $("#toastStack").appendChild(node);
  setTimeout(() => node.remove(), 2600);
}

async function api(method, path, body) {
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = {};
  }

  if (!response.ok) throw new Error(data.error || text || `HTTP ${response.status}`);
  return data;
}

async function refreshClient() {
  if (!state.token) return;
  try {
    state.user = await api("GET", "/api/auth/me").catch(() => null);
    state.connect = await api("GET", "/api/connect").catch(() => null);
    state.configs = await api("GET", "/api/connect/configs")
      .then((res) => res.configs || [])
      .catch(() => []);
    renderScreen();
  } catch (error) {
    toast(`${t("error")}: ${error.message}`, "bad");
  }
}

async function startTelegram() {
  try {
    const init = await api("POST", "/api/auth/tg-init", { mode: "login" });
    const link = init.deepLink || init.botLink;
    if (link) window.open(link, "_blank", "noopener,noreferrer");
    toast(t("waitingTelegram"));

    if (state.telegramPoll) clearInterval(state.telegramPoll);
    state.telegramPoll = setInterval(async () => {
      try {
        const check = await api("GET", `/api/auth/tg-check/${init.webToken}`);
        if (check.status === "confirmed" && check.token) {
          clearInterval(state.telegramPoll);
          state.token = check.token;
          localStorage.setItem(STORAGE.token, check.token);
          toast(t("linked"));
          await refreshClient();
          setScreen(2);
        }
      } catch {
        clearInterval(state.telegramPoll);
      }
    }, 2200);
  } catch (error) {
    toast(`${t("error")}: ${error.message}`, "bad");
  }
}

function connectionUrl() {
  return state.connect?.subUrl || state.connect?.happLink || state.configs[0]?.url || state.configs[0]?.subscriptionUrl || "";
}

async function copyKey() {
  const url = connectionUrl();
  if (!url) {
    toast(t("unavailable"), "bad");
    return;
  }
  await navigator.clipboard.writeText(url);
  toast(t("copied"));
}

function openClient() {
  const url = state.connect?.happLink || connectionUrl();
  if (!url) {
    toast(t("unavailable"), "bad");
    return;
  }
  window.location.href = url;
}

function handleAction(action) {
  if (action === "next") setScreen(state.screen + 1);
  if (action === "confirm") setScreen(4);
  if (action === "telegram") startTelegram();
  if (action === "copy") copyKey();
  if (action === "open") openClient();
}

function setupEvents() {
  $("#prevScreen").addEventListener("click", () => setScreen(state.screen - 1));
  $("#nextScreen").addEventListener("click", () => setScreen(state.screen + 1));

  document.addEventListener("click", (event) => {
    const lang = event.target.closest("[data-lang]");
    if (lang) setLang(lang.dataset.lang);

    const screen = event.target.closest("[data-screen]");
    if (screen) setScreen(Number(screen.dataset.screen));

    const access = event.target.closest("[data-access]");
    if (access) {
      state.access = access.dataset.access;
      localStorage.setItem(STORAGE.access, state.access);
      renderScreen();
    }

    const action = event.target.closest("[data-action]");
    if (action) handleAction(action.dataset.action);
  });

  window.addEventListener("keydown", (event) => {
    if (event.key === "ArrowRight") setScreen(state.screen + 1);
    if (event.key === "ArrowLeft") setScreen(state.screen - 1);
  });
}

async function init() {
  setupEvents();
  renderScreen();
  await refreshClient();
}

init();
