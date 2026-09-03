const searchInput = document.getElementById("searchInput");
const resultsEl = document.getElementById("results");
const queueListEl = document.getElementById("queueList");
const queueCountEl = document.getElementById("queueCount");
const toastEl = document.getElementById("toast");
const errorEl = document.getElementById("error");
const lockBannerEl = document.getElementById("lockBanner");

let searchTimer = null;
let socket = null;
let queueLocked = false;

function showToast(message) {
  toastEl.textContent = message;
  toastEl.classList.remove("hidden");
  setTimeout(() => toastEl.classList.add("hidden"), 2200);
}

function showError(message) {
  errorEl.textContent = message;
  errorEl.classList.remove("hidden");
}

function setQueueLocked(locked) {
  queueLocked = locked;
  lockBannerEl.classList.toggle("hidden", !locked);
  searchInput.disabled = locked;
  resultsEl.querySelectorAll("button").forEach((button) => {
    button.disabled = locked;
  });
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.error || `Request failed (${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function renderResults(tracks) {
  resultsEl.innerHTML = "";
  tracks.forEach((track) => {
    const li = document.createElement("li");
    li.className = "result-item";
    li.innerHTML = `
      <div>
        <strong>${escapeHtml(track.name)}</strong><br />
        <span class="meta">${escapeHtml(track.artist)}</span>
      </div>
      <button type="button" ${queueLocked ? "disabled" : ""}>Add</button>
    `;
    li.querySelector("button").addEventListener("click", () => addTrack(track));
    resultsEl.appendChild(li);
  });
}

function renderQueue(queue) {
  queueListEl.innerHTML = "";
  const visible = queue.filter((item) => item.status === "PENDING" || item.status === "PLAYING");
  queueCountEl.textContent = visible.length ? `(${visible.length})` : "";
  visible.forEach((item) => {
    const li = document.createElement("li");
    li.className = "queue-item";
    const statusLabel = item.status === "PLAYING" ? "Now playing" : item.addedBy;
    li.innerHTML = `
      <div>
        <strong>${escapeHtml(item.trackName)}</strong><br />
        <span class="meta">${escapeHtml(item.artistName)} · ${escapeHtml(statusLabel)}</span>
      </div>
    `;
    queueListEl.appendChild(li);
  });
}

async function addTrack(track) {
  if (queueLocked) {
    showError("Queue is locked by the host.");
    return;
  }
  const addedBy = prompt("Your name (optional):") || "Guest";
  try {
    await api("/api/queue", {
      method: "POST",
      body: JSON.stringify({
        spotifyUri: track.uri,
        trackName: track.name,
        artistName: track.artist,
        albumArtUrl: track.albumArtUrl,
        durationMs: track.durationMs,
        addedBy,
      }),
    });
    showToast(`Added ${track.name}`);
  } catch (error) {
    showError(error.message);
  }
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

searchInput.addEventListener("input", () => {
  clearTimeout(searchTimer);
  const query = searchInput.value.trim();
  if (query.length < 2) {
    resultsEl.innerHTML = "";
    return;
  }
  searchTimer = setTimeout(async () => {
    try {
      const tracks = await api(`/api/search?q=${encodeURIComponent(query)}`);
      renderResults(tracks);
    } catch (error) {
      showError("Could not reach Karaokey. Make sure you are on the same Wi-Fi.");
    }
  }, 300);
});

function connectWebSocket() {
  const protocol = location.protocol === "https:" ? "wss" : "ws";
  socket = new WebSocket(`${protocol}://${location.host}/ws`);
  socket.onmessage = (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === "queue") {
        renderQueue(message.data);
      } else if (message.type === "status") {
        setQueueLocked(Boolean(message.data.queueLocked));
      } else if (Array.isArray(message)) {
        renderQueue(message);
      }
    } catch (_) {
      // Ignore malformed frames.
    }
  };
  socket.onclose = () => setTimeout(connectWebSocket, 2000);
}

async function bootstrap() {
  try {
    const [queue, status] = await Promise.all([
      api("/api/queue"),
      api("/api/status"),
    ]);
    renderQueue(queue);
    setQueueLocked(Boolean(status.queueLocked));
    connectWebSocket();
  } catch (error) {
    showError("Can't reach Karaokey on this network. Scan the QR code on the TV again.");
  }
}

bootstrap();
