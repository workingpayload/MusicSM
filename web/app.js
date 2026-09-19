/**
 * Fills the page in from the two endpoints and keeps the download counters honest.
 *
 * Everything here is progressive enhancement. The markup ships with a working download link and
 * sensible placeholder text, so a failed fetch or a blocked script degrades to "the button still
 * downloads the app" rather than a broken page.
 */

const RELEASES_URL = "https://github.com/workingpayload/MusicSM/releases";

const el = (id) => document.getElementById(id);
const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const numberFormat = new Intl.NumberFormat();

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return null;
  const mb = bytes / (1024 * 1024);
  return mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(1)} MB`;
}

function formatDate(iso) {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
}

/**
 * Counts up to [value], easing out.
 *
 * The animation is the only reason these numbers draw attention at all, but it is also the sort
 * of thing that makes a page feel cheap if it runs when somebody has asked for less motion — so
 * it snaps instead.
 */
function setCount(node, value) {
  if (!node || !Number.isFinite(value)) return;

  const from = Number(node.dataset.count || 0);
  node.dataset.count = String(value);

  if (prefersReducedMotion || from === value) {
    node.textContent = numberFormat.format(value);
    return;
  }

  const duration = 900;
  const start = performance.now();

  const tick = (now) => {
    const t = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - t, 3);
    node.textContent = numberFormat.format(Math.round(from + (value - from) * eased));
    if (t < 1) requestAnimationFrame(tick);
  };

  requestAnimationFrame(tick);
}

/** Bumps a counter by one without waiting for the server, so the click feels acknowledged. */
function bump(node) {
  if (!node) return;
  setCount(node, Number(node.dataset.count || 0) + 1);
}

function showNoRelease(message) {
  el("release-pill").textContent = message;
  el("download-meta").textContent =
    "The download will appear here as soon as a build is published.";

  const button = el("download-btn");
  // Still a live link — it just points at the releases page instead of a file, so somebody who
  // arrived expecting a download has somewhere useful to go.
  button.classList.add("is-muted");
  button.href = RELEASES_URL;
  button.target = "_blank";
  button.rel = "noopener noreferrer";
  el("download-label").textContent = "Watch for releases";
}

function renderRelease(release) {
  const primary = release.assets[0];

  el("release-pill").textContent = `${release.version} · free and open source`;
  el("release-link").href = release.htmlUrl || RELEASES_URL;
  el("stat-version").textContent = release.version;
  el("footer-version").textContent = release.version;

  const released = formatDate(release.publishedAt);
  el("stat-released").textContent = released ? `Released ${released}` : "";
  setCount(el("stat-github"), release.githubDownloads);

  if (!primary) {
    showNoRelease(`${release.version} · no APK attached`);
    return;
  }

  el("download-btn").href = primary.href;
  el("download-btn").classList.remove("is-muted");
  el("download-label").textContent = "Download for Android";

  const size = formatBytes(primary.size);
  el("download-meta").textContent = [
    primary.name,
    size,
    released && `released ${released}`,
    "Android 7.0+",
  ]
    .filter(Boolean)
    .join(" · ");

  // More than one build (per-ABI splits, say) — offer the rest quietly rather than making the
  // visitor choose before they can do the obvious thing.
  const alternatives = release.assets.slice(1);
  const list = el("alt-builds");
  list.innerHTML = "";
  for (const asset of alternatives) {
    const assetSize = formatBytes(asset.size);
    const item = document.createElement("li");
    const link = document.createElement("a");
    link.href = asset.href;
    link.textContent = assetSize ? `${asset.name} · ${assetSize}` : asset.name;
    item.appendChild(link);
    list.appendChild(item);
  }
}

async function loadRelease() {
  try {
    const res = await fetch("/api/release");
    if (!res.ok) throw new Error(`release endpoint returned ${res.status}`);

    const data = await res.json();
    if (!data.release) {
      showNoRelease("No build published yet");
      return;
    }
    renderRelease(data.release);
  } catch {
    // The static link already points at /api/download, which resolves the asset server-side, so
    // the button keeps working even though we could not describe the build.
    el("release-pill").textContent = "Latest build";
    el("download-meta").textContent =
      "Couldn't load release details. The download button still works.";
  }
}

async function loadStats() {
  try {
    const res = await fetch("/api/stats");
    if (!res.ok) return;

    const data = await res.json();
    if (!data.enabled || data.page === null) return;

    el("stat-page-card").hidden = false;
    setCount(el("stat-page"), data.page);
  } catch {
    /* No counter configured, or it is briefly unreachable — the card just stays hidden. */
  }
}

/**
 * Counting the click.
 *
 * The increment happens server-side inside `/api/download`, which is what makes it trustworthy.
 * This only does two things on top: paint the optimistic +1 immediately, and re-read the real
 * value a moment later so the displayed number settles on the truth.
 */
function watchDownloads() {
  el("download-btn").addEventListener("click", (event) => {
    // While there is no APK the button is a link to the releases page, not a download.
    if (event.currentTarget.classList.contains("is-muted")) return;
    onDownload();
  });
  el("alt-builds").addEventListener("click", (event) => {
    if (event.target.closest("a")) onDownload();
  });
}

function onDownload() {
  bump(el("stat-github"));
  if (!el("stat-page-card").hidden) bump(el("stat-page"));
  // GitHub's own count lags by a minute or so, so only the page counter is worth re-reading.
  setTimeout(loadStats, 2500);
}

loadRelease();
loadStats();
watchDownloads();
