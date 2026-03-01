const $ = (id) => document.getElementById(id);
const defaultOrigin = window.location.origin || "http://localhost:8080";

const state = {
  baseUrl: localStorage.getItem("baseUrl") || defaultOrigin,
  token: localStorage.getItem("token") || "",
};

const page = document.body?.dataset?.page || "auth";
let trackerWatchId = null;
let trackerStartMs = null;
let trackerTimerId = null;
let trackerDistanceKm = 0;
let trackerLastPos = null;
let trackerLastPosTs = null;
let trackerCurrentSpeedKmh = null;
let trackerRecentSamples = [];
let trackerLastAccuracyM = null;
let trackerMap = null;
let trackerMarker = null;
let trackerPath = null;
let trackerPathCoords = [];
let trackerSmoothedPos = null;
let trackerActivityType = "RUN";
let trackerActivityId = null;
let trackerCollectedPoints = [];
let trackerStopping = false;

const TRACKING_CONFIG = {
  accuracyStrongM: 15,
  accuracyUsableM: 35,
  minStepM: 1.2,
  maxStepStrongM: 45,
  maxStepWeakM: 90,
  maxHumanSpeedKmh: 25,
};

function el(id) {
  return $(id);
}

function setValue(id, value) {
  const node = el(id);
  if (node) node.value = value;
}

function setText(id, value) {
  const node = el(id);
  if (node) node.textContent = value;
}

function getValue(id) {
  return (el(id)?.value || "").trim();
}

function toLocalDateTimeString(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${y}-${m}-${d}T${hh}:${mm}:${ss}`;
}

function init() {
  initFirebaseAuth();
  alignBaseUrlWithCurrentOrigin();
  guardAuthenticatedPages();
  setValue("baseUrl", state.baseUrl);
  renderAuthStatus();
  if (page === "tracker") {
    initTrackerPage();
  }
  if (page === "activity") {
    loadActivityPreview();
  }
  if (page === "activity-history") {
    loadAllActivitiesHistory();
  }
  if (page === "social") {
    loadSocialPageData();
  }
  wireCommonActions();
  wirePageActions();
  loadRunningFact();
  preventAccidentalSubmit();
  log(`Ready on ${page} page.`);
}

function initFirebaseAuth() {
  if (!window.firebase || !window.FIREBASE_WEB_CONFIG) return;
  if (!isFirebaseConfigUsable()) {
    log("Firebase config is missing. Update FIREBASE_WEB_CONFIG in signin/register HTML.");
    return;
  }
  if (!window.firebase.apps?.length) {
    window.firebase.initializeApp(window.FIREBASE_WEB_CONFIG);
  }
  handleFirebaseRedirectResult();
}

function isFirebaseConfigUsable() {
  const cfg = window.FIREBASE_WEB_CONFIG;
  if (!cfg) return false;

  const required = ["apiKey", "authDomain", "projectId", "appId"];
  for (const key of required) {
    const value = String(cfg[key] || "").trim();
    if (!value) return false;
    if (value.includes("YOUR_FIREBASE_") || value.includes("YOUR_FIREBASE_PROJECT")) {
      return false;
    }
  }
  return true;
}

function firebaseErrorHelpMessage(code, message, providerType = "google") {
  const providerLabel = providerType === "github" ? "GitHub" : "Google";
  switch (code) {
    case "auth/invalid-api-key":
      return "Firebase API key is invalid. Check FIREBASE_WEB_CONFIG.apiKey.";
    case "auth/unauthorized-domain":
      return "This localhost domain is not authorized in Firebase Auth. Add localhost in Firebase Console > Authentication > Settings > Authorized domains.";
    case "auth/operation-not-allowed":
      return "Provider is disabled in Firebase Console. Enable Google/GitHub in Authentication > Sign-in method.";
    case "auth/configuration-not-found":
      return `Firebase Authentication configuration not found for ${providerLabel}. In Firebase Console > Authentication: click Get started, enable ${providerLabel} under Sign-in method, and add localhost in Authorized domains.`;
    case "auth/popup-closed-by-user":
      return "Login popup was closed before completion. Please try again.";
    default:
      return message || "Unknown Firebase auth error.";
  }
}

async function handleFirebaseRedirectResult() {
  try {
    if (!window.firebase?.auth) return;
    const auth = window.firebase.auth();
    const result = await auth.getRedirectResult();
    if (!result?.user) return;

    const idToken = await result.user.getIdToken();
    const name = result.user.displayName || "";

    const body = await api(
      "/auth/firebase",
      {
        method: "POST",
        body: JSON.stringify({ idToken, name }),
      },
      false
    );

    setToken(body.token);
    renderAuthStatus(result.user.email || "");
    goToDashboard();
  } catch (e) {
    log("Firebase redirect login failed", { error: e.message });
  }
}

async function loginWithFirebaseProvider(providerType = "google") {
  try {
    if (!isFirebaseConfigUsable()) {
      alert("Firebase config is not set. Update FIREBASE_WEB_CONFIG in signin.html/register.html first.");
      return;
    }

    if (!window.firebase?.auth) throw new Error("Firebase SDK not loaded");

    const auth = window.firebase.auth();
    const provider = providerType === "github"
      ? new window.firebase.auth.GithubAuthProvider()
      : new window.firebase.auth.GoogleAuthProvider();

    const result = await auth.signInWithPopup(provider);
    const idToken = await result.user.getIdToken();
    const name = result.user.displayName || "";

    const body = await api(
      "/auth/firebase",
      {
        method: "POST",
        body: JSON.stringify({ idToken, name }),
      },
      false
    );

    setToken(body.token);
    renderAuthStatus(result.user.email || "");
    goToDashboard();
  } catch (e) {
    const code = e?.code || "";
    const popupBlocked = code === "auth/popup-blocked" || code === "auth/web-storage-unsupported";

    if (popupBlocked) {
      try {
        const auth = window.firebase.auth();
        const provider = providerType === "github"
          ? new window.firebase.auth.GithubAuthProvider()
          : new window.firebase.auth.GoogleAuthProvider();

        log("Popup blocked by browser. Switching to redirect login...");
        await auth.signInWithRedirect(provider);
        return;
      } catch (redirectError) {
        log("Firebase redirect fallback failed", { error: redirectError.message });
        return;
      }
    }

    const help = firebaseErrorHelpMessage(code, e.message, providerType);
    log("Firebase login failed", { error: e.message, code, help });
    alert(help);
  }
}

function initTrackerPage() {
  const params = new URLSearchParams(window.location.search);
  const type = (params.get("type") || "run").toLowerCase();
  const prettyType = type.charAt(0).toUpperCase() + type.slice(1);
  trackerActivityType = type.toUpperCase();

  setText("trackerTypeLabel", `${prettyType} Tracker`);
  setText("trackerTitle", `Live ${prettyType} in progress`);
  initTrackerMap();
  setText("gpsStatus", "Press Start to begin tracking.");
  setText("statTimer", "00:00:00");
  setText("statDistance", "0.00 km");
  setText("statCalories", "0 kcal");
  setText("statSpeed", "0.0 km/h");
}

async function startTrackerActivity() {
  try {
    if (trackerActivityId) return;
    const startBtn = el("startTrackerBtn");
    const stopBtn = el("stopTrackerBtn");
    if (startBtn) startBtn.disabled = true;
    if (stopBtn) stopBtn.disabled = false;

    resetTrackerSessionUI();

    const body = await api("/activities/start", {
      method: "POST",
      body: JSON.stringify({ type: trackerActivityType }),
    });
    trackerActivityId = body.id;
    trackerStartMs = Date.now();
    trackerTimerId = setInterval(updateTrackerTimer, 1000);
    updateTrackerTimer();
    startGpsTracking(trackerActivityType.toLowerCase());
    setText("gpsStatus", "Tracking started. Move to record route...");
  } catch (e) {
    const startBtn = el("startTrackerBtn");
    if (startBtn) startBtn.disabled = false;
    log("Start tracker activity failed", { error: e.message });
  }
}

async function stopTrackerActivity() {
  if (trackerStopping) return;
  try {
    trackerStopping = true;

    const stopBtn = el("stopTrackerBtn");
    if (stopBtn) stopBtn.disabled = true;

    if (trackerWatchId != null && navigator.geolocation) {
      navigator.geolocation.clearWatch(trackerWatchId);
      trackerWatchId = null;
    }
    if (trackerTimerId) {
      clearInterval(trackerTimerId);
      trackerTimerId = null;
    }

    const idsToStop = await findAllInProgressActivityIds();
    if (!idsToStop.length && trackerActivityId) idsToStop.push(trackerActivityId);
    if (!idsToStop.length) {
      setText("gpsStatus", "No active activity to stop.");
      return;
    }

    // add small offset to avoid endedAt being earlier than startedAt due to second truncation
    const endedAt = toLocalDateTimeString(new Date(Date.now() + 5000));
    let stoppedCount = 0;
    let lastError = null;

    for (const id of idsToStop) {
      try {
        await api(`/activities/${id}/stop`, {
          method: "POST",
          body: JSON.stringify({ endedAt }),
        });
        stoppedCount += 1;
      } catch (err) {
        lastError = err;
      }
    }

    if (stoppedCount === 0) {
      throw lastError || new Error("Could not stop any in-progress activity");
    }

    trackerActivityId = null;
    resetTrackerSessionUI();
    const startBtn = el("startTrackerBtn");
    if (startBtn) startBtn.disabled = false;
    setText("gpsStatus", `Stopped ${stoppedCount} activity(s).`);
    window.location.href = "/activity.html";
  } catch (e) {
    const stopBtn = el("stopTrackerBtn");
    if (stopBtn) stopBtn.disabled = false;
    setText("gpsStatus", "Stop failed. Please tap Stop again.");
    setText("gpsMeta", String(e.message || "Unknown stop error"));
    log("Stop tracker activity failed", { error: e.message });
  } finally {
    trackerStopping = false;
  }
}

async function findAllInProgressActivityIds() {
  try {
    const list = await api("/activities/me", { method: "GET" });
    const sorted = [...list].sort((a, b) => new Date(b.startedAt || 0) - new Date(a.startedAt || 0));
    return sorted
      .filter((a) => (a.status || "").toUpperCase() === "IN_PROGRESS")
      .map((a) => a.id)
      .filter(Boolean);
  } catch {
    return [];
  }
}

function resetTrackerSessionUI() {
  trackerStartMs = null;
  trackerDistanceKm = 0;
  trackerCurrentSpeedKmh = null;
  trackerLastPos = null;
  trackerLastPosTs = null;
  trackerRecentSamples = [];
  trackerSmoothedPos = null;
  trackerCollectedPoints = [];
  trackerPathCoords = [];
  if (trackerPath) trackerPath.setLatLngs([]);
  if (trackerMarker && trackerMap) {
    trackerMap.removeLayer(trackerMarker);
    trackerMarker = null;
  }

  setText("statTimer", "00:00:00");
  setText("statDistance", "0.00 km");
  setText("statCalories", "0 kcal");
  setText("statSpeed", "0.0 km/h");
  setText("statMotion", "Rest");
}

function initTrackerMap() {
  if (typeof L === "undefined") return;
  const mapEl = el("trackerMap");
  if (!mapEl) return;

  trackerMap = L.map("trackerMap").setView([20.5937, 78.9629], 5);
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(trackerMap);

  trackerPath = L.polyline([], { color: "#2563eb", weight: 5 }).addTo(trackerMap);
}

function updateTrackerTimer() {
  if (!trackerStartMs) return;
  const seconds = Math.floor((Date.now() - trackerStartMs) / 1000);
  const hh = String(Math.floor(seconds / 3600)).padStart(2, "0");
  const mm = String(Math.floor((seconds % 3600) / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");
  setText("statTimer", `${hh}:${mm}:${ss}`);

  updateLiveSpeedFromRecentSamples();
  updateMotionState();
}

function updateMotionState() {
  const motionEl = el("statMotion");
  if (!motionEl) return;

  // Prefer current speed, but also infer movement from recent displacement
  let inferredSpeed = trackerCurrentSpeedKmh;

  if (trackerRecentSamples.length >= 2) {
    const first = trackerRecentSamples[0];
    const last = trackerRecentSamples[trackerRecentSamples.length - 1];
    const deltaHrs = (last.ts - first.ts) / 3600000;
    if (deltaHrs > 0) {
      let km = 0;
      for (let i = 1; i < trackerRecentSamples.length; i++) {
        const a = trackerRecentSamples[i - 1];
        const b = trackerRecentSamples[i];
        km += distanceKm(a.lat, a.lon, b.lat, b.lon);
      }
      const rollingKmh = km / deltaHrs;
      if (Number.isFinite(rollingKmh) && rollingKmh >= 0) {
        inferredSpeed = Math.max(inferredSpeed || 0, rollingKmh);
      }
    }
  }

  // lower threshold so walking reliably marks as moving
  const moving = inferredSpeed != null && inferredSpeed >= 0.6;
  motionEl.textContent = moving ? "Moving" : "Rest";
}

function updateLiveSpeedFromRecentSamples() {
  if (!trackerRecentSamples.length) return;

  const now = Date.now();
  // keep only recent window for near-real-time speed
  trackerRecentSamples = trackerRecentSamples.filter((s) => now - s.ts <= 20000);
  if (trackerRecentSamples.length < 2) return;

  const first = trackerRecentSamples[0];
  const last = trackerRecentSamples[trackerRecentSamples.length - 1];
  const deltaHrs = (last.ts - first.ts) / 3600000;
  if (deltaHrs <= 0) return;

  let km = 0;
  for (let i = 1; i < trackerRecentSamples.length; i++) {
    const a = trackerRecentSamples[i - 1];
    const b = trackerRecentSamples[i];
    km += distanceKm(a.lat, a.lon, b.lat, b.lon);
  }

  const rollingKmh = km / deltaHrs;
  const canUseFallback =
    trackerLastAccuracyM != null &&
    trackerLastAccuracyM <= 20 &&
    rollingKmh >= 0 &&
    rollingKmh <= 20;

  if (canUseFallback && (trackerCurrentSpeedKmh == null || trackerCurrentSpeedKmh <= 0)) {
    trackerCurrentSpeedKmh = rollingKmh;
    setText("statSpeed", `${trackerCurrentSpeedKmh.toFixed(1)} km/h`);
  }
}

function startGpsTracking(type) {
  if (trackerWatchId != null) {
    navigator.geolocation.clearWatch(trackerWatchId);
    trackerWatchId = null;
  }

  if (!navigator.geolocation) {
    setText("gpsStatus", "GPS not supported in this browser.");
    return;
  }

  setText("gpsStatus", "Requesting GPS permission...");
  trackerWatchId = navigator.geolocation.watchPosition(
    (pos) => onGpsPosition(pos, type),
    (err) => {
      if (err.code === 1) {
        setText("gpsStatus", "GPS blocked. Please allow location permission in browser settings and tap Retry GPS Permission.");
      } else {
        setText("gpsStatus", `GPS error: ${err.message}`);
      }
    },
    {
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 5000,
    }
  );
}

function onGpsPosition(position, type) {
  const nowTs = Date.now();
  const { latitude, longitude, speed, accuracy } = position.coords;
  trackerLastAccuracyM = accuracy;

  const quality = getGpsQuality(accuracy);
  setText("gpsQuality", `Quality: ${quality.label}`);

  const smoothed = smoothPosition(latitude, longitude, accuracy);
  const lat = smoothed.lat;
  const lon = smoothed.lon;

  const weakFix = quality.level === "weak";
  if (weakFix) {
    setText("gpsStatus", `GPS weak (±${Math.round(accuracy)}m). Tracking continues with limited precision.`);
  } else {
    setText("gpsStatus", `GPS active (±${Math.round(accuracy)}m)`);
  }
  setText("gpsMeta", `Lat: ${lat.toFixed(6)}, Lon: ${lon.toFixed(6)}`);

  if (trackerLastPos) {
    const addKm = distanceKm(
      trackerLastPos.latitude,
      trackerLastPos.longitude,
      lat,
      lon
    );
    // Ignore jitter and unrealistic jumps/speeds
    const meters = addKm * 1000;
    const maxReasonableStepM = weakFix ? TRACKING_CONFIG.maxStepWeakM : TRACKING_CONFIG.maxStepStrongM;
    const deltaHrs = trackerLastPosTs ? (nowTs - trackerLastPosTs) / 3600000 : 0;
    const stepSpeedKmh = deltaHrs > 0 ? addKm / deltaHrs : 0;

    if (
      meters > TRACKING_CONFIG.minStepM &&
      meters < maxReasonableStepM &&
      stepSpeedKmh <= TRACKING_CONFIG.maxHumanSpeedKmh
    ) {
      trackerDistanceKm += addKm;
      trackerRecentSamples.push({ ts: nowTs, lat, lon });
      trackerCollectedPoints.push({ ts: nowTs, lat, lon });
    }

    // Do not derive current speed from point delta directly here (too noisy)
  }

  trackerLastPos = { latitude: lat, longitude: lon };
  trackerLastPosTs = nowTs;
  if (!trackerRecentSamples.length) {
    trackerRecentSamples.push({ ts: nowTs, lat, lon });
    trackerCollectedPoints.push({ ts: nowTs, lat, lon });
  }

  updateTrackerMap(lat, lon);

  setText("statDistance", `${trackerDistanceKm.toFixed(2)} km`);

  // Current speed: prefer native GPS speed
  if (speed != null && speed >= 0) {
    const gpsSpeedKmh = speed * 3.6;
    if (gpsSpeedKmh < 45) {
      trackerCurrentSpeedKmh = gpsSpeedKmh;
      setText("statSpeed", `${Math.max(0, trackerCurrentSpeedKmh).toFixed(1)} km/h`);
    } else {
      setText("statSpeed", "-- km/h");
    }
  } else {
    if (trackerCurrentSpeedKmh == null) {
      setText("statSpeed", "-- km/h");
    }
  }

  const calories = estimateCalories(type, trackerDistanceKm);
  setText("statCalories", `${Math.round(calories)} kcal`);
}

function getGpsQuality(accuracy) {
  if (accuracy == null) return { level: "weak", label: "Unknown" };
  if (accuracy <= TRACKING_CONFIG.accuracyStrongM) return { level: "strong", label: `Strong (±${Math.round(accuracy)}m)` };
  if (accuracy <= TRACKING_CONFIG.accuracyUsableM) return { level: "usable", label: `Usable (±${Math.round(accuracy)}m)` };
  return { level: "weak", label: `Weak (±${Math.round(accuracy)}m)` };
}

function smoothPosition(lat, lon, accuracy) {
  if (!trackerSmoothedPos) {
    trackerSmoothedPos = { lat, lon };
    return trackerSmoothedPos;
  }

  const quality = getGpsQuality(accuracy).level;
  const alpha = quality === "strong" ? 0.5 : quality === "usable" ? 0.3 : 0.18;
  trackerSmoothedPos = {
    lat: trackerSmoothedPos.lat + alpha * (lat - trackerSmoothedPos.lat),
    lon: trackerSmoothedPos.lon + alpha * (lon - trackerSmoothedPos.lon),
  };
  return trackerSmoothedPos;
}

function updateTrackerMap(lat, lon) {
  if (!trackerMap) return;

  const point = [lat, lon];
  trackerPathCoords.push(point);
  if (trackerPath) trackerPath.setLatLngs(trackerPathCoords);

  if (!trackerMarker) {
    trackerMarker = L.marker(point).addTo(trackerMap).bindPopup("You are here");
  } else {
    trackerMarker.setLatLng(point);
  }

  trackerMap.setView(point, Math.max(trackerMap.getZoom(), 17));
}

function distanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (deg) => (deg * Math.PI) / 180;
  const R = 6371;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function estimateSpeedKmh() {
  if (!trackerStartMs) return 0;
  const elapsedHrs = (Date.now() - trackerStartMs) / 3600000;
  if (elapsedHrs <= 0) return 0;
  return trackerDistanceKm / elapsedHrs;
}

function estimateCalories(type, distanceKmValue) {
  const kcalPerKm = {
    run: 62,
    walk: 45,
    cycle: 32,
  };
  return distanceKmValue * (kcalPerKm[type] || 50);
}

function loadRunningFact() {
  const factEl = el("runningFact");
  if (!factEl) return;

  const facts = [
    "A 30-minute easy run can improve mood and reduce stress hormones within the same day.",
    "Running regularly can increase your VO₂ max, helping your body use oxygen more efficiently.",
    "Strength training twice a week can significantly reduce common running injuries.",
    "Most runners benefit from easy runs at a conversational pace for better long-term endurance.",
    "Hydration affects pace: even mild dehydration can increase heart rate during workouts.",
    "Sleep is performance fuel — consistent 7–9 hours improves recovery and training quality.",
    "Dynamic warm-ups before running can improve stride efficiency and reduce injury risk.",
    "Progressive overload works best when weekly mileage increases gradually, not suddenly.",
    "Post-run protein + carbs within 60 minutes helps muscle repair and glycogen recovery.",
    "Short interval sessions can improve speed and cardiovascular fitness even for beginners."
  ];

  const index = Math.floor(Math.random() * facts.length);
  factEl.textContent = facts[index];
}

function preventAccidentalSubmit() {
  document.querySelectorAll("button").forEach((btn) => {
    if (!btn.getAttribute("type")) btn.setAttribute("type", "button");
  });
}

function alignBaseUrlWithCurrentOrigin() {
  const current = window.location.origin;
  if (!current) return;
  const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(current);
  if (!isLocal) return;
  if (state.baseUrl !== current) {
    state.baseUrl = current;
    localStorage.setItem("baseUrl", state.baseUrl);
  }
}

function guardAuthenticatedPages() {
  const protectedPages = ["dashboard", "activity", "activity-history", "social"];
  if (protectedPages.includes(page) && !state.token) {
    window.location.href = "/index.html";
  }
}

function formatDuration(seconds) {
  if (!seconds || seconds <= 0) return "--";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function estimateActivityCalories(activity) {
  const km = (activity.distanceMeters || 0) / 1000;
  const type = (activity.type || "run").toLowerCase();
  const perKm = { run: 62, walk: 45, cycle: 32 };
  return Math.round(km * (perKm[type] || 50));
}

function renderActivitiesList(items, elementId) {
  const host = el(elementId);
  if (!host) return;
  if (!items.length) {
    host.innerHTML = `<p class="muted-line">No activities yet.</p>`;
    return;
  }

  host.innerHTML = items.map((a) => {
    const km = ((a.distanceMeters || 0) / 1000).toFixed(2);
    const duration = formatDuration(a.durationSeconds || 0);
    const calories = estimateActivityCalories(a);
    const started = a.startedAt ? new Date(a.startedAt).toLocaleString() : "--";
    return `
      <article class="activity-item">
        <h4>${(a.type || "Activity").toUpperCase()} • ${a.status || "--"}</h4>
        <p class="activity-meta">Started: ${started}</p>
        <p class="activity-meta">Duration: ${duration} • Distance: ${km} km • Calories: ${calories} kcal</p>
      </article>
    `;
  }).join("");
}

async function loadActivityPreview() {
  try {
    const list = await api("/activities/me", { method: "GET" });
    const sorted = [...list].sort((a, b) => new Date(b.startedAt || 0) - new Date(a.startedAt || 0));
    renderActivitiesList(sorted.slice(0, 10), "activityPreviewList");
  } catch (e) {
    log("Load previous activities failed", { error: e.message });
  }
}

async function loadAllActivitiesHistory() {
  try {
    const list = await api("/activities/me", { method: "GET" });
    const sorted = [...list].sort((a, b) => new Date(b.startedAt || 0) - new Date(a.startedAt || 0));
    renderActivitiesList(sorted, "allActivitiesList");
  } catch (e) {
    log("Load full activity history failed", { error: e.message });
  }
}

function saveBaseUrl() {
  const value = getValue("baseUrl").replace(/\/$/, "");
  if (!value) {
    log("Base URL cannot be empty.");
    return;
  }
  state.baseUrl = value;
  localStorage.setItem("baseUrl", state.baseUrl);
  log(`Saved base URL: ${state.baseUrl}`);
}

function setToken(token) {
  state.token = token || "";
  if (state.token) localStorage.setItem("token", state.token);
  else localStorage.removeItem("token");
  renderAuthStatus();
}

function renderAuthStatus(email = "") {
  const statusEl = el("authStatus");
  if (!statusEl) return;
  if (state.token) {
    statusEl.className = "status-pill ok";
    statusEl.textContent = email ? `Logged in as ${email}` : "Logged in (token saved)";
  } else {
    statusEl.className = "status-pill muted";
    statusEl.textContent = "Not logged in";
  }
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function log(message, data) {
  const line = `[${new Date().toLocaleTimeString()}] ${message}`;
  const payload = data ? `${line}\n${JSON.stringify(data, null, 2)}` : line;
  const logEl = el("log");
  if (!logEl) {
    console.log(payload);
    return;
  }
  const current = logEl.textContent || "";
  const next = `${payload}\n\n${current}`;
  logEl.textContent = next.slice(0, 8000);
}

async function api(path, options = {}, requireAuth = true) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };
  if (requireAuth && state.token) headers.Authorization = `Bearer ${state.token}`;

  let response;
  try {
    response = await fetch(`${state.baseUrl}${path}`, {
      ...options,
      headers,
    });
  } catch (e) {
    throw new Error(`Network error: could not reach ${state.baseUrl}${path}. Check Base URL and backend port.`);
  }

  let body;
  const text = await response.text();
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${JSON.stringify(body)}`);
  }
  return body;
}

async function register() {
  try {
    if (!state.baseUrl) throw new Error("Please set Base URL first.");
    const name = getValue("regName");
    const email = getValue("regEmail");
    const password = el("regPassword")?.value || "";

    if (!name) throw new Error("Name is required.");
    if (!isValidEmail(email)) throw new Error("Enter a valid email like name@example.com");
    if (!password || password.length < 6) throw new Error("Password must be at least 6 characters.");

    const body = await api(
      "/auth/register",
      {
        method: "POST",
        body: JSON.stringify({
          name,
          email,
          password,
        }),
      },
      false
    );
    setToken(body.token);
    renderAuthStatus(email);
    goToDashboard();
    log("Register success", body);
  } catch (e) {
    log("Register failed", { error: e.message });
  }
}

async function login() {
  try {
    if (!state.baseUrl) throw new Error("Please set Base URL first.");
    const email = getValue("loginEmail");
    const password = el("loginPassword")?.value || "";

    if (!isValidEmail(email)) throw new Error("Enter a valid email like name@example.com");
    if (!password || password.length < 6) throw new Error("Password must be at least 6 characters.");

    const body = await api(
      "/auth/login",
      {
        method: "POST",
        body: JSON.stringify({
          email,
          password,
        }),
      },
      false
    );
    setToken(body.token);
    renderAuthStatus(email);
    goToDashboard();
    log("Login success", body);
  } catch (e) {
    log("Login failed", { error: e.message });
  }
}

function logout() {
  setToken("");
  setText("meOutput", "");
  log("Logged out.");
  window.location.href = "/index.html";
}

function goToDashboard() {
  window.location.href = "/dashboard.html";
}

function goToActivity() {
  window.location.href = "/activity.html";
}

function goToSocial() {
  window.location.href = "/social.html";
}

async function startActivity() {
  try {
    const body = await api("/activities/start", {
      method: "POST",
      body: JSON.stringify({ type: getValue("activityType") || "RUN" }),
    });
    log("Activity started", body);
  } catch (e) {
    log("Start activity failed", { error: e.message });
  }
}

async function addPoint() {
  try {
    const id = Number(getValue("pointActivityId"));
    const now = toLocalDateTimeString(new Date());
    const body = await api(`/activities/${id}/points`, {
      method: "POST",
      body: JSON.stringify({
        points: [
          {
            latitude: Number(getValue("pointLat")),
            longitude: Number(getValue("pointLon")),
            recordedAt: now,
            sequenceNo: Number(getValue("pointSeq") || 1),
          },
        ],
      }),
    });
    log("Point added", body);
  } catch (e) {
    log("Add point failed", { error: e.message });
  }
}

async function stopActivity() {
  try {
    const id = Number(getValue("stopActivityId"));
    const endedAt = toLocalDateTimeString(new Date());
    const body = await api(`/activities/${id}/stop`, {
      method: "POST",
      body: JSON.stringify({ endedAt }),
    });
    log("Activity stopped", body);
  } catch (e) {
    log("Stop activity failed", { error: e.message });
  }
}

async function loadMyActivities() {
  try {
    const body = await api("/activities/me", { method: "GET" });
    setText("activitiesOutput", JSON.stringify(body, null, 2));
    log("Loaded activities", body);
  } catch (e) {
    log("Load activities failed", { error: e.message });
  }
}

async function loadActivityDetail() {
  try {
    const id = Number(getValue("detailActivityId"));
    const body = await api(`/activities/${id}`, { method: "GET" });
    setText("activitiesOutput", JSON.stringify(body, null, 2));
    log("Loaded activity detail", body);
  } catch (e) {
    log("Load activity detail failed", { error: e.message });
  }
}

async function loadSocialPageData() {
  await Promise.allSettled([
    loadSocialFeed(),
    loadNotifications(),
  ]);
}

function formatWhen(value) {
  if (!value) return "--";
  return new Date(value).toLocaleString();
}

function fmtKm(distanceMeters) {
  return ((distanceMeters || 0) / 1000).toFixed(2);
}

function renderUserSearchResults(items) {
  const host = el("userSearchResults");
  if (!host) return;
  if (!items || !items.length) {
    host.innerHTML = `<p class="muted-line">No users found.</p>`;
    return;
  }

  host.innerHTML = items.map((u) => `
    <article class="social-item">
      <div>
        <h4>${u.name || "Unknown"}</h4>
        <p class="activity-meta">${u.email || ""}</p>
      </div>
      <div>
        ${u.isSelf ? `<span class="badge">You</span>` : `
          <button data-follow-id="${u.id}" class="${u.isFollowing ? "secondary" : ""}">${u.isFollowing ? "Unfollow" : "Follow"}</button>
        `}
      </div>
    </article>
  `).join("");

  host.querySelectorAll("button[data-follow-id]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = Number(btn.getAttribute("data-follow-id"));
      const action = btn.textContent?.trim().toLowerCase() === "follow" ? "follow" : "unfollow";
      try {
        await api(`/social/follow/${id}`, { method: action === "follow" ? "POST" : "DELETE" });
        await searchUsers();
        await loadSocialFeed();
        await loadNotifications();
      } catch (e) {
        log("Follow/unfollow failed", { error: e.message });
      }
    });
  });
}

async function searchUsers() {
  try {
    const q = getValue("userSearchInput");
    if (!q) {
      renderUserSearchResults([]);
      return;
    }
    const items = await api(`/social/users/search?q=${encodeURIComponent(q)}`, { method: "GET" });
    renderUserSearchResults(items);
  } catch (e) {
    log("Search users failed", { error: e.message });
  }
}

function renderFeed(items) {
  const host = el("socialFeedList");
  if (!host) return;
  if (!items || !items.length) {
    host.innerHTML = `<p class="muted-line">No feed yet. Follow runners to see their activities.</p>`;
    return;
  }

  host.innerHTML = items.map((a) => `
    <article class="social-item">
      <h4>${a.userName || "Runner"} • ${(a.type || "ACTIVITY").toUpperCase()} • ${a.status || "--"}</h4>
      <p class="activity-meta">${fmtKm(a.distanceMeters)} km • ${formatDuration(a.durationSeconds || 0)} • ${formatWhen(a.startedAt)}</p>
      <p class="activity-meta">❤️ ${a.likes || 0} • 💬 ${a.comments || 0}</p>
      <div class="social-row">
        <button data-like-id="${a.activityId}" class="${a.likedByMe ? "secondary" : ""}">${a.likedByMe ? "Unlike" : "Like"}</button>
        <input data-comment-text="${a.activityId}" type="text" placeholder="Add a comment" />
        <button data-comment-id="${a.activityId}">Comment</button>
      </div>
    </article>
  `).join("");

  host.querySelectorAll("button[data-like-id]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = Number(btn.getAttribute("data-like-id"));
      const action = btn.textContent?.trim().toLowerCase() === "like" ? "POST" : "DELETE";
      try {
        await api(`/social/activities/${id}/like`, { method: action });
        await loadSocialFeed();
        await loadNotifications();
      } catch (e) {
        log("Like toggle failed", { error: e.message });
      }
    });
  });

  host.querySelectorAll("button[data-comment-id]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const id = Number(btn.getAttribute("data-comment-id"));
      const input = host.querySelector(`input[data-comment-text="${id}"]`);
      const text = (input?.value || "").trim();
      if (!text) return;
      try {
        await api(`/social/activities/${id}/comment`, {
          method: "POST",
          body: JSON.stringify({ text }),
        });
        if (input) input.value = "";
        await loadSocialFeed();
        await loadNotifications();
      } catch (e) {
        log("Comment failed", { error: e.message });
      }
    });
  });
}

async function loadSocialFeed() {
  try {
    const items = await api("/social/feed", { method: "GET" });
    renderFeed(items);
  } catch (e) {
    log("Load feed failed", { error: e.message });
  }
}

function renderNotifications(items) {
  const host = el("socialNotificationsList");
  if (!host) return;
  if (!items || !items.length) {
    host.innerHTML = `<p class="muted-line">No notifications yet.</p>`;
    return;
  }

  host.innerHTML = items.map((n) => `
    <article class="social-item">
      <h4>${(n.type || "info").toUpperCase()}</h4>
      <p class="activity-meta">${n.message || ""}</p>
      <small>${formatWhen(n.createdAt)}</small>
    </article>
  `).join("");
}

async function loadNotifications() {
  try {
    const items = await api("/social/notifications", { method: "GET" });
    renderNotifications(items);
  } catch (e) {
    log("Load notifications failed", { error: e.message });
  }
}

function bind(id, fn) {
  const node = el(id);
  if (node) node.addEventListener("click", fn);
}

function wireCommonActions() {
  bind("saveBaseUrlBtn", saveBaseUrl);
  bind("logoutBtn", logout);
}

function wirePageActions() {
  bind("registerBtn", register);
  bind("loginBtn", login);
  bind("firebaseGoogleLoginBtn", () => loginWithFirebaseProvider("google"));
  bind("firebaseGoogleRegisterBtn", () => loginWithFirebaseProvider("google"));

  bind("goActivityBtn", goToActivity);
  bind("goActivityQuickBtn", goToActivity);
  bind("goSocialBtn", goToSocial);

  bind("startActivityBtn", startActivity);
  bind("addPointBtn", addPoint);
  bind("stopActivityBtn", stopActivity);
  bind("myActivitiesBtn", loadMyActivities);
  bind("activityDetailBtn", loadActivityDetail);

  bind("userSearchBtn", searchUsers);
  bind("refreshFeedBtn", loadSocialFeed);
  bind("refreshNotificationsBtn", loadNotifications);
  bind("showMoreActivitiesBtn", () => {
    window.location.href = "/activity-history.html";
  });
  bind("startTrackerBtn", startTrackerActivity);
  bind("stopTrackerBtn", stopTrackerActivity);

  bind("retryGpsBtn", () => {
    const params = new URLSearchParams(window.location.search);
    const type = (params.get("type") || "run").toLowerCase();
    startGpsTracking(type);
  });
}

init();