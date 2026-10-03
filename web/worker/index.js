// castbay.weenas.com's Worker: the website is static assets (dist/); only /api/* runs here.
//
// POST /api/reports: a problem report the person chose to send from CastBay (About →
// Diagnostics → Upload): plain text, already stripped on the device of media titles, links
// and names. It is kept in R2 under its ID, which the person passes on with their problem
// report; there is no way to read reports back from here. The bucket deletes them after 90
// days (a lifecycle rule). The sender's address is used only by the rate limiter, not kept.
//
// POST /api/stats: a day's anonymous usage summary from an app whose owner turned statistics
// on (one row per installation and day in D1; only whitelisted fields and counters are kept,
// anything else is refused). POST /api/stats/delete {id}: deletes an installation's rows, sent
// when statistics are turned off. A daily cron deletes rows older than a year.

const MAX_BYTES = 512 * 1024;
// Crockford's base 32: no I, L, O or U, so an ID read off a TV is not misread.
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const routes = {
      '/api/reports': receiveReport,
      '/api/stats': receiveStats,
      '/api/stats/delete': deleteStats,
    };
    const route = routes[url.pathname];
    if (!route) return json({ error: 'not found' }, 404);
    if (request.method !== 'POST') return json({ error: 'method' }, 405, { Allow: 'POST' });
    return route(request, env);
  },

  async scheduled(event, env) {
    await env.STATS.prepare("DELETE FROM daily WHERE day < date('now', '-365 days')").run();
  },
};

async function receiveReport(request, env) {
  const version = request.headers.get('X-CastBay-Version') ?? '';
  // 1.1.0, or a test build's 1.1.0-dev+6228385.
  if (!/^\d+(\.\d+){1,3}(-[0-9A-Za-z.]+)?(\+[0-9A-Za-z.]+)?$/.test(version) || version.length > 40) {
    return json({ error: 'version' }, 400);
  }
  const kind = request.headers.get('X-CastBay-Report') === 'crash' ? 'crash' : 'manual';
  if (env.UPLOAD_LIMIT) {
    const { success } = await env.UPLOAD_LIMIT.limit({ key: request.headers.get('CF-Connecting-IP') ?? 'unknown' });
    if (!success) return json({ error: 'too many' }, 429);
  }
  const length = Number(request.headers.get('Content-Length') ?? 0);
  if (length > MAX_BYTES) return json({ error: 'too large' }, 413);
  const body = await request.arrayBuffer();
  if (body.byteLength === 0) return json({ error: 'empty' }, 400);
  if (body.byteLength > MAX_BYTES) return json({ error: 'too large' }, 413);

  for (let attempt = 0; attempt < 5; attempt++) {
    const id = 'CB-' + randomId(6);
    const key = `reports/${id}.txt`;
    // onlyIf: written only if no report has this ID yet.
    const stored = await env.REPORTS.put(key, body, {
      onlyIf: { etagDoesNotMatch: '*' },
      httpMetadata: { contentType: 'text/plain; charset=utf-8' },
      customMetadata: { version, kind, received: new Date().toISOString() },
    });
    if (stored) return json({ id }, 201);
  }
  return json({ error: 'busy' }, 503);
}

function randomId(length) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (b) => ALPHABET[b % 32]).join('');
}

function json(value, status, headers = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers },
  });
}

// Every counter the app may send (UsageStats.kt); anything else refuses the whole summary.
const COUNTER = /^(cast\.(airplay|dlna)\.(mirror|music|video)|length\.(under1m|1to10m|over10m)|mirror\.(2160p|1440p|1080p|720p|smaller)\.(h264|h265)|fail\.(receiver|decoder|video))$/;
const SETTINGS = ['access', 'resolution', 'codec', 'dlna', 'lyrics', 'stats', 'picture', 'language', 'updates', 'errorReports'];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const MAX_STATS_BYTES = 8 * 1024;

async function receiveStats(request, env) {
  if (env.STATS_LIMIT) {
    const { success } = await env.STATS_LIMIT.limit({ key: request.headers.get('CF-Connecting-IP') ?? 'unknown' });
    if (!success) return json({ error: 'too many' }, 429);
  }
  const body = await readJson(request);
  const row = body && validStats(body);
  if (!row) return json({ error: 'invalid' }, 400);
  await env.STATS.prepare(
    `INSERT OR REPLACE INTO daily (id, day, app, android, sdk, maker, model, screen, device, touch, lang, counts, settings, received)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(row.id, row.day, row.app, row.android, row.sdk, row.maker, row.model, row.screen, row.device,
    row.touch ? 1 : 0, row.lang, JSON.stringify(row.counts), JSON.stringify(row.settings), new Date().toISOString()).run();
  return json({ ok: true }, 200);
}

async function deleteStats(request, env) {
  const body = await readJson(request);
  if (!body || typeof body.id !== 'string' || !UUID.test(body.id)) return json({ error: 'invalid' }, 400);
  await env.STATS.prepare('DELETE FROM daily WHERE id = ?').bind(body.id).run();
  return json({ ok: true }, 200);
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > MAX_STATS_BYTES) return null;
  try { return JSON.parse(text); } catch { return null; }
}

/** The summary with only known fields, each checked; null if anything is off. */
function validStats(b) {
  const text = (v, max, pattern) => typeof v === 'string' && v.length > 0 && v.length <= max && (!pattern || pattern.test(v));
  const now = Date.now();
  const day = Date.parse(b.day + 'T00:00:00Z');
  if (!text(b.id, 36, UUID) || !text(b.day, 10, /^\d{4}-\d{2}-\d{2}$/) || !(day > now - 9 * 86400000 && day < now + 2 * 86400000)) return null;
  if (!text(b.app, 40, /^\d+(\.\d+){1,3}(-[0-9A-Za-z.]+)?(\+[0-9A-Za-z.]+)?$/) || !text(b.android, 10, /^[0-9.]+$/)) return null;
  if (!Number.isInteger(b.sdk) || b.sdk < 21 || b.sdk > 99) return null;
  if (!text(b.maker, 40, /^[\x20-\x7e]+$/) || !text(b.model, 40, /^[\x20-\x7e]+$/)) return null;
  if (!['2160p', '1440p', '1080p', '720p', 'smaller'].includes(b.screen) || !['tv', 'car', 'other'].includes(b.device)) return null;
  if (typeof b.touch !== 'boolean' || !text(b.lang, 8, /^[a-z]{2,3}$/)) return null;
  const counts = {};
  if (!b.counts || typeof b.counts !== 'object' || Object.keys(b.counts).length > 40) return null;
  for (const [key, value] of Object.entries(b.counts)) {
    if (!COUNTER.test(key) || !Number.isInteger(value) || value < 1 || value > 100000) return null;
    counts[key] = value;
  }
  const settings = {};
  if (!b.settings || typeof b.settings !== 'object') return null;
  for (const [key, value] of Object.entries(b.settings)) {
    if (!SETTINGS.includes(key)) return null;
    if (typeof value === 'boolean' || (typeof value === 'string' && /^[A-Za-z0-9_. ]{1,20}$/.test(value))) settings[key] = value;
    else return null;
  }
  return { id: b.id, day: b.day, app: b.app, android: b.android, sdk: b.sdk, maker: b.maker, model: b.model,
    screen: b.screen, device: b.device, touch: b.touch, lang: b.lang, counts, settings };
}
