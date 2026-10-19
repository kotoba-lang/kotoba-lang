(ns nekko.canonical
  "Threshold synthesis for canonical refs from independently signed sigrefs."
  (:require [nekko.sigref :as sigref]))

(defn votes
  "Return `{commit-cid #{delegate-did ...}}` for valid sigrefs matching RID,
   ref and the configured delegate set. Duplicate signatures by one delegate
   count once."
  [rid ref-name delegates sigrefs]
  (reduce
   (fn [out sr]
     (let [signer (get sr "signer")]
       (if (and (contains? delegates signer)
                (= rid (get sr "rid"))
                (= ref-name (get sr "ref"))
                (sigref/valid? sr))
         (update out (get sr "commit") (fnil conj #{}) signer)
         out)))
   {}
   sigrefs))

(defn canonical-ref
  "Return the unique commit CID with at least `threshold` distinct delegate
   signatures. Returns nil without quorum. Throws on split quorum, since
   silently choosing by arrival order would make canonical state diverge."
  [{:keys [rid ref delegates threshold]} sigrefs]
  (when-not (and (pos-int? threshold) (<= threshold (count delegates)))
    (throw (ex-info "nekko: invalid threshold policy"
                    {:reason :invalid-threshold :threshold threshold
                     :delegates (count delegates)})))
  (let [qualified (->> (votes rid ref delegates sigrefs)
                       (keep (fn [[commit signers]]
                               (when (>= (count signers) threshold) commit)))
                       sort vec)]
    (case (count qualified)
      0 nil
      1 (first qualified)
      (throw (ex-info "nekko: conflicting commits reached quorum"
                      {:reason :split-quorum :commits qualified})))))

