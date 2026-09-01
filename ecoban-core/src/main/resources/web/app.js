const loginButton = document.getElementById("login-button");
const logoutButton = document.getElementById("logout-button");
const loginOverlay = document.getElementById("login-overlay");
const loginForm = document.getElementById("login-form");
const loginCancel = document.getElementById("login-cancel");
const loginError = document.getElementById("login-error");
const issueCard = document.getElementById("issue-card");
const issueForm = document.getElementById("issue-form");
const issueType = document.getElementById("issue-type");
const issueIp = document.getElementById("issue-ip");
const issueResult = document.getElementById("issue-result");
const searchInput = document.getElementById("search-input");
const typeFilter = document.getElementById("type-filter");
const refreshButton = document.getElementById("refresh-button");
const punishmentsBody = document.getElementById("punishments-body");

let loggedIn = false;

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

function setLoggedIn(value) {
  loggedIn = value;
  loginButton.hidden = value;
  logoutButton.hidden = !value;
  issueCard.hidden = !value;
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
    loginError.textContent = "Invalid username or password.";
    loginError.hidden = false;
    return;
  }
  closeLogin();
  setLoggedIn(true);
  loadPunishments();
});

logoutButton.addEventListener("click", async () => {
  await fetch("/api/logout", { method: "POST" });
  setLoggedIn(false);
  loadPunishments();
});

issueType.addEventListener("change", () => {
  issueIp.hidden = issueType.value !== "ipban";
});

issueForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const type = issueType.value;
  const uuid = document.getElementById("issue-uuid").value.trim();
  const name = document.getElementById("issue-name").value.trim();
  const ip = issueIp.value.trim();
  const durationMinutes = document.getElementById("issue-duration").value.trim();
  const reason = document.getElementById("issue-reason").value.trim();
  const durationMillis = durationMinutes ? Number(durationMinutes) * 60 * 1000 : 0;

  const body = { uuid, name: name || null, reason: reason || null, durationMillis };
  let path = "/api/" + type;
  if (type === "ipban") {
    path = "/api/ipban";
    body.ip = ip;
  }

  try {
    const response = await api(path, { method: "POST", body: JSON.stringify(body) });
    const data = await response.json();
    if (!response.ok) {
      issueResult.textContent = "Error: " + (data.error || "unknown error");
    } else {
      issueResult.textContent = "Issued successfully.";
      issueForm.reset();
      loadPunishments();
    }
  } catch (e) {
    // openLogin() already ran if this was a 401.
  }
});

searchInput.addEventListener("input", debounce(loadPunishments, 300));
typeFilter.addEventListener("change", loadPunishments);
refreshButton.addEventListener("click", loadPunishments);

function debounce(fn, delayMillis) {
  let timeout;
  return (...args) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => fn(...args), delayMillis);
  };
}

async function loadPunishments() {
  const query = searchInput.value.trim();
  const type = typeFilter.value;
  const params = new URLSearchParams();
  if (query) {
    params.set("q", query);
  }
  if (type) {
    params.set("type", type);
  }
  // Browsing needs no auth, so a plain fetch here (never triggers the login overlay).
  const response = await fetch("/api/punishments?" + params.toString());
  const punishments = await response.json();
  renderPunishments(punishments);
}

function renderPunishments(punishments) {
  punishmentsBody.innerHTML = "";
  for (const p of punishments) {
    const row = document.createElement("tr");

    const target = p.targetName || p.targetUuid || p.ip || "(unknown)";
    const expires = p.permanent ? "Permanent" : new Date(p.expiresAt).toLocaleString();

    row.innerHTML = `
      <td>${p.type}</td>
      <td>${escapeHtml(target)}</td>
      <td>${escapeHtml(p.reason || "")}</td>
      <td>${escapeHtml(p.operatorName || "")}</td>
      <td>${new Date(p.createdAt).toLocaleString()}</td>
      <td>${expires}</td>
      <td><span class="badge ${p.active ? "active" : "inactive"}">${p.active ? "Active" : "Inactive"}</span></td>
      <td></td>
    `;

    if (loggedIn && p.active && (p.type === "BAN" || p.type === "TEMPBAN" || p.type === "IPBAN" || p.type === "MUTE" || p.type === "TEMPMUTE")) {
      const button = document.createElement("button");
      button.className = "small ghost";
      button.textContent = "Lift";
      button.addEventListener("click", () => liftPunishment(p));
      row.lastElementChild.appendChild(button);
    }

    punishmentsBody.appendChild(row);
  }
}

async function liftPunishment(punishment) {
  const reason = prompt("Reason for lifting this punishment (optional):") || null;
  let path;
  const body = { reason };
  if (punishment.type === "IPBAN") {
    path = "/api/unbanip";
    body.ip = punishment.ip;
  } else if (punishment.type === "MUTE" || punishment.type === "TEMPMUTE") {
    path = "/api/unmute";
    body.uuid = punishment.targetUuid;
  } else {
    path = "/api/unban";
    body.uuid = punishment.targetUuid;
  }
  try {
    await api(path, { method: "POST", body: JSON.stringify(body) });
    loadPunishments();
  } catch (e) {
    // openLogin() already ran if this was a 401.
  }
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

// The punishments list loads for everyone; the session check just decides whether to
// reveal the issue form and Lift buttons (the cookie, if any, survives a page reload).
fetch("/api/session").then((response) => setLoggedIn(response.ok)).finally(loadPunishments);
