(ns kotoba.lang.authority-claims
  "Every head `lang/guest-grammar.edn` and `lang/surface-status.edn` assert is
  admitted, so that the assertion can be MEASURED instead of read.

  ## The defect this exists to catch

  Four instances of one shape were found by hand on 2026-09-03, each
  separately, none by any check:

  | claim | measured |
  |---|---|
  | `:sugar :contains?` \"bounded set membership scan\", three backends | no `(= op 'contains?)` arm existed at all |
  | `:sugar :dissoc` \"persistent bounded map filter\", three backends | same: no arm, `operation has no admitted lowering` |
  | `:set-literal :operations #{contains? conj disj}` | `conj`/`disj` had no arm; `contains?` on a set is refused deliberately |
  | `:set-literal :admission-limit 16` | measured 32 admitted / 33 refused |

  and a fifth the same day: `(count [7 8 9])` is refused `operation has no
  admitted lowering` while `:sugar :count` claims three backends and
  `:persistent-collection-semantics :operations` names it.

  The shape is one sentence: **an authority entry asserts an operation and
  nothing checks that the operation exists.** Every one of these was written
  in good faith, and every one of them was true of some backend, some day, or
  some neighbouring head. What was missing was not care. It was a place for
  the claim and the frontend to meet.

  ## What a claim is

  This namespace is the enumeration half and is PURE: it reads the two
  authority maps and answers `claimed-heads`. It does not know how to run a
  frontend, and it must not -- kotoba-sema is a `:test`-only dependency of
  this repository, and the authority must stay loadable without it.
  `kotoba.lang.authority-claim-lowering-test` supplies the measurement.

  The keys read, and the keys deliberately not read, are `claim-sources` and
  `keys-not-read` below. Both are data rather than prose because an
  enumeration that silently misses a key fails in the same direction as the
  defect it is looking for: quietly, and green."
  (:require [clojure.string :as str]
            [kotoba.lang.conformance-matrix :as cm]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

;; --- what counts as a claim ------------------------------------------------

(def claim-sources
  "The keys read, with what each asserts. Named so a reader can check the list
  against the files rather than trusting that it is complete."
  {[:guest-grammar :sugar]
   "each entry names a head -- its `:forms` when present, else the key --
    admitted on the backends in its `:backends`"
   [:guest-grammar :arithmetic] "integer arithmetic heads"
   [:guest-grammar :comparisons] "integer comparison heads"
   [:guest-grammar :predicates] "predicate and string heads"
   [:guest-grammar :core-special-forms] "definition and binding forms"
   [:guest-grammar :floating-point]
   "the named float families: :arithmetic :unary :comparisons
    :reinterpretation :conversions :bounded-transcendental :decimal-parse"
   [:guest-grammar :admitted-builtins] "builtins admitted without host-import registration"
   [:surface-status :collections] ":operations and :surface of :implemented* entries"
   [:surface-status :other-gaps] "as :collections"
   [:surface-status :checked-memory] "as :collections"})

(def keys-not-read
  "Keys that name heads but do NOT assert admission, with why. Excluding a key
  is a decision; recording it is what makes the decision reviewable.

  `:invariants` is the one that most looks like it belongs above. It does not,
  and not because it is uninteresting: its `:surface` names the heads an
  invariant is ABOUT, and the direction differs per entry. `:no-interop`
  `:surface #{. .. new import}` names heads that must NOT be admitted;
  `:bool-is-a-type-not-a-number` `:surface #{= < not empty? ...}` names heads
  that ARE. Reading it as a claim of admission would assert both."
  {[:guest-grammar :forbidden-heads]
   "the converse claim -- these must NOT be admitted. Checked by
    kotoba-sema's own conformance and by amu's guest-grammar-vendor-test,
    which compares this repository's set against `sema/forbidden-heads`."
   [:guest-grammar :string-head-host-ops]
   "the `:string-host-arg` sugar's host-import vocabulary, and that sugar
    declares `:backends #{:kotoba-wasm}` -- a different frontend, in
    kotoba-lang/kotoba, which this repository cannot drive."
   [:guest-grammar :data-head-host-ops]
   "as :string-head-host-ops, for `:data-host-arg`."
   [:guest-grammar :implicit-body-forms]
   "about how a body form is collapsed, not about whether the head exists;
    `:refuses` and `:not-body-taking` are refusal claims. Its heads all
    appear in `:sugar` or `:core-special-forms`, which ARE read."
   [:guest-grammar :diagnostic-hints]
   "a table of hints shown when a head is refused, so it names refused heads
    on purpose. Measured 2026-09-03 it names `count`, `keys`, `vals` -- which
    is consistent with them having no lowering, and directly contradicts
    `:sugar :count` claiming three backends."
   [:guest-grammar :callable-type]
   "a type descriptor's shape (`[:fn [param-types result] ...]`), not heads."
   [:surface-status :invariants]
   "names the heads an invariant is about, in both directions -- see the
    docstring above."})

(def feature-keys-that-are-not-heads
  "`:sugar` keys whose name is not a call head. `:head` names the head the
  entry describes where there is one under another name, so the claim is still
  checked rather than dropped.

  `nil` means the entry describes a reader literal, a binding shape or an
  argument position -- something with no head at all. Those are asserted
  absent by `the-not-a-head-table-does-not-hide-a-head`: measured 2026-09-03,
  `:fn-ref` and `:invoke` were in this table and BOTH are heads
  (`fn-ref requires one top-level function symbol`,
  `invoke requires an optional admitted result descriptor, a closure, and
  zero to four arguments`), so two live claims were being skipped by a table
  nothing checked. That is this file's own defect, in miniature."
  {:map-literal {:why "reader literal" :head nil}
   :set-literal {:why "reader literal" :head nil}
   :vector-literal {:why "reader literal" :head nil}
   :keyword-literal {:why "reader literal" :head nil}
   :nested-destructuring {:why "binding shapes; :forms holds prose, not heads" :head nil}
   :data-host-arg {:why "an argument POSITION in a host call" :head nil}
   :string-host-arg {:why "an argument POSITION in a host call" :head nil}
   :protocol-dispatch {:why "how a protocol call resolves, not a head" :head nil}
   :record-constructor {:why "the `(->R ...)` shape a defrecord introduces" :head nil}
   :inline-fn-callback {:why "an argument position accepting an `fn` literal" :head nil}
   :interface-contract {:why "what definterface declares, not a head" :head nil}
   :portable-string-symbol-values {:why "a value-model property of strings/symbols" :head nil}
   :typed-capability-call {:why "the feature name; the head is cap-call" :head 'cap-call}})

;; --- reading the two authorities ------------------------------------------

(def guest-grammar-path "lang/guest-grammar.edn")

#?(:clj
   (defn- slurp-repo-file [path what]
     (if-let [url (io/resource path)]
       (slurp url)
       (if (.exists (io/file path))
         (slurp path)
         (throw (ex-info (str what " missing") {:path path}))))))

(defn load-guest-grammar
  ([] #?(:clj (load-guest-grammar (slurp-repo-file guest-grammar-path "guest grammar"))
         :cljs (throw (ex-info "authority-claims/load-guest-grammar requires text inject on cljs"
                               {:path guest-grammar-path}))))
  ([edn-text] #?(:clj (edn/read-string edn-text)
                 :cljs (throw (ex-info "authority-claims requires clj" {:text (count edn-text)})))))

(def load-surface-status cm/load-surface-status)

;; `:admitted-builtins` holds STRINGS ("/" and "f32*" are not bare EDN
;; symbols); every other key holds symbols. Heads are compared as strings so
;; the two spellings of one head are one head.
(defn- head-name [x] (str (if (keyword? x) (name x) x)))

(defn- sugar-claims [gg]
  (for [[k v] (:sugar gg)
        :let [excluded (feature-keys-that-are-not-heads k)]
        h (cond
            (and excluded (nil? (:head excluded))) nil
            excluded [(:head excluded)]
            (seq (:forms v)) (filter symbol? (:forms v))
            :else [(symbol (name k))])]
    {:head (head-name h)
     :backends (:backends v)
     :source [:guest-grammar :sugar k]}))

(defn- flat-set-claims [gg k]
  (for [h (get gg k)] {:head (head-name h) :source [:guest-grammar k]}))

(defn- floating-point-claims [gg]
  (for [family [:arithmetic :unary :comparisons :reinterpretation
                :conversions :bounded-transcendental :decimal-parse]
        h (get-in gg [:floating-point family])]
    {:head (head-name h) :source [:guest-grammar :floating-point family]}))

(def claimed-surface-status-sections
  "The surface-status sections read as claims.

  NOT `conformance-matrix/surface-status-sections`, which is
  `[:invariants :collections :other-gaps]` -- that list is for a different
  question (which entries may link a conformance case) and it both includes
  `:invariants`, which is not a claim of admission, and omits
  `:checked-memory`, which is: `:kernel-memory-windows` and `:slice-carrier`
  name 61 heads between them in their `:surface`. Borrowing it silently
  dropped `:checked-memory` from this enumeration while `claim-sources` said
  it was read -- this file's own defect, caught by reading its own list back
  against the code."
  [:collections :other-gaps :checked-memory])

(defn- surface-status-claims
  "`:operations` / `:surface` of every `:implemented*` entry.

  Two shapes are honoured rather than flattened, because both are the
  authority declining to claim admission and a check that ignored them would
  report the authority's own honesty as a gap:

  * a per-head map carrying `:admitted false` -- the shape
    `:set-literal :operations` took on 2026-09-03 for `contains?`;
  * an entry whose `:measurement` records the canonical compiler REJECTING the
    surface, which is `:data-host-argument` (`the canonical compiler does not
    implement data-host-arg ... the sugar is kotoba-wasm-only`). The predicate
    is `conformance-matrix/measured-compiler-rejection?`, already used to pair
    such entries with the orphan note."
  [ss]
  (for [section claimed-surface-status-sections
        [nm entry] (get ss section)
        :when (str/starts-with? (str (:disposition entry)) ":implemented")
        :when (not (cm/measured-compiler-rejection? entry))
        [field raw] [[:operations (:operations entry)] [:surface (:surface entry)]]
        :when (coll? raw)
        [h detail] (if (map? raw) raw (map vector raw (repeat nil)))
        :when (symbol? h)
        :when (not (false? (:admitted detail)))]
    {:head (head-name h) :source [:surface-status section nm field]}))

(defn claims
  "Every claim row: `{:head \"count\" :source [...] :backends #{...}?}`."
  [gg ss]
  (vec (concat (sugar-claims gg)
               (flat-set-claims gg :arithmetic)
               (flat-set-claims gg :comparisons)
               (flat-set-claims gg :predicates)
               (flat-set-claims gg :core-special-forms)
               (flat-set-claims gg :admitted-builtins)
               (floating-point-claims gg)
               (surface-status-claims ss))))

(defn claimed-heads
  "Distinct heads, each with the sources that claim it."
  [gg ss]
  (->> (claims gg ss)
       (group-by :head)
       (map (fn [[h rows]] {:head h :sources (vec (sort-by pr-str (map :source rows)))}))
       (sort-by :head)
       vec))

;; --- gaps that are recorded rather than fixed ------------------------------

(def stale-claim-exceptions
  "Heads an authority claims that measurably have no lowering in the frontend
  this repository pins, recorded because they are not this change's to fix.

  Each group carries a date, a reason and a closing condition. A bare skip
  list would make this check unfalsifiable in the direction that matters: the
  next `count` would be added to it and nothing would say so.

  The entries are held to BOTH directions.
  `a-recorded-exception-must-still-be-a-gap` fails when a head listed here
  starts working, and names it -- so an excuse cannot outlive what it excuses.
  That is deliberate: the fix for that red is to delete the line, and the red
  is the only thing that will tell anyone the line is deletable."
  [{:heads
    #{"alloc" "alloc-checked" "bit-shift-left" "bit-shift-right" "byte-at"
      "byte-store!" "bytes-len" "bytes-ptr" "call-indirect" "cap-acquire"
      "f32" "f32*" "f32+" "f32-" "f32<" "f32<=" "f32=" "f32>" "f32>="
      "f32div" "f32neg" "f32sqrt" "has-capability?" "host-i64-roundtrip"
      "i32-store!" "i64" "i64*" "i64+" "i64-" "i64and" "i64or" "i64shl"
      "i64shr" "i64ushr" "i64xor" "max" "mem-byte-at" "mem-i32-at"
      "memory-grow" "memory-pages" "min" "mod" "rem" "result-err?"
      "result-status" "result-write!" "str-len" "str-ptr"
      "unsigned-bit-shift-right"}
    :as-of "2026-09-03"
    :claimed-by [:guest-grammar :admitted-builtins]
    :reason
    "`:admitted-builtins` carries no `:backends` annotation, and the grammar's
     own prose says what it is: `that set is the legacy wasm emitter's builtin
     vocabulary -- the neighbours are \"i64+\", \"alloc\", \"str-ptr\" -- and is
     a different surface, not a second name for these`
     (lang/guest-grammar.edn, above `:floating-point`). Measured 2026-09-03
     against kotoba-sema 24a59c74, 49 of its 190 heads have no lowering in the
     `:compiler` frontend and 141 do, so the set is a MIXTURE of two backends'
     vocabularies under one unannotated key. Reporting the 49 as gaps would
     assert they are missing from a backend that never claimed them; dropping
     the key would stop checking the 141 that are real compiler claims."
    :closes-when
    "`:admitted-builtins` gains a per-head `:backends` annotation, or is split
     into a compiler set and a legacy-emitter set. Then the compiler-claimed
     subset is checked with the rest and this group goes away. Nothing here
     depends on the split happening: until it does, the other 141 heads in the
     key are checked normally."}

   {:heads #{"keyword?" "string?" "symbol?" "string="}
    :as-of "2026-09-03"
    :claimed-by [:guest-grammar :predicates
                 :surface-status :other-gaps :portable-value-model :operations]
    :reason
    "Claimed twice each and refused `operation has no admitted lowering` at
     every probe. `string=` looks like a spelling that drifted rather than an
     unimplemented operation: `string=?` IS admitted, so is
     `string-contains?`, and `:string-predicate-typing :surface
     #{string-contains? string=?}` names both correctly -- so one file spells
     the head two ways and only one spelling exists. The three predicates are
     a different question: `string-length`, `string-substring` and
     `string-concat` beside them in `:predicates` all lower."
    :closes-when
    "`string=` is corrected to `string=?` in both `:predicates` and
     `:portable-value-model :operations`, and the three predicates are either
     lowered or moved to a `:not-yet-implemented` disposition."}])

(defn exceptions-by-head []
  (into {} (for [g stale-claim-exceptions, h (:heads g)] [h (dissoc g :heads)])))

(def min-claimed-heads
  "Evidence floor. A run that enumerated nothing found nothing wrong, and must
  not be able to say so: without this, deleting `:operations` from every
  surface-status entry would turn this check green.

  A ratchet -- raise it as the authority grows, never lower it to make a red
  go away. Measured 2026-09-03: 344 distinct heads across 10 keys."
  320)

(defn validate
  "Cross-check claims against measurement.

  `measure` is `head -> {:verdict :admitted | :refused-for-argument | :absent
  ...}` and is injected: the frontend is a `:test`-only dependency here.

  The two verdicts that are not `:absent` are BOTH admission. A head refused
  because its argument was the wrong shape is a head that exists -- `(contains?
  #{:a} :a)` is refused by name, pointing at `typed-set-contains`, and that is
  the authority being right. Collapsing the two is how a sweep produces a green
  that means nothing."
  [gg ss measure]
  (let [heads (claimed-heads gg ss)
        excepted (exceptions-by-head)
        measured (mapv #(assoc % :measurement (measure (:head %))) heads)
        absent? #(= :absent (:verdict (:measurement %)))
        problems (transient [])]
    (doseq [{:keys [head sources measurement] :as row} measured]
      (when (and (absent? row) (not (excepted head)))
        (conj! problems
               {:type :claimed-head-has-no-lowering
                :head head :sources sources
                :refusal (:msg measurement)
                :why "an authority names this head as admitted and the frontend
                      has no arm for it at any arity, in any position, with any
                      argument shape probed"}))
      (when (and (not (absent? row)) (excepted head))
        (conj! problems
               {:type :recorded-exception-no-longer-applies
                :head head
                ;; the reason prose is in the source; a failure needs the head,
                ;; when it was recorded, and what was supposed to close it
                :recorded (select-keys (excepted head) [:as-of :closes-when])
                :measured measurement
                :why "this head is recorded as a gap and it now has an arm;
                      delete it from stale-claim-exceptions"})))
    (doseq [[h _] excepted]
      (when-not (some #(= h (:head %)) heads)
        (conj! problems
               {:type :exception-for-an-unclaimed-head :head h
                :why "nothing claims this head any more, so excusing it excuses
                      nothing; delete it from stale-claim-exceptions"})))
    (let [ps (persistent! problems)
          n (count heads)]
      {:ok? (and (empty? ps) (>= n min-claimed-heads))
       :problems (cond-> ps
                   (< n min-claimed-heads)
                   (conj {:type :claim-floor :got n :need min-claimed-heads}))
       :claimed n
       :absent (count (filter absent? measured))
       :measured measured})))
