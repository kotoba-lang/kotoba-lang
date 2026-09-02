(ns kotoba.lang.clojure-core-compat-test
  "`lang/compat/clojure/core.kotoba` -- `ex-info` / `ex-message` / `ex-data` --
  RUN, not just declared, and run through the ability they exist for.

  These three are only worth having because the typed abort ability
  (`lang/abort-ability.edn`) carries a `:document` as its error type. So the
  test that matters is not `(ex-message (ex-info \"x\" d))`; it is a `throw` of
  an `ex-info` caught in another function and read back there, which is the
  shape a `.cljc` module already writes.

  The oracle for the ROUND TRIP is clojure.core itself: the same message and
  the same data through `clojure.core/ex-info`, asked the same two questions.
  Where this module deliberately diverges -- it returns a document rather than
  a Throwable, and it has no nil -- the divergence is asserted rather than
  described, so the argument for the separate `:equivalence` in
  `lang/compat.edn` cannot quietly evaporate."
  (:require [clojure.edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.kir :as kir]
            [kotoba.sema :as sema]))

(def ^:private module (slurp "lang/compat/clojure/core.kotoba"))

;; The module is a library: no `main`. Joining it to a harness is the same
;; route `clojure_string_compat_test` takes, and for the same reason -- this
;; repository has no project linker to call.
(defn- lowered [harness]
  (let [body (->> (sema/read-forms module)
                  (remove #(and (seq? %) (= 'ns (first %))))
                  (map pr-str)
                  (str/join "\n"))]
    (kir/lower (sema/analyze (str body "\n" harness)))))

(defn- run [harness]
  (long (kir/execute (lowered harness) 'main [])))

(defn- refusal [harness]
  (try (do (lowered harness) nil)
       (catch Throwable e (ex-message e))))

;; ---------------------------------------------------------------------------
;; the round trip the module exists for

(def ^:private thrower
  "(defn- parse [s :string] :i64
     (if (string=? s \"\")
       (throw (ex-info \"empty input\" (document-map (document-keyword :input) (document-string s))))
       (string-length s)))")

(deftest an-ex-info-survives-throw-catch-and-read-back
  (testing "the message comes back out of the caught document, byte for byte"
    (is (= (count "empty input")
           (run (str thrower
                     "(defn main [] :i64 (try (parse \"\") (catch e (string-length (ex-message e)))))")))))
  (testing "and so does the data map"
    (is (= 1 (run (str thrower
                       "(defn main [] :i64 (try (parse \"\") (catch e (document-count (ex-data e)))))")))))
  (testing "the ok path does not build one"
    (is (= 3 (run (str thrower
                       "(defn main [] :i64 (try (parse \"abc\") (catch e 0)))")))))
  (testing "the throwing function's inferred interface IS the document E"
    (let [hir (sema/analyze (str (->> (sema/read-forms module)
                                      (remove #(and (seq? %) (= 'ns (first %))))
                                      (map pr-str)
                                      (str/join "\n"))
                                 "\n" thrower
                                 "(defn main [] :i64 (try (parse \"\") (catch e 0)))"))
          parse (first (filter #(= 'parse (:name %)) (:functions hir)))]
      (is (= [:result :i64 :document] (:result parse)))
      (is (= #{:abort} (:effects parse))))))

(deftest a-document-error-type-survives-slice-2-propagation
  (testing "`read2` writes no throw and no try; it is aborting because `parse`
            is, and the document reaches the catcher two calls away"
    (is (= (count "empty input")
           (run (str thrower
                     "(defn- read2 [s :string] :i64 (+ 1 (parse s)))
                      (defn main [] :i64 (try (read2 \"\") (catch e (string-length (ex-message e)))))"))))))

(deftest an-explicit-catch-document-pins-the-error-type
  (is (= (count "empty input")
         (run (str thrower
                   "(defn main [] :i64 (try (parse \"\") (catch :document e (string-length (ex-message e)))))")))))

;; ---------------------------------------------------------------------------
;; against clojure.core itself

(deftest the-two-questions-agree-with-clojure-core
  (doseq [[message data] [["empty input" {:input ""}]
                          ["" {}]
                          ["boom" {:a 1 :b 2}]]]
    (testing (pr-str [message data])
      (let [oracle (clojure.core/ex-info message data)
            entries (str/join " " (map (fn [[k v]]
                                         (str "(document-keyword " k ") (document-i64 " (if (number? v) v 0) ")"))
                                       (filter (comp number? val) data)))
            built (str "(defn- build [] :document (ex-info \"" message "\" (document-map " entries ")))")]
        (is (= (count (clojure.core/ex-message oracle))
               (run (str built "(defn main [] :i64 (string-length (ex-message (build))))")))
            "ex-message answers the same message")
        (is (= (count (filter (comp number? val) (clojure.core/ex-data oracle)))
               (run (str built "(defn main [] :i64 (document-count (ex-data (build))))")))
            "ex-data answers a map of the same size")))))

;; ---------------------------------------------------------------------------
;; the divergences, asserted

(deftest there-is-no-nil-and-a-non-map-traps
  (testing "clojure.core/ex-message of an ExceptionInfo with no message is nil;
            here a document with no :message reads as the empty string"
    (is (nil? (clojure.core/ex-message (clojure.core/ex-info nil {}))))
    (is (= 0 (run "(defn- e0 [] :document (document-map (document-keyword :data) (document-i64 1)))
                   (defn main [] :i64 (string-length (ex-message (e0))))"))))
  (testing "clojure.core/ex-data of a plain Throwable is nil; here a document
            with no :data reads as document-null -- which is a VALUE, and a
            caller who treats it as a container is trapped rather than told"
    (is (nil? (clojure.core/ex-data (RuntimeException. "x"))))
    (is (= 1 (run "(defn- e0 [] :document (document-map (document-keyword :message) (document-string \"m\")))
                   (defn main [] :i64 (if (document-equal? (ex-data (e0)) (document-null)) 1 0))")))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"document-container-required"
         (run "(defn- e0 [] :document (document-map (document-keyword :message) (document-string \"m\")))
               (defn main [] :i64 (document-count (ex-data (e0))))"))
        "where (count (ex-data e)) is 0 in Clojure"))
  (testing "clojure.core/ex-message of a non-ExceptionInfo ANSWERS; here a
            document that is not a map TRAPS. Nothing is silently wrong, but
            the program stops where Clojure would have returned a value"
    (is (= "x" (clojure.core/ex-message (RuntimeException. "x"))))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"document-map-required"
         (run "(defn main [] :i64 (string-length (ex-message (document-i64 3))))")))))

(deftest the-absent-arities-are-not-symmetric
  ;; MEASURED 2026-09-02, and it is not what the obvious sentence would have
  ;; said. Under-arity is refused. OVER-arity is not: the frontend admits a
  ;; three-argument call to a two-parameter function and DROPS the third,
  ;; without a word -- `(two 1 2 3)` on `(defn- two [a b] (+ a b))` answers 3.
  ;; That is a general hole in this frontend, not something about this module,
  ;; and it is pinned here because `lang/compat.edn` would otherwise claim a
  ;; caller "gets an arity refusal, never a different answer" and be wrong in
  ;; one of the two directions.
  (testing "(ex-info msg) -- too few -- is refused at check time"
    (is (= "function call arity mismatch"
           (refusal "(defn main [] :i64 (document-count (ex-info \"m\")))"))))
  (testing "(ex-info msg data cause) -- too many -- is ADMITTED, and the cause
            is discarded silently"
    (is (nil? (refusal "(defn main [] :i64 (document-count (ex-info \"m\" (document-null) (document-null))))")))
    (is (= 3 (run "(defn- two [a :i64 b :i64] :i64 (+ a b))
                   (defn main [] :i64 (two 1 2 3))"))
        "the general case, so the hole is not read as one about ex-info")))

(deftest the-data-argument-must-be-a-document
  ;; This lives here rather than in lang/conformance/abort/ on purpose: it is
  ;; refused by the TYPE CHECKER, with code :kotoba.error/subset-reject, and
  ;; every case in the abort conformance set carries a :kotoba.error/abort-*
  ;; code that abort_conformance_test asserts. Admitting this one there would
  ;; have meant weakening the assertion that makes the set mean something.
  ;; lang/abort-ability.edn :conformance :not-here records that.
  (testing "a string where a document belongs"
    (is (= "expression type mismatch: expected document, got string"
           (refusal "(defn main [] :i64 (string-length (ex-message (ex-info \"m\" \"nope\"))))"))))
  (testing "an i64 where a document belongs"
    (is (= "expression type mismatch: expected document, got i64"
           (refusal "(defn main [] :i64 (string-length (ex-message (ex-info \"m\" 7))))"))))
  (testing "the map literal a .cljc author would actually write, `{:input s}`:
            a map literal is a CONSTANT document, and `s` is a local"
    (is (= "expression type mismatch: expected i64, got string"
           (refusal "(defn- p [s :string] :i64
                       (if (string=? s \"\") (throw (ex-info \"empty\" {:input s})) 1))
                     (defn main [] :i64 (try (p \"\") (catch e (string-length (ex-message e)))))")))))

(deftest the-manifest-entry-describes-what-is-here
  (let [entry (get-in (clojure.edn/read-string (slurp "lang/compat.edn"))
                      [:modules :clojure.core])]
    (is (= "lang/compat/clojure/core.kotoba" (:path entry)))
    (is (= '#{ex-info ex-message ex-data} (:provides entry)))
    (is (not= :exact (:equivalence entry))
        "a document is not a Throwable, and the entry must not claim it is")))
