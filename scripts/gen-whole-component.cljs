#!/usr/bin/env nbb
;; gen-whole-component.cljs -- the whole-component port, generated from the
;; oracle's own entity-specs table.
;;
;; The wave-1 cohort's twenty oracles are one generated actor template. Measured
;; 2026-09-10: diffing src/aave/main.cljc against src/abb_robotics/main.cljc with
;; the repository name normalised away leaves the docstring and the entity-specs
;; table, and nothing else. So the whole-component port is mechanical -- and the
;; parts that must NOT be mechanical are exactly where the first hand port went
;; wrong.
;;
;; That miss is the reason this exists. Porting com-abb-robotics by hand carried
;; com-aadhaar's `{"expand":"identityId"}` into the expand oracle. BOM has no
;; identityId, so every selector would have named a field that does not exist,
;; `expand` would have folded over nothing, and four arms would have gone green
;; measuring an empty fold -- in the one port whose novelty was that the fold now
;; runs twice. Here every field name, prefix and entity name is DERIVED from the
;; spec table, so that class cannot be carried.
;;
;; WRITTEN IN nbb, NOT kbb. CLAUDE.md puts new operational tooling on kbb first.
;; Measured 2026-09-10 on this machine: `which kbb` finds nothing. Recorded here
;; rather than silently choosing the old host; the kbb port is owed.
;;
;;   nbb gen-whole-component.cljs <template.kotoba> <repo-dir> <src-dir-name>
;;
;; exit 0  wrote the component
;; exit 2  REFUSED -- could not parse the oracle, or the template lost an anchor

(ns gen-whole-component
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            ["fs" :as fs]
            ["path" :as path]))

(def argv (vec (remove #(str/ends-with? % ".cljs") (drop 2 (js->clj js/process.argv)))))

(defn refuse! [msg data]
  (println (str "REFUSED\t" msg "\t" (pr-str data)))
  (.exit js/process 2))

;; --- the oracle's table ----------------------------------------------------

(defn read-specs
  "The `entity-specs` vector out of a .cljc oracle, as data.

  Read with the EDN reader rather than pattern-matched: the table is ordinary
  data and a regex over it would be a second, weaker parser for something that
  already has one."
  [cljc]
  (let [i (str/index-of cljc "(def entity-specs")]
    (when-not i (refuse! "no entity-specs in the oracle" {}))
    (let [open (str/index-of cljc "[" i)
          ;; balance brackets from the opening one
          end (loop [j open depth 0]
                (cond
                  (>= j (count cljc)) (refuse! "unbalanced entity-specs vector" {})
                  (= \[ (nth cljc j)) (recur (inc j) (inc depth))
                  (= \] (nth cljc j)) (if (= 1 depth) j (recur (inc j) (dec depth)))
                  :else (recur (inc j) depth)))
          specs (edn/read-string (subs cljc open (inc end)))]
      (when-not (and (vector? specs) (seq specs) (every? map? specs))
        (refuse! "entity-specs did not read as a non-empty vector of maps" {:got (type specs)}))
      specs)))

;; --- derived names ---------------------------------------------------------

(defn- names [ks] (str/join "," (map name ks)))
(defn- pairs [m] (str/join "," (map (fn [[k v]] (str (name k) ":" (if (keyword? v) (name v) v))) m)))

(defn plan
  "Which entity plays which role in the fixtures, derived rather than chosen.

  root      the first spec with no refs -- the thing other rows point AT
  ref-ent   the first spec that HAS refs, so the expand fold has something to do
  float-ent the first spec with a :float coercion, which is the blocked arm"
  [specs]
  (let [root (or (first (filter #(empty? (:refs %)) specs)) (first specs))
        ref-ent (first (filter #(seq (:refs %)) specs))
        float-ent (first (filter #(some #{:float} (vals (:coerce %))) specs))]
    {:root root
     :ref-ent ref-ent
     :float-ent (or float-ent (second specs) root)
     :refs-exercised? (boolean ref-ent)
     :float-reached? (boolean float-ent)}))

;; --- the eight tables ------------------------------------------------------

(defn tables [specs]
  (let [arm (fn [f] (str/join "\n" (map (fn [s] (str "    (string=? entity \"" (:entity s) "\") \"" (f s) "\"")) specs)))]
    (str
     "(defn entity-count [] :i64 " (count specs) ")\n\n"
     "(defn entity-at [i :i64] :string\n  (cond\n"
     (str/join "\n" (map-indexed (fn [i s] (str "    (= i " i ") \"" (:entity s) "\"")) specs))
     "\n    :else \"\"))\n\n"
     "(defn plural-of [entity :string] :string\n  (cond\n" (arm :plural) "\n    :else \"\"))\n\n"
     "(defn id-prefix-of [entity :string] :string\n  (cond\n" (arm :id-prefix) "\n    :else \"\"))\n\n"
     "(defn fields-of [entity :string] :string\n  (cond\n" (arm #(names (:fields %))) "\n    :else \"\"))\n\n"
     "(defn required-of [entity :string] :string\n  (cond\n" (arm #(names (:required %))) "\n    :else \"\"))\n\n"
     ";; `:coerce` as `field:kind` pairs. A spec that declares `{}` answers \"\".\n"
     ";; A `float` arm is the one the aarch64 backend refuses -- see `coerce-field`.\n"
     "(defn coerce-table-of [entity :string] :string\n  (cond\n" (arm #(pairs (:coerce %))) "\n    :else \"\"))\n\n"
     ";; `:refs` as `field:entity` pairs, comma-separated. A spec with two refs\n"
     ";; makes `expand`'s fold run twice over one row.\n"
     "(defn refs-of [entity :string] :string\n  (cond\n" (arm #(pairs (:refs %))) "\n    :else \"\"))")))

;; --- the fixtures ----------------------------------------------------------

(defn- jobj [kvs] (str "{" (str/join "," (map (fn [[k v]] (str "\\\"" k "\\\":\\\"" v "\\\"")) kvs)) "}"))

(defn fixtures
  "fixture-data, seeded-row and the ref row, with every field name taken from
  the spec table. This is the block the hand port got wrong."
  [{:keys [root ref-ent float-ent]}]
  (let [rreq (map name (:required root))
        rfields (map name (:fields root))
        ;; The coercion fixture must CONTAIN the coerced field. Built from
        ;; `:required` alone it does not, whenever the coerced field is optional
        ;; -- and measured 2026-09-10 across the wave-1 sample, that is the
        ;; common case, not the rare one: com-aave, com-abb-robotics, com-adobe
        ;; and com-acquia all declare a coercion on a field they do not require.
        ;; com-abb-robotics is the sharp instance: Robot coerces `payloadKg` to
        ;; :float and requires only [model status], so a fixture from `:required`
        ;; omits the one field the arm exists to exercise, and the hand-written
        ;; component it replaced carried "payloadKg":"150.5" precisely because a
        ;; person noticed.
        fcoerce (map (fn [[k v]] [(name k) (case (if (keyword? v) (name v) (str v))
                                             "int" "7" "float" "1.5" "bool" "true" "v")])
                     (:coerce float-ent))
        freq (concat (map (fn [f] [f "v"]) (map name (:required float-ent)))
                     (remove (fn [[k _]] (some #{k} (map name (:required float-ent)))) fcoerce))
        extra (first (remove (set rreq) rfields))
        ref-fields (map name (keys (:refs ref-ent)))
        ref-targets (map (fn [[_ v]] v) (:refs ref-ent))
        ref-other (first (remove (set ref-fields) (map name (:fields ref-ent))))]
    {:fixture-data
     (str "(defn fixture-data [sel :i64] :string\n  (cond\n"
          "    (= sel 0) \"" (jobj (map (fn [f] [f "v"]) rreq)) "\"\n"
          "    (= sel 1) \"" (jobj (map (fn [f] [f "v"]) (take 1 rreq))) "\"\n"
          "    (= sel 2) \"" (jobj (concat (map (fn [f] [f "v"]) rreq) [["bogus" "x"]])) "\"\n"
          "    (= sel 3) \"" (jobj freq) "\"\n"
          "    (= sel 4) \"" (jobj (map (fn [f] [f "v"]) (map name (:required ref-ent)))) "\"\n"
          "    :else \"{}\"))")
     :degenerate-fixture-1? (<= (count rreq) 1)
     :seeded-row
     (str "(defn seeded-row [i :i64] :string\n"
          "  (jobj (jsep (jsep (jkv \"id\" (string-concat \"" (:id-prefix root) "_\" (string-from-i64 i)))\n"
          ;; NO fallback to (first rreq). When every field is required there is
          ;; no non-required field to carry the row index, and repeating the
          ;; first required one emits it TWICE -- a duplicate JSON key, the same
          ;; defect the ref-field-2 fallback below was removed for. Measured
          ;; 2026-09-11 on the shipped com-airtable component, whose Base
          ;; declares :fields and :required identical, so `seeded-row` wrote
          ;; "name" twice. Pad with the same `_` slot the <2-required case uses.
          "                    " (if extra
                                   (str "(jkv-raw \"" extra "\" (string-from-i64 i)))")
                                   (str "(jkv \"_\" \"\"))"))
          "\n              (jsep " (str/join " " (map (fn [f] (str "(jkv \"" f "\" \"v\")")) (take 2 rreq)))
          (if (< (count rreq) 2) " (jkv \"_\" \"\")" "") "))))")
     :seed-extra extra
     :ref-fields ref-fields
     :ref-targets ref-targets
     :ref-other ref-other
     :ref-arity (count ref-fields)}))


;; --- the filters oracle, from the root spec's OWN fields --------------------
;;
;; This block was reached only by the token pass, and the token pass substitutes
;; ENTITY names and REF-FIELD names -- not ordinary field names. So the template
;; repository's field names came through unchanged. Measured 2026-09-11 across
;; the wave-1 cohort: seventeen of twenty components filter on `jurisdiction`
;; and `externalId`, which are com-aadhaar's fields. Where they are foreign the
;; component ignores the unknown key and returns every row, so all six selectors
;; answer the SAME NUMBER -- com-agones and com-adyen both measured 5,5,5,5,5,5,
;; against com-aadhaar's 5,3,5,1,0,5 and com-aave's identical 5,3,5,1,0,5 where
;; the names were adapted. Six selectors and one value is a layer that cannot
;; tell a working filter from a broken one, which is the exact class this
;; generator's header claims cannot be carried.
;;
;; The floor: selector 1 must match every row and selector 2 none, on the SAME
;; field. That pair is what makes the rest mean anything -- a filter that
;; matched everything and a filter that matched nothing are the two ends, and
;; a component that ignores the key silently collapses them.

(defn filters-oracle [{:keys [root]} f]
  (let [rreq (map name (:required root))
        req1 (first rreq)
        extra (:seed-extra f)
        arms [(str "      ;; no filter at all -- the row count itself\n"
                   "      (= sel 0) (arr-count (apply-filters rows \"{}\" fields))")
              (str "      ;; a required field, the value every seeded row carries\n"
                   "      (= sel 1) (arr-count (apply-filters rows \"{\\\"" req1 "\\\":\\\"v\\\"}\" fields))")
              (str "      ;; the SAME field, a value no seeded row carries\n"
                   "      (= sel 2) (arr-count (apply-filters rows \"{\\\"" req1 "\\\":\\\"zzz\\\"}\" fields))")]
        idx (when extra
              [(str "      ;; the row index, which is unique -- exactly one row\n"
                    "      (= sel 3) (arr-count (apply-filters rows \"{\\\"" extra "\\\":\\\"1\\\"}\" fields))")
               (str "      ;; the row index, out of range -- no row\n"
                    "      (= sel 4) (arr-count (apply-filters rows \"{\\\"" extra "\\\":\\\"99\\\"}\" fields))")])]
    (str ";; Selector 1 must return every row and selector 2 none, on the same\n"
         ";; field; without that pair a green run cannot be told from a filter\n"
         ";; whose key this repository does not have.\n"
         (if idx
           ";; Selectors 3 and 4 index a non-required field that carries the row\n;; number, so they reach the one-row and the no-row cases.\n"
           ";; There are NO selectors 3 and 4: every field of this entity is\n;; required, so no field carries the row index and a uniqueness case\n;; cannot be derived. Recorded rather than faked with a repeated field.\n")
         "(defn oracle-filters [sel :i64] :i64\n"
         "  (let [rows (store-rows (seeded-store 5) \"" (:entity root) "\")\n"
         "        fields (fields-of \"" (:entity root) "\")]\n    (cond\n"
         (str/join "\n" (concat arms idx))
         "\n      :else (arr-count (apply-filters rows \"{\\\"notAField\\\":\\\"x\\\"}\" fields)))))")))


;; --- the handlers oracle, from the root spec's OWN fields -------------------
;;
;; Same carried-name class as the filters oracle, and it damaged two selectors
;; instead of a whole layer. Selector 6 is the update-SUCCESS arm -- selector 7
;; is already its 404 arm and selector 13 hashes a field the update is supposed
;; to have written -- but the template updates with {"jurisdiction":"US"}, and
;; no entity in this cohort except com-aadhaar's declares `jurisdiction`. An
;; unknown field is rejected, so selector 6 measured 400 and the 200 path was
;; never exercised. Selector 13 then hashed a field the rejected response does
;; not carry: measured 2026-09-11, it returned 7 in com-agones AND in
;; com-airtable, while selector 14 differed between them. A hash that does not
;; vary with the repository is hashing a constant.
;;
;; Nothing is lost by making 6 succeed. `reject-unknown` is one function and
;; `handle-create` calls it with the same `(fields-of entity)`; selector 2
;; already feeds it an unknown key. The rejection arm stays covered, and the
;; success arm stops being missing.

(defn handlers-oracle [{:keys [root]}]
  (let [ent (:entity root)
        pre (:id-prefix root)
        req1 (name (first (:required root)))
        upd (str "{\\\"" req1 "\\\":\\\"updated\\\"}")]
    (str ";; Selector 6 is the update SUCCESS arm and must be 200; 7 is its 404.\n"
         ";; Selector 13 hashes the field selector 6 wrote, so it is meaningful\n"
         ";; only while 6 succeeds -- if 6 ever returns 400 again, 13 is hashing\n"
         ";; an absent field and will read the same in every repository.\n"
         "(defn oracle-handlers [sel :i64] :i64\n"
         "  (let [store (seeded-store 2)]\n    (cond\n"
         "      (= sel 0) (as-int (field (handle-create store \"" ent "\" (fixture-data 0) 1) \"status\"))\n"
         "      (= sel 1) (as-int (field (handle-create store \"" ent "\" (fixture-data 1) 1) \"status\"))\n"
         "      (= sel 2) (as-int (field (handle-create store \"" ent "\" (fixture-data 2) 1) \"status\"))\n"
         "      (= sel 3) (as-int (field (handle-list store \"" ent "\" \"{}\") \"status\"))\n"
         "      (= sel 4) (as-int (field (handle-get store \"" ent "\" \"" pre "_0\" \"{}\") \"status\"))\n"
         "      (= sel 5) (as-int (field (handle-get store \"" ent "\" \"nope\" \"{}\") \"status\"))\n"
         "      (= sel 6) (as-int (field (handle-update store \"" ent "\" \"" pre "_0\"\n"
         "                                              \"" upd "\" 1) \"status\"))\n"
         "      (= sel 7) (as-int (field (handle-update store \"" ent "\" \"nope\" \"{}\" 1) \"status\"))\n"
         "      (= sel 8) (as-int (field (handle-delete store \"" ent "\" \"" pre "_0\") \"status\"))\n"
         "      (= sel 9) (as-int (field (handle-delete store \"" ent "\" \"nope\") \"status\"))\n"
         "      (= sel 10) (arr-count (store-rows (raw-field (handle-delete store \"" ent "\" \"" pre "_0\")\n"
         "                                                   \"store\")\n"
         "                                        \"" ent "\"))\n"
         "      (= sel 11) (arr-count (store-rows (raw-field (handle-create store \"" ent "\"\n"
         "                                                                 (fixture-data 0) 1)\n"
         "                                                   \"store\")\n"
         "                                        \"" ent "\"))\n"
         "      (= sel 12) (as-int (field (raw-field (handle-list store \"" ent "\" \"{}\") \"body\") \"total\"))\n"
         "      (= sel 13) (str-hash (field (raw-field (handle-update store \"" ent "\" \"" pre "_0\"\n"
         "                                                            \"" upd "\" 1)\n"
         "                                             \"body\")\n"
         "                                  \"" req1 "\"))\n"
         "      :else (str-hash (raw-field (handle-get store \"" ent "\" \"" pre "_1\" \"{}\") \"body\")))))")))

;; --- the ref row and the expand oracle, by ref ARITY ------------------------
;;
;; These two were the last blocks still reached only by the token pass, and that
;; is where a measured defect lived. `ref-field-2` fell back to `ref-field-1`
;; when the target entity had ONE ref, so a one-ref repository got
;; `childPartId -> projectId` on top of `parentPartId -> projectId`. Measured
;; 2026-09-10 on the shipped com-accela component: `cred-row` emitted
;; "projectId" TWICE -- a duplicate JSON key -- and `oracle-expand` selector 4,
;; the "expand BOTH refs" case, asked for "projectId,projectId" and returned
;; EXACTLY selector 0's value, 303754089. A selector that cannot tell a fold
;; that ran twice from one that ran once measures nothing, and it went green.
;;
;; Neither is caught by `amu check`: both objects are built with `jkv` calls, so
;; the compiler has no schema for them and answers :ok true either way.
;;
;; So they are generated, and the BOTH selector exists only when there is a
;; second ref for it to be about.

(defn ref-row [specs {:keys [ref-ent]} f]
  (let [prefix-of (into {} (map (juxt :entity :id-prefix) specs))
        rf (vec (:ref-fields f))
        rt (vec (:ref-targets f))
        filler (remove (set rf) (map name (:required ref-ent)))
        kv (fn [i] (str "(jkv \"" (nth rf i) "\" (string-concat \""
                        (get prefix-of (let [t (nth rt i)] (if (keyword? t) (name t) t)) "row") "_\" (string-from-i64 i)))"))
        third (if (> (count rf) 1) (kv 1)
                  (str "(jkv \"" (or (first filler) "note") "\" \"v\")"))
        fourth (str "(jkv \"" (or (if (> (count rf) 1) (first filler) (second filler)) "note2") "\" \"v\")")]
    (str ";; A " (:entity ref-ent) " row pointing at the row its "
         (if (> (count rf) 1) "TWO ref fields name" "one ref field names")
         ".\n"
         (if (> (count rf) 1)
           ";; Two refs make `expand`'s fold run twice over one row, which is the\n;; case `oracle-expand` selector 4 is about.\n"
           ";; One ref means the fold runs over a single pair, and a fold over one\n;; is not a fold -- which is why no selector 4 is generated below.\n")
         "(defn cred-row [i :i64] :string\n"
         "  (jobj (jsep (jsep (jkv \"id\" (string-concat \"" (:id-prefix ref-ent) "_\" (string-from-i64 i)))\n"
         "                    " (kv 0) ")\n"
         "              (jsep " third " " fourth "))))")))

(defn expand-oracle [{:keys [root]} f]
  (let [rf (vec (:ref-fields f))
        f1 (first rf)
        arms [(str "      ;; the ref resolves\n      (= sel 0) (str-hash (expand store rec \"{\\\"expand\\\":\\\"" f1 "\\\"}\"\n                                  (refs-of \"REF_ENT\")))")
              (str "      ;; a field `expand` is not asked for\n      (= sel 1) (str-hash (expand store rec \"{\\\"expand\\\":\\\"other\\\"}\"\n                                  (refs-of \"REF_ENT\")))")
              (str "      ;; the ref names a row the store does not hold\n      (= sel 2) (str-hash (expand store (cred-row 9) \"{\\\"expand\\\":\\\"" f1 "\\\"}\"\n                                  (refs-of \"REF_ENT\")))")
              (str "      ;; a spec with NO refs -- the fold runs over nothing\n      (= sel 3) (str-hash (expand store rec \"{\\\"expand\\\":\\\"" f1 "\\\"}\"\n                                  (refs-of \"" (:entity root) "\")))")]
        both (when (> (count rf) 1)
               (str "      ;; BOTH refs -- the fold runs twice over one row\n      (= sel 4) (str-hash (expand store rec \"{\\\"expand\\\":\\\"" (str/join "," rf) "\\\"}\"\n                                  (refs-of \"REF_ENT\")))"))]
    (str ";; Selectors 1 and 3 must collapse to the unexpanded record and 2 must\n"
         ";; differ from both; without that floor a green 0 cannot be told from a\n"
         ";; fold over nothing."
         (if both "\n;; Selector 4 exists because this entity declares two refs.\n"
             "\n;; There is NO selector 4: this entity declares one ref, so a `BOTH`\n;; case would duplicate selector 0 and measure nothing.\n")
         "(defn oracle-expand [sel :i64] :i64\n"
         "  (let [store (seeded-both 3)\n        rec (cred-row 1)]\n    (cond\n"
         (str/join "\n" (remove nil? (conj arms both)))
         "\n      :else (str-hash rec))))")))

;; --- splice ---------------------------------------------------------------
;;
;; The template's own names are the canon. Every one of them is replaced by the
;; role the target's spec table gives it, and a guard refuses when a target
;; entity happens to carry a canon name in a DIFFERENT role -- a silent
;; mis-mapping there would produce a component that compiles and addresses the
;; wrong rows.

(def canon
  {:root "Part" :ref-ent "BOM" :float-ent "Robot"
   :root-prefix "abbrobot_par" :ref-prefix "abbrobot_bom"
   :ref-field-1 "parentPartId" :ref-field-2 "childPartId"
   :ns "abb-robotics" :ns-prefix "abb_robotics" :src "abb_robotics"})

(defn- block
  "Replace the whole `(defn NAME ...)` form, found by its header and ended by
  the next top-level `(defn ` or `;; ---`. Anchors are checked: a template that
  lost one REFUSES rather than writing a component with a stale fixture."
  [src name replacement]
  (let [head (str "(defn " name " ")
        i0 (str/index-of src head)
        _ (when-not i0 (refuse! "template lost an anchor" {:defn name}))
        ;; Walk BACK over the comment lines immediately above the form and
        ;; replace those too. Measured 2026-09-10: regenerating cred-row for a
        ;; one-ref repository left the template's comment in place, so the file
        ;; said the row points at its target "through BOTH of its ref fields"
        ;; and named a `:childProjectId` that the substitution had invented --
        ;; directly above a generated body with one ref and no such field. A
        ;; comment reached only by token substitution carries exactly the
        ;; staleness the generated body was written to avoid, and it is worse
        ;; than a stale name because it reads as an explanation.
        i (loop [k i0]
            (let [prev-start (inc (or (str/last-index-of src "\n" (- k 2)) -1))]
              (if (and (> k 0) (str/starts-with? (subs src prev-start (min (count src) (+ prev-start 2))) ";;"))
                (recur prev-start)
                k)))]
    (let [rest-from (+ i0 (count head))
          j (or (first (sort (remove nil? [(str/index-of src "\n(defn " rest-from)
                                           (str/index-of src "\n;; ---" rest-from)])))
                (count src))]
      (str (subs src 0 i) replacement (subs src j)))))

(defn- guard! [specs p]
  (let [by-name (into {} (map (juxt :entity identity) specs))
        ;; Built by reduce, not as a literal: when a repository has no float
        ;; coercion the float role falls back to an entity that may already
        ;; hold another role, and a map literal with a duplicate key throws.
        ;; First role assigned wins, which matches the substitution order.
        role (reduce (fn [m [k e]] (if (contains? m e) m (assoc m e k)))
                     {}
                     [[:root (:entity (:root p))]
                      [:ref-ent (:entity (:ref-ent p))]
                      [:float-ent (:entity (:float-ent p))]])]
    (doseq [[k v] (select-keys canon [:root :ref-ent :float-ent])]
      (when (and (contains? by-name v) (not= k (get role v)))
        (refuse! "a target entity carries a template name in another role"
                 {:entity v :template-role k :target-role (get role v)})))))

(let [[tmpl-path repo-dir src-name] argv]
  (when-not (and tmpl-path repo-dir src-name)
    (refuse! "usage: <template.kotoba> <repo-dir> <src-dir-name>" {:argv argv}))
  (let [cljc-path (path/join repo-dir "src" src-name "main.cljc")]
    (when-not (fs/existsSync cljc-path) (refuse! "no oracle" {:path cljc-path}))
    (let [specs (read-specs (fs/readFileSync cljc-path "utf8"))
          p (plan specs)
          f (fixtures p)
          _ (guard! specs p)
          ;; A repository where NO spec declares a ref cannot get a derived
          ;; expand oracle. Measured 2026-09-10 on com-aave, whose four specs
          ;; are all `:refs {}`: `ref-row` indexed the empty ref-field vector and
          ;; the run died with "No item 0 in vector of length 0" -- an index
          ;; error, not a refusal, so it named nothing and wrote nothing.
          ;;
          ;; Refusing rather than inventing. The hand-written com-aave component
          ;; solves this by passing `expand` a SYNTHETIC ref table --
          ;; "liquidityIndex:ReserveData" -- so the fold runs even though the
          ;; real table yields nothing, with selector 0 left on the real table to
          ;; document that emptiness. That is a good design and a person chose
          ;; it. A generator that invented such a pair on its own would be
          ;; manufacturing the test input whose absence it exists to detect, and
          ;; the resulting arms would go green over a fold the production path
          ;; never performs.
          ;; SCOPED TO THE TWO BLOCKS THAT NEED A REF, not to the whole run.
          ;;
          ;; This was a `refuse!`, and refusing the run froze three repositories
          ;; out of every later fix. com-aftership, com-adyen and com-airtable
          ;; declare :refs {} on every spec, so each generator improvement since
          ;; -- the entity table, the fixtures, oracle-filters, oracle-handlers,
          ;; none of which touch a ref -- stopped at this line and left them
          ;; behind. Measured 2026-09-11: all three still answer 5,5,5,5,5,5 for
          ;; oracle-filters and 400/7 for oracle-handlers selectors 6 and 13,
          ;; the two defects already fixed everywhere else.
          ;;
          ;; The original reasoning is kept and still holds: a generator that
          ;; invented a ref pair would manufacture the input whose absence it
          ;; exists to detect. So cred-row and oracle-expand are still NOT
          ;; derived here. They are left exactly as they stand, which for these
          ;; repositories is a fold over nothing -- recorded as
          ;; :expand-fold :not-exercised in each migration record, so the
          ;; vacuity is on the record rather than hidden.
          ;;
          ;; What changes is only which blocks the absence stops.
          refs? (:refs-exercised? p)
          tmpl (fs/readFileSync tmpl-path "utf8")
          ;; 1. the tables, regenerated whole.
          ;;
          ;; ORDER MATTERS and it cost a wrong component to find out. Inserting
          ;; the generated block first and then deleting the seven originals by
          ;; name deletes the GENERATED ones -- `block` finds the first match,
          ;; and after the insert that is the new text. The originals survive,
          ;; the token pass rewrites their entity names, and the result compiles
          ;; while `id-prefix-of` still answers the template's prefixes. Delete
          ;; first, insert second.
          body (reduce (fn [acc n] (block acc n "")) tmpl
                       ["entity-at" "plural-of" "id-prefix-of" "fields-of"
                        "required-of" "coerce-table-of" "refs-of"])
          body (block body "entity-count" (str (tables specs) "\n\n"))
          ;; 2. the fixtures, regenerated whole
          body (block body "fixture-data" (str (:fixture-data f) "\n\n"))
          body (block body "seeded-row" (str (:seeded-row f) "\n\n"))
          ;; The ref row and the expand oracle, regenerated whole rather than
          ;; reached by the token pass. See the note above `ref-row`.
          body (block body "oracle-filters" (str (filters-oracle p f) "\n\n"))
          body (block body "oracle-handlers" (str (handlers-oracle p) "\n\n"))
          body (if refs? (block body "cred-row" (str (ref-row specs p f) "\n\n")) body)
          body (if refs?
                 (block body "oracle-expand"
                        (str (str/replace (expand-oracle p f) "REF_ENT" (:entity (:ref-ent p)))
                             "\n\n"))
                 body)
          ;; With no ref anywhere, the three ref blocks are DELETED rather than
          ;; carried. Leaving them was tried first and the evidence floor caught
          ;; it: the generator builds from the TEMPLATE, so an un-regenerated
          ;; cred-row keeps `parentPartId`, `childPartId` and the `BOM` entity,
          ;; and the output would name a relationship this repository does not
          ;; have. `cred-row`, `seeded-both` and `oracle-expand` form a closed
          ;; cluster -- cred-row feeds seeded-both, both feed oracle-expand, and
          ;; the only tie outside it is the oracle-expand export -- so the three
          ;; come out together or not at all.
          ;;
          ;; This is a public-surface change and it is the deliberate kind:
          ;; ADR-q9 allows dropping an export by an explicit API decision, and
          ;; a fold over nothing is exactly what should be absent rather than
          ;; present and vacuous.
          body (if refs? body
                   (reduce (fn [acc n] (block acc n ""))
                           body ["cred-row" "seeded-both" "oracle-expand"]))
          body (if refs? body
                   (let [before "oracle-facts oracle-expand oracle-clock"
                         after  "oracle-facts oracle-clock"]
                     (when-not (str/includes? body before)
                       (refuse! "cannot drop the oracle-expand export"
                                {:expected before
                                 :hint "the export vector moved; deleting the defn without the export would not compile"}))
                     (str/replace body before after)))
          ;; 3. every remaining canon token, by role
          subs [[(:ref-field-1 canon) (or (first (:ref-fields f)) (:ref-field-1 canon))]
                ;; NO fallback to ref-field-1. That fallback is what produced a
                ;; duplicate JSON key and a selector that duplicated another
                ;; selector's value. With cred-row and oracle-expand now
                ;; generated, a surviving `childPartId` means some OTHER block
                ;; still carries it, and the evidence floor below will say so
                ;; rather than papering over it with an alias.
                [(:ref-field-2 canon) (or (second (:ref-fields f)) (:ref-field-2 canon))]
                [(:root-prefix canon) (:id-prefix (:root p))]
                [(:ref-prefix canon) (:id-prefix (:ref-ent p))]
                [(:root canon) (:entity (:root p))]
                [(:ref-ent canon) (:entity (:ref-ent p))]
                [(:float-ent canon) (:entity (:float-ent p))]
                [(:ns canon) (str/replace src-name "_" "-")]
                [(:ns-prefix canon) src-name]
                [(str "src/" (:src canon) "/") (str "src/" src-name "/")]]
          ;; ENTITY NAMES are replaced on a word boundary; everything else is a
          ;; plain substring. Measured 2026-09-11 on com-agora: the template's
          ;; root entity is `Part`, agora's is `Channel`, and an unanchored
          ;; replacement rewrote the FIELD `maxParticipants` into
          ;; `maxChannelicipants` -- inside coerce-table-of and fields-of, both
          ;; of which are strings, so `amu check` returned :ok true and the
          ;; evidence floor (which scans for surviving `abbrobot` stems) saw
          ;; nothing wrong with a corrupted TARGET word.
          ;;
          ;; This is the same shape as the `.clj` / `.cljc` prefix aliasing this
          ;; workspace hit earlier: a substring replacement firing inside a
          ;; longer word. Prefixes and namespace tokens keep the plain form --
          ;; `abbrobot_par` cannot occur inside another identifier here, and
          ;; anchoring them would break `src/abb_robotics/`.
          entity-names (set (map (fn [[a _]] a)
                                 [[(:root canon)] [(:ref-ent canon)] [(:float-ent canon)]]))
          ;; A pair whose REPLACEMENT is nil is dropped, and said out loud.
          ;;
          ;; `ref-prefix` and `ref-ent` resolve through `(:ref-ent p)`, which is
          ;; nil when no spec declares a ref. While this ran only behind the
          ;; whole-run refusal that could not happen; with the refusal scoped to
          ;; the two ref blocks it can, and `str/replace` with a nil replacement
          ;; throws "Cannot read properties of null" from inside clojure.string
          ;; -- an error that names the reducer, not the token. Substituting a
          ;; token for nothing is never right, so this guard belongs here
          ;; whatever the caller.
          droppable (remove (fn [[_ b]] (string? b)) subs)
          _ (when (seq droppable)
              (println (str "SKIPPED-TOKENS\t"
                            (str/join "," (map first droppable))
                            "\tno target in this repository's spec table; left as they stand")))
          body (reduce (fn [acc [a b]]
                         (cond
                           (not (string? b)) acc
                           (= a b) acc
                           (contains? entity-names a)
                           (str/replace acc (re-pattern (str "\\b" a "\\b")) b)
                           :else (str/replace acc a b)))
                       body subs)
          out (path/join repo-dir "src" src-name "whole_component.kotoba")]
      ;; Evidence floor. `amu check` passes on a component whose tables still
      ;; answer the TEMPLATE's prefixes -- measured, that is exactly what the
      ;; first run of this generator produced -- so the check that matters is
      ;; that no canon token survived. A generator that cannot say this REFUSES
      ;; rather than writing.
      ;; The stems, not the canon values. Measured: the first version of this
      ;; check looked only at `canon`, and the four tokens that actually
      ;; survived were `abbrobot_cha`, `abbrobot_wor`, `abbrobot_rob` and
      ;; `abbrobot_pro` -- id prefixes of template entities the canon map never
      ;; names. A check written from the substitution table can only see what
      ;; the substitution table already handles.
      (let [stems (if (= src-name (:src canon)) []
                      (concat ["abbrobot" "abb_robotics" "abb-robotics"]
                              ;; A template ref-field that survives means a block
                              ;; carrying it was not regenerated. Previously the
                              ;; alias hid this by rewriting it to a real name.
                              (remove (set (:ref-fields f))
                                      [(:ref-field-1 canon) (:ref-field-2 canon)])))
            survivors (filter #(str/includes? body %) stems)]
        (when (seq survivors)
          (refuse! "template tokens survived the substitution"
                   {:tokens (vec survivors)
                    :hint "a table or fixture block was not regenerated; see the ORDER MATTERS note"})))
      (fs/writeFileSync out body)
      (println (str "SPECS\t" (count specs) "\t" (str/join "," (map :entity specs))))
      (println (str "ROOT\t" (:entity (:root p))))
      (println (str "REFS\t" (if (:refs-exercised? p)
                               (str (:entity (:ref-ent p)) " " (pairs (:refs (:ref-ent p))))
                               "none -- expand folds over nothing in this repository")))
      (println (str "FLOAT\t" (if (:float-reached? p) (:entity (:float-ent p)) "not reached")))
      (when (:degenerate-fixture-1? f)
        (println "WARN\tthe root entity requires one field, so fixture 1 cannot be a partial record"))
      (when-not refs?
        (println (str "DROPPED-BLOCKS\tcred-row,seeded-both,oracle-expand\t"
                      "no spec declares a ref, so this component has no expand "
                      "oracle; the oracle-expand export is dropped with them")))
      (println (str "WROTE\t" out "\t" (count (str/split-lines body)) " lines")))))
