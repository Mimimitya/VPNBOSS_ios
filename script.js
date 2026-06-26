const API_BASE = new URLSearchParams(window.location.search).get("api") || "https://sekretnik1.vps.webdock.cloud";

const STORAGE = {
  lang: "vpnboss_lang",
  token: "vpnboss_token",
  active: "vpnboss_active_screen",
  access: "vpnboss_access",
};

const TEXT = {
  ru: {
    welcome: "Добро пожаловать в VPNBOSS",
    telegramTitle: "Перед началом работы вам потребуется привязать свой Telegram",
    telegramSmall: "Это безопасное подтверждение доступа. Вход и управление выполняются через Telegram или сайт.",
    trial: "Поздравляем! Вы получили доступ к пробному периоду PRO на 5 дней",
    choose: "Выберите подписку, которую вы уже получили через Telegram или сайт",
    autoTitle: "Ваш аккаунт готов",
    autoSmall: "Включите автоподключение, чтобы клиент запускал доступ автоматически.",
    next: "Далее",
    confirm: "Подтвердить",
    auto: "Автоподключение",
    status: "Статус",
    on: "Включено",
    off: "Выключено",
    telegram: "Войти через Telegram",
    start: "START",
    pro: "PRO",
    menu: "Меню",
    subscription: "Подписка",
    connection: "Подключение",
    copied: "Ссылка скопирована",
    unavailable: "Доступ пока не найден",
    waiting: "Откройте Telegram и подтвердите доступ",
    linked: "Telegram подтверждён",
    open: "Открыть",
    error: "Ошибка",
  },
  en: {
    welcome: "Welcome to VPNBOSS",
    telegramTitle: "Before you start, link your Telegram",
    telegramSmall: "Access is confirmed through Telegram or the website.",
    trial: "You received trial PRO access for 5 days",
    choose: "Choose the subscription already issued through Telegram or the website",
    autoTitle: "Your account is ready",
    autoSmall: "Enable auto-connect so the client can start automatically.",
    next: "Next",
    confirm: "Confirm",
    auto: "Auto-connect",
    status: "Status",
    on: "On",
    off: "Off",
    telegram: "Continue with Telegram",
    start: "START",
    pro: "PRO",
    menu: "Menu",
    subscription: "Subscription",
    connection: "Connection",
    copied: "Link copied",
    unavailable: "Access not found yet",
    waiting: "Open Telegram and confirm access",
    linked: "Telegram confirmed",
    open: "Open",
    error: "Error",
  },
  es: {
    welcome: "Bienvenido a VPNBOSS",
    telegramTitle: "Antes de empezar, vincula Telegram",
    telegramSmall: "El acceso se confirma por Telegram o el sitio web.",
    trial: "Tienes acceso PRO de prueba por 5 días",
    choose: "Elige la suscripción emitida por Telegram o el sitio web",
    autoTitle: "Tu acceso está listo",
    autoSmall: "Activa la conexión automática para iniciar el cliente.",
    next: "Siguiente",
    confirm: "Confirmar",
    auto: "Autoconexión",
    status: "Estado",
    on: "Activado",
    off: "Desactivado",
    telegram: "Continuar con Telegram",
    start: "START",
    pro: "PRO",
    menu: "Menú",
    subscription: "Suscripción",
    connection: "Conexión",
    copied: "Enlace copiado",
    unavailable: "Acceso aún no encontrado",
    waiting: "Abre Telegram y confirma el acceso",
    linked: "Telegram confirmado",
    open: "Abrir",
    error: "Error",
  },
};

const state = {
  lang: normalizeLang(localStorage.getItem(STORAGE.lang) || navigator.language),
  token: localStorage.getItem(STORAGE.token) || "",
  active: Number(localStorage.getItem(STORAGE.active) || 0),
  access: localStorage.getItem(STORAGE.access) || "pro",
  profile: null,
  connect: null,
  configs: [],
  telegramPoll: null,
};

const screenKeys = ["welcome", "telegram", "trial", "choose", "auto", "status", "menu"];

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function normalizeLang(value) {
  const lang = String(value || "ru").slice(0, 2).toLowerCase();
  return ["ru", "en", "es"].includes(lang) ? lang : "ru";
}

function t(key) {
  return TEXT[state.lang]?.[key] ?? TEXT.ru[key] ?? key;
}

function telegramSvg() {
  return `
    <svg class="telegram-icon" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="12" fill="#1d9bf0"></circle>
      <path d="M18.9 6.7 16.8 17c-.16.72-.58.9-1.18.56l-3.25-2.4-1.57 1.52c-.17.17-.32.32-.66.32l.24-3.31 6.02-5.44c.26-.24-.06-.37-.4-.13l-7.44 4.69-3.2-1c-.7-.22-.72-.7.15-1.04l12.52-4.83c.58-.21 1.08.14.87.76Z" fill="#fff"></path>
    </svg>
  `;
}

function logo() {
  return `<img class="phone-logo" src="./assets/vpnboss-logo.png" alt="VPNBOSS" />`;
}

function topBar() {
  return `
    <div class="phone-top">
      ${logo()}
      <span></span>
      <span class="tiny-arrow">➜</span>
    </div>
  `;
}

function frame(content, key) {
  return `
    <article class="phone" data-screen="${key}" data-active="${screenKeys[state.active] === key}">
      <div class="phone-inner">
        ${content}
      </div>
    </article>
  `;
}

function welcomeScreen() {
  return frame(`
    ${topBar()}
    <div class="screen-center">
      <h1 class="headline large">${t("welcome")}</h1>
    </div>
    <div class="screen-bottom">
      <span class="next">${t("next")} →</span>
    </div>
  `, "welcome");
}

function telegramScreen() {
  return frame(`
    ${topBar()}
    <div class="screen-center">
      <h2 class="headline">${t("telegramTitle")}</h2>
      <button class="black-pill" data-action="telegram" type="button">
        ${telegramSvg()}
        <span>${t("telegram")}</span>
      </button>
      <p class="telegram-copy">${t("telegramSmall")}</p>
    </div>
    <div class="screen-bottom">
      <span class="next">${t("next")} →</span>
    </div>
  `, "telegram");
}

function trialScreen() {
  return frame(`
    ${topBar()}
    <div class="screen-center">
      <h2 class="headline">${t("trial")}</h2>
    </div>
    <div class="screen-bottom">
      <span class="next">${t("next")} →</span>
    </div>
  `, "trial");
}

function chooseScreen() {
  return frame(`
    ${topBar()}
    <div class="screen-center">
      <p class="access-copy">${t("choose")}</p>
      <div class="access-list">
        ${accessRow("start", t("start"))}
        ${accessRow("pro", t("pro"))}
      </div>
    </div>
    <div class="screen-bottom">
      <button class="black-pill" data-action="confirm" type="button">${t("confirm")}</button>
    </div>
  `, "choose");
}

function accessRow(key, label) {
  const active = state.access === key ? " active" : "";
  return `
    <button class="access-row${active}" data-access="${key}" type="button">
      <span class="coin">${key === "pro" ? "P" : "S"}</span>
      <span>${label}</span>
      <span>✓</span>
    </button>
  `;
}

function autoScreen() {
  return frame(`
    ${topBar()}
    <div class="screen-center center-text">
      <div class="question-circle">?</div>
      <h2 class="headline">${t("autoTitle")}</h2>
      <p class="caption">${t("autoSmall")}</p>
    </div>
    <div class="screen-bottom">
      <button class="black-pill" data-action="next" type="button">${t("auto")}</button>
    </div>
  `, "auto");
}

function statusScreen() {
  const active = hasConnection();
  return frame(`
    ${topBar()}
    <div class="power-wrap">
      <button class="power-button" data-action="copy" type="button" aria-label="${t("connection")}"></button>
      <div class="status-label">${active ? t("on") : t("off")}</div>
    </div>
    <div class="screen-bottom center-text">
      <button class="outline-pill" data-action="copy" type="button">${t("connection")}</button>
    </div>
  `, "status");
}

function menuScreen() {
  return frame(`
    ${topBar()}
    <div class="menu-arc">
      <div class="menu-list">
        <button class="menu-item" data-action="telegram" type="button">
          <span class="menu-icon">${telegramSvg()}</span>
          <span>Telegram</span>
        </button>
        <button class="menu-item" data-action="copy" type="button">
          <span class="menu-icon">✓</span>
          <span>${hasConnection() ? t("connection") : t("unavailable")}</span>
        </button>
        <button class="menu-item" data-action="next" type="button">
          <span class="menu-icon">↻</span>
          <span>${t("auto")}</span>
        </button>
      </div>
    </div>
    <div class="screen-bottom">
      <button class="outline-pill" data-action="open" type="button">${t("open")}</button>
    </div>
  `, "menu");
}

function render() {
  const markup = [
    welcomeScreen(),
    telegramScreen(),
    trialScreen(),
    chooseScreen(),
    autoScreen(),
    statusScreen(),
    menuScreen(),
  ].join("");

  $("#screenBoard").innerHTML = markup;
  $("#activePhone").innerHTML = [welcomeScreen, telegramScreen, trialScreen, chooseScreen, autoScreen, statusScreen, menuScreen][state.active]();

  document.documentElement.lang = state.lang;
  $$(".lang-btn").forEach((button) => button.classList.toggle("active", button.dataset.lang === state.lang));
}

function setActive(index) {
  state.active = (index + screenKeys.length) % screenKeys.length;
  localStorage.setItem(STORAGE.active, String(state.active));
  render();
}

function setLang(lang) {
  state.lang = normalizeLang(lang);
  localStorage.setItem(STORAGE.lang, state.lang);
  render();
}

function hasConnection() {
  return Boolean(connectionUrl());
}

function connectionUrl() {
  return (
    state.connect?.subUrl ||
    state.connect?.happLink ||
    state.configs?.[0]?.url ||
    state.configs?.[0]?.subscriptionUrl ||
    state.configs?.[0]?.link ||
    ""
  );
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

async function refreshAccess() {
  if (!state.token) return;
  try {
    state.profile = await api("GET", "/api/auth/me").catch(() => null);
    state.connect = await api("GET", "/api/connect").catch(() => null);
    state.configs = await api("GET", "/api/connect/configs")
      .then((data) => data.configs || [])
      .catch(() => []);
    render();
  } catch (error) {
    toast(`${t("error")}: ${error.message}`, "bad");
  }
}

async function startTelegram() {
  try {
    const init = await api("POST", "/api/auth/tg-init", { mode: "login" });
    const link = init.deepLink || init.botLink;
    if (link) window.open(link, "_blank", "noopener,noreferrer");
    toast(t("waiting"));

    if (state.telegramPoll) clearInterval(state.telegramPoll);
    state.telegramPoll = setInterval(async () => {
      try {
        const check = await api("GET", `/api/auth/tg-check/${init.webToken}`);
        if (check.status === "confirmed" && check.token) {
          clearInterval(state.telegramPoll);
          state.token = check.token;
          localStorage.setItem(STORAGE.token, check.token);
          toast(t("linked"));
          await refreshAccess();
          setActive(2);
        }
      } catch {
        clearInterval(state.telegramPoll);
      }
    }, 2200);
  } catch (error) {
    toast(`${t("error")}: ${error.message}`, "bad");
  }
}

async function copyConnection() {
  const url = connectionUrl();
  if (!url) {
    toast(t("unavailable"), "bad");
    return;
  }
  await navigator.clipboard.writeText(url);
  toast(t("copied"));
}

function openConnection() {
  const url = state.connect?.happLink || connectionUrl();
  if (!url) {
    toast(t("unavailable"), "bad");
    return;
  }
  window.location.href = url;
}

function toast(message, type = "ok") {
  const node = document.createElement("div");
  node.className = `toast ${type === "bad" ? "bad" : ""}`;
  node.textContent = message;
  $("#toastStack").appendChild(node);
  setTimeout(() => node.remove(), 2600);
}

function action(name) {
  if (name === "telegram") startTelegram();
  if (name === "copy") copyConnection();
  if (name === "open") openConnection();
  if (name === "confirm") setActive(4);
  if (name === "next") setActive(state.active + 1);
}

function bind() {
  document.addEventListener("click", (event) => {
    const lang = event.target.closest("[data-lang]");
    if (lang) setLang(lang.dataset.lang);

    const screen = event.target.closest("[data-screen]");
    if (screen && !event.target.closest("[data-action], [data-access]")) {
      setActive(screenKeys.indexOf(screen.dataset.screen));
    }

    const access = event.target.closest("[data-access]");
    if (access) {
      state.access = access.dataset.access;
      localStorage.setItem(STORAGE.access, state.access);
      render();
    }

    const currentAction = event.target.closest("[data-action]");
    if (currentAction) action(currentAction.dataset.action);

    if (event.target.closest("[data-prev]")) setActive(state.active - 1);
    if (event.target.closest("[data-next]")) setActive(state.active + 1);
  });

  window.addEventListener("keydown", (event) => {
    if (event.key === "ArrowLeft") setActive(state.active - 1);
    if (event.key === "ArrowRight") setActive(state.active + 1);
  });
}

bind();
render();
refreshAccess();
