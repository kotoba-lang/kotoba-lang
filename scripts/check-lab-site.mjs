#!/usr/bin/env node

import fs from "node:fs";
import vm from "node:vm";

const keywordMarker = Symbol("keyword");

class EdnParser {
  constructor(input) {
    this.input = input;
    this.index = 0;
  }

  parse() {
    this.skip();
    const value = this.readValue();
    this.skip();
    return value;
  }

  readValue() {
    this.skip();
    const ch = this.peek();
    if (ch === "{") return this.readMap();
    if (ch === "[") return this.readVector();
    if (ch === '"') return this.readString();
    if (ch === ":") return this.readKeyword();
    return this.readSymbol();
  }

  readMap() {
    this.expect("{");
    const result = {};
    while (true) {
      this.skip();
      if (this.peek() === "}") {
        this.index += 1;
        return result;
      }
      const key = this.readValue();
      const value = this.readValue();
      result[this.keyName(key)] = value;
    }
  }

  readVector() {
    this.expect("[");
    const result = [];
    while (true) {
      this.skip();
      if (this.peek() === "]") {
        this.index += 1;
        return result;
      }
      result.push(this.readValue());
    }
  }

  readString() {
    this.expect('"');
    let out = "";
    while (this.index < this.input.length) {
      const ch = this.input[this.index++];
      if (ch === '"') return out;
      if (ch === "\\") {
        const next = this.input[this.index++];
        if (next === "n") out += "\n";
        else if (next === "t") out += "\t";
        else out += next;
      } else {
        out += ch;
      }
    }
    throw new Error("Unterminated string");
  }

  readKeyword() {
    this.expect(":");
    const token = this.readToken();
    return { [keywordMarker]: true, value: token };
  }

  readSymbol() {
    const token = this.readToken();
    if (/^-?\d+(\.\d+)?$/.test(token)) return Number(token);
    if (token === "true") return true;
    if (token === "false") return false;
    if (token === "nil") return null;
    return token;
  }

  readToken() {
    const start = this.index;
    while (this.index < this.input.length) {
      const ch = this.input[this.index];
      if (/\s|[\]\}\[\{"]/.test(ch)) break;
      this.index += 1;
    }
    if (start === this.index) {
      throw new Error(`Unexpected token at ${this.index}`);
    }
    return this.input.slice(start, this.index);
  }

  keyName(value) {
    if (value && value[keywordMarker]) return value.value;
    return String(value);
  }

  skip() {
    while (this.index < this.input.length) {
      const ch = this.input[this.index];
      if (/\s|,/.test(ch)) {
        this.index += 1;
        continue;
      }
      if (ch === ";") {
        while (this.index < this.input.length && this.input[this.index] !== "\n") {
          this.index += 1;
        }
        continue;
      }
      break;
    }
  }

  peek() {
    return this.input[this.index];
  }

  expect(ch) {
    if (this.input[this.index] !== ch) {
      throw new Error(`Expected ${ch} at ${this.index}`);
    }
    this.index += 1;
  }
}

function parseEdnMap(path) {
  return new EdnParser(fs.readFileSync(path, "utf8")).parse();
}

function keywordText(value) {
  if (value && value[keywordMarker]) return value.value;
  return String(value ?? "");
}

function extractLabUi(source) {
  const marker = "(def lab-ui";
  const start = source.indexOf(marker);
  if (start === -1) throw new Error("lab-ui definition not found");
  const bodyStart = source.indexOf("{", start);
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = bodyStart; i < source.length; i += 1) {
    const ch = source[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === "\\") escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') inString = true;
    if (ch === "{") depth += 1;
    if (ch === "}") depth -= 1;
    if (depth === 0) return source.slice(bodyStart, i + 1);
  }
  throw new Error("lab-ui map did not close");
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

function assert(condition, message) {
  if (!condition) fail(message);
}

function requireKeys(label, value, keys) {
  for (const key of keys.map(keywordText)) {
    assert(Object.prototype.hasOwnProperty.call(value, key), `${label} missing ${key}`);
  }
}

function loadSiteParser() {
  const source = fs
    .readFileSync("docs/site/app.js", "utf8")
    .replace(/boot\(\);\s*$/, "globalThis.__labSiteTest = { EdnParser, extractLabUi };");
  const sandbox = { console, Symbol };
  vm.runInNewContext(source, sandbox, { filename: "docs/site/app.js" });
  return sandbox.__labSiteTest;
}

const contract = parseEdnMap("lang/lab.edn");
const labSource = fs.readFileSync("docs/site/lab.kotoba", "utf8");
const appSource = fs.readFileSync("docs/site/app.js", "utf8");
const indexSource = fs.readFileSync("docs/site/index.html", "utf8");

const directLab = new EdnParser(extractLabUi(labSource)).parse();
const siteParser = loadSiteParser();
const siteLab = new siteParser.EdnParser(siteParser.extractLabUi(labSource)).parse();

assert(JSON.stringify(directLab) === JSON.stringify(siteLab), "site parser diverges from check parser");
assert(appSource.includes('fetch("./lab.kotoba"'), "app.js must load lab.kotoba");
assert(indexSource.includes("./app.js"), "index.html must load app.js");
assert(indexSource.includes("./styles.css"), "index.html must load styles.css");

const notebook = directLab["lab/notebook"];
requireKeys("notebook", notebook, contract["kotoba.lab.contract/required-notebook-keys"]);

const cellKinds = new Set(contract["kotoba.lab.contract/cell-kinds"].map(keywordText));
const cellStatuses = new Set(contract["kotoba.lab.contract/cell-statuses"].map(keywordText));
const artifactKinds = new Set(contract["kotoba.lab.contract/artifact-kinds"].map(keywordText));
const capabilities = new Set(
  contract["kotoba.lab.contract/capabilities"].map((capability) =>
    keywordText(capability.id),
  ),
);

const cells = notebook["lab/cells"];
assert(Array.isArray(cells) && cells.length > 0, "notebook must contain cells");
const cellIds = new Set(cells.map((cell) => cell["cell/id"]));
assert(cellIds.size === cells.length, "cell ids must be unique");

for (const cell of cells) {
  requireKeys(`cell ${cell["cell/id"]}`, cell, contract["kotoba.lab.contract/required-cell-keys"]);
  assert(cellKinds.has(keywordText(cell["cell/kind"])), `unknown cell kind ${keywordText(cell["cell/kind"])}`);
  assert(
    cellStatuses.has(keywordText(cell["cell/status"])),
    `unknown cell status ${keywordText(cell["cell/status"])}`,
  );
  for (const dependency of cell["cell/depends-on"]) {
    assert(cellIds.has(dependency), `${cell["cell/id"]} depends on missing cell ${dependency}`);
  }
  for (const capability of cell["cell/policy"]) {
    assert(capabilities.has(keywordText(capability)), `${cell["cell/id"]} uses unknown capability ${keywordText(capability)}`);
  }
}

const artifacts = notebook["lab/artifacts"];
assert(Array.isArray(artifacts) && artifacts.length > 0, "notebook must contain artifacts");
for (const artifact of artifacts) {
  requireKeys(
    `artifact ${artifact["artifact/name"]}`,
    artifact,
    contract["kotoba.lab.contract/required-artifact-keys"],
  );
  assert(
    artifactKinds.has(keywordText(artifact["artifact/kind"])),
    `unknown artifact kind ${keywordText(artifact["artifact/kind"])}`,
  );
  assert(String(artifact["artifact/cid"]).startsWith("bafy-"), `${artifact["artifact/name"]} must use a CID-like id`);
}

const evidence = notebook["lab/evidence"];
requireKeys("evidence", evidence, contract["kotoba.lab.contract/required-evidence-keys"]);
for (const capability of evidence["evidence/capabilities-used"]) {
  assert(capabilities.has(keywordText(capability)), `evidence uses unknown capability ${keywordText(capability)}`);
}

console.log(
  `ok kotoba-lab site notebook="${notebook["lab/title"]}" cells=${cells.length} artifacts=${artifacts.length}`,
);
