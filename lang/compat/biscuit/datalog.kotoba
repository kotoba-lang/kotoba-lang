(ns biscuit.datalog
  "The bounded Datalog a biscuit authorizer runs.

  ## Why this is not a second copy of `kotoba-lang/datalog`

  That library is the workspace's Datalog engine and this one deliberately
  does not replace it. They answer different shapes:

  | | `kotoba-lang/datalog` | here |
  |---|---|---|
  | facts | **EAV triples**, indexed | **n-ary** `[pred t …]`, no index |
  | source | a database value | a token's blocks |
  | size | the corpus | tens of facts |
  | termination | range-restricted | range-restricted **plus a budget** |

  Biscuit's facts are n-ary predicates (`right(\"file1\", \"read\")`), and
  flattening them into triples to reuse an EAV engine means inventing entity
  ids that the token never had and that two implementations would have to
  agree on. The workspace rule is not to reimplement the same thing; an
  n-ary evaluator over a token is a different thing, and it is ~100 lines
  because it never needs an index.

  **The trigger to revisit**: if a third n-ary evaluator appears, extract
  one. Two things that can substitute for each other are one thing
  (ADR-2607299700) — these two cannot.

  ## The budget is a safety property, not a performance knob

  An authorizer runs attacker-supplied rules. Range restriction guarantees
  termination in theory; a rule with five variables over a hundred facts
  terminates in numbers nobody wants to serve. So evaluation carries a fact
  budget and **exceeding it is a distinct outcome** — `:refused` with
  `:budget-exceeded`, never an empty result set. A truncated derivation that
  answered `no facts` would be an authorizer that denies for the wrong
  reason today and allows for the wrong reason tomorrow."
  (:require [clojure.set :as set]))

(def default-budget
  "Facts an evaluation may derive before it refuses. Deliberately small: a
  token is not a database."
  1024)

(defn variable?
  "Variables are symbols beginning with `?`. Constants are anything else —
  strings, numbers, keywords, booleans."
  [t]
  (and (symbol? t) (= \? (first (name t)))))

(defn- unify [pattern fact bindings]
  (when (and (= (count pattern) (count fact))
             (= (first pattern) (first fact)))
    (reduce (fn [b [p f]]
              (cond
                (nil? b) (reduced nil)
                (variable? p) (if-let [v (get b p)]
                                (if (= v f) b (reduced nil))
                                (assoc b p f))
                (= p f) b
                :else (reduced nil)))
            bindings
            (map vector (rest pattern) (rest fact)))))

(defn- solve
  "All bindings satisfying `body` against `facts`."
  [body facts]
  (reduce (fn [bindings pattern]
            (into #{}
                  (comp (mapcat (fn [b] (keep #(unify pattern % b) facts)))
                        (remove nil?))
                  bindings))
          #{{}}
          body))

(defn- ground [pattern bindings]
  (mapv (fn [t] (if (variable? t) (get bindings t t) t)) pattern))

(defn- unbound-head-vars [{:keys [head body]}]
  (let [body-vars (into #{} (comp (mapcat rest) (filter variable?)) body)]
    (into [] (comp (filter variable?) (remove body-vars)) (rest head))))

(defn saturate
  "Facts closed under `rules`, or a refusal.

  -> `{:facts #{…} :steps n}` / `{:refused :budget-exceeded :facts n}` /
  `{:refused :unsafe-rule :rule r :vars [...]}`.

  A rule whose head carries a variable its body never binds is **unsafe** and
  is refused rather than skipped: skipping it silently changes what the token
  says, and the party who wrote it is the one who can fix it."
  ([facts rules] (saturate facts rules default-budget))
  ([facts rules budget]
   (if-let [bad (first (keep (fn [r] (let [v (unbound-head-vars r)]
                                       (when (seq v) [r v])))
                             rules))]
     {:refused :unsafe-rule :rule (first bad) :vars (second bad)}
     (loop [known (set facts) steps 0]
       (let [derived (into #{}
                           (mapcat (fn [{:keys [head body]}]
                                     (map #(ground head %) (solve body known))))
                           rules)
             next* (set/union known derived)]
         (cond
           (> (count next*) budget) {:refused :budget-exceeded :facts (count next*)}
           (= next* known) {:facts known :steps steps}
           :else (recur next* (inc steps))))))))

(defn query
  "Bindings for `body` over `facts`. `#{}` means no derivation — which is an
  answer, not a failure."
  [facts body]
  (solve body facts))

(defn satisfied?
  "Does `body` have at least one derivation?"
  [facts body]
  (boolean (seq (query facts body))))
