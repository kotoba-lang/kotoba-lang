(ns biscuit.expression
  "Biscuit expressions: a stack machine, evaluated for one variable binding.

  ## A declared subset, evaluated completely -- not a partial evaluator

  `biscuit.wire` states the rule this namespace has to live under: *an operator
  it silently dropped is a check that no longer restricts, which reads as a
  more permissive token rather than as an error*. So this does not implement
  some operators and ignore the others. It implements a NAMED subset and
  refuses, by name, any expression containing anything outside it.

  The subset is chosen by what a delegation actually needs to say:

      check if time($t), $t < 2026-09-01T00:00:00Z     a token that expires
      check if amount($a), $a <= 1000                  a token with a ceiling

  which is comparison, negation, and boolean combination. Everything else in
  the spec -- string operations, arithmetic, sets, regex, length, type-of, FFI,
  closures -- is refused. Refusing is not a gap here; it is the difference
  between this and a partial evaluator.

  ## The stack

  `ops` is a flat list in postfix order. A value pushes, a unary pops one and
  pushes one, a binary pops two and pushes one. A well-formed expression leaves
  exactly one value. Anything else -- underflow, leftovers, a non-boolean at
  the end of a check -- is refused rather than guessed at."
  (:require [kotoba.lang.text :as str]))

(def binary-ops
  "The binary operators this repo evaluates, by their spec enum value.

  Numbers come from biscuit-auth/biscuit `schema.proto` `OpBinary.Kind`, read
  rather than inferred. The ones absent here are absent on purpose."
  {0 :less-than 1 :greater-than 2 :less-or-equal 3 :greater-or-equal
   4 :equal 13 :and 14 :or})

(def unary-ops
  "`OpUnary.Kind`. Parens is a no-op that exists so a decoder can round-trip
  source; Negate is boolean negation."
  {0 :negate 1 :parens})

(defn- refuse [reason & [detail]]
  (cond-> {:refused reason} detail (assoc :detail detail)))

(defn- resolve-term
  "A term against one binding. An unbound variable is refused, not treated as
  nil: a comparison against nil would silently answer something."
  [t binding]
  (if (and (symbol? t) (str/starts-with? (name t) "?"))
    (if (contains? binding t)
      {:value (get binding t)}
      (refuse :unbound-variable t))
    {:value t}))

(defn- date? [v] (and (vector? v) (= :date (first v)) (number? (second v))))

(defn- comparable
  "-> `[a b]` as numbers when the two are the same ordered type, else nil.

  Integers compare with integers, and dates with dates -- `biscuit.wire`
  decodes a date as `[:date seconds]`, so the seconds are what order it. A
  date against a bare integer is refused: the two are different types in the
  spec, and comparing them would mean deciding on the issuer's behalf that a
  number was meant as a time.

  Strings and booleans have no order here. Refusing beats picking one, because
  a bound evaluated under a different collation than the issuer assumed is
  worse than a bound that will not evaluate."
  [a b]
  (cond
    (and (number? a) (number? b)) [a b]
    (and (date? a) (date? b)) [(second a) (second b)]
    :else nil))

(defn- apply-binary [k l r]
  (case k
    :equal {:value (= l r)}
    (:less-than :greater-than :less-or-equal :greater-or-equal)
    (if-let [[a b] (comparable l r)]
      {:value (case k
                :less-than (< a b) :greater-than (> a b)
                :less-or-equal (<= a b) :greater-or-equal (>= a b))}
      (refuse :not-comparable {:left l :right r}))
    (:and :or)
    (if-not (and (boolean? l) (boolean? r))
      (refuse :not-boolean {:left l :right r})
      {:value (if (= k :and) (and l r) (or l r))})))

(defn- apply-unary [k v]
  (case k
    :parens {:value v}
    :negate (if (boolean? v) {:value (not v)} (refuse :not-boolean {:value v}))))

(defn evaluate
  "-> `{:value v}` or `{:refused reason}`.

  `ops` is a decoded expression: a vector of `[:value term]`, `[:unary kind]`
  and `[:binary kind]`, where the kinds are the spec's enum NUMBERS. Unknown
  numbers are refused by name rather than skipped."
  [ops binding]
  (loop [[op & more] (vec ops) stack []]
    (if (nil? op)
      (cond
        (= 1 (count stack)) {:value (first stack)}
        (zero? (count stack)) (refuse :empty-expression)
        :else (refuse :values-left-on-the-stack {:count (count stack)}))
      (let [[kind arg] op
            step
            (case kind
              :value (resolve-term arg binding)
              :unary (if-let [k (unary-ops arg)]
                       (if (< (count stack) 1)
                         (refuse :stack-underflow {:op k})
                         (apply-unary k (peek stack)))
                       (refuse :unary-operator-not-evaluated {:kind arg}))
              :binary (if-let [k (binary-ops arg)]
                        (if (< (count stack) 2)
                          (refuse :stack-underflow {:op k})
                          (apply-binary k (nth stack (- (count stack) 2)) (peek stack)))
                        (refuse :binary-operator-not-evaluated {:kind arg}))
              (refuse :unknown-op {:kind kind}))]
        (if (:refused step)
          step
          (recur more (conj (case kind
                              :value stack
                              :unary (pop stack)
                              :binary (pop (pop stack)))
                            (:value step))))))))

(defn holds?
  "-> `{:ok? true}` when every expression evaluates to TRUE for `binding`, or
  `{:refused reason}`.

  A non-boolean result is refused, not coerced. `false` is an answer; a string
  where a boolean belongs is a token this implementation cannot judge."
  [exprs binding]
  (reduce (fn [_ ops]
            (let [r (evaluate ops binding)]
              (cond
                (:refused r) (reduced r)
                (not (boolean? (:value r))) (reduced (refuse :not-boolean {:value (:value r)}))
                (false? (:value r)) (reduced {:ok? false})
                :else {:ok? true})))
          {:ok? true}
          exprs))
