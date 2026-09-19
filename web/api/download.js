import { apkAssets, fetchReleases, latestStable, recordDownload } from './_lib.js';

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
 * unrecognised falls back to the newest APK rather than erroring.
 */
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');

  let releases;
  try {
    releases = await fetchReleases();
  } catch {
    return res.status(502).send('Could not reach GitHub. Please try the release page instead.');
  }

  const requested = String(req.query?.id ?? '');
  const everyApk = releases.flatMap((release) => apkAssets(release));
  const newest = apkAssets(latestStable(releases));

  const asset = everyApk.find((a) => String(a.id) === requested) ?? newest[0];
  if (!asset) {
    return res.status(404).send('No APK has been published yet.');
  }

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

  res.setHeader('Location', target.toString());
  return res.status(302).end();
}
