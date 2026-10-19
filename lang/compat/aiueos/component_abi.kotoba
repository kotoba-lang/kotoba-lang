(ns aiueos.component-abi
  "Authority-side translation for the portable Kotoba Component ABI.

   This namespace has no provider implementation. It only says which named
   Component imports Aiueos may decide and which existing Aiueos capability a
   successful decision must contain. Kototama still owns engine-specific
   bindings."
  (:require [kotoba.abi.contract :as abi]))

(def component-import->capability
  "Closed, one-to-one authority vocabulary for every v1 Kotoba Component
  import.  A capability that is not offered by the deployment graph remains a
  denial; this map never supplies a fallback or ambient host implementation."
  {(abi/component-import-key 1) :identity/sign
   (abi/component-import-key 2) :identity/verify
   (abi/component-import-key 3) :hash/sha256
   (abi/component-import-key 4) :http/post
   (abi/component-import-key 5) :log/read
   (abi/component-import-key 6) :log/write
   (abi/component-import-key 7) :clock/monotonic
   (abi/component-import-key 13) :http/get-stream
   (abi/component-import-key 14) :object/get-stream
   (abi/component-import-key 15) :object/put-block
   (abi/component-import-key 16) :object/compare-and-set-ref})

(declare decision-grants-imports?)

(defn capability-for-import [component-import]
  (get component-import->capability component-import))

(defn requested-capabilities!
  "Translate a closed set of ABI imports into Aiueos capabilities. An unknown
  import is denied before policy evaluation, so it cannot become ambient host
  authority through a fallback mapping."
  [imports]
  (when-not (set? imports)
    (throw (ex-info "Component imports must be a set"
                    {:phase :aiueos-component-abi})))
  (let [capabilities (mapv capability-for-import imports)]
    (when (some nil? capabilities)
      (throw (ex-info "Component import has no Aiueos authority mapping"
                      {:phase :aiueos-component-abi
                       :imports imports})))
   (set capabilities)))

(def lease-keys
  #{:aiueos/lease-id :aiueos/epoch :aiueos/not-before :aiueos/expires-at
    :aiueos/capabilities :aiueos/component-imports :aiueos/abilities})

(defn narrow-ability
  "Intersect an artifact-requested ability with an Aiueos policy ceiling.

  Policy may only reduce numeric authority. Target and operation must remain
  exact so a policy typo cannot silently redirect a capability. The effective
  audit id is authority-owned, allowing policy to bind all resulting receipts
  to its own audit stream."
  [requested ceiling]
  (when-not (and (abi/valid-ability? requested)
                 (abi/valid-ability? ceiling)
                 (= (:target requested) (:target ceiling))
                 (= (:operation requested) (:operation ceiling)))
    (throw (ex-info "Ability policy must match the requested target and operation"
                    {:phase :aiueos-ability-policy
                     :requested requested :ceiling ceiling})))
  (-> requested
      (assoc :max-bytes (min (:max-bytes requested) (:max-bytes ceiling))
             :max-items (min (:max-items requested) (:max-items ceiling))
             :deadline-ms (min (:deadline-ms requested) (:deadline-ms ceiling))
             :audit-id (:audit-id ceiling))))

(defn narrow-abilities
  "Apply a closed per-import Aiueos policy to requested abilities.

  When POLICY is supplied its keys must exactly match IMPORTS: missing entries
  deny issuance and extra entries cannot smuggle authority into the lease.
  A nil policy preserves the legacy exact descriptor for callers that have not
  yet configured a policy ceiling."
  [imports requested policy]
  (when-not (and (set? imports)
                 (map? requested)
                 (= imports (set (keys requested)))
                 (every? abi/valid-ability? (vals requested))
                 (or (nil? policy)
                     (and (map? policy)
                          (= imports (set (keys policy)))
                          (every? abi/valid-ability? (vals policy)))))
    (throw (ex-info "Ability policy must be complete and closed over Component imports"
                    {:phase :aiueos-ability-policy :imports imports})))
  (if (nil? policy)
    requested
    (reduce-kv
     (fn [effective import ability]
       (assoc effective import (narrow-ability ability (get policy import))))
     {}
     requested)))

(defn issue-lease
  "Issue the authority-side, short-lived Component lease after a grant.
  The caller is responsible for transporting/signing this exact data when it
  crosses a process boundary; Kototama must never synthesize it."
  [{:keys [decision imports abilities ability-policy now epoch ttl-ms lease-id]}]
  (when-not (and (= :grant (:aiueos/decision decision))
                 (set? imports) (map? abilities)
                 (= imports (set (keys abilities)))
                 (every? abi/valid-ability? (vals abilities))
                 (integer? now) (not (neg? now))
                 (pos-int? epoch) (pos-int? ttl-ms)
                 (string? lease-id) (seq lease-id)
                 (decision-grants-imports? decision imports))
    (throw (ex-info "Aiueos cannot issue an invalid Component lease"
                    {:phase :aiueos-component-lease})))
  (let [effective (narrow-abilities imports abilities ability-policy)]
    {:aiueos/lease-id lease-id :aiueos/epoch epoch
     :aiueos/not-before now :aiueos/expires-at (+ now ttl-ms)
     :aiueos/capabilities (requested-capabilities! imports)
     :aiueos/component-imports imports :aiueos/abilities effective}))

(defn lease-authorizes?
  "Check a lease at every provider invocation. Epoch mismatch is revocation:
  Murakumo can advance the epoch without trusting the guest or runtime."
  [lease current-epoch now import ability]
  (and (map? lease) (= lease-keys (set (keys lease)))
       (pos-int? current-epoch) (integer? now)
       (= current-epoch (:aiueos/epoch lease))
       (<= (:aiueos/not-before lease) now)
       (< now (:aiueos/expires-at lease))
       (contains? (:aiueos/component-imports lease) import)
       (= ability (get (:aiueos/abilities lease) import))
       (contains? (:aiueos/capabilities lease) (capability-for-import import))))

(defn decision-grants-imports?
  "True only when a grant decision contains every authority needed by IMPORTS.
  Extra Aiueos capabilities do not become Component bindings; Kototama still
  binds exactly the artifact's declared import set."
  [decision imports]
  (and (= :grant (:aiueos/decision decision))
       (let [requested (requested-capabilities! imports)
             granted (set (:aiueos/capabilities decision))]
         (every? granted requested))))
