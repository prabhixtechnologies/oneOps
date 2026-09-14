/**
 * Merge commerce paths/schemas into backend/apidocs.json when the committed snapshot
 * predates those operations (the checked-in dump still has mail and no storefront).
 *
 * Existing keys win, so a live springdoc export is not overwritten by this overlay.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const snapshotPath = path.resolve(here, "../../../backend/apidocs.json");
const overlayPath = path.resolve(here, "../overlay.openapi.json");

const base = JSON.parse(fs.readFileSync(snapshotPath, "utf8"));
const overlay = JSON.parse(fs.readFileSync(overlayPath, "utf8"));

base.paths ??= {};
base.components ??= {};
base.components.schemas ??= {};

let addedPaths = 0;
let addedSchemas = 0;
for (const [p, spec] of Object.entries(overlay.paths ?? {})) {
  if (!base.paths[p]) {
    base.paths[p] = spec;
    addedPaths += 1;
  }
}
for (const [name, schema] of Object.entries(overlay.components?.schemas ?? {})) {
  if (!base.components.schemas[name]) {
    base.components.schemas[name] = schema;
    addedSchemas += 1;
  }
}

if (addedPaths || addedSchemas) {
  fs.writeFileSync(snapshotPath, `${JSON.stringify(base)}\n`);
  console.log(
    `Merged commerce overlay into apidocs.json (+${addedPaths} paths, +${addedSchemas} schemas).`,
  );
} else {
  console.log("apidocs.json already contains the commerce overlay keys.");
}
