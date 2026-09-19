import { REPO, apkAssets, fetchReleases, latestStable, totalApkDownloads } from './_lib.js';

/**
 * Latest release metadata plus lifetime download totals.
 *
 * Cached at the edge for five minutes. That is what keeps a busy day from burning through
 * GitHub's unauthenticated rate limit, and it is why the live click counter lives in
 * `/api/stats` instead of here — mixing a fast-moving number into a cached response would
 * either stale the number or defeat the cache.
 */
export default async function handler(req, res) {
  try {
    const releases = await fetchReleases();
    res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate=86400');

    // Counted across every release, including ones older than the current build, so the figure
    // is "how many people have installed MusicSM" rather than "how many took the newest build".
    const githubDownloads = totalApkDownloads(releases);
    const release = latestStable(releases);

    if (!release) {
      return res.status(200).json({ repo: REPO, release: null, githubDownloads });
    }

    const assets = apkAssets(release);
    return res.status(200).json({
      repo: REPO,
      githubDownloads,
      release: {
        version: release.tag_name,
        name: release.name || release.tag_name,
        publishedAt: release.published_at,
        htmlUrl: release.html_url,
        assets: assets.map((a) => ({
          id: a.id,
          name: a.name,
          size: a.size,
          downloads: a.download_count || 0,
          href: `/api/download?id=${a.id}`,
        })),
      },
    });
  } catch (err) {
    res.setHeader('Cache-Control', 'no-store');
    return res.status(502).json({ error: String(err?.message || err) });
  }
}
