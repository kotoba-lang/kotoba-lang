(ns jp-go-dds.kotoba-oracle
  "Runs the shipped decision cores.

  `kotoba/*.kotoba` holds the decisions; `resources/jp_go_dds/oracle/*.kir.edn`
  is what was compiled from them and what ships. This namespace is the seam,
  and it is deliberately thin: it resolves a resource, executes an export, and
  decides nothing.

  ## Why this exists

  Every core here landed with a `*-parity-test` that compiled the `.kotoba`
  fresh and required the same answers as the `.cljc`. That was the right first
  step and those tests are still here. But two implementations bound by a test
  are still two implementations, and the measure of a port is not how many host
  lines went away, it is whether the AUTHORITY moved (ADR-2608112100). Until
  now it had not: the `.kotoba` was a checked replica and the `.cljc` was what
  ran. Now, on the JVM, the `.kotoba` is what runs for the decisions it can
  express, and the `.cljc` keeps the halves that are not decisions -- walking a
  list of cells, building a hiccup vector, naming a key.

  ## THIS FILE IS `.clj`, NOT `.cljc`, AND THAT IS THE WHOLE COMPATIBILITY STORY

  jp-go-dds is the base design system of this workspace; ~170 repositories
  depend on it and many of them build for the browser or run under nbb with an
  explicit `--classpath`. Delegating on ClojureScript would cost every one of
  them two things:

    1. `io/resource` does not exist there, so `register-kir!` would be the only
       way in -- every consumer would have to register a KIR before it could
       render a button;
    2. `kotoba.kir` would have to be on their classpath, which for the nbb
       consumers means adding kotoba-kir + kotoba-hir + security by hand.

  A narrowing like that, multiplied by 170, is not worth the symmetry. So this
  namespace is JVM-only and the hosts require it under `#?(:clj ...)`. A
  ClojureScript consumer of this library has to do NOTHING differently: the
  `:cljs` branches are the same code they were, byte for byte.

  The cost is stated rather than hidden: on ClojureScript the rules are still a
  second implementation. `kotoba-oracle-test` binds those `*-host` functions
  against the shipped guest directly, so the second implementation is checked
  against the authority rather than against nothing -- but it is checked, not
  derived, and it will stay that way until this seam can cross runtimes without
  charging consumers for it.

  ## No fallback around a missing artifact

  A missing or unreadable artifact throws. It does not quietly run something
  else, because a silent fallback is how a decision stops being the one that
  shipped. Which branch runs is decided by the runtime and the data, never by
  whether the artifact loaded.

  ## The guest ABI, measured 2026-08-12 at this repo's pins

  | value | how the host passes it |
  |---|---|
  | `:i64` | a JVM `long` (an `int` is accepted too) |
  | `:string` / `:bool` | itself |
  | `:keyword` | itself -- a string is refused, `\"value is not a keyword\"` |
  | record | `[<resolved descriptor> field ...]` in DECLARED order |
  | `:document` | `[\"vector\" [...]]`, refused above 32 items |

  A record parameter is declared `[:ref :c/marker]`, and passing that ref as
  the head is refused: the head has to be the RESOLVED `[:record ...]`
  descriptor, which lives in the artifact's `:schemas`. `record-type` does that
  lookup so a host never writes a field order down a second time.

  ## If anyone ever extends this seam to ClojureScript, read this first

  Two asymmetries make a green JVM suite worth nothing as evidence about
  ClojureScript, and neither can reach this library TODAY only because nothing
  here crosses the boundary on that runtime:

    1. `kir/execute` coerces a TOP-LEVEL `:i64` argument and accepts a host
       integer, but an `:i64` field INSIDE a record goes through
       `value/bounded-typed-value!`, which on ClojureScript demands a
       `js/BigInt` and rejects a `js/Number`. **`:tbl/cell` carries
       `[:index :i64]`**, so `table/row-header-cell?` is exactly that shape:
       porting it would need an `i64` conversion (see
       `kotoba.crdt.kotoba-oracle`'s `i64` / `i64-value`) or it would throw
       `value is not a signed i64` on every browser build while every JVM test
       stayed green. Measured on `kotoba-lang/calendar`, 2026-08-12.
    2. `utf8-substring!` at some older kir pins guards with `(integer? start)`,
       which is false for a `js/BigInt`, so any core that formats an integer
       into a string breaks. **This repository is not exposed**: compiler
       875e3882 declared kotoba-kir 03222197, whose `kir/value.cljc` is
       BYTE-IDENTICAL to 6d08e3c, including the `:cljs` BigInt arm of
       `bounded-host-byte-offset`. The current pair is amu 1e21a1f / kir
       6d08e3c: emitter and interpreter stay matched, and `string=?` /
       `string-contains?` are `:bool`, which is a source migration of the
       cores rather than a pin bump by itself.

  Either way, the cost of crossing is not this file — it is asking 170
  repositories to put `kotoba.kir` on their classpath."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kotoba.kir :as kir]))

(def cores
  "Oracle id -> the .kotoba it was compiled from, under kotoba/."
  {:bridge-document   "kotoba/bridge_document.kotoba"
   :button            "kotoba/button.kotoba"
   :components        "kotoba/components.kotoba"
   :dark-declarations "kotoba/dark_declarations.kotoba"
   :dark-mirror       "kotoba/dark_mirror.kotoba"
   :select-banner     "kotoba/select_banner.kotoba"
   :sheet-plan        "kotoba/sheet_plan.kotoba"
   :table             "kotoba/table.kotoba"})

(def fuel
  "Execution fuel per call. The same value the parity harnesses have always
  used, so a call that runs under a test runs in production."
  262144)

(defn resource-path [id]
  (str "jp_go_dds/oracle/" (name id) ".kir.edn"))

(def ^:private registered
  "Pre-parsed KIR, installed explicitly. This is how the delegation gate proves
  the host reads the artifact rather than having quietly kept a copy."
  (atom {}))

(defn register-kir!
  "Install a parsed KIR for `id`, bypassing the resource read."
  [id kir]
  (swap! registered assoc id kir)
  kir)

(defn deregister-kir!
  "Drop a registration, so `id` reads the shipped artifact again."
  [id]
  (swap! registered dissoc id)
  nil)

(defn- read-artifact [id]
  (let [path (resource-path id)]
    (if-let [url (io/resource path)]
      (edn/read-string (slurp url))
      (throw (ex-info "shipped decision core is missing — run `kbb -M:test:gen`"
                      {:oracle id :path path})))))

(def ^:private cache (atom {}))

(defn kir
  "The shipped KIR for `id`, read once.

  A registration wins over the cache: it is an explicit instruction, and a
  caller that registers after something already read the artifact means the
  registration, not the read."
  [id]
  (or (get @registered id)
      (get @cache id)
      (let [loaded (read-artifact id)]
        (swap! cache assoc id loaded)
        loaded)))

(defn signature
  "The shipped declaration of `export`: `:params`, `:param-types`, `:result`.

  Throws if the export is not there, because a host asking for a signature is
  about to build an argument out of it."
  [id export]
  (let [export (symbol (name export))]
    (or (first (filter #(= export (:name %)) (:functions (kir id))))
        (throw (ex-info "shipped core does not declare that export"
                        {:oracle id :export export})))))

(defn param-types
  "Declared parameter types of `export`, in order. Record parameters appear as
  `[:ref :schema/name]`; see `record-type`."
  [id export]
  (:param-types (signature id export)))

(defn record-type
  "Resolve a `[:ref :schema/name]` parameter to the `[:record name fields]`
  descriptor the entry boundary requires as a record's head.

  The resolution is out of the ARTIFACT, so a record's fields and -- the part
  that matters for a positional ABI -- their declared order come from the
  source of the rule rather than from a host copy of it."
  [id param-type]
  (if (and (vector? param-type) (= :ref (first param-type)))
    (or (get (:schemas (kir id)) (second param-type))
        (throw (ex-info "shipped core declares no such schema"
                        {:oracle id :ref param-type})))
    param-type))

(defn record-fields
  "`[[field type] …]` of a `[:record name fields]` descriptor, in declared
  order."
  [descriptor]
  (nth descriptor 2))

(defn record
  "Build a guest record argument: the resolved descriptor, then fields in
  DECLARED order. Pair with `record-fields` so the order comes from the
  artifact rather than from a host that wrote it down again."
  [descriptor field-values]
  (into [descriptor] field-values))

(defn document
  "Build a guest `:document` vector value from already-encoded members."
  [members]
  ["vector" (vec members)])

(defn call
  "Execute an export of a shipped core. Args and result are guest ABI values."
  [id export args]
  (kir/execute (kir id) (symbol (name export)) (vec args) {:fuel fuel}))
