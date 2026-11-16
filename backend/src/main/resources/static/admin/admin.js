const $ = (id) => document.getElementById(id);

const DAYS = 7;
const LIVE_REFRESH_MS = 3000;
const ANALYTICS_REFRESH_MS = 15000;
const SESSION_REFRESH_MS = 10000;
const EVENTS_LIMIT = 100;
const SESSIONS_LIMIT = 50;

let analyticsLoadedAt = 0;
let sessionsLoadedAt = 0;
let refreshInProgress = false;
let refreshTimer;

async function getJson(url, signal) {
  const response = await fetch(url, {
    signal,
    cache: "no-store",
    headers: { Accept: "application/json" }
  });
  if (response.status === 401 || response.status === 403) {
    location.href = "/login.html";
    throw new Error("Not signed in");
  }
  if (!response.ok) throw new Error(url + " -> HTTP " + response.status);
  return response.json();
}

function formatDuration(totalSeconds) {
  const s = Math.max(0, Math.round(Number(totalSeconds) || 0));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return h + "h " + m + "m";
  if (m > 0) return m + "m " + sec + "s";
  return sec + "s";
}

function formatTime(iso) {
  if (!iso) return "-";
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString();
}

function formatHour(hour) {
  return String(hour).padStart(2, "0") + ":00";
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function setStatus(text, error) {
  const status = $("status");
  status.textContent = text;
  status.classList.toggle("error", Boolean(error));
}

function fillSlotFilter(slots) {
  const select = $("slotFilter");
  const current = select.value;
  const slotNumbers = [...new Set(slots.map((slot) => slot.slotNumber || Number(String(slot.slot).replace("P", ""))))]
    .filter((slot) => Number.isInteger(slot) && slot > 0)
    .sort((a, b) => a - b);
  select.replaceChildren(el("option", "", "All slots"));
  select.firstElementChild.value = "";
  for (const number of slotNumbers) {
    const option = el("option", "", "P" + number);
    option.value = String(number);
    select.appendChild(option);
  }
  select.value = slotNumbers.includes(Number(current)) ? current : "";
}

function renderSlots(slots) {
  const grid = $("slotGrid");
  grid.replaceChildren();
  if (!slots.length) {
    grid.appendChild(el("p", "empty", "No slot events have been recorded yet."));
    return;
  }
  for (const slot of slots) {
    const tile = el("div", "slot " + (slot.occupiedNow ? "occupied" : "vacant"));
    tile.append(
      el("div", "name", slot.slot),
      el("div", "state", slot.occupiedNow ? "OCCUPIED" : "VACANT"),
      el("div", "since", slot.arrivals + " arrivals, " + slot.occupancyPct + "% occupied")
    );
    grid.appendChild(tile);
  }
}

function renderTrend(containerId, values, label, value, suffix, max) {
  const container = $(containerId);
  container.replaceChildren();
  if (!values.length) {
    container.appendChild(el("p", "empty", "No data available."));
    return;
  }
  for (const item of values) {
    const amount = Number(value(item)) || 0;
    const row = el("div", "trend");
    const track = el("div", "trend-track");
    const bar = el("div", "trend-bar");
    bar.style.width = Math.min(100, Math.max(0, amount / max * 100)) + "%";
    track.appendChild(bar);
    row.append(
      el("span", "trend-label", label(item)),
      track,
      el("span", "trend-value", amount + suffix)
    );
    container.appendChild(row);
  }
}

async function loadAnalytics(signal) {
  const result = await getJson("/api/admin/analytics?days=" + DAYS, signal);
  const summary = result.summary;
  const slots = Array.isArray(result.slots) ? result.slots : [];
  const longest = slots.reduce((max, slot) => Math.max(max, Number(slot.longestStaySeconds) || 0), 0);

  $("total").textContent = summary.slotsSeen;
  $("available").textContent = Math.max(0, summary.slotsSeen - summary.occupiedNow);
  $("occupied").textContent = summary.occupiedNow;
  $("today").textContent = summary.totalArrivals;
  $("avg").textContent = formatDuration(summary.avgStaySeconds);
  $("longest").textContent = formatDuration(longest);
  $("occupancy").textContent = (Number(summary.avgOccupancyPct) || 0) + "%";
  $("busiest").textContent = summary.busiestSlot || "-";
  $("peak").textContent = summary.peakHour == null ? "-" : formatHour(summary.peakHour);

  fillSlotFilter(slots);
  renderSlots(slots);
  renderTrend("dailyTrend", result.daily || [], (item) => item.date, (item) => item.arrivals, "", Math.max(1, ...((result.daily || []).map((item) => item.arrivals || 0))));
  renderTrend("hourlyTrend", result.hourly || [], (item) => formatHour(item.hour), (item) => item.occupancyPct, "%", 100);
  analyticsLoadedAt = Date.now();
}

async function loadSessions(signal) {
  const slot = $("slotFilter").value;
  const query = slot ? "&slot=" + encodeURIComponent(slot) : "";
  const sessions = await getJson("/api/admin/sessions?limit=" + SESSIONS_LIMIT + query, signal);
  const body = $("sessionRows");
  body.replaceChildren();
  if (!sessions.length) {
    const row = el("tr");
    const cell = el("td", "empty", "No parking sessions recorded yet.");
    cell.colSpan = 6;
    row.appendChild(cell);
    body.appendChild(row);
    sessionsLoadedAt = Date.now();
    return;
  }
  for (const session of sessions) {
    const row = el("tr");
    row.append(
      el("td", "", session.id),
      el("td", "", "P" + session.slotNumber),
      el("td", "", formatTime(session.startedAt)),
      el("td", "", formatTime(session.endedAt)),
      el("td", "", formatDuration(session.durationSeconds)),
      el("td", session.active ? "badge-active" : "badge-done", session.active ? "Still parked" : "Finished")
    );
    body.appendChild(row);
  }
  sessionsLoadedAt = Date.now();
}

async function loadEvents(signal) {
  const selectedSlot = $("slotFilter").value;
  let events = await getJson("/api/events?limit=" + EVENTS_LIMIT, signal);
  if (selectedSlot) events = events.filter((event) => String(event.slotNumber) === selectedSlot);
  const body = $("eventRows");
  body.replaceChildren();
  if (!events.length) {
    const row = el("tr");
    const cell = el("td", "empty", "No slot changes recorded yet.");
    cell.colSpan = 4;
    row.appendChild(cell);
    body.appendChild(row);
    return;
  }
  for (const event of events.slice(0, 20)) {
    const row = el("tr");
    row.append(
      el("td", "", event.id),
      el("td", "", "P" + event.slotNumber),
      el("td", event.occupied ? "occupied-text" : "vacant-text", event.occupied ? "Occupied" : "Vacant"),
      el("td", "", formatTime(event.recordedAt))
    );
    body.appendChild(row);
  }
}

async function refresh(forceAnalytics, forceSessions) {
  if (refreshInProgress) return;
  refreshInProgress = true;
  const controller = new AbortController();
  const tasks = [["live slots", loadEvents(controller.signal)]];
  if (forceSessions || Date.now() - sessionsLoadedAt >= SESSION_REFRESH_MS) {
    tasks.push(["sessions", loadSessions(controller.signal)]);
  }
  if (forceAnalytics || Date.now() - analyticsLoadedAt >= ANALYTICS_REFRESH_MS) {
    tasks.push(["analytics", loadAnalytics(controller.signal)]);
  }

  const results = await Promise.allSettled(tasks.map((task) => task[1]));
  const failures = results
    .map((result, index) => result.status === "rejected" ? tasks[index][0] : null)
    .filter(Boolean);
  if (failures.length) {
    console.error("Admin dashboard update failed:", failures);
    setStatus("Update failed: " + failures.join(", "), true);
  } else {
    setStatus("Updated " + new Date().toLocaleTimeString(), false);
  }
  refreshInProgress = false;
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => refresh(false), LIVE_REFRESH_MS);
}

$("slotFilter").addEventListener("change", () => refresh(false, true));
$("logout").addEventListener("click", async () => {
  await fetch("/api/auth/logout", { method: "POST", cache: "no-store" });
  location.href = "/login.html";
});

getJson("/api/auth/me")
  .then((user) => { $("user").textContent = user.fullName || user.username; })
  .catch((error) => console.error("Could not load current user:", error));

refresh(true, true);
