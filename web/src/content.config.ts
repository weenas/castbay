// Release notes, one Markdown file per version and language (src/content/releases/<lang>/
// <version>.md). The changelog pages list them; the release workflow publishes the same
// notes on GitHub.
import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { z } from 'astro/zod';

const releases = defineCollection({
  loader: glob({ pattern: '*/*.md', base: './src/content/releases' }),
  schema: z.object({
    version: z.string(),
    date: z.coerce.date(),
  }),
});

export const collections = { releases };
