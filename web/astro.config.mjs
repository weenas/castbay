// castbay.weenas.com: static pages. `npm run build` builds them into dist/ and copies that to
// ../website, which is committed and which Cloudflare publishes as it is.
import { defineConfig } from 'astro/config';
import { satteri } from '@astrojs/markdown-satteri';

export default defineConfig({
  site: 'https://castbay.weenas.com',
  // guide.md becomes guide.html (served at /guide) and zh/index.astro zh/index.html, as the
  // hand-written site had them.
  build: { format: 'preserve' },
  trailingSlash: 'ignore',
  // Keep the HTML readable, and quotes as written (Chinese text uses straight quotes).
  compressHTML: false,
  markdown: { processor: satteri({ features: { smartPunctuation: false } }) },
});
