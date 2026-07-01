const state = {
  data: null,
  selectedCellId: null,
};

const keyword = Symbol("keyword");

class EdnParser {
  constructor(input) {
    this.input = input;
    this.index = 0;
  }

  parse() {
    this.skip();
    return this.readValue();
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
    return { [keyword]: true, value: token };
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
    return this.input.slice(start, this.index);
  }

  keyName(value) {
    if (value && value[keyword]) return value.value;
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

function text(id, value) {
  document.getElementById(id).textContent = value ?? "-";
}

function keywordText(value) {
  if (value && value[keyword]) return value.value;
  return String(value ?? "");
}

function statusClass(status) {
  return `status-${keywordText(status)}`;
}

function selectedCell() {
  return state.data["lab/notebook"]["lab/cells"].find(
    (cell) => cell["cell/id"] === state.selectedCellId,
  );
}

function render() {
  const data = state.data;
  const notebook = data["lab/notebook"];
  const cells = notebook["lab/cells"];
  state.selectedCellId ||= cells[0]["cell/id"];

  text("app-status", `${keywordText(data["app/status"])} / ${data["app/repo"]}`);
  text("app-title", data["app/title"]);
  text("notebook-title", notebook["lab/title"]);
  text("replay-status", notebook["lab/replay-status"]);
  text("run-id", notebook["lab/run-id"]);
  text("runtime", notebook["lab/runtime"]);

  renderCells(cells);
  renderSelectedCell();
  renderArtifacts(notebook["lab/artifacts"]);
  renderEvidence(notebook["lab/evidence"]);
}

function renderCells(cells) {
  const list = document.getElementById("cell-list");
  list.innerHTML = "";
  cells.forEach((cell, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `cell-button ${
      cell["cell/id"] === state.selectedCellId ? "is-active" : ""
    }`;
    button.addEventListener("click", () => {
      state.selectedCellId = cell["cell/id"];
      render();
    });

    const deps = cell["cell/depends-on"];
    button.innerHTML = `
      <span class="cell-index">${String(index + 1).padStart(2, "0")}</span>
      <span>
        <span class="cell-name">${cell["cell/title"]}</span>
        <span class="cell-deps">${keywordText(cell["cell/kind"])} / ${
          deps.length ? deps.join(", ") : "root"
        }</span>
      </span>
      <span class="status-pill ${statusClass(cell["cell/status"])}">${keywordText(
        cell["cell/status"],
      )}</span>
    `;
    list.appendChild(button);
  });
}

function renderSelectedCell() {
  const cell = selectedCell();
  text("selected-kind", keywordText(cell["cell/kind"]));
  text("selected-status", keywordText(cell["cell/status"]));
  text("selected-title", cell["cell/title"]);
  text("source-view", cell["cell/source"]);
  text("cell-output", cell["cell/output"]);

  const strip = document.getElementById("policy-strip");
  strip.innerHTML = "";
  const policy = cell["cell/policy"];
  if (policy.length === 0) {
    const chip = document.createElement("span");
    chip.className = "policy-chip";
    chip.textContent = "no runtime capability";
    strip.appendChild(chip);
    return;
  }
  policy.forEach((cap) => {
    const chip = document.createElement("span");
    chip.className = "policy-chip";
    chip.textContent = keywordText(cap);
    strip.appendChild(chip);
  });
}

function renderArtifacts(artifacts) {
  const panel = document.getElementById("artifacts-tab");
  panel.innerHTML = "";
  artifacts.forEach((artifact) => {
    const row = document.createElement("div");
    row.className = "artifact-row";
    row.innerHTML = `
      <strong>${artifact["artifact/name"]}</strong>
      <span class="artifact-meta">${keywordText(artifact["artifact/kind"])} / ${
        artifact["artifact/media-type"]
      } / ${artifact["artifact/size"]}</span>
      <code>${artifact["artifact/cid"]}</code>
    `;
    panel.appendChild(row);
  });
}

function renderEvidence(evidence) {
  const panel = document.getElementById("evidence-tab");
  panel.innerHTML = "";
  Object.entries(evidence).forEach(([key, value]) => {
    const row = document.createElement("div");
    row.className = "evidence-row";
    const rendered = Array.isArray(value)
      ? value.map(keywordText).join(", ")
      : keywordText(value);
    row.innerHTML = `<strong>${key}</strong><code>${rendered}</code>`;
    panel.appendChild(row);
  });
}

function setupInteractions() {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((item) => {
        item.classList.toggle("is-active", item === tab);
      });
      const active = tab.dataset.tab;
      document
        .getElementById("artifacts-tab")
        .classList.toggle("is-hidden", active !== "artifacts");
      document
        .getElementById("evidence-tab")
        .classList.toggle("is-hidden", active !== "evidence");
    });
  });

  document.getElementById("run-button").addEventListener("click", () => {
    const cell = selectedCell();
    cell["cell/status"] = { [keyword]: true, value: "succeeded" };
    cell["cell/output"] = `run complete / ${new Date().toISOString()}`;
    render();
  });

  document.getElementById("replay-button").addEventListener("click", () => {
    text("cell-output", "replay clean / inputs and wasm cid unchanged");
  });

  document.getElementById("evidence-button").addEventListener("click", () => {
    document.querySelector('[data-tab="evidence"]').click();
  });
}

async function boot() {
  setupInteractions();
  try {
    const response = await fetch("./lab.kotoba", { cache: "no-store" });
    const source = await response.text();
    const edn = extractLabUi(source);
    state.data = new EdnParser(edn).parse();
    render();
  } catch (error) {
    text("notebook-title", "Failed to load lab.kotoba");
    text("source-view", error.stack || String(error));
  }
}

boot();
