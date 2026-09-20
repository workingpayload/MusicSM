# MusicSM download page

A single static page plus three serverless functions. No framework, no build step and no npm
dependencies — it runs on Node's built-in `fetch`.

```
web/
├── index.html        the page
├── styles.css        palette, typography and glass lifted from ui/theme/Palette.kt
├── app.js            fills the page in from the API; pure progressive enhancement
├── icon.png          the launcher icon, used for the favicon and nav
├── icon-large.jpg    the hero artwork, cropped from the 1024px source icon
└── api/
    ├── _lib.js       shared GitHub + Redis helpers (the _ prefix keeps it off the router)
    ├── release.js    latest release + lifetime totals, edge-cached for 5 minutes
    ├── stats.js      the live page-click counter, never cached
    └── download.js   counts the click, then redirects to the APK on GitHub
```

## Deploying

1. Push this repo to GitHub.
2. In Vercel, **Add New → Project**, import the repo, and set **Root Directory** to `web`.
3. Leave the framework preset as **Other**. There is nothing to build.
4. Deploy.

That is the whole setup. With no environment variables at all the page works and shows GitHub's
own download count — the click counter simply stays hidden.

## Download counting

Two numbers are shown, because neither one is a superset of the other:

| Counter | Source | What it means |
| --- | --- | --- |
| **Downloads from GitHub** | GitHub's `download_count` | Every APK fetch across **every release ever published**, however it was reached. Ground truth for the file, and impossible to reset or inflate from here. Counted lifetime rather than per-version deliberately: scoping it to the newest release would reset the number to zero on every publish. |
| **Downloads from this page** | `INCR` in Redis | Clicks that actually started on this page, all time. |

`/api/download` increments the counter and then **302s to GitHub** rather than proxying the file.
Streaming ~6 MB through a function for every download would be slow and costly when GitHub's CDN
already does it well, and proxying would hide the download from GitHub's own counter.

`?id=` is matched against the assets of *every* release, so a link to an older version still
resolves to that exact build. An unrecognised id falls back to the newest APK rather than
erroring.

### Turning on the page counter

It needs any Redis with an Upstash-compatible REST API:

1. In your Vercel project: **Storage → Create Database → Upstash for Redis** (there is a free tier).
2. Connect it to the project. Vercel injects `KV_REST_API_URL` and `KV_REST_API_TOKEN`
   automatically — those are the only two names the code looks for.
3. Redeploy.

The counter card appears on its own once those variables exist. If Redis is ever unreachable the
functions swallow the error and still serve the download: a broken counter must never stop
somebody installing the app.

Keys used:

- `musicsm:downloads:total`
- `musicsm:downloads:asset:<file name>`

## Environment variables

All optional.

| Variable | Default | Why you might set it |
| --- | --- | --- |
| `GITHUB_REPO` | `workingpayload/MusicSM` | Point the page at a different repo. |
| `GITHUB_TOKEN` | — | Lifts GitHub's 60-requests-per-hour unauthenticated limit, which serverless functions share across a region. The 5-minute edge cache normally keeps usage far below it, so this is only worth adding if you see `502`s from `/api/release`. |
| `KV_REST_API_URL` | — | Set for you by the Upstash integration. |
| `KV_REST_API_TOKEN` | — | Set for you by the Upstash integration. |

## Publishing a new version

The page always reads `releases/latest`, so shipping an update is just:

1. Build the release APK.
2. Create a GitHub release tagged `vX.Y.Z` and attach the `.apk`.

Version, file size, release date and download count all update on their own within five minutes.
Attach more than one APK (per-ABI splits, for instance) and the extras appear as secondary chips
under the main button.

## Caching, and the one rule to remember

`index.html` is never cached, so a deploy is visible immediately. The assets it references are
cached differently on purpose:

| Asset | Policy | Why |
| --- | --- | --- |
| `index.html`, `/api/stats` | no cache | must always be current |
| `styles.css`, `app.js` | `no-cache` (revalidate every load) | a few KB, and a stale copy paired with fresh HTML breaks the page |
| `icon.png`, `icon-large.jpg` | `immutable`, one year | heavy, and rarely change |

**If you change an image, bump its `?v=` in `index.html`.** The images are served `immutable`, so
browsers will not re-request them otherwise — the query string is what makes the URL new.

This matters because the two are not independent. The HTML and the CSS/JS are written against
each other, and an earlier version of this page cached all three for an hour: returning visitors
got new HTML with hour-old CSS and JS, which stretched the hero image and left the download
counter showing a placeholder dash. `no-cache` on the two text assets removes that whole class of
bug for the cost of one 304 per visit.

## Local development

```bash
npm i -g vercel
cd web
vercel dev
```

`vercel dev` is required rather than any static server, because `/api/*` needs the functions
runtime.
