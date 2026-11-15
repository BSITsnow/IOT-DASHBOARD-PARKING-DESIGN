<<<<<<< HEAD
const $ = (id) => document.getElementById(id);

async function getJson(url) {
  const response = await fetch(url);
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

refresh();
setInterval(refresh, 3000);

let me = null;

// ---------- tabs ----------
function showTab(name) {
  for (const b of document.querySelectorAll("#tabs button")) b.classList.toggle("on", b.dataset.tab === name);
  for (const s of document.querySelectorAll("main > section")) s.hidden = s.id !== "tab-" + name;
  if (name === "analytics") loadAnalytics();
  if (name === "users") loadUsers();
  if (name === "activity") loadActivity();
  try { sessionStorage.setItem("adminTab", name); } catch (e) { /* storage may be blocked */ }
}
$("tabs").addEventListener("click", e => { if (e.target.dataset.tab) showTab(e.target.dataset.tab); });
const currentTab = () => document.querySelector("#tabs button.on").dataset.tab;

// ---------- analytics ----------
function card(label, value, sub) {
  return h("div", { class: "card" }, h("p", {}, label), h("strong", {}, value), sub ? h("small", {}, sub) : null);
}

async function loadAnalytics() {
  let r;
  try { r = await api("/api/admin/analytics?days=" + $("days").value); }
  catch (e) { $("range").textContent = e.message; return; }
  const s = r.summary;
  $("range").textContent = s.from ? "Data from " + fmtTime(s.from) + " to " + fmtTime(s.to) : "No slot data recorded yet";

  $("summary").replaceChildren(
    card("Occupied now", s.occupiedNow + " / " + s.slotsSeen, s.occupancyNowPct + "%"),
    card("Average occupancy", s.avgOccupancyPct + "%", "over the period"),
    card("Arrivals", s.totalArrivals, "cars parked"),
    card("Average stay", s.avgStaySeconds ? fmtDuration(s.avgStaySeconds) : "-"),
    card("Peak hour", s.peakHour == null ? "-" : fmtHour(s.peakHour)),
    card("Busiest slot", s.busiestSlot || "-"));

  const maxHour = Math.max(1, ...r.hourly.map(x => x.occupancyPct));
  $("hourly").replaceChildren(...r.hourly.map(x =>
    h("div", { class: "col" + (x.hour === s.peakHour ? " peak" : ""), title: fmtHour(x.hour) + ": " + x.occupancyPct + "%" },
      h("i", { style: "height:" + (x.occupancyPct / maxHour * 100) + "%" }),
      h("span", {}, x.hour % 3 === 0 ? String(x.hour) : ""))));

  const maxDay = Math.max(1, ...r.daily.map(x => x.arrivals));
  const every = Math.ceil(r.daily.length / 10);
  $("daily").replaceChildren(...r.daily.map((x, i) =>
    h("div", { class: "col", title: x.date + ": " + x.arrivals + " arrivals" },
      h("i", { style: "height:" + (x.arrivals / maxDay * 100) + "%" }),
      h("span", {}, i % every === 0 ? x.date.slice(5) : ""))));

  const rows = $("slot-rows");
  rows.replaceChildren();
  if (!r.slots.length) rows.append(h("tr", {}, h("td", { colspan: "7", class: "empty" }, "No data yet")));
  for (const p of r.slots) {
    rows.append(h("tr", {},
      h("td", {}, h("strong", {}, p.slot)),
      h("td", {}, h("span", { class: "pill " + (p.occupiedNow ? "bad" : "ok") }, p.occupiedNow ? "Occupied" : "Vacant")),
      h("td", {}, h("div", { style: "display:flex;align-items:center;gap:8px" },
        h("div", { class: "bar", style: "width:90px" }, h("i", { style: "width:" + p.occupancyPct + "%" })), p.occupancyPct + "%")),
      h("td", {}, p.arrivals), h("td", {}, p.completedStays),
      h("td", {}, p.completedStays ? fmtDuration(p.avgStaySeconds) : "-"),
      h("td", {}, p.completedStays ? fmtDuration(p.longestStaySeconds) : "-")));
  }
}
$("days").addEventListener("change", loadAnalytics);

// ---------- users ----------
async function loadUsers() {
  const users = await api("/api/admin/users");
  const rows = $("user-rows");
  rows.replaceChildren();
  for (const u of users) {
    const self = u.id === me.id;
    rows.append(h("tr", {},
      h("td", {}, h("strong", {}, u.username), self ? h("span", { class: "muted" }, " (you)") : null),
      h("td", {}, u.fullName || "-"),
      h("td", {}, h("span", { class: "pill " + (u.role === "ADMIN" ? "warn" : "muted") }, u.role)),
      h("td", {}, h("span", { class: "pill " + (u.active ? "ok" : "bad") }, u.active ? "Active" : "Disabled")),
      h("td", { class: "mask", title: "Stored as a salted hash" }, "••••••••"),
      h("td", {}, fmtTime(u.lastLoginAt)),
      h("td", { class: "actions-cell" }, h("div", { class: "actions" },
        h("button", { class: "small", onclick: () => resetPassword(u) }, "Reset password"),
        h("button", { class: "small", disabled: self, onclick: () => update(u, { role: u.role === "ADMIN" ? "CLIENT" : "ADMIN" }) },
          u.role === "ADMIN" ? "Make client" : "Make admin"),
        h("button", { class: "small", disabled: self, onclick: () => update(u, { active: !u.active }) }, u.active ? "Disable" : "Enable"),
        h("button", { class: "small danger", disabled: self, onclick: () => remove(u) }, "Delete")))));
  }
}

async function update(u, changes) {
  try { await api("/api/admin/users/" + u.id, { method: "PUT", body: changes }); loadUsers(); }
  catch (e) { alert(e.message); }
}
async function resetPassword(u) {
  const pw = prompt("New password for " + u.username + " (8+ characters):");
  if (pw === null) return;
  try { await api("/api/admin/users/" + u.id, { method: "PUT", body: { password: pw } }); alert("Password reset for " + u.username + "."); }
  catch (e) { alert(e.message); }
}
async function remove(u) {
  if (!confirm("Delete the account '" + u.username + "'? This cannot be undone.")) return;
  try { await api("/api/admin/users/" + u.id, { method: "DELETE" }); loadUsers(); }
  catch (e) { alert(e.message); }
}

$("new-user").addEventListener("submit", async ev => {
  ev.preventDefault();
  const form = ev.target, msg = $("new-user-msg");
  msg.className = "msg"; msg.textContent = "";
  const body = Object.fromEntries(new FormData(form));
  try {
    await api("/api/admin/users", { method: "POST", body });
    form.reset(); msg.className = "msg good"; msg.textContent = "Account created.";
    loadUsers();
  } catch (e) { msg.className = "msg err"; msg.textContent = e.message; }
});

// ---------- activity ----------
async function loadActivity() {
  const [logins, events] = await Promise.all([api("/api/admin/logins?limit=50"), api("/api/events?limit=50")]);
  const lr = $("login-rows"); lr.replaceChildren();
  if (!logins.length) lr.append(h("tr", {}, h("td", { colspan: "5", class: "empty" }, "No sign-ins yet")));
  for (const l of logins) {
    lr.append(h("tr", {}, h("td", {}, fmtTime(l.loggedAt)), h("td", {}, l.username || "-"),
      h("td", {}, h("span", { class: "pill " + (l.success ? "ok" : "bad") }, l.success ? "Success" : "Failed")),
      h("td", {}, l.note || ""), h("td", {}, l.ipAddress || "")));
  }
  const er = $("event-rows"); er.replaceChildren();
  if (!events.length) er.append(h("tr", {}, h("td", { colspan: "4", class: "empty" }, "No slot changes yet")));
  for (const e of events) {
    er.append(h("tr", {}, h("td", {}, e.id), h("td", {}, "P" + e.slotNumber),
      h("td", { class: e.occupied ? "occupied" : "vacant" }, e.occupied ? "Occupied" : "Vacant"),
      h("td", {}, fmtTime(e.recordedAt))));
  }
}

// ---------- start ----------
(async function init() {
  me = await api("/api/auth/me");
  if (me.role !== "ADMIN") { location.href = "/client/index.html"; return; }
  $("who").textContent = me.fullName || me.username;
  passwordForm($("pw-box"));
  let first = "analytics";
  try { first = sessionStorage.getItem("adminTab") || first; } catch (e) { /* ignore */ }
  showTab(document.querySelector('#tabs [data-tab="' + first + '"]') ? first : "analytics");
  setInterval(() => { if (!document.hidden && currentTab() === "analytics") loadAnalytics(); }, 5000);
})();
>>>>>>> 9a650f05159ae8b62142f9780e931f06e227c939
