(ns nekko.announce
  "Adapters bridging kotoba-rad's signed sigref format to
   kotoba-lang/p2p's pluggable :sign-announce/:verify-announce? hooks
   (kotoba.p2p.sync/new-node) -- this repo does not depend on p2p (nor
   vice versa); the two compose purely through the plain-map message
   shape p2p documents, the same decoupling kotoba-git/kotoba-rad already
   use with each other.

   A p2p head-announce message is {:type :head-announce :graph :head-cid
   :seq :origin :from}. Treating :graph as a sigref's ref-name and :seq as
   its ts, a signed announce is exactly a sigref -- no new signing
   primitive is needed, only this adapter."
  (:require [nekko.sigref :as sigref]
            [nekko.push-gate :as push-gate]))

(defn sign-announce-fn
  "A p2p :sign-announce hook: signs {:graph :head-cid :seq} as a sigref
   under rid (ref-name = graph, commit = head-cid, ts = seq) and attaches
   it as the message's :sigref."
  [signer-seed rid]
  (fn [{:keys [graph head-cid seq] :as msg}]
    (assoc msg :sigref (sigref/sign signer-seed rid graph head-cid seq))))

(defn verify-announce-fn
  "A p2p :verify-announce? hook: true iff the message carries a :sigref
   whose (rid, ref, commit, ts) match this announce's (rid, graph,
   head-cid, seq) exactly, whose signature verifies, and whose signer is
   currently an authorized delegate (or the owner) per the RID's identity
   journal (journal-get-fn/journal-head-cid/owner-did/rid -- the same
   arguments nekko.push-gate/authorize-push? takes)."
  [journal-get-fn journal-head-cid owner-did rid]
  (fn [{:keys [graph head-cid seq sigref]}]
    (boolean
     (and sigref
          (= (get sigref "ts") seq)
          (push-gate/authorize-push? journal-get-fn journal-head-cid owner-did
                                      rid graph head-cid sigref)))))
