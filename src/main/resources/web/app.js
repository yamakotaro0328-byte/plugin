const loginButton = document.getElementById("login-button");
const logoutButton = document.getElementById("logout-button");
const sessionUsername = document.getElementById("session-username");
const themeToggle = document.getElementById("theme-toggle");
const loginOverlay = document.getElementById("login-overlay");
const loginForm = document.getElementById("login-form");
const loginCancel = document.getElementById("login-cancel");
const loginError = document.getElementById("login-error");
const toastStack = document.getElementById("toast-stack");

const statTotalMoney = document.getElementById("stat-total-money");
const statTotalMoneyLabel = document.getElementById("stat-total-money-label");
const statAccounts = document.getElementById("stat-accounts");
const statAverage = document.getElementById("stat-average");
const statHighest = document.getElementById("stat-highest");

const baltopSpinner = document.getElementById("baltop-spinner");
const baltopLimitSelect = document.getElementById("baltop-limit");
const baltopList = document.getElementById("baltop-list");
const baltopEmpty = document.getElementById("baltop-empty");

const lookupForm = document.getElementById("lookup-form");
const lookupNameInput = document.getElementById("lookup-name");
const lookupResult = document.getElementById("lookup-result");
const lookupEmpty = document.getElementById("lookup-empty");

const ecoCard = document.getElementById("eco-card");
const ecoForm = document.getElementById("eco-form");
const ecoAction = document.getElementById("eco-action");
const ecoName = document.getElementById("eco-name");
const ecoAmount = document.getElementById("eco-amount");
const ecoResult = document.getElementById("eco-result");
const quickAmounts = document.getElementById("quick-amounts");

let loggedIn = false;
let currencyPlural = "money";
const THEME_KEY = "ecotp-theme";
const QUICK_AMOUNTS = [100, 1000, 10000, 100000];

// ---- theme ----

function applyTheme(theme) {
  if (theme === "light" || theme === "dark") {
    document.documentElement.setAttribute("data-theme", theme);
  } else {
    document.documentElement.removeAttribute("data-theme");
  }
}

function currentTheme() {
  const explicit = document.documentElement.getAttribute("data-theme");
  if (explicit) {
    return explicit;
  }
  return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

themeToggle.addEventListener("click", () => {
  const next = currentTheme() === "dark" ? "light" : "dark";
  applyTheme(next);
  try {
    localStorage.setItem(THEME_KEY, next);
  } catch (e) {
    // Private browsing or storage disabled - the toggle still works for this page load.
  }
});

try {
  const stored = localStorage.getItem(THEME_KEY);
  if (stored) {
    applyTheme(stored);
  }
} catch (e) {
  // Ignore - falls back to the system theme.
}

// ---- toasts ----

function showToast(message, type) {
  const toast = document.createElement("div");
  toast.className = "toast" + (type ? " " + type : "");
  toast.textContent = message;
  toastStack.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}

// ---- auth ----

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  if (response.status === 401) {
    setLoggedIn(false);
    openLogin();
    throw new Error("Not logged in");
  }
  return response;
}

function setLoggedIn(value, username) {
  loggedIn = value;
  loginButton.hidden = value;
  logoutButton.hidden = !value;
  ecoCard.hidden = !value;
  sessionUsername.hidden = !value;
  sessionUsername.textContent = value ? `Signed in as ${username}` : "";
}

function openLogin() {
  loginError.hidden = true;
  loginOverlay.hidden = false;
}

function closeLogin() {
  loginOverlay.hidden = true;
  loginForm.reset();
}

loginButton.addEventListener("click", openLogin);
loginCancel.addEventListener("click", closeLogin);
loginOverlay.addEventListener("click", (event) => {
  if (event.target === loginOverlay) {
    closeLogin();
  }
});

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginError.hidden = true;
  const username = document.getElementById("login-username").value;
  const password = document.getElementById("login-password").value;
  const response = await fetch("/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    loginError.textContent = data.error || "Invalid username or password.";
    loginError.hidden = false;
    return;
  }
  closeLogin();
  setLoggedIn(true, username);
  showToast("Signed in.", "success");
});

logoutButton.addEventListener("click", async () => {
  await fetch("/api/logout", { method: "POST" });
  setLoggedIn(false);
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !loginOverlay.hidden) {
    closeLogin();
  }
});

// ---- formatting ----

function formatMoney(amount) {
  return Math.round(amount).toLocaleString();
}

function avatarElement(name, size) {
  const img = document.createElement("img");
  img.className = "avatar";
  img.style.width = size + "px";
  img.style.height = size + "px";
  img.src = `https://mc-heads.net/avatar/${encodeURIComponent(name)}/${size}`;
  img.alt = "";
  img.loading = "lazy";
  return img;
}

// ---- stats ----

async function loadStats() {
  const response = await fetch("/api/stats");
  const stats = await response.json();
  currencyPlural = stats.currencyPlural || "money";
  statTotalMoneyLabel.textContent = "Total " + currencyPlural;
  statTotalMoney.textContent = formatMoney(stats.totalMoney);
  statAccounts.textContent = stats.accountCount.toLocaleString();
  const average = stats.accountCount > 0 ? stats.totalMoney / stats.accountCount : 0;
  statAverage.textContent = formatMoney(average);
  const highest = stats.topPreview.length > 0 ? stats.topPreview[0] : null;
  statHighest.textContent = highest ? formatMoney(highest.balance) : "–";
  statHighest.title = highest ? highest.name : "";
}

// ---- baltop ----

baltopLimitSelect.addEventListener("change", () => loadBaltop());

async function loadBaltop() {
  const limit = baltopLimitSelect.value;
  baltopSpinner.hidden = false;
  try {
    const response = await fetch(`/api/baltop?limit=${encodeURIComponent(limit)}`);
    const entries = await response.json();
    renderBaltop(entries);
  } finally {
    baltopSpinner.hidden = true;
  }
}

function renderBaltop(entries) {
  baltopList.innerHTML = "";
  baltopEmpty.hidden = entries.length > 0;
  entries.forEach((entry, index) => {
    const li = document.createElement("li");
    li.classList.add("clickable");
    li.addEventListener("click", () => {
      lookupNameInput.value = entry.name;
      lookupForm.requestSubmit();
    });

    const rank = document.createElement("span");
    rank.className = "leaderboard-rank";
    rank.textContent = String(index + 1);

    const avatar = avatarElement(entry.name, 22);

    const name = document.createElement("span");
    name.className = "leaderboard-name";
    name.textContent = entry.name;

    const balance = document.createElement("span");
    balance.className = "leaderboard-count";
    balance.textContent = formatMoney(entry.balance);

    li.append(rank, avatar, name, balance);
    baltopList.appendChild(li);
  });
}

// ---- player lookup ----

lookupForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const name = lookupNameInput.value.trim();
  if (!name) {
    return;
  }
  lookupResult.hidden = true;
  lookupEmpty.hidden = true;
  const response = await fetch(`/api/player?name=${encodeURIComponent(name)}`);
  if (!response.ok) {
    lookupEmpty.hidden = false;
    return;
  }
  const data = await response.json();
  renderLookupResult(data);
  if (loggedIn) {
    ecoName.value = data.name;
  }
});

function renderLookupResult(data) {
  lookupResult.innerHTML = "";
  lookupResult.hidden = false;

  const avatar = avatarElement(data.name, 40);
  avatar.className = "lookup-avatar";

  const info = document.createElement("div");
  info.className = "lookup-info";
  const name = document.createElement("div");
  name.className = "lookup-name";
  name.textContent = data.name;
  const uuid = document.createElement("div");
  uuid.className = "lookup-uuid";
  uuid.textContent = data.uuid;
  info.append(name, uuid);

  const balance = document.createElement("div");
  balance.className = "lookup-balance";
  balance.textContent = formatMoney(data.balance);

  lookupResult.append(avatar, info, balance);
}

// ---- give/take/set ----

for (const amount of QUICK_AMOUNTS) {
  const chip = document.createElement("button");
  chip.type = "button";
  chip.className = "quick-reason-chip";
  chip.textContent = formatMoney(amount);
  chip.addEventListener("click", () => {
    ecoAmount.value = amount;
  });
  quickAmounts.appendChild(chip);
}

ecoForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const name = ecoName.value.trim();
  const action = ecoAction.value;
  const amount = Number(ecoAmount.value);
  if (!name || Number.isNaN(amount) || amount < 0) {
    return;
  }
  try {
    const response = await api("/api/eco", { method: "POST", body: JSON.stringify({ name, action, amount }) });
    const data = await response.json();
    if (!response.ok) {
      setEcoResult("Error: " + (data.error || "unknown error"), "error");
      showToast(data.error || "Failed to apply the change.", "error");
      return;
    }
    setEcoResult(`${name} is now at ${formatMoney(data.balance)} ${currencyPlural}.`, "success");
    showToast("Balance updated.", "success");
    loadStats();
    loadBaltop();
    if (lookupNameInput.value.trim().toLowerCase() === name.toLowerCase()) {
      lookupForm.requestSubmit();
    }
  } catch (e) {
    // openLogin() already ran if this was a 401.
  }
});

function setEcoResult(text, state) {
  ecoResult.textContent = text;
  ecoResult.classList.remove("success", "error");
  if (state) {
    ecoResult.classList.add(state);
  }
}

// The public stats/baltop load for everyone; the session check just decides whether to
// reveal the give/take/set form (the cookie, if any, survives a page reload).
fetch("/api/session")
  .then(async (response) => {
    const data = response.ok ? await response.json() : null;
    setLoggedIn(response.ok, data && data.username);
  })
  .finally(() => {
    loadStats();
    loadBaltop();
  });
