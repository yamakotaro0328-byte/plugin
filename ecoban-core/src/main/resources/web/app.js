const loginButton = document.getElementById("login-button");
const logoutButton = document.getElementById("logout-button");
const themeToggle = document.getElementById("theme-toggle");
const loginOverlay = document.getElementById("login-overlay");
const loginForm = document.getElementById("login-form");
const loginCancel = document.getElementById("login-cancel");
const loginError = document.getElementById("login-error");
const issueCard = document.getElementById("issue-card");
const issueForm = document.getElementById("issue-form");
const issueType = document.getElementById("issue-type");
const issueIp = document.getElementById("issue-ip");
const issueResult = document.getElementById("issue-result");
const quickReasons = document.getElementById("quick-reasons");
const searchInput = document.getElementById("search-input");
const typeFilter = document.getElementById("type-filter");
const historyToggle = document.getElementById("history-toggle");
const autoRefreshToggle = document.getElementById("auto-refresh-toggle");
const refreshButton = document.getElementById("refresh-button");
const punishmentsBody = document.getElementById("punishments-body");
const tableSpinner = document.getElementById("table-spinner");
const emptyState = document.getElementById("empty-state");
const pagePrev = document.getElementById("page-prev");
const pageNext = document.getElementById("page-next");
const pageInfo = document.getElementById("page-info");
const bulkLiftButton = document.getElementById("bulk-lift-button");
const exportButton = document.getElementById("export-button");
const selectAllCol = document.getElementById("select-all-col");
const selectAllCheckbox = document.getElementById("select-all-checkbox");
const thIssued = document.getElementById("th-issued");
const thExpires = document.getElementById("th-expires");
const statTotal = document.getElementById("stat-total");
const statBans = document.getElementById("stat-bans");
const statMutes = document.getElementById("stat-mutes");
const statWarns = document.getElementById("stat-warns");
const statAlltime = document.getElementById("stat-alltime");
const activityChart = document.getElementById("activity-chart");
const leaderboardList = document.getElementById("leaderboard-list");
const leaderboardEmpty = document.getElementById("leaderboard-empty");
const topTargetsList = document.getElementById("top-targets-list");
const topTargetsEmpty = document.getElementById("top-targets-empty");
const issueNameInput = document.getElementById("issue-name");
const issueNameSuggestions = document.getElementById("issue-name-suggestions");
const issueUuidInput = document.getElementById("issue-uuid");
const issueDurationUnit = document.getElementById("issue-duration-unit");
const liftOverlay = document.getElementById("lift-overlay");
const liftForm = document.getElementById("lift-form");
const liftCancel = document.getElementById("lift-cancel");
const liftReasonInput = document.getElementById("lift-reason");
const liftSummary = document.getElementById("lift-summary");
const detailOverlay = document.getElementById("detail-overlay");
const detailBody = document.getElementById("detail-body");
const detailClose = document.getElementById("detail-close");
const playerOverlay = document.getElementById("player-overlay");
const playerBody = document.getElementById("player-body");
const playerClose = document.getElementById("player-close");
const toastStack = document.getElementById("toast-stack");

let pendingLift = null;
let loggedIn = false;
let currentPage = 1;
const PAGE_SIZE = 25;
let liveInterval = null;
let sortColumn = null;
let sortAscending = false;
let hasLoadedPunishmentsOnce = false;
const selectedIds = new Set();
const punishmentsById = new Map();

const QUICK_REASONS = ["Cheating", "Griefing", "Spamming", "Advertising", "Abusive language", "Ban evasion"];
const THEME_KEY = "ecoban-theme";

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

async function copyToClipboard(text, button) {
  try {
    await navigator.clipboard.writeText(text);
    const original = button.textContent;
    button.textContent = "Copied!";
    setTimeout(() => {
      button.textContent = original;
    }, 1200);
  } catch (e) {
    showToast("Couldn't copy to clipboard.", "error");
  }
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

function setLoggedIn(value) {
  loggedIn = value;
  loginButton.hidden = value;
  logoutButton.hidden = !value;
  issueCard.hidden = !value;
  selectAllCol.hidden = !value;
  if (!value) {
    selectedIds.clear();
    updateBulkLiftVisibility();
  }
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
    const data = await response.json().catch(() => ({}));
    loginError.textContent = data.error || "Invalid username or password.";
    loginError.hidden = false;
    return;
  }
  closeLogin();
  setLoggedIn(true);
  showToast("Signed in.", "success");
  loadPunishments();
});

logoutButton.addEventListener("click", async () => {
  await fetch("/api/logout", { method: "POST" });
  setLoggedIn(false);
  loadPunishments();
});

// ---- issue form ----

for (const reason of QUICK_REASONS) {
  const chip = document.createElement("button");
  chip.type = "button";
  chip.className = "quick-reason-chip";
  chip.textContent = reason;
  chip.addEventListener("click", () => {
    document.getElementById("issue-reason").value = reason;
  });
  quickReasons.appendChild(chip);
}

issueType.addEventListener("change", () => {
  issueIp.hidden = issueType.value !== "ipban";
});

// Suggests names from past punishment records as the operator types, and auto-fills the UUID
// field once they pick (or type out) an exact match - saves hunting down a UUID by hand for a
// player who's been punished before.
let issueNameToUuid = new Map();

issueNameInput.addEventListener("input", debounce(async () => {
  const query = issueNameInput.value.trim();
  if (query.length < 2) {
    issueNameSuggestions.innerHTML = "";
    return;
  }
  const response = await fetch(`/api/punishments?q=${encodeURIComponent(query)}&limit=20&activeOnly=false`);
  const data = await response.json();
  const matches = new Map();
  for (const p of data.items) {
    if (p.targetName && p.targetUuid && !matches.has(p.targetName)) {
      matches.set(p.targetName, p.targetUuid);
    }
  }
  issueNameToUuid = matches;
  issueNameSuggestions.innerHTML = "";
  for (const name of matches.keys()) {
    const option = document.createElement("option");
    option.value = name;
    issueNameSuggestions.appendChild(option);
  }
}, 250));

issueNameInput.addEventListener("change", () => {
  const uuid = issueNameToUuid.get(issueNameInput.value.trim());
  if (uuid && !issueUuidInput.value.trim()) {
    issueUuidInput.value = uuid;
  }
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
      showToast(data.error || "Failed to issue punishment.", "error");
    } else {
      setIssueResult("Issued successfully.", "success");
      showToast(`${type.toUpperCase()} issued.`, "success");
      issueForm.reset();
      currentPage = 1;
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

// ---- punishments table ----

searchInput.addEventListener("input", debounce(() => {
  currentPage = 1;
  loadPunishments();
}, 300));
typeFilter.addEventListener("change", () => {
  currentPage = 1;
  loadPunishments();
});
historyToggle.addEventListener("change", () => {
  currentPage = 1;
  loadPunishments();
});
refreshButton.addEventListener("click", () => {
  loadPunishments();
  loadStats();
});
pagePrev.addEventListener("click", () => {
  if (currentPage > 1) {
    currentPage--;
    loadPunishments();
  }
});
pageNext.addEventListener("click", () => {
  currentPage++;
  loadPunishments();
});
autoRefreshToggle.addEventListener("change", () => {
  if (autoRefreshToggle.checked) {
    liveInterval = setInterval(() => {
      loadPunishments();
      loadStats();
    }, 8000);
  } else if (liveInterval) {
    clearInterval(liveInterval);
    liveInterval = null;
  }
});

for (const th of [thIssued, thExpires]) {
  th.addEventListener("click", () => {
    const column = th.dataset.sort;
    if (sortColumn === column) {
      sortAscending = !sortAscending;
    } else {
      sortColumn = column;
      sortAscending = false;
    }
    updateSortHeaders();
    currentPage = 1;
    loadPunishments();
  });
}

function updateSortHeaders() {
  for (const th of [thIssued, thExpires]) {
    const active = th.dataset.sort === sortColumn;
    th.classList.toggle("sort-active", active);
    th.classList.toggle("sort-asc", active && sortAscending);
  }
}

selectAllCheckbox.addEventListener("change", () => {
  if (selectAllCheckbox.checked) {
    for (const checkbox of punishmentsBody.querySelectorAll(".row-select")) {
      selectedIds.add(Number(checkbox.value));
      checkbox.checked = true;
    }
  } else {
    for (const checkbox of punishmentsBody.querySelectorAll(".row-select")) {
      selectedIds.delete(Number(checkbox.value));
      checkbox.checked = false;
    }
  }
  updateBulkLiftVisibility();
});

function updateBulkLiftVisibility() {
  bulkLiftButton.hidden = selectedIds.size === 0;
  bulkLiftButton.textContent = `Lift selected (${selectedIds.size})`;
}

bulkLiftButton.addEventListener("click", () => {
  const items = Array.from(selectedIds).map((id) => punishmentsById.get(id)).filter(Boolean);
  if (items.length === 0) {
    return;
  }
  pendingLift = { bulk: items };
  liftSummary.textContent = `${items.length} punishment${items.length === 1 ? "" : "s"}`;
  liftReasonInput.value = "";
  liftOverlay.hidden = false;
  liftReasonInput.focus();
});

// ---- keyboard shortcuts ----

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    if (!detailOverlay.hidden) {
      closeDetail();
    } else if (!playerOverlay.hidden) {
      closePlayer();
    } else if (!liftOverlay.hidden) {
      closeLiftModal();
    } else if (!loginOverlay.hidden) {
      closeLogin();
    }
    return;
  }
  const tag = document.activeElement && document.activeElement.tagName;
  const isTyping = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
  if (event.key === "/" && !isTyping) {
    event.preventDefault();
    searchInput.focus();
  }
});

function debounce(fn, delayMillis) {
  let timeout;
  return (...args) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => fn(...args), delayMillis);
  };
}

function currentFilterParams() {
  const query = searchInput.value.trim();
  const type = typeFilter.value;
  const params = new URLSearchParams();
  if (query) {
    params.set("q", query);
  }
  if (type) {
    params.set("type", type);
  }
  params.set("activeOnly", String(!historyToggle.checked));
  return params;
}

async function loadPunishments() {
  const params = currentFilterParams();
  params.set("page", String(currentPage));
  params.set("limit", String(PAGE_SIZE));
  if (sortColumn) {
    params.set("sort", sortColumn);
    params.set("dir", sortAscending ? "asc" : "desc");
  }
  exportButton.href = "/api/export.csv?" + params.toString();
  tableSpinner.hidden = false;
  if (!hasLoadedPunishmentsOnce) {
    renderSkeletonRows(6);
  }
  try {
    // Browsing needs no auth, so a plain fetch here (never triggers the login overlay).
    const response = await fetch("/api/punishments?" + params.toString());
    const data = await response.json();
    hasLoadedPunishmentsOnce = true;
    renderPunishments(data.items);
    renderPagination(data.page, data.limit, data.total);
  } finally {
    tableSpinner.hidden = true;
  }
}

function renderSkeletonRows(count) {
  punishmentsBody.innerHTML = "";
  for (let i = 0; i < count; i++) {
    const row = document.createElement("tr");
    row.className = "skeleton-row";
    for (let c = 0; c < 8; c++) {
      const td = document.createElement("td");
      const bar = document.createElement("div");
      bar.className = "skeleton-bar";
      bar.style.width = c === 2 ? "70%" : "50%";
      td.appendChild(bar);
      row.appendChild(td);
    }
    punishmentsBody.appendChild(row);
  }
}

function renderPagination(page, limit, total) {
  const totalPages = Math.max(1, Math.ceil(total / limit));
  pageInfo.textContent = `Page ${page} of ${totalPages} · ${total} total`;
  pagePrev.disabled = page <= 1;
  pageNext.disabled = page >= totalPages;
}

async function loadStats() {
  const response = await fetch("/api/stats");
  const stats = await response.json();
  statTotal.textContent = stats.activeTotal;
  statBans.textContent = stats.activeBans;
  statMutes.textContent = stats.activeMutes;
  statWarns.textContent = stats.activeWarns;
  statAlltime.textContent = stats.allTimeTotal;
  renderActivityChart(stats.daily);
  renderRankedList(leaderboardList, leaderboardEmpty, stats.topOperators, (op) => op.name, null);
  renderRankedList(topTargetsList, topTargetsEmpty, stats.topTargets, (t) => t.name || t.uuid, (t) => openPlayer(t.uuid));
}

function renderActivityChart(daily) {
  activityChart.innerHTML = "";
  const max = Math.max(1, ...daily.map((day) => day.count));

  const tooltip = document.createElement("div");
  tooltip.className = "chart-tooltip";
  tooltip.hidden = true;
  activityChart.appendChild(tooltip);

  for (const day of daily) {
    const wrap = document.createElement("div");
    wrap.className = "activity-bar-wrap";

    const bar = document.createElement("div");
    bar.className = "activity-bar";
    const heightPct = (day.count / max) * 100;
    bar.style.height = Math.max(3, heightPct) + "%";
    bar.addEventListener("mouseenter", () => {
      tooltip.textContent = `${new Date(day.date).toLocaleDateString(undefined, { month: "short", day: "numeric" })} · ${day.count}`;
      tooltip.hidden = false;
      const wrapRect = wrap.getBoundingClientRect();
      const chartRect = activityChart.getBoundingClientRect();
      tooltip.style.left = wrapRect.left - chartRect.left + wrapRect.width / 2 + "px";
    });
    bar.addEventListener("mouseleave", () => {
      tooltip.hidden = true;
    });

    const label = document.createElement("span");
    label.className = "activity-bar-label";
    label.textContent = new Date(day.date).toLocaleDateString(undefined, { day: "numeric", month: "numeric" });

    wrap.append(bar, label);
    activityChart.appendChild(wrap);
  }
}

/** Shared renderer for the staff leaderboard and the most-punished-players list - same rank
 * badge/name/count layout, differing only in what labels each row and what a click does. */
function renderRankedList(listEl, emptyEl, items, getLabel, onClick) {
  listEl.innerHTML = "";
  emptyEl.hidden = items.length > 0;
  items.forEach((item, index) => {
    const li = document.createElement("li");
    if (onClick) {
      li.classList.add("clickable");
      li.addEventListener("click", () => onClick(item));
    }

    const rank = document.createElement("span");
    rank.className = "leaderboard-rank";
    rank.textContent = String(index + 1);

    const name = document.createElement("span");
    name.className = "leaderboard-name";
    name.textContent = getLabel(item);

    const count = document.createElement("span");
    count.className = "leaderboard-count";
    count.textContent = String(item.count);

    li.append(rank, name, count);
    listEl.appendChild(li);
  });
}

function typeBadgeClass(type) {
  return "type-badge type-" + type.toLowerCase();
}

const RELATIVE_TIME_UNITS = [
  ["year", 365 * 86_400_000],
  ["month", 30 * 86_400_000],
  ["week", 7 * 86_400_000],
  ["day", 86_400_000],
  ["hour", 3_600_000],
  ["minute", 60_000],
];

/** "3 days ago" / "in 3 days" - a coarser, glanceable read than a full timestamp, which is still
 * available via the element's title attribute wherever this is used. */
function relativeTime(millis) {
  const diff = millis - Date.now();
  const magnitude = Math.abs(diff);
  for (const [name, unitMillis] of RELATIVE_TIME_UNITS) {
    if (magnitude >= unitMillis) {
      const value = Math.round(magnitude / unitMillis);
      const plural = value === 1 ? name : name + "s";
      return diff > 0 ? `in ${value} ${plural}` : `${value} ${plural} ago`;
    }
  }
  return diff > 0 ? "in a moment" : "just now";
}

function avatarUrl(uuid, size) {
  return uuid ? `https://mc-heads.net/avatar/${uuid}/${size}` : null;
}

function avatarElement(uuid, size) {
  const url = avatarUrl(uuid, size);
  if (url) {
    const img = document.createElement("img");
    img.className = "avatar";
    img.style.width = size + "px";
    img.style.height = size + "px";
    img.src = url;
    img.alt = "";
    img.loading = "lazy";
    return img;
  }
  const placeholder = document.createElement("div");
  placeholder.className = "avatar-placeholder";
  placeholder.style.width = size + "px";
  placeholder.style.height = size + "px";
  placeholder.textContent = "?";
  return placeholder;
}

function renderPunishments(punishments) {
  punishmentsBody.innerHTML = "";
  emptyState.hidden = punishments.length > 0;
  selectedIds.clear();
  punishmentsById.clear();
  selectAllCheckbox.checked = false;
  updateBulkLiftVisibility();

  for (const p of punishments) {
    punishmentsById.set(p.id, p);
    const liftable = p.active && (p.type === "BAN" || p.type === "TEMPBAN" || p.type === "IPBAN" || p.type === "MUTE" || p.type === "TEMPMUTE");

    const row = document.createElement("tr");
    row.addEventListener("click", () => openDetail(p.id));

    if (loggedIn) {
      const selectCell = document.createElement("td");
      if (liftable) {
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.className = "row-select";
        checkbox.value = String(p.id);
        checkbox.addEventListener("click", (event) => event.stopPropagation());
        checkbox.addEventListener("change", () => {
          if (checkbox.checked) {
            selectedIds.add(p.id);
          } else {
            selectedIds.delete(p.id);
            selectAllCheckbox.checked = false;
          }
          updateBulkLiftVisibility();
        });
        selectCell.appendChild(checkbox);
      }
      row.appendChild(selectCell);
    }

    const typeCell = document.createElement("td");
    const typeBadge = document.createElement("span");
    typeBadge.className = typeBadgeClass(p.type);
    typeBadge.textContent = p.type;
    typeCell.appendChild(typeBadge);

    const targetCell = document.createElement("td");
    targetCell.className = "target-cell";
    const targetWrap = document.createElement("div");
    targetWrap.className = "target-wrap";
    targetWrap.appendChild(avatarElement(p.targetUuid, 20));
    const nameSpan = document.createElement("span");
    nameSpan.className = "target-name";
    nameSpan.textContent = p.targetName || p.targetUuid || p.ip || "(unknown)";
    targetWrap.appendChild(nameSpan);
    if (p.targetUuid) {
      targetWrap.addEventListener("click", (event) => {
        event.stopPropagation();
        openPlayer(p.targetUuid);
      });
    }
    targetCell.appendChild(targetWrap);

    const reasonCell = document.createElement("td");
    reasonCell.className = "reason-cell";
    reasonCell.textContent = p.reason || "";

    const operatorCell = document.createElement("td");
    operatorCell.textContent = p.operatorName || "";

    const issuedCell = document.createElement("td");
    issuedCell.textContent = relativeTime(p.createdAt);
    issuedCell.title = new Date(p.createdAt).toLocaleString();

    const expiresCell = document.createElement("td");
    if (p.permanent) {
      expiresCell.textContent = "Permanent";
    } else {
      expiresCell.textContent = relativeTime(p.expiresAt);
      expiresCell.title = new Date(p.expiresAt).toLocaleString();
    }

    const statusCell = document.createElement("td");
    const statusBadge = document.createElement("span");
    statusBadge.className = "badge " + (p.active ? "active" : "inactive");
    statusBadge.textContent = p.active ? "Active" : "Inactive";
    statusCell.appendChild(statusBadge);

    const actionCell = document.createElement("td");
    if (loggedIn && liftable) {
      const button = document.createElement("button");
      button.className = "small ghost";
      button.textContent = "Lift";
      button.addEventListener("click", (event) => {
        event.stopPropagation();
        liftPunishment(p);
      });
      actionCell.appendChild(button);
    }

    row.append(typeCell, targetCell, reasonCell, operatorCell, issuedCell, expiresCell, statusCell, actionCell);
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
  const pending = pendingLift;
  const reason = liftReasonInput.value.trim() || null;
  closeLiftModal();
  if (!pending) {
    return;
  }
  const items = pending.bulk ? pending.bulk : [pending];
  let succeeded = 0;
  try {
    for (const punishment of items) {
      await liftOne(punishment, reason);
      succeeded++;
    }
  } catch (e) {
    // openLogin() already ran if this was a 401 - fall through to refresh whatever did land.
  }
  if (succeeded > 0) {
    showToast(succeeded === 1 ? "Punishment lifted." : `${succeeded} punishments lifted.`, "success");
  }
  loadPunishments();
  loadStats();
});

async function liftOne(punishment, reason) {
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
  await api(path, { method: "POST", body: JSON.stringify(body) });
}

// ---- punishment detail modal ----

async function openDetail(id) {
  history.replaceState(null, "", `#/punishment/${id}`);
  detailOverlay.hidden = false;
  detailBody.replaceChildren(loadingParagraph());
  try {
    const response = await fetch(`/api/punishment?id=${encodeURIComponent(id)}`);
    if (!response.ok) {
      detailBody.replaceChildren(emptyParagraph("Punishment not found."));
      return;
    }
    renderDetail(await response.json());
  } catch (e) {
    detailBody.replaceChildren(emptyParagraph("Failed to load."));
  }
}

function closeDetail() {
  detailOverlay.hidden = true;
  clearHashIfMatches("#/punishment/");
}

detailClose.addEventListener("click", closeDetail);
detailOverlay.addEventListener("click", (event) => {
  if (event.target === detailOverlay) {
    closeDetail();
  }
});

function renderDetail(p) {
  detailBody.innerHTML = "";

  const header = document.createElement("div");
  header.className = "detail-header";
  const avatar = avatarElement(p.targetUuid, 56);
  avatar.className = avatar.className.replace("avatar", "detail-avatar");
  header.appendChild(avatar);

  const heading = document.createElement("div");
  heading.className = "detail-heading";
  const title = document.createElement("h2");
  title.textContent = `${p.type} #${p.id}`;
  const badges = document.createElement("div");
  badges.className = "detail-badges";
  badges.appendChild(makeBadge(typeBadgeClass(p.type), p.type));
  badges.appendChild(makeBadge("badge " + (p.active ? "active" : "inactive"), p.active ? "Active" : "Inactive"));
  if (p.permanent) {
    badges.appendChild(makeBadge("badge inactive", "Permanent"));
  }
  heading.append(title, badges);
  header.appendChild(heading);
  detailBody.appendChild(header);

  const grid = document.createElement("dl");
  grid.className = "detail-grid";
  addDetailRow(grid, "Player", p.targetName || "–", p.targetUuid);
  if (p.targetUuid) {
    addDetailRow(grid, "UUID", p.targetUuid, p.targetUuid);
  }
  if (p.ip) {
    addDetailRow(grid, "IP", p.ip, p.ip);
  }
  addDetailRow(grid, "Reason", p.reason || "(none given)");
  addDetailRow(grid, "Operator", p.operatorName || "–");
  addDetailRow(grid, "Issued", `${relativeTime(p.createdAt)} · ${new Date(p.createdAt).toLocaleString()}`);
  addDetailRow(grid, "Expires", p.permanent ? "Permanent" : `${relativeTime(p.expiresAt)} · ${new Date(p.expiresAt).toLocaleString()}`);
  if (!p.active && p.removedByName) {
    addDetailRow(grid, "Lifted by", p.removedByName);
    if (p.removedReason) {
      addDetailRow(grid, "Lift reason", p.removedReason);
    }
  }
  detailBody.appendChild(grid);

  const footer = document.createElement("div");
  footer.className = "detail-footer";
  const copyLinkButton = document.createElement("button");
  copyLinkButton.className = "ghost small";
  copyLinkButton.textContent = "Copy link";
  copyLinkButton.addEventListener("click", () => copyToClipboard(location.href, copyLinkButton));
  footer.appendChild(copyLinkButton);
  if (p.targetUuid) {
    const viewProfileButton = document.createElement("button");
    viewProfileButton.className = "ghost small";
    viewProfileButton.textContent = "View player profile";
    viewProfileButton.addEventListener("click", () => {
      closeDetail();
      openPlayer(p.targetUuid);
    });
    footer.appendChild(viewProfileButton);
  }
  detailBody.appendChild(footer);
}

function addDetailRow(grid, label, value, copyValue) {
  const dt = document.createElement("dt");
  dt.textContent = label;
  const dd = document.createElement("dd");
  const span = document.createElement("span");
  span.textContent = value;
  dd.appendChild(span);
  if (copyValue) {
    const button = document.createElement("button");
    button.className = "copy-button";
    button.textContent = "Copy";
    button.addEventListener("click", () => copyToClipboard(copyValue, button));
    dd.appendChild(button);
  }
  grid.append(dt, dd);
}

function makeBadge(className, text) {
  const span = document.createElement("span");
  span.className = className;
  span.textContent = text;
  return span;
}

// ---- player profile modal ----

async function openPlayer(uuid) {
  history.replaceState(null, "", `#/player/${uuid}`);
  playerOverlay.hidden = false;
  playerBody.replaceChildren(loadingParagraph());
  try {
    const response = await fetch(`/api/player?uuid=${encodeURIComponent(uuid)}`);
    if (!response.ok) {
      playerBody.replaceChildren(emptyParagraph("No record for that player."));
      return;
    }
    renderPlayer(await response.json());
  } catch (e) {
    playerBody.replaceChildren(emptyParagraph("Failed to load."));
  }
}

function closePlayer() {
  playerOverlay.hidden = true;
  clearHashIfMatches("#/player/");
}

playerClose.addEventListener("click", closePlayer);
playerOverlay.addEventListener("click", (event) => {
  if (event.target === playerOverlay) {
    closePlayer();
  }
});

function renderPlayer(data) {
  playerBody.innerHTML = "";

  const header = document.createElement("div");
  header.className = "player-header";
  const avatar = avatarElement(data.uuid, 56);
  avatar.className = avatar.className.replace("avatar", "player-avatar");
  header.appendChild(avatar);

  const heading = document.createElement("div");
  heading.className = "player-heading";
  const title = document.createElement("h2");
  title.textContent = data.name || "(unknown name)";
  const sub = document.createElement("p");
  sub.className = "modal-subtitle";
  sub.style.margin = "0";
  sub.textContent = data.uuid;
  heading.append(title, sub);
  header.appendChild(heading);
  playerBody.appendChild(header);

  const counts = document.createElement("div");
  counts.className = "player-counts";
  const bans = (data.counts.BAN || 0) + (data.counts.TEMPBAN || 0) + (data.counts.IPBAN || 0);
  const mutes = (data.counts.MUTE || 0) + (data.counts.TEMPMUTE || 0);
  const tiles = [["Bans", bans], ["Mutes", mutes], ["Warns", data.counts.WARN || 0], ["Kicks", data.counts.KICK || 0]];
  for (const [label, value] of tiles) {
    const tile = document.createElement("div");
    tile.className = "player-count-tile";
    const valueSpan = document.createElement("span");
    valueSpan.className = "count-value";
    valueSpan.textContent = String(value);
    const labelSpan = document.createElement("span");
    labelSpan.className = "count-label";
    labelSpan.textContent = label;
    tile.append(valueSpan, labelSpan);
    counts.appendChild(tile);
  }
  playerBody.appendChild(counts);

  const list = document.createElement("div");
  list.className = "player-history-list";
  if (data.history.length === 0) {
    list.appendChild(emptyParagraph("No punishment history."));
  }
  for (const p of data.history) {
    const row = document.createElement("div");
    row.className = "player-history-row";
    row.addEventListener("click", () => {
      closePlayer();
      openDetail(p.id);
    });

    const badge = document.createElement("span");
    badge.className = typeBadgeClass(p.type);
    badge.textContent = p.type;

    const reason = document.createElement("span");
    reason.className = "player-history-reason";
    reason.textContent = p.reason || "(no reason)";

    const date = document.createElement("span");
    date.className = "player-history-date";
    date.textContent = new Date(p.createdAt).toLocaleDateString();

    row.append(badge, reason, date);
    list.appendChild(row);
  }
  playerBody.appendChild(list);

  if (loggedIn) {
    playerBody.appendChild(buildNotesSection(data.uuid));
  }
}

// ---- staff notes ----

function buildNotesSection(uuid) {
  const section = document.createElement("div");
  section.className = "notes-section";

  const heading = document.createElement("h3");
  heading.textContent = "Staff notes";
  section.appendChild(heading);

  const list = document.createElement("div");
  list.className = "notes-list";
  section.appendChild(list);

  const form = document.createElement("form");
  form.className = "note-form";
  const textarea = document.createElement("textarea");
  textarea.placeholder = "Add a note for other staff (e.g. known alt, watch for griefing)...";
  const submitButton = document.createElement("button");
  submitButton.type = "submit";
  submitButton.textContent = "Add";
  form.append(textarea, submitButton);
  section.appendChild(form);

  async function refreshNotes() {
    list.replaceChildren(emptyParagraph("Loading…"));
    try {
      const response = await api(`/api/notes?uuid=${encodeURIComponent(uuid)}`);
      renderNotesList(list, await response.json(), refreshNotes);
    } catch (e) {
      list.replaceChildren(emptyParagraph("Failed to load notes."));
    }
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = textarea.value.trim();
    if (!text) {
      return;
    }
    try {
      await api("/api/notes", { method: "POST", body: JSON.stringify({ uuid, text }) });
      textarea.value = "";
      refreshNotes();
    } catch (e) {
      // openLogin() already ran if this was a 401.
    }
  });

  refreshNotes();
  return section;
}

function renderNotesList(list, notes, onChanged) {
  list.innerHTML = "";
  if (notes.length === 0) {
    list.appendChild(emptyParagraph("No notes yet."));
    return;
  }
  for (const note of notes) {
    const item = document.createElement("div");
    item.className = "note-item";

    const header = document.createElement("div");
    header.className = "note-item-header";
    const authorGroup = document.createElement("span");
    const author = document.createElement("span");
    author.className = "note-item-author";
    author.textContent = note.authorName || "unknown";
    authorGroup.append(author, document.createTextNode(" · " + new Date(note.createdAt).toLocaleString()));
    header.appendChild(authorGroup);

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.className = "note-delete";
    deleteButton.textContent = "✕";
    deleteButton.title = "Delete note";
    deleteButton.addEventListener("click", async () => {
      try {
        await api("/api/notes/delete", { method: "POST", body: JSON.stringify({ id: note.id }) });
        onChanged();
      } catch (e) {
        // openLogin() already ran if this was a 401.
      }
    });
    header.appendChild(deleteButton);

    const text = document.createElement("div");
    text.className = "note-item-text";
    text.textContent = note.text;

    item.append(header, text);
    list.appendChild(item);
  }
}

function loadingParagraph() {
  return emptyParagraph("Loading…");
}

function emptyParagraph(text) {
  const p = document.createElement("p");
  p.className = "empty-state";
  p.textContent = text;
  return p;
}

function clearHashIfMatches(prefix) {
  if (location.hash.startsWith(prefix)) {
    history.replaceState(null, "", location.pathname + location.search);
  }
}

// ---- permalink routing ----

function handleInitialHash() {
  const match = location.hash.match(/^#\/(punishment|player)\/(.+)$/);
  if (!match) {
    return;
  }
  if (match[1] === "punishment") {
    openDetail(match[2]);
  } else {
    openPlayer(match[2]);
  }
}

// The punishments list loads for everyone; the session check just decides whether to
// reveal the issue form and Lift buttons (the cookie, if any, survives a page reload).
fetch("/api/session").then((response) => setLoggedIn(response.ok)).finally(loadPunishments);
loadStats();
handleInitialHash();
