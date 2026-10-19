(ns ipni.head
  "SignedHead: the publisher's current advertisement-chain tip.

  Served at `GET /ipni/v1/head`. sign-fn is injected. This namespace
  does not own a key.")

(defn signed-head
  [{:keys [ad-cid sign-fn]}]
  (cond
    (not (string? ad-cid)) {:error :invalid-ad-cid :value ad-cid}
    (not (ifn? sign-fn)) {:error :sign-fn-required}
    :else
    (let [sig (sign-fn ad-cid)]
      {:schema :ipni.signed-head
       :ad-cid ad-cid
       :signature sig})))
