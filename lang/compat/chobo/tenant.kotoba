(ns chobo.tenant
  "Tenant + operator trust level (pure), extracted from itonami's org/repo
  tenant isolation + open-business operator model. Portable .cljc.

  A Tenant is {:id :name :plan-id :capabilities :operator-level}. Capabilities
  are a set of scoped actions (e.g. #{:read :billing:read :billing:write}).
  Operator trust levels (from cloud-itonami open-business):
  :contributor → :self-host → :certified → :managed → :core."

  (:refer-clojure :exclude [tenant]))

(def operator-levels
  [:contributor :self-host :certified :managed :core])

(defn operator-level-rank
  "Index of a level in `operator-levels`, or -1 if unknown. Portable."
  [lvl]
  (loop [i 0 [x & rest] operator-levels]
    (cond
      (nil? x) -1
      (= x lvl) i
      :else (recur (inc i) rest))))

(defrecord Tenant [id name plan-id capabilities operator-level])

(defn tenant
  ([id]
   (tenant id {}))
  ([id {:keys [name plan-id capabilities operator-level]}]
   (->Tenant id (or name id) plan-id (set capabilities) (or operator-level :contributor))))

(defn has-cap? [t cap] (contains? (:capabilities t #{}) cap))

(defn grant [t cap] (update t :capabilities (fnil conj #{}) cap))

(defn revoke [t cap] (update t :capabilities disj cap))

(defn at-least-level?
  "True if tenant's operator-level rank >= target's."
  [t target]
  (let [tr (operator-level-rank (:operator-level t :contributor))
        tgt (operator-level-rank target)]
    (and (>= tr 0) (>= tgt 0) (>= tr tgt))))
