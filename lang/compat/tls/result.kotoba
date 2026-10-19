(ns tls.result
  "Every fallible operation in this library returns a value, never throws.

  `[:ok v]` / `[:error e]`, which is the portable spelling of Kotoba's
  `[:result T E]` (root CLAUDE.md: errors are returned as values, and that is a
  permanent constraint rather than a backend gap). A TLS implementation is the
  worst possible place for an exception to carry a partial result out of a
  verifier: the whole point of an AEAD open is that on failure there is no
  plaintext, and a thrown `ex-info` with the plaintext in its `ex-data` is
  exactly the shape that leaks one.

  Every error carries the alert a real client would send (`tls.alert`) so a
  refusal is not merely a message -- it is the wire behaviour."
  (:refer-clojure :exclude [val]))

(defn ok [v] [:ok v])

(defn error
  "`alert` is a `tls.alert` keyword; `reason` a stable keyword a test can
   assert on; `data` optional context. No plaintext, key material or secret
   ever goes in `data` -- errors are logged."
  ([alert reason] (error alert reason {}))
  ([alert reason data] [:error (merge {:tls/alert alert :tls/reason reason} data)]))

(defn ok? [r] (and (vector? r) (= :ok (first r))))
(defn error? [r] (and (vector? r) (= :error (first r))))

(defn val
  "The value of an `[:ok v]`. Throws on an error -- deliberately: reaching for
   the value of a result you did not check is a bug in the caller, and the
   alternative (returning nil) is the exact shape this library exists to
   avoid, where a failed check and a successful one return the same thing."
  [r]
  (if (ok? r)
    (second r)
    (throw (ex-info "tls.result/val on an error result" {:result r}))))

(defn err [r] (when (error? r) (second r)))

(defn reason [r] (:tls/reason (err r)))
(defn alert [r] (:tls/alert (err r)))

(defmacro let-ok
  "Bind `[:ok v]` results, short-circuiting on the first error.

     (let-ok [a (parse x)
              b (parse y)]
       (ok (+ a b)))"
  [bindings & body]
  (if (empty? bindings)
    `(do ~@body)
    (let [[sym expr & more] bindings
          r (gensym "r")]
      `(let [~r ~expr]
         (if (error? ~r)
           ~r
           (let [~sym (second ~r)]
             (let-ok [~@more] ~@body)))))))
