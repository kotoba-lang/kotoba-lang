(ns langchain.repo-profile
  "Machine-checkable storage contract for repository-driven Agents.

  The profile describes an architectural boundary, not a crypto
  implementation. Private user state is locally queryable/editable EDN and is
  admitted to a repository or remote only after it has been sealed. Public
  facts require an explicit classification instead of silently weakening the
  default."
  (:require [clojure.set :as set]))

(def profile-id :kotoba/local-agent-kagi-chunks-v1)

(def repo-kinds
  #{:cloud-itonami :actor :artificial-organism})

(def required-values
  {:profile/id profile-id
   :query/location :local
   :query/api :datomic-datascript-subset
   :query/remote-capability? false
   :working-edn/editable? true
   :working-edn/private-git-policy :deny
   :mutation/membrane :reconcile
   :persistence/shape :append-only-transactions
   :remote/payload :kagi-chunked-edn
   :remote/head :kotobase
   :remote/transport :datalad})

(defn default-profile
  "Return the normative profile for one supported repository kind."
  [repo-kind]
  (assoc required-values :repo/kind repo-kind))

(defn violations
  "Return deterministic profile violations. Empty means the repository has
  declared the common boundary. This does not replace ciphertext or leak
  scanning at publish time."
  [profile]
  (let [missing (set/difference (set (keys required-values))
                                (set (keys profile)))
        mismatched (for [[attribute expected] required-values
                         :let [actual (get profile attribute)]
                         :when (and (contains? profile attribute)
                                    (not= expected actual))]
                     {:type :profile/value-mismatch
                      :attribute attribute
                      :expected expected
                      :actual actual})
        public-invalid? (and (= :allow-explicit-public
                                (:working-edn/public-git-policy profile))
                             (not= :required
                                   (:data/public-classification profile)))]
    (vec
     (concat
      (for [attribute (sort missing)]
        {:type :profile/missing :attribute attribute})
      mismatched
      (when-not (repo-kinds (:repo/kind profile))
        [{:type :profile/unsupported-repo-kind
          :attribute :repo/kind
          :actual (:repo/kind profile)
          :supported repo-kinds}])
      (when public-invalid?
        [{:type :profile/public-classification-required
          :attribute :data/public-classification}])))))

(defn conforming?
  [profile]
  (empty? (violations profile)))

(defn validate!
  "Return PROFILE or fail closed with all violations."
  [profile]
  (if-let [problems (seq (violations profile))]
    (throw (ex-info "repository storage profile does not conform"
                    {:type :repo-profile/non-conforming
                     :violations (vec problems)}))
    profile))
