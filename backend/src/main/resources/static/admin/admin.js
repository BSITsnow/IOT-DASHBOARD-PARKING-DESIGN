const $ = (id) => document.getElementById(id);

async function getJson(url) {
  const response = await fetch(url);
  if (response.status === 401 || response.status === 403) {
    location.href = "/login.html";          // session expired or not signed in
    throw new Error("Not signed in");
  }
  if (!response.ok) throw new Error(url + " -> HTTP " + response.status);
  return response.json();
}

function formatDuration(totalSeconds) {
  const s = Math.max(0, Math.round(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h > 0) return h + "h " + m + "m";
  if (m > 0) return m + "m " + sec + "s";
  return sec + "s";
}

function formatTime(iso) {
  return iso ? new Date(iso).toLocaleString() : "-";
}

async function loadSummary() {
  const s = await getJson("/api/admin/summary");
  $("total").textContent = s.totalSlots;
  $("available").textContent = s.availableNow;
  $("occupied").textContent = s.occupiedNow;
  $("today").textContent = s.sessionsToday;
  $("avg").textContent = formatDuration(s.avgStaySeconds);
  $("longest").textContent = formatDuration(s.longestStaySeconds);
  fillSlotFilter(s.totalSlots);
}

let filterBuilt = false;
function fillSlotFilter(totalSlots) {
  if (filterBuilt) return;
  const select = $("slotFilter");
  for (let n = 1; n <= totalSlots; n++) {
    const option = document.createElement("option");
    option.value = n;
    option.textContent = "P" + n;
    select.appendChild(option);
  }
  select.addEventListener("change", refresh);
  filterBuilt = true;
}

async function loadSlots() {
  const slots = await getJson("/api/admin/slots");
  const grid = $("slotGrid");
  grid.innerHTML = "";
  for (const s of slots) {
    const div = document.createElement("div");
    div.className = "slot " + (s.occupied ? "occupied" : "vacant");
    const since = s.occupied
      ? "Parked " + formatDuration((Date.now() - new Date(s.since).getTime()) / 1000)
      : "";
    div.innerHTML =
      '<div class="name">P' + s.slotNumber + "</div>" +
      '<div class="state">' + (s.occupied ? "OCCUPIED" : "VACANT") + "</div>" +
      '<div class="since">' + since + "</div>";
    grid.appendChild(div);
  }
}

async function loadSessions() {
  const slot = $("slotFilter").value;
  const url = "/api/admin/sessions?limit=50" + (slot ? "&slot=" + slot : "");
  const sessions = await getJson(url);
  const body = $("sessionRows");
  body.innerHTML = "";
  for (const s of sessions) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      "<td>" + s.id + "</td>" +
      "<td>P" + s.slotNumber + "</td>" +
      "<td>" + formatTime(s.startedAt) + "</td>" +
      "<td>" + formatTime(s.endedAt) + "</td>" +
      "<td>" + formatDuration(s.durationSeconds) + "</td>" +
      '<td class="' + (s.active ? "badge-active" : "badge-done") + '">' +
        (s.active ? "Still parked" : "Finished") + "</td>";
    body.appendChild(tr);
  }
}

async function loadEvents() {
  const events = await getJson("/api/events?limit=20");
  const body = $("eventRows");
  body.innerHTML = "";
  for (const e of events) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      "<td>" + e.id + "</td>" +
      "<td>P" + e.slotNumber + "</td>" +
      '<td class="' + (e.occupied ? "occupied-text" : "vacant-text") + '">' +
        (e.occupied ? "Occupied" : "Vacant") + "</td>" +
      "<td>" + formatTime(e.recordedAt) + "</td>";
    body.appendChild(tr);
  }
}

async function refresh() {
  try {
    await Promise.all([loadSummary(), loadSlots(), loadSessions(), loadEvents()]);
    $("status").textContent = "Updated " + new Date().toLocaleTimeString();
  } catch (err) {
    console.error(err);
    $("status").textContent = "Cannot reach backend";
  }
}

$("logout").addEventListener("click", async () => {
  await fetch("/api/auth/logout", { method: "POST" });
  location.href = "/login.html";
});

fetch("/api/auth/me").then((r) => r.ok ? r.json() : null).then((u) => {
  if (u) $("user").textContent = u.fullName || u.username;
}).catch(() => {});

refresh();
setInterval(refresh, 3000);
