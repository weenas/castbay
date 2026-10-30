// Puts the newest CastBay Desktop release's installers on the site, at /desktop/<version>/<file>,
// and a note on them at /desktop/latest.json: downloads from github.com are slow or blocked in
// some networks, and the desktop app updates itself from here (then from GitHub). The note names
// the version, the release page and each file's SHA-256, which the app checks the download
// against. The release workflow rebuilds the site after each release, so it stays current.
// Anything that goes wrong here only leaves the files out (the app then uses GitHub); the build
// goes on.
import { createHash } from 'node:crypto';
import { appendFile, mkdir, writeFile } from 'node:fs/promises';

const REPO = 'weenas/castbay';
// The installers the app asks for: the .msi in each language, the .deb of each architecture; and the
// portable .zip, which the download page offers.
const WANTED = /^(CastBay-Desktop-[\d.]+-windows-x64-(zh-CN|en-US)\.msi|CastBay-Desktop-[\d.]+-windows-x64\.zip|castbay-desktop_[\d.]+_(amd64|arm64)\.deb)$/;
// The addresses that do not change with the version, for the download page: /desktop/<name> goes to the
// file of the newest release (the site's copy, else GitHub's).
const ALIASES = [
  ['windows-zh', /-windows-x64-zh-CN\.msi$/],
  ['windows-en', /-windows-x64-en-US\.msi$/],
  ['windows-zip', /-windows-x64\.zip$/],
  ['linux', /_amd64\.deb$/],
];
// The static assets' limit is 25 MiB a file; a bigger one is left out (the app uses GitHub for it).
const MAX_BYTES = 25 * 1024 * 1024 - 1;

const compareVersions = (a, b) => {
  const x = a.split('.').map(Number);
  const y = b.split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) - (y[i] ?? 0);
  }
  return 0;
};

async function retry(action, attempts = 3) {
  for (let i = 1; ; i++) {
    try {
      return await action();
    } catch (error) {
      if (i >= attempts) throw error;
      await new Promise((resolve) => setTimeout(resolve, 3000 * i));
    }
  }
}

export default function latestDesktop() {
  return {
    name: 'latest-desktop',
    hooks: {
      'astro:build:done': async ({ dir, logger }) => {
        try {
          const headers = { Accept: 'application/vnd.github+json' };
          if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
          const releases = await retry(async () => {
            const response = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30`, { headers, signal: AbortSignal.timeout(15000) });
            if (!response.ok) throw new Error(`releases: HTTP ${response.status}`);
            return response.json();
          });
          const newest = releases
            .filter((r) => !r.draft && /^desktop-v\d+(\.\d+)*$/.test(r.tag_name))
            .sort((a, b) => compareVersions(b.tag_name.slice(9), a.tag_name.slice(9)))[0];
          if (!newest) throw new Error('no desktop release');
          const version = newest.tag_name.slice('desktop-v'.length);
          const files = {};
          for (const asset of newest.assets.filter((a) => WANTED.test(a.name))) {
            if (asset.size > MAX_BYTES) {
              logger.warn(`${asset.name} (${asset.size} bytes) is too big for the site, left to GitHub`);
              continue;
            }
            const bytes = await retry(async () => {
              const download = await fetch(asset.browser_download_url, { signal: AbortSignal.timeout(180000) });
              if (!download.ok) throw new Error(`${asset.name}: HTTP ${download.status}`);
              return Buffer.from(await download.arrayBuffer());
            });
            const sha256 = createHash('sha256').update(bytes).digest('hex');
            if (asset.digest && asset.digest !== `sha256:${sha256}`) throw new Error(`${asset.name}: checksum mismatch`);
            await mkdir(new URL(`desktop/${version}/`, dir), { recursive: true });
            await writeFile(new URL(`desktop/${version}/${asset.name}`, dir), bytes);
            files[asset.name] = sha256;
          }
          if (Object.keys(files).length === 0) throw new Error(`no installers in ${newest.tag_name}`);
          // Where each alias goes: the copy on the site, or GitHub's when the file was too big to keep.
          const redirects = ALIASES.map(([alias, pattern]) => {
            const asset = newest.assets.find((a) => pattern.test(a.name));
            if (!asset) return `/desktop/${alias} ${newest.html_url} 302\n`;
            return `/desktop/${alias} ${files[asset.name] ? `/desktop/${version}/${asset.name}` : asset.browser_download_url} 302\n`;
          });
          await appendFile(new URL('_redirects', dir), redirects.join(''));
          await writeFile(
            new URL('desktop/latest.json', dir),
            JSON.stringify({ version, page: newest.html_url, files }, null, 2) + '\n'
          );
          logger.info(`CastBay Desktop ${version}: ${Object.keys(files).length} installers`);
        } catch (error) {
          logger.warn(`CastBay Desktop installers left out of the site: ${error}`);
          // The download page's addresses then lead to the releases.
          const page = `https://github.com/${REPO}/releases`;
          await appendFile(new URL('_redirects', dir), ALIASES.map(([alias]) => `/desktop/${alias} ${page} 302\n`).join('')).catch(() => {});
        }
      },
    },
  };
}
