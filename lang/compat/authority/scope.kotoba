(ns authority.scope
  "Authority as a value in a lattice, not a string in a comparison.

  Every capability check in this fleet is the same question — does what the
  holder was granted cover what they are asking for — and it is written once
  per URI scheme, each time as a string equality with a trailing-`*` special
  case. That shape has a failure mode it cannot see. `cacao.core/covers?`
  strips the `*` and calls `starts-with?`, so

      kotoba://graph/alice*   covers   kotoba://graph/alice-evil

  Today's minters happen to always put the `*` after a `/`, so the hazard is
  latent rather than live. But it is latent in the *comparison*, which means
  every future minter has to keep an invariant by hand that nothing checks.

  A scope here is a vector of segments. The same grant is
  `[\"kotoba\" \"graph\" \"alice\"]`, segments compare by `=`, and there is
  no substring left to confuse. Prefix confusion is not rejected; it is
  unrepresentable.

  **The wire format does not change.** `render` reproduces the existing
  strings byte for byte and `parse` accepts them, so adopting this is a
  replacement for the comparison, not a migration of the tokens. All eight
  schemes measured in the fleet on 2026-08-15 round-trip:

      kotoba://can/…  kotoba://graph/…  kotoba://op/…  kotoba://org/…
      kotoba://cap/…  kotoba://authority/…  kotoba://tenant/…
      kotoba-rad://<rid>/push/<ref>

  ## The order

  `covers?` is a partial order over scopes and `meet` is its greatest lower
  bound. Attenuation is therefore not a rule that delegation obeys — it is
  the only thing `meet` can produce. See `authority.chain`.

  ## Bottom is the empty set, not a sentinel

  There is no `::denied` value. A grant that reaches nothing is a scope set
  with no members, and `covered?` on it is false through the ordinary code
  path rather than a special case. This is deliberate: `nil` punning to
  \"allow\" is how a missing policy becomes a public database, and a
  sentinel is only safe while every caller remembers to test for it."
  (:require [kotoba.lang.text :as str]))

;; ── shape ────────────────────────────────────────────────────────────────────

(def ^:private separator "://")

(defn- segment?
  "A path segment: a non-empty string that cannot change the shape of the
  rendering it will appear in.

  `/` is excluded because it is the segment delimiter — a segment carrying
  one would render into two, and the round-trip law would be the thing that
  broke rather than the check. `\"*\"` is excluded because the wildcard has
  a value of its own (`:*`); permitting both spellings would make two
  distinct scopes render identically."
  [x]
  (and (string? x) (seq x)
       (not (str/includes? x "/"))
       (not= "*" x)))

(defn valid?
  "Whether SCOPE is a scope: a scheme, at least one segment, and `:*` only
  ever last.

  A wildcard in the middle would have to mean `any single segment`, which is
  a second wildcard semantics nothing on the wire can express. One meaning
  or none."
  [scope]
  (and (vector? scope)
       (<= 2 (count scope))
       (segment? (first scope))
       (every? #(or (= :* %) (segment? %)) scope)
       (not-any? #(= :* %) (subvec scope 0 (dec (count scope))))))

;; ── wire ─────────────────────────────────────────────────────────────────────

(defn parse
  "A resource string → a scope, or nil when it is not one.

  nil rather than a throw or an error map because the only safe reading of
  an unparseable grant is that it grants nothing, and `nil` cannot be
  accidentally treated as authority by the functions below — they all take
  scopes and a non-scope covers nothing. Use `parse-all` when the *count* of
  rejects matters, which is whenever a caller is deciding on a set."
  [s]
  (when (string? s)
    (let [i (str/index-of s separator)]
      (when (and i (pos? i))
        (let [scheme (subs s 0 i)
              path (subs s (+ i (count separator)))]
          ;; empty segments are rejected here rather than normalised away:
          ;; `a//b` and `a/b` must not be the same authority
          (when (and (seq path)
                     (not (str/starts-with? path "/"))
                     (not (str/ends-with? path "/"))
                     (not (str/includes? path "//")))
            (let [scope (into [scheme]
                              (map #(if (= "*" %) :* %))
                              (str/split path #"/"))]
              (when (valid? scope) scope))))))))

(defn render
  "A scope → its resource string, or nil when SCOPE is not one.

  Inverse of `parse` on every valid scope, which is what lets this namespace
  be adopted without reminting a single token."
  [scope]
  (when (valid? scope)
    (str (first scope) separator
         (str/join "/" (map #(if (= :* %) "*" %) (subvec scope 1))))))

(defn parse-all
  "Resource strings → `{:scopes #{…} :rejected [\"…\"]}`.

  Both halves, always. A caller that ignores `:rejected` still ends up with
  a *narrower* authority than it was handed, which is the safe direction; a
  caller that checks it can refuse the whole grant. What no caller can do is
  mistake `nothing parsed` for `nothing was sent` — the distinction the
  five-question rule (superproject ADR-2608136000) exists to keep."
  [strings]
  (reduce (fn [acc s]
            (if-let [scope (parse s)]
              (update acc :scopes conj scope)
              (update acc :rejected conj s)))
          {:scopes #{} :rejected []}
          (or strings [])))

;; ── the order ────────────────────────────────────────────────────────────────

(defn covers?
  "Does PARENT grant at least everything CHILD grants?

  Reflexive, transitive and antisymmetric, so this is a partial order and
  `meet` below is its greatest lower bound.

  A trailing `:*` covers any *strictly longer* scope sharing the prefix —
  `[\"k\" \"graph\" :*]` reaches `[\"k\" \"graph\" \"g1\"]` but not
  `[\"k\" \"graph\"]` itself. That matches the string convention it
  replaces, and it is the safer of the two readings: a grant over a
  namespace's contents is not a grant over the namespace."
  [parent child]
  (and (vector? parent) (vector? child)
       (let [np (count parent) nc (count child)]
         (loop [i 0]
           (cond
             (= i np) (= i nc)                        ; exact, parent exhausted
             (= :* (nth parent i)) (and (= i (dec np)) ; wildcard: only as a tail
                                        (> nc i))
             (= i nc) false                           ; child ran out first
             (not= (nth parent i) (nth child i)) false
             :else (recur (inc i)))))))

(defn meet
  "The greatest scope both PARENT and CHILD grant, or nil when they share
  nothing.

  Because scopes form a tree under `covers?`, two comparable scopes meet at
  the narrower one and two incomparable scopes have no common lower bound at
  all. There is no case where a meet must be synthesised."
  [a b]
  (cond (covers? a b) b
        (covers? b a) a
        :else nil))

(defn normalize
  "SCOPES → the antichain granting exactly the same authority.

  A scope covered by another in the same set contributes nothing, so
  dropping it changes no decision. Two grants that differ only in redundant
  members become `=`, which is what makes a canonical form possible and what
  keeps `meet-sets` from growing on every fold.

  Deduplication is the result set's job, not `distinct`'s: this has to accept
  its own output, and `clojure.core/distinct` destructures its argument
  sequentially, so a set input reaches `nth` and throws. The idempotence law
  in `authority.scope-test` is what found that."
  [scopes]
  (let [v (into [] (filter valid?) (or scopes []))]
    ;; a duplicate does not remove itself: `(not= % x)` is false for its twin,
    ;; and the result set collapses the pair
    (into #{} (remove (fn [x] (some #(and (not= % x) (covers? % x)) v))) v)))

(defn meet-sets
  "The greatest authority both scope sets grant.

  O(|a|·|b|) before normalisation, and antichains in practice hold a handful
  of scopes — a delegation chain folds in time linear in its length."
  [a b]
  (normalize (for [x a y b :let [m (meet x y)] :when m] m)))

(defn covered?
  "Is REQUESTED within SCOPES? The one question the rest of the fleet asks.

  False on an empty set, on a malformed request, and on a request nothing in
  the set reaches — one code path, so there is no arrangement of inputs that
  answers this by accident."
  [scopes requested]
  (boolean (some #(covers? % requested) scopes)))

(defn sorted
  "SCOPES rendered and sorted — the deterministic form for receipts, hashes
  and diffs. Sorting on the rendering rather than the vector avoids ordering
  `:*` against a string, which is not a comparison either platform defines."
  [scopes]
  (vec (sort (keep render scopes))))
