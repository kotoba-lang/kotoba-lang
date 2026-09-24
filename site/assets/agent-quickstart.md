# Kotoba CLI quickstart for AI agents

This is an executable acceptance check, not just an installation recipe. It
installs the released CLI, runs its self-check, compiles a zero-authority
program to WebAssembly, and executes the exported function.

## 1. Install

On macOS or Linux with Homebrew:

```sh
brew tap kotoba-lang/kotoba
brew trust kotoba-lang/kotoba
brew install kotoba
brew list --versions kotoba
```

Homebrew 6 requires the explicit `brew trust` step. Release artifacts and the
checksum-verifying alternative installer are maintained at
https://github.com/kotoba-lang/kotoba/releases.

### 1b. Worktree-local install (no Homebrew, no package manager)

For a sandboxed or unattended agent run that may write only inside its own
worktree and may not install packages: the release archive is one static
executable (`kotoba`) plus `LICENSE` and `README.md`, with no runtime
dependency. Download it next to your program, verify the digest, and call it
by path.

```sh
mkdir -p .kotoba-toolchain
ASSET=kotoba-darwin-arm64       # or kotoba-darwin-amd64, kotoba-linux-amd64
curl -fsSL -o .kotoba-toolchain/$ASSET.tar.gz \
  https://github.com/kotoba-lang/kotoba/releases/latest/download/$ASSET.tar.gz
curl -fsSL -o .kotoba-toolchain/$ASSET.tar.gz.sha256 \
  https://github.com/kotoba-lang/kotoba/releases/latest/download/$ASSET.tar.gz.sha256
(cd .kotoba-toolchain && shasum -a 256 -c $ASSET.tar.gz.sha256)
tar -xzf .kotoba-toolchain/$ASSET.tar.gz -C .kotoba-toolchain
.kotoba-toolchain/kotoba selfhost check --json
```

The `.sha256` file is `<hex digest>  <file name>` (two spaces), the shape
`shasum -c` / `sha256sum -c` read. Add `.kotoba-toolchain/` to `.gitignore`;
the archive is about 42 MB and is not source. Use `.kotoba-toolchain/kotoba`
wherever the steps below say `kotoba`. Measured 2026-09-17 on macOS arm64 with
v0.7.3 and an environment reduced to `PATH`, `LANG`, a private `HOME` and
`TMPDIR`: steps 2–5 below all passed. If your environment has no network from
the shell (some sandboxes), this download is the step that fails; do not
report the later steps as passed.

## 2. Check the compiler seed

```sh
kotoba selfhost check --json
```

Accept only JSON with `kotoba.cli/ok?` equal to `true`, `kotoba.cli/code`
equal to `valid`, and an empty `kotoba.selfhost/problems` vector.

## 3. Create a program

Create `hello.kotoba` with exactly:

```clojure
(defn main []
  (+ 40 2))
```

## 4. Compile to WebAssembly

```sh
kotoba compile hello.kotoba --target wasm --output hello.wasm --json
```

Accept only JSON with `kotoba.cli/ok?` equal to `true` and `kotoba.cli/code`
equal to `emitted`. Confirm that `hello.wasm` exists and begins with the Wasm
magic bytes `00 61 73 6d`.

## 5. Execute the exported function

Node.js 18 or newer can perform a host-independent check:

```sh
node -e 'const fs=require("node:fs");const b=fs.readFileSync("hello.wasm");const m=new WebAssembly.Module(b);if(WebAssembly.Module.imports(m).length)throw Error("unexpected host imports");const i=new WebAssembly.Instance(m,{});if(i.exports.main()!==42n)throw Error("expected 42");console.log("kotoba wasm verified: main() = 42")'
```

Expected final line:

```text
kotoba wasm verified: main() = 42
```

This program requests no host effects and the emitted module has no imports.
For effectful programs, begin with the capability guide:
https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/lang/capability-values.md

## Evidence boundary

Some CLI commands are adapter-owned and may return a `planned` response with
`host-action: adapter-required`. That is not execution evidence. The five
steps above are the portable acceptance path because they inspect and execute
the emitted artifact.

The current public language/implementation binding is recorded separately at
https://github.com/kotoba-lang/kotoba-lang/blob/main/docs/generated/release.md.
Do not assume that an installed formula version is bound to the current
language profile without checking that release record.

## Next: trusted state and service discovery

- Graph & Ontology database (workspace → agent token → Biscuit → transact → query): https://graph.kotoba.cloud/agent-quickstart.md
- Kotoba Cloud capabilities and signed packages: https://kotoba.cloud/agent-quickstart.md
- Machine documentation index: https://kotoba-lang.org/llms.txt

Cloud hosted apply is not offered today. Follow each service's capability and
authorization profile; compiling a local program grants no service access.
