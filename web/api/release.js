import {
  REPO,
  apkAssets,
  desktopReleasesUrl,
  fetchReleases,
  latestStable,
  loadDesktop,
  newestPlatformAsset,
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
    const [releases, desktop] = await Promise.all([fetchReleases(), loadDesktop()]);
    // An unreadable desktop repo leaves the total short and the desktop buttons undescribed:
    // don't hold that for long.
    res.setHeader(
      'Cache-Control',
      desktop.downloads === null
        ? 's-maxage=60, stale-while-revalidate=300'
        : 's-maxage=300, stale-while-revalidate=86400',
    );

    // Counted across every release, including ones older than the current build, so the figure
    // is "how many people have installed MusicSM" rather than "how many took the newest build" —
    // and across both apps: the Android APKs plus MusicSM Desktop's installers. Each app's total
    // is reconciled through its own baseline (env offset + optional Redis) so it never drops on
    // a reset. DOWNLOADS_OVERRIDE (env) forces an exact number to display instead.
    const override = process.env.DOWNLOADS_OVERRIDE;
    let githubDownloads;
    let downloads = null;
    if (override != null && override !== '') {
      githubDownloads = Math.max(0, Number(override) || 0);
    } else {
      const android = await reconcileGithubTotal(totalApkDownloads(releases));
      githubDownloads = android + (desktop.downloads ?? 0);
      downloads = { android, desktop: desktop.downloads };
    }
    const common = {
      repo: REPO,
      githubDownloads,
      downloads,
      // Left out (not null) when the desktop repo couldn't be read, so the page can tell "nothing
      // published yet" from "don't know right now".
      ...(desktop.downloads === null ? {} : { desktop: describeDesktop(desktop.releases) }),
      desktopReleasesUrl: desktopReleasesUrl(),
    };
    const release = latestStable(releases);

    if (!release) {
      return res.status(200).json({ ...common, release: null });
    }

    const assets = apkAssets(release);
    return res.status(200).json({
      ...common,
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

/** The newest desktop installer for each platform, or `null` before the first one is published. */
function describeDesktop(releases) {
  const platforms = {};
  for (const platform of ['windows', 'mac']) {
    const found = newestPlatformAsset(releases, platform);
    platforms[platform] = found && {
      version: found.release.tag_name,
      publishedAt: found.release.published_at,
      htmlUrl: found.release.html_url,
      name: found.asset.name,
      size: found.asset.size,
      href: `/api/download?desktop=${platform}&id=${found.asset.id}`,
    };
  }
  return platforms.windows || platforms.mac ? platforms : null;
}
