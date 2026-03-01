const el = (id) => document.getElementById(id);

let token = localStorage.getItem("token") || "";
let selectedActivityId = null;

function log(obj) {
  el("output").textContent = typeof obj === "string" ? obj : JSON.stringify(obj, null, 2);
}

function baseUrl() {
  return el("baseUrl").value.trim().replace(/\/$/, "");
}

async function api(path, method = "GET", body = null, auth = false) {
  const headers = { "Content-Type": "application/json" };
  if (auth && token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(baseUrl() + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null,
  });
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }
  if (!res.ok) throw data;
  return data;
}

async function register() {
  const data = await api("/auth/register", "POST", {
    name: el("name").value,
    email: el("email").value,
    password: el("password").value,
  });
  token = data.token;
  localStorage.setItem("token", token);
  log(data);
}

async function login() {
  const data = await api("/auth/login", "POST", {
    email: el("email").value,
    password: el("password").value,
  });
  token = data.token;
  localStorage.setItem("token", token);
  log(data);
}

async function loadActivities() {
  const data = await api("/activities/me", "GET", null, true);
  const ul = el("activities");
  ul.innerHTML = "";
  data.forEach((a) => {
    const li = document.createElement("li");
    li.textContent = `#${a.id} ${a.type} ${a.status} dist=${a.distanceMeters ?? 0}`;
    li.style.cursor = "pointer";
    li.onclick = () => {
      selectedActivityId = a.id;
      el("activityId").value = String(a.id);
    };
    ul.appendChild(li);
  });
  log(data);
}

async function startActivity() {
  const data = await api("/activities/start", "POST", { type: el("activityType").value || "RUN" }, true);
  selectedActivityId = data.id;
  el("activityId").value = String(data.id);
  log(data);
}

async function stopSelected() {
  const id = Number(el("activityId").value || selectedActivityId);
  if (!id) return log("Select activity ID first");

  const now = new Date();
  const p1 = new Date(now.getTime() - 120000).toISOString().slice(0, 19);
  const p2 = new Date(now.getTime() - 60000).toISOString().slice(0, 19);
  const p3 = new Date(now.getTime()).toISOString().slice(0, 19);

  await api(`/activities/${id}/points`, "POST", {
    points: [
      { latitude: 12.9716, longitude: 77.5946, recordedAt: p1, sequenceNo: 1 },
      { latitude: 12.9720, longitude: 77.5950, recordedAt: p2, sequenceNo: 2 },
      { latitude: 12.9730, longitude: 77.5960, recordedAt: p3, sequenceNo: 3 }
    ]
  }, true);

  const endedAt = new Date().toISOString().slice(0, 19);
  const data = await api(`/activities/${id}/stop`, "POST", { endedAt }, true);
  log(data);
}

async function likeActivity() {
  const id = Number(el("activityId").value || selectedActivityId);
  if (!id) return log("Enter activity ID");
  const data = await api(`/social/activities/${id}/like`, "POST", null, true);
  log(data);
}

async function commentActivity() {
  const id = Number(el("activityId").value || selectedActivityId);
  if (!id) return log("Enter activity ID");
  const data = await api(`/social/activities/${id}/comment`, "POST", { text: el("comment").value || "Great run!" }, true);
  log(data);
}

function wire() {
  el("registerBtn").onclick = () => register().catch(log);
  el("loginBtn").onclick = () => login().catch(log);
  el("loadActivitiesBtn").onclick = () => loadActivities().catch(log);
  el("startBtn").onclick = () => startActivity().catch(log);
  el("stopBtn").onclick = () => stopSelected().catch(log);
  el("likeBtn").onclick = () => likeActivity().catch(log);
  el("commentBtn").onclick = () => commentActivity().catch(log);
}

function init() {
  el("baseUrl").value = localStorage.getItem("baseUrl") || "";
  el("baseUrl").addEventListener("change", () => {
    localStorage.setItem("baseUrl", el("baseUrl").value.trim());
  });
  wire();
  log("Set backend URL, then register/login.");
}

init();
