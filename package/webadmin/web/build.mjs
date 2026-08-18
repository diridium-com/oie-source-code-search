/*
 * Build the OIE Source Code Search web admin plugin.
 *
 * Compiles the JSX source (web/plugin.jsx) to the plain-ESM file the host
 * serves raw and plugin.json points to (web/plugin.js). The browser can't run
 * JSX, so this MUST run before the committed plugin.js is up to date.
 *
 * IMPORTANT: run `npm run build` BEFORE `mvn package`. The repo packages
 * webadmin/ via maven-resources-plugin (filtering=false), copying files
 * verbatim — so web/plugin.js is a build artifact that must be regenerated and
 * committed whenever web/plugin.jsx changes. (Committing the built plugin.js is
 * sufficient for now; do not restructure the Maven build.)
 *
 * The @oie/* packages stay EXTERNAL — the host's import map resolves them at
 * runtime; they are never bundled.
 */

import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import postcss from 'postcss';
import tailwindcss from '@tailwindcss/postcss';
import { readFileSync, writeFileSync } from 'node:fs';

const here = dirname(fileURLToPath(import.meta.url));

// Generate the Tailwind utility CSS (theme + utilities, no preflight) by
// scanning the converted plugin.jsx, and emit it as a JS module the bundle
// imports. esbuild's bundle:true inlines this into plugin.js, so only
// plugin.js ships. scs-css.generated.js is a build intermediate (gitignored).
const twInput = readFileSync(resolve(here, 'tailwind.css'), 'utf8');
const twResult = await postcss([tailwindcss()]).process(twInput, { from: resolve(here, 'tailwind.css') });
writeFileSync(resolve(here, 'scs-css.generated.js'), 'export const SCS_CSS = ' + JSON.stringify(twResult.css) + ';\n');

await build({
    entryPoints: [resolve(here, 'plugin.jsx')],
    outfile: resolve(here, 'plugin.js'),
    bundle: true,
    format: 'esm',
    target: 'es2022',
    jsx: 'transform',
    jsxFactory: 'React.createElement',
    jsxFragment: 'React.Fragment',
    external: ['@oie/web-api', '@oie/web-ui', '@oie/web-shell']
});

console.log('built web/plugin.js');
