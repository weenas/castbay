// Replaces ../website with the fresh build in dist/. (Astro can't build there directly: its
// prerendering runs from the output folder and needs this project's node_modules.)
import { cpSync, rmSync } from 'node:fs';

const from = new URL('../dist/', import.meta.url);
const to = new URL('../../website/', import.meta.url);
rmSync(to, { recursive: true, force: true });
cpSync(from, to, { recursive: true });
console.log('Copied dist/ to website/');
