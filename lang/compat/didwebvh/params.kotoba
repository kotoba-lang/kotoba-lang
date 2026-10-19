(ns didwebvh.params
  "The `parameters` object: what may appear, when, and what is in force.

   Parameters INHERIT. An entry that omits one keeps the previous value, so
   the state a resolver checks against is never the entry in front of it --
   it is the fold of every entry so far. `initial` starts that fold and
   `advance` continues it; both return the same `active` map, and both throw
   on a violation rather than returning a diminished one, because a
   parameters object that is wrong is not a DID whose properties are unknown.

   The one rule worth reading twice is `:authorized-keys`, because it is not
   `:update-keys`:

   - first entry -- the keys the entry itself declares;
   - later entry, no pre-rotation -- the keys the PREVIOUS entry left in
     force. An entry may rotate `updateKeys`, but it is signed by the old
     set, which is what makes rotation an act of the current holder;
   - later entry, pre-rotation active -- the keys THIS entry declares, which
     are admissible only because the previous entry committed to their hashes
     first. That is the whole value of pre-rotation: a stolen current key
     cannot name its thief's key as the successor, because the successor was
     named before the theft."
  (:require [didwebvh.hash :as h]
            [didwebvh.witness :as witness]))

(defn- fail! [code msg data]
  (throw (ex-info msg (assoc data :didwebvh/error code))))

(def ^:private known
  #{"method" "scid" "updateKeys" "nextKeyHashes" "portable" "witness"
    "watchers" "deactivated" "ttl"})

(defn- string-vector! [code v label]
  (when-not (and (sequential? v) (every? string? v))
    (fail! code (str label " must be an array of strings") {:value v}))
  (vec v))

(defn- check-known! [params]
  (let [unknown (remove known (keys params))]
    ;; Unknown parameters are refused rather than ignored. A resolver that
    ;; skips what it does not recognise silently accepts a log written for a
    ;; method it does not implement, and reports the pass as if it had checked.
    (when (seq unknown)
      (fail! :didwebvh/unknown-parameter
             "the parameters object carries a name this version does not define"
             {:unknown (vec unknown)}))))

(defn- check-method! [method]
  (when-not (= h/method-1-0 method)
    (fail! :didwebvh/unsupported-method
           (str "this library implements " h/method-1-0 " only")
           {:method method})))

(defn initial
  "The active parameters after the first entry."
  [params]
  (check-known! params)
  (let [method (get params "method")
        scid (get params "scid")
        update-keys (get params "updateKeys")
        next-key-hashes (get params "nextKeyHashes" [])
        portable (get params "portable" false)
        wit (get params "witness" {})
        watchers (get params "watchers" [])
        deactivated (get params "deactivated" false)
        ttl (get params "ttl" 3600)]
    (check-method! method)
    (when-not (string? scid)
      (fail! :didwebvh/missing-scid "the first entry must carry `scid`" {:scid scid}))
    (when-not (= h/scid-length (count scid))
      (fail! :didwebvh/bad-scid-length
             (str "a did:webvh 1.0 SCID is exactly " h/scid-length " base58btc characters")
             {:scid scid :length (count scid)}))
    (when-not (h/sha256-multihash? scid)
      (fail! :didwebvh/bad-scid-multihash
             "the SCID is not a base58btc sha2-256 multihash" {:scid scid}))
    (when-not (and (sequential? update-keys) (seq update-keys))
      (fail! :didwebvh/missing-update-keys
             "the first entry must carry a non-empty `updateKeys`" {:updateKeys update-keys}))
    (when-not (boolean? portable)
      (fail! :didwebvh/bad-portable "`portable` must be a boolean" {:portable portable}))
    (witness/validate! wit)
    (let [update-keys (string-vector! :didwebvh/bad-update-keys update-keys "updateKeys")
          next-key-hashes (string-vector! :didwebvh/bad-next-key-hashes
                                          next-key-hashes "nextKeyHashes")]
      {:method method
       :scid scid
       :update-keys update-keys
       :authorized-keys update-keys
       :next-key-hashes next-key-hashes
       :pre-rotation? (boolean (seq next-key-hashes))
       :portable? portable
       :witness wit
       :watchers (string-vector! :didwebvh/bad-watchers watchers "watchers")
       :deactivated? deactivated
       :ttl ttl})))

(defn advance
  "The active parameters after an entry that is not the first."
  [active params]
  (check-known! params)
  (let [pre-rotation? (:pre-rotation? active)
        declares-update-keys? (contains? params "updateKeys")
        declares-next? (contains? params "nextKeyHashes")]
    (when (contains? params "scid")
      (fail! :didwebvh/scid-restated
             "`scid` appears only in the first entry" {:scid (get params "scid")}))
    (when-let [method (get params "method")]
      (check-method! method))
    (when (contains? params "portable")
      (let [portable (get params "portable")]
        (when-not (boolean? portable)
          (fail! :didwebvh/bad-portable "`portable` must be a boolean" {:portable portable}))
        (when portable
          ;; Turning portability ON later would let a DID that was published
          ;; as pinned to one origin quietly become movable.
          (fail! :didwebvh/portable-enabled-late
                 "`portable` may only be set true in the first entry" {}))))
    (when (and pre-rotation? (not (and declares-update-keys? declares-next?)))
      (fail! :didwebvh/pre-rotation-inheritance
             "while pre-rotation is active every entry must state both `updateKeys` and `nextKeyHashes`"
             {:declared (vec (keys params))}))
    (let [update-keys (if declares-update-keys?
                        (string-vector! :didwebvh/bad-update-keys
                                        (get params "updateKeys") "updateKeys")
                        (:update-keys active))
          next-key-hashes (if declares-next?
                            (string-vector! :didwebvh/bad-next-key-hashes
                                            (get params "nextKeyHashes") "nextKeyHashes")
                            (:next-key-hashes active))
          wit (if (contains? params "witness") (get params "witness") (:witness active))]
      (when pre-rotation?
        (let [committed (set (:next-key-hashes active))
              offending (remove #(contains? committed (h/key-hash %)) update-keys)]
          ;; Every key, not only the new ones: a set that quietly retains a
          ;; key nobody pre-committed is a set the previous controller never
          ;; approved.
          (when (seq offending)
            (fail! :didwebvh/uncommitted-update-key
                   "an updateKey was not pre-committed in the previous entry's nextKeyHashes"
                   {:keys (vec offending)}))))
      (witness/validate! wit)
      {:method (or (get params "method") (:method active))
       :scid (:scid active)
       :update-keys update-keys
       :authorized-keys (if pre-rotation? update-keys (:update-keys active))
       :next-key-hashes next-key-hashes
       :pre-rotation? (boolean (seq next-key-hashes))
       :portable? (if (contains? params "portable")
                    (get params "portable")
                    (:portable? active))
       :witness wit
       :watchers (if (contains? params "watchers")
                   (string-vector! :didwebvh/bad-watchers (get params "watchers") "watchers")
                   (:watchers active))
       :deactivated? (if (contains? params "deactivated")
                       (get params "deactivated")
                       (:deactivated? active))
       :ttl (if (contains? params "ttl") (get params "ttl") (:ttl active))})))
