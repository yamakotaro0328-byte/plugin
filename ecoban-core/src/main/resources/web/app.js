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
const tableSpinner = document.getElementById("table-spinner");
const emptyState = document.getElementById("empty-state");
const truncationHint = document.getElementById("truncation-hint");
const statTotal = document.getElementById("stat-total");
const statBans = document.getElementById("stat-bans");
const statMutes = document.getElementById("stat-mutes");
const statWarns = document.getElementById("stat-warns");
const issueDurationUnit = document.getElementById("issue-duration-unit");
const liftOverlay = document.getElementById("lift-overlay");
const liftForm = document.getElementById("lift-form");
const liftCancel = document.getElementById("lift-cancel");
const liftReasonInput = document.getElementById("lift-reason");
const liftSummary = document.getElementById("lift-summary");
let pendingLift = null;

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

const DURATION_UNIT_MILLIS = { minutes: 60_000, hours: 3_600_000, days: 86_400_000, weeks: 604_800_000 };

issueForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const type = issueType.value;
  const uuid = document.getElementById("issue-uuid").value.trim();
  const name = document.getElementById("issue-name").value.trim();
  const ip = issueIp.value.trim();
  const durationValue = document.getElementById("issue-duration").value.trim();
  const reason = document.getElementById("issue-reason").value.trim();
  const durationMillis = durationValue ? Number(durationValue) * DURATION_UNIT_MILLIS[issueDurationUnit.value] : 0;

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
      setIssueResult("Error: " + (data.error || "unknown error"), "error");
    } else {
      setIssueResult("Issued successfully.", "success");
      issueForm.reset();
      loadPunishments();
      loadStats();
    }
  } catch (e) {
    // openLogin() already ran if this was a 401.
  }
});

function setIssueResult(text, state) {
  issueResult.textContent = text;
  issueResult.classList.remove("success", "error");
  if (state) {
    issueResult.classList.add(state);
  }
}

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
  tableSpinner.hidden = false;
  try {
    // Browsing needs no auth, so a plain fetch here (never triggers the login overlay).
    const response = await fetch("/api/punishments?" + params.toString());
    const punishments = await response.json();
    renderPunishments(punishments);
  } finally {
    tableSpinner.hidden = true;
  }
}

/** A separate, unfiltered fetch just for the summary tiles, so searching/filtering the table
 * below doesn't make the overall counts look like they changed. */
async function loadStats() {
  const response = await fetch("/api/punishments?limit=1000");
  const punishments = await response.json();
  const isBan = (t) => t === "BAN" || t === "TEMPBAN" || t === "IPBAN";
  const isMute = (t) => t === "MUTE" || t === "TEMPMUTE";
  statTotal.textContent = punishments.length;
  statBans.textContent = punishments.filter((p) => isBan(p.type)).length;
  statMutes.textContent = punishments.filter((p) => isMute(p.type)).length;
  statWarns.textContent = punishments.filter((p) => p.type === "WARN").length;
}

function typeBadgeClass(type) {
  return "type-badge type-" + type.toLowerCase();
}

function renderPunishments(punishments) {
  punishmentsBody.innerHTML = "";
  emptyState.hidden = punishments.length > 0;
  // The API defaults to a 100-row limit when no explicit limit is given (see loadPunishments) -
  // hitting that count exactly is the only signal the client has that more rows exist.
  truncationHint.hidden = punishments.length < 100;
  for (const p of punishments) {
    const row = document.createElement("tr");

    const target = p.targetName || p.targetUuid || p.ip || "(unknown)";
    const expires = p.permanent ? "Permanent" : new Date(p.expiresAt).toLocaleString();

    row.innerHTML = `
      <td><span class="${typeBadgeClass(p.type)}">${p.type}</span></td>
      <td class="target-cell">${escapeHtml(target)}</td>
      <td class="reason-cell">${escapeHtml(p.reason || "")}</td>
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

/** Opens the themed confirmation modal instead of a native prompt() - the actual API call happens
 * in liftForm's submit handler once the operator confirms. */
function liftPunishment(punishment) {
  pendingLift = punishment;
  const target = punishment.targetName || punishment.targetUuid || punishment.ip || "(unknown)";
  liftSummary.textContent = `${punishment.type} on ${target}`;
  liftReasonInput.value = "";
  liftOverlay.hidden = false;
  liftReasonInput.focus();
}

function closeLiftModal() {
  liftOverlay.hidden = true;
  pendingLift = null;
}

liftCancel.addEventListener("click", closeLiftModal);

liftForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const punishment = pendingLift;
  const reason = liftReasonInput.value.trim() || null;
  closeLiftModal();
  if (!punishment) {
    return;
  }
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
    loadStats();
  } catch (e) {
    // openLogin() already ran if this was a 401.
  }
});

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

// The punishments list loads for everyone; the session check just decides whether to
// reveal the issue form and Lift buttons (the cookie, if any, survives a page reload).
fetch("/api/session").then((response) => setLoggedIn(response.ok)).finally(loadPunishments);
loadStats();
