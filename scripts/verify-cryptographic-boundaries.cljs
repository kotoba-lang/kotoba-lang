#!/usr/bin/env nbb
(ns verify-cryptographic-boundaries
  (:require ["node:fs" :as fs]
            ["node:path" :as path]
            [cljs.reader :as reader]
            [clojure.string :as str]))

(def required-keys
  [:boundary/id :boundary/owner :boundary/kind :boundary/classification
   :boundary/admission :boundary/pq-required])

(defn- pq-suite? [suite]
  (and suite
       (let [text (str/lower-case (name suite))]
         (or (str/includes? text "ml-kem")
             (str/includes? text "ml-dsa")))))

(defn problems [inventory]
  (let [boundaries (:kotoba.crypto-boundaries/boundaries inventory)
        policy (:kotoba.crypto-boundaries/policy inventory)
        ids (mapv :boundary/id boundaries)
        id-set (set ids)]
    (vec
     (concat
      (when-not (= 1 (:kotoba.crypto-boundaries/version inventory))
        [{:problem :inventory/version}])
      (when-not (and (= :post-quantum (:new-boundary-floor policy))
                     (= :reject (:classical-only policy))
                     (= :reject (:unknown-suite policy))
                     (= :reject (:missing-pq-material policy))
                     (false? (:legacy-development-compatibility-required policy)))
        [{:problem :inventory/policy-floor-invalid}])
      (when-not (<= 8 (count boundaries))
        [{:problem :inventory/too-few-boundaries :count (count boundaries)}])
      (when-not (= (count ids) (count id-set))
        [{:problem :inventory/duplicate-boundary-id}])
      (for [id (:required-boundary-ids policy) :when (not (contains? id-set id))]
        {:problem :inventory/required-boundary-missing :boundary id})
      (mapcat
       (fn [boundary]
         (let [id (:boundary/id boundary)
               admitted? (= :admitted (:boundary/admission boundary))
               blocked? (= :blocked (:boundary/admission boundary))
               external? (= :external (:boundary/classification boundary))]
           (concat
            (for [k required-keys :when (nil? (get boundary k))]
              {:problem :boundary/required-field-missing :boundary id :field k})
            (when (and (= :managed (:boundary/classification boundary)) admitted?
                       (not (true? (:boundary/pq-required boundary))))
              [{:problem :boundary/pq-not-required :boundary id}])
            (when (and (= :managed (:boundary/classification boundary))
                       (not (or admitted? blocked?)))
              [{:problem :boundary/managed-admission-invalid :boundary id}])
            (when (and blocked? (not (true? (:boundary/pq-required boundary))))
              [{:problem :boundary/blocked-pq-not-required :boundary id}])
            (when (and (= :managed (:boundary/classification boundary)) admitted?
                       (not (pq-suite? (:boundary/suite boundary))))
              [{:problem :boundary/pq-suite-missing :boundary id}])
            (when (and admitted? (not= :reject (:boundary/downgrade boundary)))
              [{:problem :boundary/downgrade-not-rejected :boundary id}])
            (when (and admitted?
                       (or (empty? (:boundary/implementation boundary))
                           (empty? (:boundary/tests boundary))))
              [{:problem :boundary/evidence-missing :boundary id}])
            (when (and blocked?
                       (or (not= :fail-closed (:boundary/enforcement boundary))
                           (empty? (:boundary/gaps boundary))))
              [{:problem :boundary/blocked-without-enforcement :boundary id}])
            (when (and external? (str/blank? (:boundary/limitation boundary)))
              [{:problem :boundary/external-limitation-missing :boundary id}])
            (when (and external?
                       (or (not= :external (:boundary/admission boundary))
                           (not (false? (:boundary/pq-required boundary)))))
              [{:problem :boundary/external-classification-invalid :boundary id}])
            (when (str/includes? (str/lower-case (str (:boundary/suite boundary)))
                                 "classical-only")
              [{:problem :boundary/classical-only-suite :boundary id}]))))
       boundaries)))))

(defn- read-inventory [file]
  (reader/read-string (fs/readFileSync file "utf8")))

(defn- self-test! [inventory]
  (let [boundaries (:kotoba.crypto-boundaries/boundaries inventory)
        first-admitted (first (keep-indexed #(when (= :admitted (:boundary/admission %2)) %1)
                                            boundaries))
        blocked-index (first (keep-indexed #(when (= :blocked (:boundary/admission %2)) %1)
                                           boundaries))
        cases
        [{:name :valid :value inventory :valid? true}
         {:name :pq-stripped
          :value (assoc-in inventory
                           [:kotoba.crypto-boundaries/boundaries first-admitted :boundary/suite]
                           :x25519+aes-256-gcm)
          :valid? false}
         {:name :blocked-falsely-admitted
          :value (assoc-in inventory
                           [:kotoba.crypto-boundaries/boundaries blocked-index :boundary/admission]
                           :admitted)
          :valid? false}
         {:name :duplicate-id
          :value (update inventory :kotoba.crypto-boundaries/boundaries conj (first boundaries))
          :valid? false}
         {:name :external-falsely-managed
          :value (assoc-in inventory
                           [:kotoba.crypto-boundaries/boundaries (dec (count boundaries))
                            :boundary/classification]
                           :managed)
          :valid? false}]]
    (doseq [{:keys [name value valid?]} cases]
      (let [actual (empty? (problems value))]
        (when-not (= valid? actual)
          (throw (ex-info "cryptographic boundary self-test failed"
                          {:case name :expected valid? :actual actual
                           :problems (problems value)})))))
    (println (str (count cases) " cases OK"))))

(let [args (vec *command-line-args*)
      root-idx (.indexOf args "--root")
      root (if (neg? root-idx) "." (get args (inc root-idx)))
      file (path/join (path/resolve root) "security" "cryptographic-boundaries.edn")
      inventory (read-inventory file)
      found (problems inventory)]
  (when (seq found)
    (doseq [problem found] (println (pr-str problem)))
    (js/process.exit 1))
  (println (str "cryptographic-boundaries: OK "
                (count (:kotoba.crypto-boundaries/boundaries inventory))
                " boundaries"))
  (when (some #{"--self-test"} args) (self-test! inventory)))
