(ns kotoba.physics.contract
  "Portable contract shared by games, world builders and CAE backends.

  A scene is immutable EDN. Realtime backends implement `step`; engineering
  backends implement `solve`. Fidelity and units are explicit so a caller
  cannot silently substitute a game approximation for a qualified result."
  (:require [clojure.set :as set]))

(def contract-version 1)
(def fidelities #{:realtime :reduced-order :high-fidelity})
(def si-units {:length :m :mass :kg :time :s :temperature :K})

(defprotocol PhysicsBackend
  (descriptor [backend] "Stable backend identity, capabilities and fidelity.")
  (step [backend scene dt-s] "Advance a realtime scene by dt seconds.")
  (solve [backend case] "Solve a finite engineering case."))

(defn make-scene
  "Create the shared scene envelope. Entities remain domain-owned EDN maps."
  [{:keys [id dimensions entities collision] :as input}]
  (when-not (and id (#{2 3} dimensions) (vector? entities))
    (throw (ex-info "physics scene requires id, 2/3 dimensions and vector entities" {:input input})))
  {:physics/version contract-version :physics/kind :scene :scene/id id
   :scene/dimensions dimensions :scene/units si-units :scene/entities entities
   :scene/collision (or collision {})})

(defn make-case
  "Create an auditable finite-solve request from a shared scene."
  [{:keys [id scene domain backend-kind fidelity controls provenance] :as input}]
  (when-not (and id (= :scene (:physics/kind scene)) domain backend-kind
                 (contains? fidelities fidelity) (map? controls))
    (throw (ex-info "invalid physics case" {:input input})))
  {:physics/version contract-version :physics/kind :case :case/id id
   :case/scene scene :case/domain domain :case/backend backend-kind
   :case/fidelity fidelity :case/controls controls :case/provenance (or provenance {})})

(defn capabilities [backend] (set (:capabilities (descriptor backend))))

(defn supports?
  "True only when all requested capabilities and the exact fidelity match."
  [backend fidelity requested]
  (let [d (descriptor backend)]
    (and (= fidelity (:fidelity d))
         (set/subset? (set requested) (capabilities backend)))))

(defn require-backend!
  [backend fidelity requested]
  (when-not (supports? backend fidelity requested)
    (throw (ex-info "physics backend does not satisfy requested fidelity/capabilities"
                    {:requested-fidelity fidelity :requested-capabilities (set requested)
                     :backend (descriptor backend)})))
  backend)

(defn make-result
  "Build a result whose qualification can never be inferred from its numbers."
  [{:keys [case-id backend status fields qualification evidence] :as input}]
  (when-not (and case-id backend (#{:completed :rejected :failed} status)
                 (map? fields) (map? qualification))
    (throw (ex-info "invalid physics result" {:input input})))
  {:physics/version contract-version :physics/kind :result :case/id case-id
   :result/backend backend :result/status status :result/fields fields
   :result/qualification qualification :result/evidence (vec evidence)})

(defn qualified?
  "Qualification is scope-specific and explicit; completion alone is not enough."
  [result scope]
  (and (= :completed (:result/status result))
       (= :qualified (get-in result [:result/qualification scope]))))
