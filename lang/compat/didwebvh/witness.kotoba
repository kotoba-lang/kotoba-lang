(ns didwebvh.witness
  "Witnesses and the threshold, which is the part of did:webvh that makes
   multi-party control a METHOD rule rather than an operational promise.

   `updateKeys` does not do this. Several keys may be listed and any ONE of
   them signs a valid entry -- the list is a set of equals, not a quorum. The
   witness parameter is where `m-of-n` lives, and a resolver that finds fewer
   than `threshold` weight of valid witness proofs must refuse the update.

       {\"threshold\" 3
        \"witnesses\" [{\"id\" \"did:key:zSecurity\"}
                     {\"id\" \"did:key:zLegal\"}
                     {\"id\" \"did:key:zOperations\"}
                     {\"id\" \"did:key:zAuditor\"}
                     {\"id\" \"did:key:zRecovery\"}]}

   `weight` defaults to 1, so the shape above is plain 3-of-5.

   What witnesses sign is the VERSION, not the entry: the spec says the proofs
   \"use the versionId as input data\", so the signed document is
   `{\"versionId\": \"<versionId>\"}`. The versionId already commits to the
   entry through its hash, so a witness approving a version approves exactly
   the bytes that hashed to it -- and a proof cannot be replayed onto another
   version, because the version is what was signed.

   The file is a JSON ARRAY of `{\"versionId\", \"proof\"}` objects published
   beside the log as `did-witness.json` (see `didwebvh.did/witness-url`)."
  (:require [kotoba.lang.text :as str]
            [didwebvh.proof :as proof]))

(defn- fail! [code msg data]
  (throw (ex-info msg (assoc data :didwebvh/error code))))

(defn configured?
  "Is a witness set in force? The default `{}` means no witnesses, and the
   distinction matters: an empty witness parameter is a decision to need none,
   not an absent one."
  [witness]
  (boolean (and (map? witness) (seq (get witness "witnesses")))))

(defn weight-of [w]
  (let [weight (get w "weight")]
    (if (nil? weight) 1 weight)))

(defn total-weight [witness]
  (reduce + 0 (map weight-of (get witness "witnesses"))))

(defn validate!
  "Throws unless `witness` is a well-formed witness parameter. `{}` passes:
   declaring no witnesses is legal."
  [witness]
  (when-not (map? witness)
    (fail! :didwebvh/bad-witness "the witness parameter must be an object" {:witness witness}))
  (when (configured? witness)
    (let [threshold (get witness "threshold")
          witnesses (get witness "witnesses")
          ids (map #(get % "id") witnesses)]
      (when-not (and (integer? threshold) (pos? threshold))
        (fail! :didwebvh/bad-threshold "threshold must be a positive integer"
               {:threshold threshold}))
      (when-not (every? #(and (string? %) (str/starts-with? % "did:key:")) ids)
        (fail! :didwebvh/bad-witness-id "every witness id must be a did:key"
               {:ids ids}))
      (when-not (= (count ids) (count (set ids)))
        (fail! :didwebvh/duplicate-witness "witness ids must be unique" {:ids ids}))
      (when-not (every? #(and (integer? (weight-of %)) (pos? (weight-of %))) witnesses)
        (fail! :didwebvh/bad-witness-weight "a witness weight must be a positive integer"
               {:witnesses witnesses}))
      (when (> threshold (total-weight witness))
        (fail! :didwebvh/unreachable-threshold
               "threshold exceeds the total weight of the witnesses -- no set of proofs could ever satisfy it"
               {:threshold threshold :total-weight (total-weight witness)})))))

(defn document
  "The document a witness signs for one version."
  [version-id]
  {"versionId" version-id})

(defn proofs-for
  "The proofs `did-witness.json` carries for one versionId, or nil when it
   carries none. nil and `[]` are both \"no proofs\" here; the caller reports
   the threshold miss, so there is nothing to distinguish."
  [witness-file version-id]
  (some (fn [entry]
          (when (= version-id (get entry "versionId"))
            (get entry "proof")))
        (or witness-file [])))

(defn verify
  "Does `witness-file` carry threshold weight of valid witness proofs for
   `version-id`?

   Returns `{:ok? bool :weight n :threshold n :approved [ids] :rejected [{}]}`.
   A witness counts once, however many proofs it filed, and only if at least
   one of them verifies under ITS OWN key -- a proof signed by some other key
   is not that witness's approval no matter which array it sits in.

   `opts` is passed to `didwebvh.proof/verify`; `:verify-signature` is the
   only key that means anything here (`:allowed` is this function's own
   answer and is not the caller's to give)."
  ([witness version-id witness-file] (verify witness version-id witness-file {}))
  ([witness version-id witness-file opts]
  (if-not (configured? witness)
    {:ok? true :weight 0 :threshold 0 :approved [] :rejected []}
    (let [proofs (proofs-for witness-file version-id)
          doc (document version-id)
          results
          (for [w (get witness "witnesses")
                :let [id (get w "id")
                      mk (subs id (count "did:key:"))
                      verdicts (map #(proof/verify doc % (assoc opts :allowed #{mk})) (or proofs []))
                      ok (some :ok? verdicts)]]
            {:id id :weight (weight-of w) :ok? (boolean ok)
             :reasons (vec (remove nil? (map :error verdicts)))})
          approved (filter :ok? results)
          weight (reduce + 0 (map :weight approved))
          threshold (get witness "threshold")]
      {:ok? (>= weight threshold)
       :weight weight
       :threshold threshold
       :approved (mapv :id approved)
       :rejected (vec (remove :ok? results))}))))
