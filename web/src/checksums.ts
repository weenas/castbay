// The SHA-256 of each release's APK, as GitHub computed it for the upload, fetched once when
// the site is built. Without the network (or over GitHub's rate limit) the changelog just
// leaves them out; the build doesn't fail.
const API = 'https://api.github.com/repos/weenas/castbay/releases?per_page=100';

let digests: Promise<Map<string, string>> | undefined;

/** Version ("1.0.79") → the SHA-256 (hex) of CastBay-<version>.apk. */
export function apkDigests(): Promise<Map<string, string>> {
  digests ??= (async () => {
    const found = new Map<string, string>();
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
        if (digest) found.set(version, digest);
      }
    } catch (error) {
      console.warn(`[checksums] release digests unavailable: ${error}`);
    }
    return found;
  })();
  return digests;
}
