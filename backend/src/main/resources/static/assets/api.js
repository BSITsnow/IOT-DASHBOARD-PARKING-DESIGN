// Small helpers shared by every page.
async function api(path, options = {}) {
  const init = { method: options.method || "GET", credentials: "same-origin", headers: {} };
  if (options.body !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(options.body);
  }
  const res = await fetch(path, init);
  let data = null;
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (res.status === 401 && !options.noRedirect) {
    location.href = "/login.html";
    throw new Error("Signed out");
  }
  if (!res.ok) throw new Error((data && (data.message || data.error)) || ("Request failed (" + res.status + ")"));
  return data;
}

// Builds DOM nodes with textContent only, so user-supplied text can never inject HTML.
function h(tag, attrs, ...kids) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (k === "class") node.className = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else if (v !== false && v != null) node.setAttribute(k, v === true ? "" : v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    node.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return node;
}

function fmtDuration(sec) {
  sec = Math.round(sec || 0);
  if (sec < 60) return sec + "s";
  const m = Math.floor(sec / 60);
  if (m < 60) return m + "m " + (sec % 60) + "s";
  const hr = Math.floor(m / 60);
  return hr + "h " + (m % 60) + "m";
}
function fmtTime(iso) { return iso ? new Date(iso).toLocaleString() : "never"; }
function fmtHour(hr) { return String(hr).padStart(2, "0") + ":00"; }

async function logout() {
  try { await api("/api/auth/logout", { method: "POST", noRedirect: true }); } catch (e) { /* ignore */ }
  location.href = "/login.html";
}

// Both the client and admin pages offer "change my password".
function passwordForm(container) {
  const cur = h("input", { type: "password", autocomplete: "current-password" });
  const next = h("input", { type: "password", autocomplete: "new-password", minlength: "8" });
  const msg = h("div", { class: "msg" });
  const save = h("button", { class: "primary", type: "button", onclick: async () => {
    msg.className = "msg"; msg.textContent = "";
    try {
      await api("/api/auth/password", { method: "POST", body: { current: cur.value, next: next.value } });
      cur.value = ""; next.value = "";
      msg.className = "msg good"; msg.textContent = "Password changed.";
    } catch (e) { msg.className = "msg err"; msg.textContent = e.message; }
  } }, "Save new password");
  container.append(
    h("label", {}, "Current password"), cur,
    h("label", {}, "New password (8+ characters)"), next,
    h("div", { style: "margin-top:14px" }, save), msg);
}
