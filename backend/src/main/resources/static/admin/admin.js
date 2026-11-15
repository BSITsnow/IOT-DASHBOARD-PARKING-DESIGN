async function loadEvents() {
  const status = document.getElementById("status");
  try {
    const response = await fetch("/api/events?limit=50");
    if (!response.ok) throw new Error("HTTP " + response.status);
    const events = await response.json();

    document.getElementById("count").textContent = events.length;
    document.getElementById("last").textContent =
      events.length ? new Date(events[0].recordedAt).toLocaleTimeString() : "-";

    const rows = document.getElementById("rows");
    rows.innerHTML = "";
    for (const e of events) {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td>${e.id}</td>
        <td>P${e.slotNumber}</td>
        <td class="${e.occupied ? "occupied" : "vacant"}">
          ${e.occupied ? "Occupied" : "Vacant"}</td>
        <td>${new Date(e.recordedAt).toLocaleString()}</td>`;
      rows.appendChild(tr);
    }
    status.textContent = "Updated " + new Date().toLocaleTimeString();
  } catch (err) {
    status.textContent = "Cannot reach backend";
  }
}

loadEvents();
setInterval(loadEvents, 3000);   // refresh every 3 seconds