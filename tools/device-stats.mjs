#!/usr/bin/env node
// Per-model summary of the opt-in usage stats, as a draft for the website's compatibility list.
// Read-only: it queries the castbay-stats D1 database through wrangler (log in first) and prints
// a table; with --json FILE it also writes the draft, without any device ids, for review.
//
//   node tools/device-stats.mjs [--days 90] [--min-devices 5] [--json draft.json]

import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const args = process.argv.slice(2);
const option = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
};
const days = Number(option('--days', 90));
const minDevices = Number(option('--min-devices', 5));
const jsonOut = option('--json', null);
if (!Number.isInteger(days) || days < 1 || !Number.isInteger(minDevices) || minDevices < 1) {
  console.error('--days and --min-devices must be positive integers');
  process.exit(1);
}

const web = join(dirname(fileURLToPath(import.meta.url)), '..', 'web');
const query = (sql) => {
  const out = execFileSync('npx', ['wrangler', 'd1', 'execute', 'castbay-stats', '--remote', '--json', '--command', sql],
    { cwd: web, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return JSON.parse(out)[0].results;
};

const since = `date('now', '-${days} days')`;
// Each device once, by its latest day in the window, for what describes it.
const latest = `SELECT d.* FROM daily d JOIN (SELECT id, max(day) AS day FROM daily WHERE day >= ${since} GROUP BY id) l
  ON d.id = l.id AND d.day = l.day`;
const models = query(`SELECT maker, model, device, count(*) AS devices, max(day) AS last,
    group_concat(DISTINCT android) AS android, group_concat(DISTINCT screen) AS screen, group_concat(DISTINCT app) AS app
  FROM (${latest}) GROUP BY maker, model, device ORDER BY devices DESC, maker, model`);
const counts = query(`SELECT r.maker AS maker, r.model AS model, j.key AS key, sum(j.value) AS n, count(DISTINCT r.id) AS devices
  FROM (SELECT * FROM daily WHERE day >= ${since}) r, json_each(r.counts) j GROUP BY 1, 2, 3`);

const byModel = new Map();
for (const row of counts) {
  const entry = byModel.get(`${row.maker}|${row.model}`) ?? { casts: 0, fails: 0, castDevices: new Set(), failures: {}, mirror: {} };
  if (row.key.startsWith('cast.')) entry.casts += row.n;
  if (row.key.startsWith('fail.')) {
    entry.fails += row.n;
    entry.failures[row.key.slice(5)] = row.n;
  }
  if (row.key.startsWith('mirror.')) entry.mirror[row.key.slice(7)] = row.n;
  byModel.set(`${row.maker}|${row.model}`, entry);
}

// Without enough devices nothing is said about a model: a few are easy to tell apart.
const draft = models.map((m) => {
  const c = byModel.get(`${m.maker}|${m.model}`) ?? { casts: 0, fails: 0, failures: {}, mirror: {} };
  const failRate = c.casts > 0 ? c.fails / c.casts : null;
  let suggestion = 'not enough devices';
  if (m.devices >= minDevices) {
    if (c.casts === 0) suggestion = 'no casts yet';
    else if (failRate > 0.2) suggestion = 'problems, look into it';
    else suggestion = 'works for users';
  }
  return { maker: m.maker, model: m.model, device: m.device, devices: m.devices, last: m.last, android: m.android,
    screen: m.screen, app: m.app, casts: c.casts, fails: c.fails, failRate, failures: c.failures, mirror: c.mirror, suggestion };
});

console.log(`window: last ${days} days, listed at ${minDevices}+ devices\n`);
const pad = (v, n) => String(v).padEnd(n);
console.log(pad('model', 34), pad('type', 6), pad('devs', 5), pad('casts', 6), pad('fails', 6), pad('android', 9), pad('last', 11), 'suggestion');
for (const d of draft) {
  console.log(pad(`${d.maker} ${d.model}`, 34), pad(d.device, 6), pad(d.devices, 5), pad(d.casts, 6), pad(d.fails, 6),
    pad(d.android, 9), pad(d.last, 11), d.suggestion);
}
if (jsonOut) {
  writeFileSync(jsonOut, JSON.stringify({ generated: new Date().toISOString().slice(0, 10), days, minDevices, models: draft }, null, 2) + '\n');
  console.log(`\nwrote ${jsonOut}`);
}
