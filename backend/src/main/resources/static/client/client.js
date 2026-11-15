const $ = id => document.getElementById(id);

async function refresh() {
  try {
    const s = await api("/api/client/status");
    $("total").textContent = s.total;
    $("available").textContent = s.available;
    $("occupied").textContent = s.occupied;
    $("percent").textContent = s.percent + "%";
    $("percent-bar").style.width = s.percent + "%";
    $("full").hidden = !(s.total > 0 && s.available === 0);

    const grid = $("slots");
    grid.replaceChildren();
    if (!s.slots.length) grid.append(h("div", { class: "empty" }, "No slot data yet."));
    for (const slot of s.slots) {
      grid.append(h("div", { class: "slot " + (slot.occupied ? "taken" : "free") },
        slot.id, h("small", {}, slot.occupied ? "Occupied" : "Available")));
    }

    $("dot").className = "dot " + (s.live ? "live" : "stale");
    $("updated").textContent = s.live
      ? "Live. Last update " + new Date(s.updatedAt).toLocaleTimeString()
      : "Sensors not reporting. Showing last known state" + (s.updatedAt ? " from " + fmtTime(s.updatedAt) : "") + ".";
  } catch (e) {
    $("dot").className = "dot stale";
    $("updated").textContent = "Cannot reach the server.";
  }
}

(async function init() {
  const me = await api("/api/auth/me");
  $("who").textContent = me.fullName || me.username;
  passwordForm($("pw-card"));
  $("pw-toggle").onclick = () => { $("pw-card").hidden = !$("pw-card").hidden; };
  refresh();
  setInterval(refresh, 2000);
})();
