import { describe, expect, it } from "vitest";
import { TAG_TONES, marks, toneFor } from "@prabhixtechnologies/brand";

/**
 * That the brand package can be imported at all, assets included.
 *
 * This was written when `@prabhixtechnologies/brand` was a `file:` dependency on a sibling
 * checkout: its node_modules entry was a link out of this repository, Vite checked
 * `server.fs.allow` against the real path, and getting that wrong failed every import of the
 * package with "Denied ID" rather than a missing file — a whole-suite error with nothing in it
 * naming the config line responsible. That is what happened to Mailroom's accessibility tests,
 * which could not run for weeks while the CI failure pointed at the test file.
 *
 * The package installs from GitHub Packages now, so there is no link and no allow-list, but the
 * test is worth more than it was rather than less. The published tarball serves `dist/index.js`,
 * and the `new URL("../marks/…", import.meta.url)` inside it resolves one level up from there —
 * so this asserts that `marks/` is actually in the tarball's `files` and sits at the depth the
 * compiled entry expects. That is a packaging property, invisible in this repository, and exactly
 * the kind that breaks on a version bump.
 *
 * Importing `marks` is the part that matters: it is the only export that reaches for an asset.
 * Nothing else in this suite imports the tag page, which is why the original breakage was latent.
 *
 * The assertion accepts a data URL as well as a path because the marks are small enough for Vite
 * to inline, and which of the two comes back is a build detail — `assetsInlineLimit`, and the
 * size of an SVG somebody may well edit. What is being tested is that a URL comes back at all.
 */
describe("the brand package", () => {
  it("resolves its mark assets", () => {
    expect(marks.favicon).toMatch(/^data:image\/svg\+xml,|favicon\.svg$/);
  });

  it("exports the generated tag swatches", () => {
    expect(TAG_TONES).toContain("indigo");
    expect(TAG_TONES).toContain(toneFor("any seed at all"));
  });
});
