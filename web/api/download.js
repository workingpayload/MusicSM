import {
  DESKTOP_REPO,
  REPO,
  apkAssets,
  desktopReleasesUrl,
  fetchReleases,
  isDesktopPlatform,
  latestStable,
  newestPlatformAsset,
  newestWearAsset,
  phoneApks,
  platformAssets,
  recordDownload,
} from './_lib.js';

/**
 * GitHub only ever serves release assets from these hosts.
 *
 * Belt and braces: the redirect target already comes from a release we just read back from the
 * API rather than from the caller, but pinning the host means even a compromised or unexpected
 * API response cannot turn this endpoint into an open redirect.
 */
const ALLOWED_HOSTS = new Set([
  'github.com',
  'objects.githubusercontent.com',
  'release-assets.githubusercontent.com',
]);

/**
 * Counts a download, then hands the browser straight to the file.
 *
 * A redirect rather than a proxy: streaming ~19 MB through a serverless function for every
 * download would be slow, costly and pointless when GitHub's CDN is already doing it well — and
 * proxying would also hide the download from GitHub's own counter.
 *
 * `?id=` selects a specific asset and is matched against *every* release, so an old link or a
 * bookmark for a previous version still resolves to the build it asked for. Anything
 * unrecognised falls back to the newest phone APK rather than erroring.
 *
 * `?wear=1` gives the newest Wear OS APK instead (the watch app ships in the same releases).
 *
 * `?desktop=windows|mac` does the same for MusicSM Desktop's installers, from its own repo.
 */
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  const platform = String(req.query?.desktop ?? '');
  if (platform) return desktopDownload(req, res, platform);

  let releases;
  try {
    releases = await fetchReleases();
  } catch {
    return res.status(502).send('Could not reach GitHub. Please try the release page instead.');
  }

  const requested = String(req.query?.id ?? '');
  const everyApk = releases.flatMap((release) => apkAssets(release));
  const pinned = everyApk.find((a) => String(a.id) === requested);

  if (!pinned && req.query?.wear) {
    const wear = newestWearAsset(releases)?.asset;
    return wear ? countAndRedirect(res, wear) : redirect(res, `https://github.com/${REPO}/releases`);
  }

  const asset = pinned ?? phoneApks(latestStable(releases))[0];
  if (!asset) {
    return res.status(404).send('No APK has been published yet.');
  }

  return countAndRedirect(res, asset);
}

/**
 * A desktop installer: `?id=` pins one (from any release), otherwise the newest stable one for the
 * platform. With nothing to hand out — no desktop release yet, or GitHub unreachable — the visitor
 * lands on the desktop releases page instead, which is where the file would be anyway.
 */
async function desktopDownload(req, res, platform) {
  const releasesPage = desktopReleasesUrl();
  if (!isDesktopPlatform(platform) || !releasesPage) {
    return res.status(404).send('No desktop build for that platform.');
  }

  let releases;
  try {
    releases = await fetchReleases(DESKTOP_REPO);
  } catch {
    return redirect(res, releasesPage);
  }

  const requested = String(req.query?.id ?? '');
  const pinned = releases
    .flatMap((release) => platformAssets(release, platform))
    .find((a) => String(a.id) === requested);
  const asset = pinned ?? newestPlatformAsset(releases, platform)?.asset;
  if (!asset) return redirect(res, releasesPage);

  return countAndRedirect(res, asset);
}

async function countAndRedirect(res, asset) {
  let target;
  try {
    target = new URL(asset.browser_download_url);
  } catch {
    return res.status(502).send('That release asset has no usable download URL.');
  }
  if (!ALLOWED_HOSTS.has(target.hostname)) {
    return res.status(502).send('Unexpected download host; refusing to redirect.');
  }

  await recordDownload(asset.name);

  return redirect(res, target.toString());
}

function redirect(res, location) {
  res.setHeader('Location', location);
  return res.status(302).end();
}
