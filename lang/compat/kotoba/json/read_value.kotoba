(ns kotoba.json.read-value
  "read-array, read-object, read-value -- addressed on its own.

  Split out of json.core on 2026-09-09 (ADR-2609091200). The unit
  here is the DEFINITION, and this repo's deps.edn names exactly the
  definitions it reaches -- nothing else.

  It holds 3 definitions, not one, because they call each
  other: read-array and read-object and read-value are mutually recursive, and the
  source says so itself with a (declare ...). Two definitions that call each
  other cannot be two repos with an acyclic dependency, so the unit is the
  cycle. Unison groups mutually recursive definitions the same way."
  (:require [kotoba.json.char-code :refer [char-code]]
            [kotoba.json.read-number :refer [read-number]]
            [kotoba.json.read-string-star :refer [read-string*]]
            [kotoba.json.skip-ws :refer [skip-ws]]
            [kotoba.json.starts :refer [starts?]])
)

(declare read-array read-object read-value)

(defn read-array [s i]
  (loop [i (skip-ws s (inc i)) out []]
    (case (nth s i nil)
      \] [out (inc i)]
      (let [[v j] (read-value s i)
            j (skip-ws s j)
            ch (nth s j nil)]
        (case ch
          \, (recur (skip-ws s (inc j)) (conj out v))
          \] [(conj out v) (inc j)]
          (throw (ex-info "expected comma or array end" {:pos j :char ch})))))))

(defn read-object [s i]
  (loop [i (skip-ws s (inc i)) out {}]
    (case (nth s i nil)
      \} [out (inc i)]
      (let [[k j] (read-string* s i)
            j (skip-ws s j)]
        (when-not (= \: (nth s j nil))
          (throw (ex-info "expected object colon" {:pos j})))
        (let [[v j] (read-value s (skip-ws s (inc j)))
              j (skip-ws s j)
              ch (nth s j nil)]
          (case ch
            \, (recur (skip-ws s (inc j)) (assoc out k v))
            \} [(assoc out k v) (inc j)]
            (throw (ex-info "expected comma or object end" {:pos j :char ch}))))))))

(defn read-value [s i]
  (let [i (skip-ws s i)
        ch (nth s i nil)]
    (cond
      (= ch \") (read-string* s i)
      (= ch \{) (read-object s i)
      (= ch \[) (read-array s i)
      (= ch \t) (if (starts? s i "true") [true (+ i 4)] (throw (ex-info "invalid JSON token" {:pos i})))
      (= ch \f) (if (starts? s i "false") [false (+ i 5)] (throw (ex-info "invalid JSON token" {:pos i})))
      (= ch \n) (if (starts? s i "null") [nil (+ i 4)] (throw (ex-info "invalid JSON token" {:pos i})))
      ;; Numeric literals for the same reason as `hex-val`: on ClojureScript
      ;; `(int \\0)` is 0 but `(int \\a)` is ALSO 0, so the old bound admitted
      ;; every letter into `read-number`.
      (or (= ch \-) (and ch (<= 48 (char-code ch) 57))) (read-number s i)
      :else (throw (ex-info "invalid JSON value" {:pos i :char ch})))))
