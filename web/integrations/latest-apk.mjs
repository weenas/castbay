// Puts the latest release's APK on the site itself, at /CastBay.apk, with /apk (short enough
// to type on a TV remote) pointing to it: downloads from GitHub are often slow or blocked in
// China. The release workflow rebuilds the site after each release, so it stays current.
// If GitHub can't be reached while building, /apk points to GitHub's copy instead.
// /latest.json tells the app's updater about it: the version, the SHA-256 and where to get it.
import { createHash } from 'node:crypto';
import { writeFile } from 'node:fs/promises';

const REPO = 'weenas/castbay';
const GITHUB_APK = `https://github.com/${REPO}/releases/latest/download/CastBay.apk`;

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
