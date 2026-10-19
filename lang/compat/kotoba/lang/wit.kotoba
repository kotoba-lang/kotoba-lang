(ns kotoba.lang.wit
  "WIT (WebAssembly Interface Types) interface as EDN + a deny-by-default
  capability-token data model. Layer 2 (cap/effect) of the kotoba foundational
  stdlib.

  This is the *user-facing* face of the existing enforcement boundary in
  `kotoba-clj` (`effects.rs` / `policy.rs`): it gives a cell author a typed
  vocabulary for what a host import is and what a cell may request. It invents
  NO new enforcement gate — a policy is just a set of granted capability tokens,
  and `allows?` is deny-by-default because an ungranted capability is simply not
  in the set. The compiler's per-cid, interprocedural effect gate remains the
  authoritative check; this lib is the data contract the author writes against.

  Zero third-party runtime deps; .cljc (JVM / SCI / CLJS / GraalVM / kotoba-WASM).")

;; ---------- shapes ----------

(defn interface? [x]
  (and (map? x)
       (string? (:wit/interface x))
       (or (nil? (:wit/functions x)) (vector? (:wit/functions x)))
       (or (nil? (:wit/types x))     (map? (:wit/types x)))))

(defn function? [x]
  (and (map? x)
       (string? (:name x))
       (or (nil? (:params x)) (vector? (:params x)))))

;; ---------- capabilities ----------

(defn- infer-effects [fn-name]
  ;; a conservative, name-based inference for the common kotoba host imports.
  ;; A function may override via :wit/effects. Anything unrecognized is just
  ;; :call — the gate still checks it, it simply carries no finer effect.
  (let [n (name fn-name)]
    (cond
      (re-find #"^(get|read|list|head|peek)" n) #{:read}
      (re-find #"^(put|write|set|delete|remove|create|update|post|patch)" n) #{:write}
      :else #{:call})))

(defn capability
  "Mint a capability token for `interface-name` / `fn-name`. `opts` may carry
  `:effects` (a set); otherwise effects are inferred from `fn-name`. The token
  is plain EDN — content-addressable, comparable."
  ([interface-name fn-name]
   (capability interface-name fn-name nil))
  ([interface-name fn-name opts]
   (let [effects (or (:effects opts) (:wit/effects opts) (infer-effects fn-name))]
     {:wit/capability (str interface-name "/" (name fn-name))
      :wit/effects    (set effects)})))

(defn capabilities
  "Return the seq of capability tokens an interface exposes — one per function.
  Functions may declare `:wit/effects` to override name-based inference."
  [iface]
  (eduction (comp (map (fn [f]
                         (capability (:wit/interface iface)
                                     (:name f)
                                     {:effects (:wit/effects f)})))
                  (distinct))
            (:wit/functions iface)))

;; ---------- validation ----------

(defn- problem [path val reason]
  {:path path :val val :reason reason})

(defn- validate-function [f path]
  (cond-> '()
    (not (function? f))
    (conj (problem (conj path :function) f :wit/not-a-function))
    (and (function? f) (some #(not (and (map? %) (string? (:name %)) (string? (:type %))))
                             (:params f)))
    (conj (problem (conj path :function :params) f :wit/bad-param))))

(defn validate-interface
  "Return a seq of problem maps for `iface`. Empty seq means valid."
  [iface]
  (cond
    (not (interface? iface))
    (list (problem [] iface :wit/not-an-interface))

    :else
    (let [fns  (:wit/functions iface)
          dups (for [[name freq] (frequencies (map :name fns))
                     :when (> freq 1)]
                 name)]
      (concat
       (mapcat (fn [i f] (validate-function f [:wit/functions i])) (range) fns)
       (when (seq dups)
         (list (problem [:wit/functions] dups :wit/duplicate-function-names)))))))

;; ---------- policy (deny-by-default) ----------

(defn policy
  "Return an empty (deny-all) policy. A policy is the set of capability tokens
  a caller has granted. Anything not in the set is denied — by construction,
  not by convention."
  []
  #{})

(defn grant
  "Return a policy that additionally allows `cap` (a capability string or
  token). Idempotent."
  [pol cap]
  (conj pol (if (map? cap) (:wit/capability cap) cap)))

(defn revoke
  "Return a policy that no longer allows `cap`."
  [pol cap]
  (disj pol (if (map? cap) (:wit/capability cap) cap)))

(defn allows?
  "True iff `pol` grants `cap`. Deny-by-default: an empty policy allows nothing."
  [pol cap]
  (contains? pol (if (map? cap) (:wit/capability cap) cap)))
