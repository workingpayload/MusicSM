import {
  REPO,
  apkAssets,
  desktopDownloads,
  fetchReleases,
  latestStable,
  reconcileGithubTotal,
  totalApkDownloads,
} from './_lib.js';

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
    // DOWNLOADS_OVERRIDE (env) forces an exact number to display, so the desktop repo isn't read.
    const override = process.env.DOWNLOADS_OVERRIDE;
    const overridden = override != null && override !== '';
    const [releases, desktop] = await Promise.all([
      fetchReleases(),
      overridden ? null : desktopDownloads(),
    ]);
    // A desktop count that couldn't be read leaves the total short: don't hold that for long.
    res.setHeader(
      'Cache-Control',
      !overridden && desktop === null
        ? 's-maxage=60, stale-while-revalidate=300'
        : 's-maxage=300, stale-while-revalidate=86400',
    );

    // Counted across every release, including ones older than the current build, so the figure
    // is "how many people have installed MusicSM" rather than "how many took the newest build" —
    // and across both apps: the Android APKs plus MusicSM Desktop's installers. Each app's total
    // is reconciled through its own baseline (env offset + optional Redis) so it never drops on
    // a reset.
    let githubDownloads;
    let downloads = null;
    if (overridden) {
      githubDownloads = Math.max(0, Number(override) || 0);
    } else {
      const android = await reconcileGithubTotal(totalApkDownloads(releases));
      githubDownloads = android + (desktop ?? 0);
      downloads = { android, desktop };
    }
    const release = latestStable(releases);

    if (!release) {
      return res.status(200).json({ repo: REPO, release: null, githubDownloads, downloads });
    }

    const assets = apkAssets(release);
    return res.status(200).json({
      repo: REPO,
      githubDownloads,
      downloads,
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
