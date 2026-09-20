/**
 * Shared helpers for the three endpoints.
 *
 * Files prefixed with `_` are not routed by Vercel, so this stays a private module.
 *
 * Two counters are deliberately kept side by side:
 *
 * - **GitHub's own `download_count`** is the ground truth for the file. It counts every fetch of
 *   the asset however it was reached, so it can never be reset or inflated by this site.
 * - **The page counter** in Redis counts clicks that actually went through this landing page.
 *
 * Neither is a superset of the other, which is why the page shows both.
 */

const KV_URL = process.env.KV_REST_API_URL;
const KV_TOKEN = process.env.KV_REST_API_TOKEN;

export const REPO = process.env.GITHUB_REPO || 'workingpayload/MusicSM';
export const COUNTER_KEY = 'musicsm:downloads:total';

// GitHub resets an asset's `download_count` to 0 whenever the APK is deleted and re-uploaded, or a
// release is recreated — so a naive sum across releases drops on every such publish. These two keys
// let us keep a durable lifetime figure that never goes backwards: BASELINE is the running sum of
// every GitHub total that was later wiped, and LAST_SEEN is the most recent GitHub total observed.
export const BASELINE_KEY = 'musicsm:downloads:baseline';
export const LAST_SEEN_KEY = 'musicsm:downloads:githubseen';

/**
 * Whether a Redis store is wired up.
 *
 * The site is designed to work with no environment variables at all — without a store the page
 * simply shows GitHub's count and hides its own. Deploying should never be blocked on setting
 * up a database.
 */
export const kvConfigured = Boolean(KV_URL && KV_TOKEN);

async function kvFetch(path, body) {
  const res = await fetch(`${KV_URL}${path}`, {
    method: body ? 'POST' : 'GET',
    headers: {
      Authorization: `Bearer ${KV_TOKEN}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`KV responded ${res.status}`);
  return res.json();
}

/** Page-driven download total, or `null` when there is no store or it is unreachable. */
export async function readTotal() {
  if (!kvConfigured) return null;
  try {
    const { result } = await kvFetch(`/get/${encodeURIComponent(COUNTER_KEY)}`);
    return Number(result ?? 0);
  } catch {
    return null;
  }
}

/**
 * Bumps the running total and a per-asset tally in one round trip.
 *
 * Never throws: a broken counter must not stop somebody downloading the app.
 */
export async function recordDownload(assetName) {
  if (!kvConfigured) return null;
  try {
    const out = await kvFetch('/pipeline', [
      ['INCR', COUNTER_KEY],
      ['INCR', `musicsm:downloads:asset:${assetName}`],
    ]);
    return Number(out?.[0]?.result ?? 0);
  } catch {
    return null;
  }
}

/**
 * Turns GitHub's (resettable) summed download count into the figure the page shows.
 *
 * A fixed offset from the `DOWNLOADS_BASELINE` env var is always added, so the number can be topped
 * up with **no database at all** — set it in the Vercel dashboard and redeploy. On top of that, when
 * a Redis store is configured the count also survives GitHub asset resets: if GitHub's total drops
 * below what we last saw, the old value is folded into a persistent baseline. Any error or missing
 * store degrades gracefully to `rawGitHubTotal + envOffset` — a counter must never break the page.
 */
export async function reconcileGithubTotal(currentTotal) {
  // Manual bump that needs no store: just an env var. e.g. DOWNLOADS_BASELINE=133 shows 133 more.
  const envOffset = Number(process.env.DOWNLOADS_BASELINE || 0) || 0;
  if (!kvConfigured) return currentTotal + envOffset;
  try {
    const out = await kvFetch('/pipeline', [
      ['GET', BASELINE_KEY],
      ['GET', LAST_SEEN_KEY],
    ]);
    const baseline = Number(out?.[0]?.result ?? 0) || 0;
    const lastSeen = Number(out?.[1]?.result ?? 0) || 0;

    const writes = [];
    let newBaseline = baseline;
    if (currentTotal < lastSeen) {
      // A reset happened since last time: preserve the count that GitHub just threw away.
      newBaseline = baseline + lastSeen;
      writes.push(['SET', BASELINE_KEY, String(newBaseline)]);
    }
    if (currentTotal !== lastSeen) {
      writes.push(['SET', LAST_SEEN_KEY, String(currentTotal)]);
    }
    if (writes.length) await kvFetch('/pipeline', writes);

    return newBaseline + currentTotal + envOffset;
  } catch {
    return currentTotal + envOffset;
  }
}

/**
 * Every release, newest first.
 *
 * One call rather than hitting `releases/latest` separately: the page needs the newest build for
 * the download button *and* the download totals from every release ever published, and both come
 * out of this single response.
 *
 * Capped at 100 releases, which is GitHub's maximum page size. Beyond that the totals would
 * silently undercount and this would need real pagination.
 */
export async function fetchReleases() {
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'musicsm-landing-page',
  };
  // Optional: lifts the unauthenticated 60-requests-per-hour limit, which serverless functions
  // share across a whole region. The edge cache on /api/release normally keeps us well under it.
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;

  const res = await fetch(
    `https://api.github.com/repos/${REPO}/releases?per_page=100`,
    { headers },
  );
  if (res.status === 404) return [];
  if (!res.ok) throw new Error(`GitHub responded ${res.status}`);

  const body = await res.json();
  return Array.isArray(body) ? body : [];
}

/**
 * The build to actually offer people.
 *
 * Mirrors what `releases/latest` would have returned — drafts and pre-releases are skipped, so a
 * release candidate sitting at the top of the list never becomes the download everyone gets.
 */
export function latestStable(releases) {
  return releases.find((r) => !r.draft && !r.prerelease) ?? null;
}

const APK = /\.apk$/i;

/** Just the installable builds — release notes, mapping files and checksums are not downloads. */
export function apkAssets(release) {
  return (release?.assets ?? []).filter((a) => APK.test(a.name));
}

/**
 * Lifetime APK downloads across every release.
 *
 * Deliberately not scoped to the current version: the interesting number is how many people have
 * installed MusicSM, and counting only the newest release would reset that to zero on every
 * single publish.
 */
export function totalApkDownloads(releases) {
  return releases.reduce(
    (total, release) =>
      total + apkAssets(release).reduce((n, a) => n + (a.download_count || 0), 0),
    0,
  );
}
