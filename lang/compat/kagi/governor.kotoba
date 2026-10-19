(ns kagi.governor
  "AccessGovernor — 知能ノードから独立した検閲器。proposal を受け取り verdict を返す。
  knowledge ノード(advisor)は本検査に一切の可視性を持たない。

  hard violation(即 hold): RBAC / purpose / 最小開示 / consent。
  soft escalation(人間承認へ): 低 confidence / 高価値(high-value) / 異常(anomaly)。

  単一不変条件: ここが ok? でない開示/書込/共有/鍵操作を operation は決して副作用に回さない。"
  (:require [kagi.store :as store]
            [kotoba.security.abac :as abac]
            [kotoba.security.information-flow :as flow]))

(defn- information-flow-violations [request context proposal]
  (if (= :item/reveal (:op request))
    (let [result
          (flow/evaluate-egress
           {:subject (:did context) :purpose (:purpose context) :now (:now context)
            :input-classifications [(or (:classification request)
                                        (:classification proposal)
                                        :restricted)]
            :output-classification (or (:output-classification request)
                                       (:classification request)
                                       (:classification proposal)
                                       :restricted)
            :declassification-grant (:declassification-grant context)})]
      (mapv (fn [{:information-flow/keys [control message]}]
              {:rule (keyword "information-flow" (name control)) :detail message})
            (:information-flow/violations result)))
    []))

(def permissions
  "role → 許可される op。"
  {:owner  #{:item/create :item/update :item/rotate :item/reveal :item/list
             :share/grant :share/revoke}
   :member #{:item/reveal :item/list :item/update}
   :viewer #{:item/reveal :item/list}})

;; ───────── hard checks ─────────

(defn- rbac-violations [{:keys [op]} {:keys [role]}]
  (cond-> []
    (not (contains? (get permissions role #{}) op))
    (conj {:rule :rbac :detail (str role " は " op " の権限を持たない")})))

(defn- granted? [st item-id did]
  (some (fn [g] (and (= did (:grant/recipient g))
                     (not (:grant/revoked g))))
        (store/grants-of st item-id)))

(defn- access-violations [{:keys [op item-id]} {:keys [role did]} st]
  (cond-> []
    (and item-id (#{:item/reveal :item/update} op) (not= role :owner)
         (not (granted? st item-id did)))
    (conj {:rule :rbac-subject :detail (str did " は item " item-id " への grant を持たない")})))

(defn- purpose-violations [{:keys [op]} {:keys [purpose]}]
  (cond-> []
    (and (= op :item/reveal) (nil? purpose))
    (conj {:rule :purpose :detail "reveal には用途(purpose)宣言が必要"})))

(defn- consent-violations [{:keys [op]} {:keys [consent?]}]
  (cond-> []
    (and (#{:share/grant} op) (not consent?))
    (conj {:rule :consent :detail "共有には対象者 consent が必要"})))

(defn- abac-violations
  "Evaluate subject/resource/action/environment attributes.  The policy is
  optional for backwards compatibility, but once supplied every declared
  condition fails closed on a missing attribute."
  [request context proposal policy]
  (let [resource-tenant (or (:tenant request) (:tenant proposal))
        common-policy (cond-> {:policy/id (:id policy)}
                        (:allowed-purposes policy)
                        (assoc :purpose/allowed (:allowed-purposes policy))
                        (:require-device-trust? policy)
                        (assoc :environment/require-device-trust? true)
                        (:allowed-network-zones policy)
                        (assoc :environment/network-zones (:allowed-network-zones policy))
                        (:tenant-isolation? policy) (assoc :tenant/isolation? true)
                        (:not-before policy) (assoc :valid/not-before (:not-before policy))
                        (:expires-at policy) (assoc :valid/expires-at (:expires-at policy))
                        (:max-reveal-bytes policy)
                        (assoc :disclosure/max-bytes (:max-reveal-bytes policy)))
        result (abac/evaluate
                {:subject {:id (:did context) :role (:role context)
                           :tenant (:tenant context) :clearance (:clearance context)}
                 :resource {:id (:item-id request) :tenant resource-tenant
                            :classification (or (:classification request)
                                                (:classification proposal)
                                                (:default-classification policy))}
                 :action {:id (:op request)}
                 :environment {:now (:now context) :network-zone (:network-zone context)
                               :device-trusted? (:device-trusted? context)}
                 :purpose (:purpose context)
                 :disclosure-bytes (when (= :item/reveal (:op request))
                                     (:plaintext-bytes proposal))}
                common-policy)]
    (mapv (fn [{:abac/keys [control message]}]
            {:rule (case control
                     :purpose :abac-purpose
                     :environment-device :abac-device
                     :environment-network-zone :abac-network-zone
                     :tenant-isolation :abac-tenant
                     :classification :abac-clearance
                     :not-before :abac-not-before
                     :expired :abac-expired
                     :disclosure-size :abac-disclosure-size
                     (keyword "abac" (name control)))
             :detail message})
          (:abac/violations result))))

;; ───────── 検査本体 ─────────

(def ^:const confidence-floor 0.6)

(def high-value
  "高価値カテゴリ: reveal/rotate に人間承認を要求。"
  #{:recovery-code :root-credential :signing-key})

(defn check
  "Evaluate the request.  `context :abac-policy` activates the common
  subject/resource/action/environment checks without creating an ambient
  policy lookup. Returns the policy id and evaluated attributes for receipts."
  ([request context proposal st]
   (check request context proposal st (:abac-policy context)))
  ([request context proposal st policy]
   (let [hard (into [] (concat (rbac-violations request context)
                               (access-violations request context st)
                               (purpose-violations request context)
                               (consent-violations request context)
                               (information-flow-violations request context proposal)
                               (abac-violations request context proposal (or policy {}))))
        conf (:confidence proposal 1.0)
        low? (< conf confidence-floor)
        hv?  (boolean (some high-value [(:category proposal) (:category request)]))
        anom? (boolean (:anomaly? proposal))
        hard? (boolean (seq hard))]
     {:ok?         (and (not hard?) (not low?) (not hv?) (not anom?))
      :violations  hard
      :confidence  conf
      :hard?       hard?
      :high-value? hv?
      :policy-id   (:id policy)
      :attributes  {:subject {:did (:did context) :role (:role context)
                              :tenant (:tenant context) :clearance (:clearance context)}
                    :resource {:item-id (:item-id request)
                               :tenant (or (:tenant request) (:tenant proposal))
                               :classification (or (:classification request)
                                                   (:classification proposal)
                                                   :public)}
                    :action (:op request)
                    :environment {:now (:now context)
                                  :network-zone (:network-zone context)
                                  :device-trusted? (:device-trusted? context)}}
      :escalate?   (and (not hard?) (or low? hv? anom?))})))

(defn verdict->disposition [{:keys [hard? escalate?]}]
  (cond hard? :hold escalate? :escalate :else :commit))
