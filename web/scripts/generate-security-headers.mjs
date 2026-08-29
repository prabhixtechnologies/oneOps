#!/usr/bin/env node
/**
 * Fills in the CSP script hashes for nginx, from the HTML that was actually built.
 *
 * Run after `vite build`, before the nginx stage copies the result:
 *
 *   node scripts/generate-security-headers.mjs dist security-headers.conf.template out.conf
 *
 * Hashing the built output rather than the source is the whole point. Vite minifies the inline theme
 * bootstrap in index.html, so source and artifact differ, and the browser only ever agrees with the
 * artifact. Every entry point is scanned, so adding a second HTML entry needs no change here.
 */
import { createHash } from "node:crypto";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

const [distDir = "dist", templatePath = "security-headers.conf.template", outPath] = process.argv.slice(2);

if (!outPath) {
  console.error("usage: generate-security-headers.mjs <distDir> <template> <out>");
  process.exit(2);
}

const PLACEHOLDER = "__INLINE_SCRIPT_HASHES__";

// Inline means "has no src". An external script is covered by 'self' and must not be hashed.
const INLINE_SCRIPT = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g;

const template = await readFile(templatePath, "utf8");
if (!template.includes(PLACEHOLDER)) {
  console.error(`${templatePath} does not contain ${PLACEHOLDER}; refusing to write a policy that would not match the build`);
  process.exit(1);
}

const htmlFiles = (await readdir(distDir)).filter((name) => name.endsWith(".html"));
if (htmlFiles.length === 0) {
  console.error(`no .html files in ${distDir}; run the build first`);
  process.exit(1);
}

const hashes = new Set();
for (const name of htmlFiles) {
  const html = await readFile(join(distDir, name), "utf8");
  for (const [, body] of html.matchAll(INLINE_SCRIPT)) {
    if (body.trim() === "") continue;
    // The HTML parser normalises CR and CRLF to LF before the script's text is hashed, so a file
    // checked out on Windows and one checked out in the Linux image must produce the same value.
    // Without this the hash was correct only on whichever platform generated it.
    const normalized = body.replace(/\r\n?/g, "\n");
    hashes.add(`'sha256-${createHash("sha256").update(normalized, "utf8").digest("base64")}'`);
  }
}

// The console has an inline theme bootstrap in every entry document. Finding none means the
// extraction broke rather than that the app changed, and shipping the policy anyway would block
// that script in production while every local check still passed.
if (hashes.size === 0) {
  console.error(
    `found no inline scripts across ${htmlFiles.join(", ")}. If that is now genuinely true, ` +
      `delete this guard deliberately rather than letting a silent regex failure lock out a script.`,
  );
  process.exit(1);
}

const sorted = [...hashes].sort();
// replaceAll, not replace: a single replace substitutes only the first occurrence, so a mention of
// the placeholder anywhere above the policy line would consume it and leave the real one in place —
// producing a policy that names a literal token as a source expression and blocks the script.
await writeFile(outPath, template.replaceAll(PLACEHOLDER, sorted.join(" ")), "utf8");
console.log(`security headers written to ${outPath} with ${sorted.length} script hash(es): ${sorted.join(" ")}`);
