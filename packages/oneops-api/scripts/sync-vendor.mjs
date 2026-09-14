/**
 * Copies this package (no node_modules) into Platform/marketing/vendor/oneops-api so the
 * marketing site can file:-depend on it from an isolated Platform checkout.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.resolve(here, "..");
const marketing = path.resolve(here, "../../../../Platform/marketing");
const dest = path.join(marketing, "vendor/oneops-api");

if (!fs.existsSync(marketing)) {
  console.log("Platform/marketing is not a sibling of oneOps; skipping vendor sync.");
  process.exit(0);
}

fs.rmSync(dest, { recursive: true, force: true });
fs.mkdirSync(dest, { recursive: true });

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name === ".git") continue;
    const fp = path.join(from, entry.name);
    const tp = path.join(to, entry.name);
    if (entry.isDirectory()) copyDir(fp, tp);
    else fs.copyFileSync(fp, tp);
  }
}

copyDir(path.join(src, "src"), path.join(dest, "src"));
for (const name of ["README.md", "tsconfig.json"]) {
  const fp = path.join(src, name);
  if (fs.existsSync(fp)) fs.copyFileSync(fp, path.join(dest, name));
}

const pkg = JSON.parse(fs.readFileSync(path.join(src, "package.json"), "utf8"));
delete pkg.scripts;
delete pkg.devDependencies;
fs.writeFileSync(path.join(dest, "package.json"), `${JSON.stringify(pkg, null, 2)}\n`);
fs.writeFileSync(
  path.join(dest, "VENDOR.md"),
  `Vendored snapshot of \`@prabhix/oneops-api\` from the oneOps repository.

Refresh from a workspace that has both repos:

\`\`\`
cd ../../oneOps/packages/oneops-api
npm run sync:vendor
\`\`\`
`,
);
console.log(`Synced @prabhix/oneops-api -> ${dest}`);
