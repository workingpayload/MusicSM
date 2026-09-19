import { kvConfigured, readTotal } from './_lib.js';

/**
 * The live page-click counter.
 *
 * Kept separate from `/api/release` precisely so it can be uncached: this is the one number that
 * must be correct the instant after somebody presses download. It is a single Redis GET, so it
 * is cheap enough to serve uncached.
 */
export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  return res.status(200).json({
    enabled: kvConfigured,
    page: await readTotal(),
  });
}
