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
        freq (map name (:required float-ent))
        extra (first (remove (set rreq) rfields))
        ref-fields (map name (keys (:refs ref-ent)))
        ref-targets (map (fn [[_ v]] v) (:refs ref-ent))
        ref-other (first (remove (set ref-fields) (map name (:fields ref-ent))))]
    {:fixture-data
     (str "(defn fixture-data [sel :i64] :string\n  (cond\n"
          "    (= sel 0) \"" (jobj (map (fn [f] [f "v"]) rreq)) "\"\n"
          "    (= sel 1) \"" (jobj (map (fn [f] [f "v"]) (take 1 rreq))) "\"\n"
          "    (= sel 2) \"" (jobj (concat (map (fn [f] [f "v"]) rreq) [["bogus" "x"]])) "\"\n"
          "    (= sel 3) \"" (jobj (map (fn [f] [f "v"]) freq)) "\"\n"
          "    (= sel 4) \"" (jobj (map (fn [f] [f "v"]) (map name (:required ref-ent)))) "\"\n"
          "    :else \"{}\"))")
     :degenerate-fixture-1? (<= (count rreq) 1)
     :seeded-row
     (str "(defn seeded-row [i :i64] :string\n"
          "  (jobj (jsep (jsep (jkv \"id\" (string-concat \"" (:id-prefix root) "_\" (string-from-i64 i)))\n"
          "                    " (if extra
                                   (str "(jkv-raw \"" extra "\" (string-from-i64 i)))")
                                   (str "(jkv \"" (first rreq) "\" \"v\"))"))
          "\n              (jsep " (str/join " " (map (fn [f] (str "(jkv \"" f "\" \"v\")")) (take 2 rreq)))
          (if (< (count rreq) 2) " (jkv \"_\" \"\")" "") "))))")
     :ref-fields ref-fields
     :ref-targets ref-targets
     :ref-other ref-other
     :ref-arity (count ref-fields)}))

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
          body (block body "cred-row" (str (ref-row specs p f) "\n\n"))
          body (block body "oracle-expand"
                      (str (str/replace (expand-oracle p f) "REF_ENT" (:entity (:ref-ent p)))
                           "\n\n"))
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
          body (reduce (fn [acc [a b]] (if (= a b) acc (str/replace acc a b))) body subs)
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
      (println (str "WROTE\t" out "\t" (count (str/split-lines body)) " lines")))))
