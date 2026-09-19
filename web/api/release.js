import { REPO, apkAssets, fetchLatestRelease } from './_lib.js';

/**
 * Latest release metadata for the page.
 *
 * Cached at the edge for five minutes. That is what keeps a busy day from burning through
 * GitHub's unauthenticated rate limit, and it is why the live click counter lives in
 * `/api/stats` instead of here — mixing a fast-moving number into a cached response would
 * either stale the number or defeat the cache.
 */
export default async function handler(req, res) {
  try {
    const release = await fetchLatestRelease();
    res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate=86400');

    if (!release) {
      return res.status(200).json({ repo: REPO, release: null });
    }

    const assets = apkAssets(release);
    return res.status(200).json({
      repo: REPO,
      release: {
        version: release.tag_name,
        name: release.name || release.tag_name,
        publishedAt: release.published_at,
        htmlUrl: release.html_url,
        githubDownloads: assets.reduce((total, a) => total + (a.download_count || 0), 0),
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
