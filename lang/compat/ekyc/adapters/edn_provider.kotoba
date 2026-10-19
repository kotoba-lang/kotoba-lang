(ns ekyc.adapters.edn-provider
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [ekyc.adapters.provider :as provider]))

(defn- read-state [file]
  (if (.exists (io/file file))
    (edn/read-string (slurp file))
    {:sessions {} :evidence {} :custody []}))

(defn- write-state! [file state]
  (let [f (io/file file)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit f (pr-str state))
    state))

(defn- evidence-ref [session-id check]
  (str "edn://ekyc/" session-id "/evidence/" (name check)))

(defn edn-custody [file]
  (let [lock (Object.)]
    (reify provider/IEvidenceCustody
      (commit-evidence! [_ payload _opts]
        (locking lock
          (let [ref (or (:evidence-ref payload)
                        (evidence-ref (:session-id payload) (:check payload)))
                record (assoc payload :evidence-ref ref)
                state (update (read-state file) :custody conj record)]
            (write-state! file state)
            {:evidence-ref ref}))))))

(defn edn-provider [file]
  (let [lock (Object.)]
    (reify provider/IEkycProviderClient
      (create-session! [_ payload _opts]
        (locking lock
          (let [session-ref (str "edn://ekyc/session/" (:id payload))
                session (assoc payload :status :submitted :session-ref session-ref)]
            (write-state! file (assoc-in (read-state file) [:sessions (:id payload)] session))
            {:status :submitted
             :session-ref session-ref
             :provider (:provider payload)})))
      (upload-evidence! [_ payload _opts]
        (locking lock
          (let [ref (or (:custody-ref payload)
                        (:evidence-ref payload)
                        (evidence-ref (:session-id payload) (:check payload)))
                record (assoc payload
                              :status :verified
                              :evidence-ref ref
                              :confidence (or (:confidence payload) 1000))]
            (write-state! file (update-in (read-state file)
                                          [:evidence (:session-id payload)]
                                          (fnil assoc {})
                                          (:check payload)
                                          record))
            {:status :verified
             :evidence-ref ref
             :confidence (:confidence record)
             :observed-at (:observed-at payload)})))
      (retrieve-result! [_ payload _opts]
        (locking lock
          (let [state (read-state file)
                session-id (:session-id payload)
                evidence (vals (get-in state [:evidence session-id] {}))]
            {:status (if (seq evidence) :verified :review)
             :evidence (mapv #(select-keys % [:check :status :evidence-ref :source :confidence :observed-at])
                             evidence)}))))))

(defn state [file]
  (read-state file))
