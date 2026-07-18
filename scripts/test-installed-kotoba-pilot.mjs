import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";

const artifactPath = process.argv[2];
assert.ok(artifactPath, "compiled artifact path is required");
const generated = await import(pathToFileURL(artifactPath).href);
assert.match(generated.kotobaArtifact.sourceDigest, /^[0-9a-f]{64}$/);
assert.match(generated.kotobaArtifact.kirDigest, /^[0-9a-f]{64}$/);
const api = generated.instantiateKotoba({});
assert.equal(api.bump(32n), 42n);
console.log("kotoba-lang installed-release migration pilot passed");
