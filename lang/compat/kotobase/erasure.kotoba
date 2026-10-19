(ns kotobase.erasure
  "Binding an erasure scope to a key, so that erasure is something that can
  actually be done and afterwards told apart from loss.

  The chain preserves retracted facts on purpose (ADR-2607071610: that is what
  an audit view is for). A datom cannot be unwritten, so `erase this person's
  data` is only answerable if the value was never in the chain to begin with —
  it is a content address, and the bytes are sealed under a key that can be
  destroyed. `kotobase.classification` is what forces sensitive attributes into
  that shape; this namespace is what makes the second half true.

  The plane holds no crypto and no vault. `kagi` already has the whole key
  lifecycle — unlock → VMK → compartment key → per-item DEK → AES-256-GCM, with
  NIST-shaped states and a transition table where `:destroyed` is reachable only
  from `:revoked` or `:decrypt-or-verify-only`, so an active key cannot be
  destroyed by accident. So this namespace does not implement any of it: the
  host injects a `key-state` lookup, and kagi is one implementation of it.

  The distinction this exists for: **absent because erased and absent because
  missing must not look the same.** One is a completed obligation with a record;
  the other is a fault. A reader who cannot tell them apart will report a broken
  store as a fulfilled deletion request, or the reverse."
  (:require [kotobase.classification :as classification]))

(def erased-states
  "Key states after which the sealed bytes are unrecoverable. `:revoked` is not
  one of them: a revoked key is out of use and still exists, so the content is
  withheld rather than gone, and saying `erased` about it would be a false
  claim on a compliance record."
  #{:destroyed})

(def withheld-states
  "Key states where the bytes exist and may not be unwrapped now."
  #{:preactive :revoked})

(defn scope-errors
  "What is wrong with the binding from erasure scopes to keys.

  `schema` is a classified schema; `bindings` maps an erasure scope to a key
  id. Every scope the schema names needs one: a scope with no key is a
  deletion request with nothing to destroy, and it fails here rather than at
  the moment somebody asks."
  [schema bindings]
  (let [scopes (into #{} (keep (comp :erasure-scope val)) schema)
        bound (set (keys bindings))]
    (vec
     (concat
      (for [scope (sort (remove bound scopes))]
        {:error :erasure-scope-has-no-key :scope scope})
      (for [[scope key-id] (sort-by key bindings)
            :when (not (and (string? key-id) (seq key-id)))]
        {:error :key-id-must-be-a-non-empty-string :scope scope :value key-id})
      (for [scope (sort (remove scopes bound))]
        ;; a key bound to nothing is not dangerous, but it is a scope somebody
        ;; believes is covered
        {:error :key-bound-to-no-scope :scope scope})))))

(defn resolve-reference
  "What a by-reference value is, given the key that seals it.

  Returns one of:
  `{:content/state :available :content/address …}`
  `{:content/state :erased :content/address … :content/key-id … :content/erased-at …}`
  `{:content/state :withheld :content/address … :content/key-state …}`

  `:erased` carries the address on purpose. The hash stays in the chain and is
  a dangling reference afterwards — that is not a leak and not an oversight, it
  is the honest record that a fact existed here and its content is gone."
  [{:keys [address key-id key-state destroyed-at]}]
  (cond
    (not (and (string? address) (seq address)))
    {:content/state :invalid :content/reason :no-address}

    (not (and (string? key-id) (seq key-id)))
    ;; sealed bytes with no key named is neither erased nor available, and
    ;; guessing either way is how a fault gets filed as a deletion
    {:content/state :invalid :content/reason :no-key-id :content/address address}

    (contains? erased-states key-state)
    {:content/state :erased :content/address address :content/key-id key-id
     :content/erased-at destroyed-at}

    (contains? withheld-states key-state)
    {:content/state :withheld :content/address address :content/key-state key-state}

    (= :active key-state)
    {:content/state :available :content/address address}

    (= :decrypt-or-verify-only key-state)
    ;; retired for new sealing, still readable — which is the point of the
    ;; state existing
    {:content/state :available :content/address address}

    :else
    {:content/state :invalid :content/reason :unknown-key-state
     :content/address address :content/key-state key-state}))

(defn erasure-plan
  "Which keys a request touches, for a schema and a set of scopes.

  Deliberately returns the keys rather than destroying anything: destruction
  runs in the vault, under its own governor and its own ledger, and a plane
  that could destroy a key from a query path would be a plane that can erase
  evidence."
  [schema bindings scopes]
  (let [scopes (set scopes)
        attributes (into (sorted-set)
                         (keep (fn [[attribute {:keys [class erasure-scope]}]]
                                 (when (and (contains? scopes erasure-scope)
                                            (not (classification/inline-allowed? class)))
                                   attribute)))
                         schema)
        inline (into (sorted-set)
                     (keep (fn [[attribute {:keys [class erasure-scope]}]]
                             (when (and (contains? scopes erasure-scope)
                                        (classification/inline-allowed? class))
                               attribute)))
                     schema)]
    {:erasure/keys (into (sorted-set) (keep bindings) scopes)
     :erasure/attributes attributes
     ;; an inline attribute inside a scope being erased is a problem the plan
     ;; must name: destroying the key does nothing to it, and it stays in the
     ;; chain forever
     :erasure/not-erasable-inline inline
     :erasure/complete? (empty? inline)}))
