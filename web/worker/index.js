// castbay.weenas.com's Worker: the website is static assets (dist/); only /api/* runs here.
//
// POST /api/reports: a problem report the person chose to send from CastBay (About →
// Diagnostics → Upload): plain text, already stripped on the device of media titles, links
// and names. It is kept in R2 under its ID, which the person passes on with their problem
// report; there is no way to read reports back from here. The bucket deletes them after 90
// days (a lifecycle rule). The sender's address is used only by the rate limiter, not kept.

const MAX_BYTES = 512 * 1024;
// Crockford's base 32: no I, L, O or U, so an ID read off a TV is not misread.
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/api/reports') {
      if (request.method !== 'POST') return json({ error: 'method' }, 405, { Allow: 'POST' });
      return receiveReport(request, env);
    }
    return json({ error: 'not found' }, 404);
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
