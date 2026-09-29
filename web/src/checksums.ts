// Each release's APK: its SHA-256, as GitHub computed it for the upload, where to download it
// and whether it was a pre-release, fetched once when the site is built. Without the network (or over GitHub's rate limit) the changelog just
// leaves them out; the build doesn't fail.
const API = 'https://api.github.com/repos/weenas/castbay/releases?per_page=100';

export interface ReleaseApk {
  /** Its SHA-256, in hex. */
  sha256: string;
  /** Where to download CastBay-<version>.apk. */
  url: string;
  prerelease: boolean;
}

let apks: Promise<Map<string, ReleaseApk>> | undefined;

/** Version ("1.0.79") → its release's CastBay-<version>.apk. */
export function releaseApks(): Promise<Map<string, ReleaseApk>> {
  apks ??= (async () => {
    const found = new Map<string, ReleaseApk>();
    try {
      const headers: Record<string, string> = { Accept: 'application/vnd.github+json' };
      // CI passes its token, for a higher rate limit.
      if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
      const response = await fetch(API, { headers, signal: AbortSignal.timeout(15000) });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      for (const release of await response.json()) {
        const version = String(release.tag_name).replace(/^v/, '');
        const apk = release.assets?.find((a: { name: string }) => a.name === `CastBay-${version}.apk`);
        const digest = apk?.digest?.startsWith('sha256:') ? apk.digest.slice(7) : null;
        if (digest) {
          found.set(version, { sha256: digest, url: apk.browser_download_url, prerelease: Boolean(release.prerelease) });
        }
      }
    } catch (error) {
      console.warn(`[checksums] release digests unavailable: ${error}`);
    }
    return found;
  })();
  return apks;
}
