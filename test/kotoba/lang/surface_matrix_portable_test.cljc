(ns kotoba.lang.surface-matrix-portable-test
  "The genuinely portable slice of kotoba.lang.surface-matrix-test:
  `validate-status` and `render-markdown` take a surface-status map directly
  and touch no file. `load-surface-status`'s `:cljs` branch deliberately
  throws (`... requires text inject on cljs`), and `write-matrix!` /
  `check-matrix!` / `-main` are `#?(:clj ...)`-guarded entirely -- they do not
  exist as vars on this host at all, appropriately, since writing
  `docs/lang/surface-matrix.md` is not something a browser or nbb script
  should decide to do as a side effect of loading this namespace.

  This file exercises the render/validate LOGIC against a small synthetic
  surface-status, not this repository's actual `lang/surface-status.edn` --
  the tests that check specific real-data fields
  (`computed-invoke-keeps-dynamic-heads-visible-...`, etc.) and the real
  generated-file check (`on-disk-matrix-matches-regenerated`) stay in
  kotoba.lang.surface-matrix-test (.clj)."
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer [deftest is testing] :include-macros true])
            [kotoba.lang.surface-matrix :as sm]
            [kotoba.lang.text :as str]))

(def ^:private status
  {:kotoba.lang.surface-status/version 3
   :kotoba.lang.surface-status/profile-version 1
   :kotoba.lang.surface-status/as-of "2026-09-09"
   :kotoba.lang.surface-status/adr "ADR-0000000000"
   :dispositions {:no-ambient-authority {:meaning "capability required for every host effect"}}
   :invariants {:no-ambient-authority :no-ambient-authority}
   :collections {:bounded-vector {:disposition :implemented :implementation #{:kir :wasm32-kotoba-v1}}}
   :other-gaps {:nested-let {:disposition :partial :note "example gap"}}})

(deftest a-well-formed-status-validates
  (let [v (sm/validate-status status)]
    (is (true? (:ok? v)) (pr-str (:problems v)))))

(deftest a-status-missing-a-section-is-refused
  (let [v (sm/validate-status (dissoc status :collections))]
    (is (false? (:ok? v)))
    (is (some #(and (= :missing-section (:type %)) (= :collections (:section %))) (:problems v)))))

(deftest a-status-with-no-version-number-is-refused
  (let [v (sm/validate-status (assoc status :kotoba.lang.surface-status/version "3"))]
    (is (false? (:ok? v)))
    (is (some #(= :missing-version (:type %)) (:problems v)))))

(deftest render-markdown-contains-header-and-every-section
  (let [md (sm/render-markdown status)]
    (is (str/includes? md "# Kotoba language surface matrix"))
    (is (str/includes? md "WBS: **T2.2**"))
    (is (str/includes? md "## Security / language invariants"))
    (is (str/includes? md "## Collections"))
    (is (str/includes? md "## Other surface (gaps & partials)"))
    (is (str/includes? md "`no-ambient-authority`"))))

(deftest render-markdown-escapes-pipes-and-newlines-in-notes
  (let [md (sm/render-markdown
            (assoc-in status [:other-gaps :nested-let :note] "a | pipe\nand a newline"))]
    (is (str/includes? md "a \\| pipe and a newline"))
    (is (not (str/includes? md "a | pipe\nand")))))

(deftest render-markdown-lists-multiple-backends-sorted-and-comma-joined
  (let [md (sm/render-markdown
            (assoc-in status [:collections :bounded-vector :implementation]
                      #{:wasm32-kotoba-v1 :kir}))]
    (is (str/includes? md "kir, wasm32-kotoba-v1"))))
