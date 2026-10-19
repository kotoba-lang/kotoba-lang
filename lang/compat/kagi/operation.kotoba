(ns kagi.operation
  "VaultActor — vault の 1 操作 = 1 supervised run(langgraph-clj StateGraph)。
  知能ノード(:advise)は proposal のみ。AccessGovernor(:govern)と phase gate(:decide)を
  必ず通り、**副作用は :effect / :hold の 2 ノードだけ**。`:advise` から `:effect` へ
  Governor を迂回する辺は無い(単一不変条件をグラフ位相で保証)。

  注入境界(swap, not rewrite):
    - Store    (MemStore | KotobaStore)        — `store` 引数
    - Provider (BouncyCastle | kotoba-crypto)  — :crypto opt
    - Advisor  (mock | LLM/異常検知)            — :advisor opt
    - Phase    (0→3)                            — :phase in ctx

  human-in-the-loop = `interrupt-before #{:request-approval}`。承認者は
  `{:approval {:status :approved}}`(または :rejected)で resume する
  (break-glass / 高価値 reveal / 復旧)。"
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [kagi.advisor :as advisor]
            [kagi.governor :as governor]
            [kagi.phase :as phase]
            [kagi.crypto :as crypto]
            [kagi.key-registry :as key-registry]
            [kagi.cacao :as cacao]
            [kagi.ledger :as ledger]
            [kagi.rotation :as rotation]
            [kagi.vault :as vault]
            [kagi.store :as store]))

;; ───────── 台帳 fact ─────────

(defn- commit-fact [request context]
  {:t :committed :op (:op request) :actor (:did context)
   :item (:item-id request) :disposition :commit})

(defn- hold-fact [request context verdict]
  {:t :policy-hold :op (:op request) :actor (:did context)
   :item (:item-id request) :disposition :hold
   :basis (mapv :rule (:violations verdict))})

;; ───────── 副作用(:effect ノードからのみ) ─────────

(defn- item-dek
  "owner が VMK から compartment KEK を導出し item の wrap を解いて DEK を得る。"
  [_store* crypto* context it]
  (let [kek (crypto/compartment-key crypto* (:vmk context) (:item/compartment it))]
    [kek (crypto/unwrap-dek crypto* kek (:item/wrap it))]))

(defn- rotate-item-plan
  [store* crypto* context it]
  (let [item-id (:item/id it)
        aad (vault/item-aad item-id)
        [kek dek] (item-dek store* crypto* context it)
        pt (crypto/open-item crypto* dek (:item/nonce it)
                             (store/block-get store* (:item/cid it)) aad)
        {ndek :dek nnonce :nonce nct :ciphertext} (crypto/seal-item crypto* pt aad)
        ver (inc (:item/version it 1))
        ncid (str "cid:" item-id ":v" ver)
        next-item (assoc it :item/cid ncid :item/nonce nnonce
                        :item/version ver :item/key-epoch ver
                        :item/key-created-at (:now context)
                        :item/wrap (crypto/wrap-dek crypto* kek ndek))]
    {:block {:cid ncid :bytes nct}
     :item next-item :dek ndek :version ver
     :from-key (:item/cid it) :to-key ncid
     :parent (:item/rotation-event it)}))

(defn- do-effect
  "op を実際に実行する。ここだけが store を変更し item を復号開示する。"
  [store* crypto* request context _proposal]
  (case (:op request)
    (:item/create :item/update)
    (let [{:keys [item-id compartment category plaintext]} request
          prev (store/item store* item-id)
          comp (or compartment (:item/compartment prev))
          cat  (or category (:item/category prev))
          aad  (vault/item-aad item-id)
          {:keys [dek nonce ciphertext]} (crypto/seal-item crypto* plaintext aad)
          kek  (crypto/compartment-key crypto* (:vmk context) comp)
          ver  (inc (:item/version prev 0))
          cid  (str "cid:" item-id ":v" ver)]
      (store/block-put! store* cid ciphertext)
      (store/put-item! store* #:item{:id item-id :compartment comp :category cat
                                     :cid cid :nonce nonce :version ver
                                     :key-epoch ver
                                     :key-created-at (:now context)
                                     :wrap (crypto/wrap-dek crypto* kek dek)
                                     :created-by (:did context)})
      {:effect :stored :item item-id :version ver})

    :item/reveal
    (let [{:keys [item-id]} request
          it  (store/item store* item-id)
          [_ dek] (item-dek store* crypto* context it)
          pt  (crypto/open-item crypto* dek (:item/nonce it)
                                (store/block-get store* (:item/cid it))
                                (vault/item-aad item-id))]
      {:effect :revealed :item item-id :plaintext pt :purpose (:purpose context)})

    :item/rotate
    (let [{:keys [item-id]} request
          plan (rotate-item-plan store* crypto* context (store/item store* item-id))]
      {:effect :rotated :item item-id :version (:version plan)
       :rekey-plan (assoc plan :grants [])})

    :share/grant
    (let [{:keys [item-id recipient-did]} request
          it  (store/item store* item-id)
          [_ dek] (item-dek store* crypto* context it)
          recipient (store/member store* recipient-did)
          _ (key-registry/authorize! (:member/kem-key recipient) :encapsulate (:now context))
          rpk (:member/kem-pub recipient)
          env (crypto/share-dek crypto* rpk dek)]
      (store/put-grant! store* #:grant{:id (str item-id "->" recipient-did)
                                       :item item-id :recipient recipient-did
                                       :envelope env :cap :member})
      {:effect :shared :item item-id :to recipient-did})

    :share/revoke
    (let [{:keys [item-id recipient-did]} request
          it (store/item store* item-id)
          {:keys [dek version] :as plan} (rotate-item-plan store* crypto* context it)
          grants (mapv
                  (fn [grant]
                    (if (= recipient-did (:grant/recipient grant))
                      (assoc grant :grant/revoked true :grant/revoked-at (:now context))
                      (let [rdid (:grant/recipient grant)
                            recipient (store/member store* rdid)
                            _ (key-registry/authorize! (:member/kem-key recipient)
                                                       :encapsulate (:now context))]
                        (assoc grant
                               :grant/envelope
                               (crypto/share-dek crypto* (:member/kem-pub recipient) dek)
                               :grant/key-epoch version))))
                  (store/grants-of store* item-id))]
      ;; Previously issued envelopes remain historical artifacts, but every
      ;; non-revoked recipient receives only the fresh epoch DEK.
      {:effect :revoked-and-rekeyed :item item-id :to recipient-did :version version
       :rekey-plan (assoc plan :grants grants)})

    :item/list
    {:effect :listed
     :items (mapv :item/id (store/items-in store* (:compartment request)))}))

;; ───────── グラフ ─────────

(defn build
  "VaultActor グラフを store に束ねてコンパイル。"
  [store* & [{:keys [advisor crypto checkpointer signer signer-key]
              :or {advisor      (advisor/mock-advisor)
                   crypto       (crypto/bc-provider)
                   checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels {:request     {:default nil}
                   :context     {:default nil}
                   :proposal    {:default nil}
                   :verdict     {:default nil}
                   :disposition {:default nil}
                   :result      {:default nil}
                   :approval    {:default nil}
                   :audit       {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :authn
                  (fn [{:keys [context]}]
                    ;; depth-1 self-mint: actor が自分の鍵由来 graph の owner として
                    ;; 公開鍵束(:register = identity/member-record)を提示すれば登録する
                    ;; (owner hand-off も共有 token も不要)。:cacao があれば SIWE/Ed25519 を検証し、
                    ;; 失敗なら以降の副作用に進めず :hold へ送る。
                    (when-let [m (:register context)] (store/put-member! store* m))
                    (let [authed? (if-let [c (:cacao context)]
                                    (:ok? (cacao/verify c {:aud (:aud context)}))
                                    true)]
                      {:context (assoc context :authed? authed?)
                       :audit [{:t :authn :actor (:did context) :verified? authed?}]})))
      (g/add-node :advise
                  (fn [{:keys [request context]}]
                    (let [p (advisor/-advise advisor request context)]
                      {:proposal p :audit [(advisor/trace request p)]})))
      (g/add-node :govern
                  (fn [{:keys [request context proposal]}]
                    {:verdict (governor/check request context proposal store*)}))
      (g/add-node :decide
                  (fn [{:keys [request context verdict]}]
                    (let [base (governor/verdict->disposition verdict)
                          ph   (:phase context phase/default-phase)
                          {:keys [disposition reason]} (phase/gate ph request base)]
                      (case disposition
                        :hold     {:disposition :hold
                                   :audit [(cond-> (hold-fact request context verdict)
                                             reason (assoc :phase-reason reason))]}
                        :escalate {:disposition :escalate
                                   :audit [{:t :approval-requested :op (:op request)
                                            :actor (:did context)}]}
                        :commit   {:disposition :commit}))))
      (g/add-node :request-approval
                  (fn [{:keys [approval]}]
                    ;; interrupt-before で停止 → 承認者が approval を入れて resume
                    {:disposition (if (= :approved (:status approval)) :commit :hold)}))
      (g/add-node :effect
                  (fn [{:keys [request context proposal]}]
                    (let [r (do-effect store* crypto request context proposal)
                          f (commit-fact request context)
                          ;; append-chained-ledger! (not a separate
                          ;; store/ledger read + make-entry + append-ledger!)
                          ;; so the :ledger/seq/:ledger/prev-hash the entry
                          ;; chains onto is always the CURRENT ledger, even
                          ;; under concurrent :effect/:hold calls -- see
                          ;; kagi.store's append-chained-ledger! docstring.
                          key-opts {:key signer-key :now (:now context)}]
                      (if-let [plan (:rekey-plan r)]
                        (do
                          (when-not (and signer signer-key)
                            (throw (ex-info "rotation requires an active managed authority signer"
                                            {:op (:op request) :actor (:did context)})))
                          (let [event0 (rotation/new-event
                                      {:subject (:item r) :purpose :item-dek
                                       :from-key (:from-key plan) :to-key (:to-key plan)
                                       :from-epoch (dec (:version plan))
                                       :reason (if (= :share/revoke (:op request))
                                                 :recipient-revoked :scheduled)
                                       :parents (cond-> [] (:parent plan) (conj (:parent plan)))
                                       :not-before (:now context)})
                              event (rotation/sign-authorized crypto event0 (:did context) signer)
                              item* (assoc (:item plan) :item/rotation-event (:rotation/id event))
                              committed (store/commit-rekey!
                                         store* (assoc plan :item item* :rotation-event event)
                                         #(ledger/make-entry % (assoc f :rotation/id (:rotation/id event))
                                                             crypto signer key-opts))]
                            {:result (dissoc r :rekey-plan)
                             :audit [(:ledger-entry committed)]}))
                        (let [e (store/append-chained-ledger!
                                 store* #(ledger/make-entry % f crypto signer key-opts))]
                          {:result r :audit [e]})))))
      (g/add-node :hold
                  (fn [{:keys [request context verdict audit]}]
                    (let [hf (or (last (filter #(#{:policy-hold} (:t %)) audit))
                                 (hold-fact request context verdict))]
                      (store/append-chained-ledger!
                       store* #(ledger/make-entry % hf crypto signer
                                                  {:key signer-key :now (:now context)}))
                      {:result {:effect :held}})))
      (g/set-entry-point :intake)
      (g/add-edge :intake :authn)
      (g/add-conditional-edges :authn
                               (fn [{:keys [context]}]
                                 (if (false? (:authed? context)) :hold :advise)))
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges :decide
                               (fn [{:keys [disposition]}]
                                 (case disposition
                                   :commit   :effect
                                   :escalate :request-approval
                                   :hold)))
      (g/add-conditional-edges :request-approval
                               (fn [{:keys [disposition]}]
                                 (if (= :commit disposition) :effect :hold)))
      (g/set-finish-point :effect)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                        :interrupt-before #{:request-approval}})))
