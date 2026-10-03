// Puts the latest release's APK on the site itself, at /CastBay.apk, with /apk (short enough
// to type on a TV remote) pointing to it: downloads from GitHub are often slow or blocked in
// China. The release workflow rebuilds the site after each release, so it stays current.
// If GitHub can't be reached while building, /apk points to GitHub's copy instead.
// /latest.json tells the app's updater about it: the version, the SHA-256, where to get it, and
// the latest versions' release notes (from src/content/releases), which About shows before
// updating.
import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';

const REPO = 'weenas/castbay';
const GITHUB_APK = `https://github.com/${REPO}/releases/latest/download/CastBay.apk`;
const RELEASES = new URL('../src/content/releases/', import.meta.url);
// Enough to cover anyone a few updates behind; older notes are on the changelog page.
const NOTES_VERSIONS = 10;

/** "1.0.9" before "1.0.10". */
const compareVersions = (a, b) => {
  const x = a.split('.').map(Number);
  const y = b.split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) - (y[i] ?? 0);
  }
  return 0;
};

/** A changelog entry's bullet points as plain text, for the TV: no Markdown marks or links. */
const plainItems = (markdown) =>
  markdown
    .split('\n')
    .filter((line) => line.startsWith('- '))
    .map((line) =>
      line
        .slice(2)
        .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
        .replace(/\*\*([^*]+)\*\*/g, '$1')
        .replace(/`([^`]+)`/g, '$1')
        .trim()
    );

/** The newest versions' notes, newest first: [{ version, date, en: [...], zh: [...] }]. */
async function releaseNotes() {
  const read = async (lang) => {
    const notes = {};
    for (const file of await readdir(new URL(`${lang}/`, RELEASES))) {
      if (!file.endsWith('.md')) continue;
      const text = await readFile(new URL(`${lang}/${file}`, RELEASES), 'utf8');
      const [, front = '', body = ''] = text.split(/^---$/m);
      const version = /version:\s*(\S+)/.exec(front)?.[1];
      const date = /date:\s*(\S+)/.exec(front)?.[1] ?? '';
      if (version) notes[version] = { date, items: plainItems(body) };
    }
    return notes;
  };
  const [en, zh] = await Promise.all([read('en'), read('zh')]);
  return Object.keys(en)
    .sort(compareVersions)
    .reverse()
    .slice(0, NOTES_VERSIONS)
    .map((version) => ({ version, date: en[version].date, en: en[version].items, zh: zh[version]?.items ?? en[version].items }));
}

export default function latestApk() {
  return {
    name: 'latest-apk',
    hooks: {
      'astro:build:done': async ({ dir, logger }) => {
        let target = GITHUB_APK;
        let latest = null;
        try {
          const headers = { Accept: 'application/vnd.github+json' };
          // CI passes its token, for a higher rate limit.
          if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
          const response = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
            headers,
            signal: AbortSignal.timeout(15000),
          });
          if (!response.ok) throw new Error(`release: HTTP ${response.status}`);
          const release = await response.json();
          const asset = release.assets?.find((a) => a.name === 'CastBay.apk');
          if (!asset) throw new Error(`no CastBay.apk in ${release.tag_name}`);
          const download = await fetch(asset.browser_download_url, { signal: AbortSignal.timeout(120000) });
          if (!download.ok) throw new Error(`APK: HTTP ${download.status}`);
          const apk = Buffer.from(await download.arrayBuffer());
          // The same file GitHub has, byte for byte, so the changelog's checksum holds.
          const sha256 = createHash('sha256').update(apk).digest('hex');
          if (asset.digest && asset.digest !== `sha256:${sha256}`) throw new Error('APK checksum mismatch');
          await writeFile(new URL('CastBay.apk', dir), apk);
          target = '/CastBay.apk';
          latest = {
            version: String(release.tag_name).replace(/^v/, ''),
            sha256,
            // The app tries these in order; GitHub's copy is the same file.
            urls: ['https://castbay.weenas.com/CastBay.apk', asset.browser_download_url],
            page: release.html_url,
          };
          try {
            latest.notes = await releaseNotes();
          } catch (error) {
            logger.warn(`release notes left out of latest.json: ${error}`);
          }
          logger.info(`CastBay.apk from ${release.tag_name} (${apk.length} bytes, SHA-256 ${sha256})`);
        } catch (error) {
          logger.warn(`latest APK unavailable, /apk points to GitHub: ${error}`);
        }
        await writeFile(new URL('_redirects', dir), `/apk ${target} 302\n`);
        // Without it, the app asks GitHub instead.
        if (latest) await writeFile(new URL('latest.json', dir), JSON.stringify(latest, null, 2) + '\n');
      },
    },
  };
}
