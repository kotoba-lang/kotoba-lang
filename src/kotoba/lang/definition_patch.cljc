(ns kotoba.lang.definition-patch
  "Patch/share interchange over payload-v2 :definition-cid.

  This namespace does not hash definitions. It consumes
  `kotoba.lang.code-identity`, which delegates to
  `kotoba.kir.definition-identity`. A CID here is identity, never authority."
  (:require [clojure.string :as str]
            [kotoba.lang.code-identity :as identity]))

(def patch-version 1)
(def share-version 1)
(def hasher-authority :kotoba.kir/definition-identity)
(def parallel-hasher :kotoba.codebase/typed-code)

(def parallel-hasher-cids
  "Typed-code CIDs measured 2026-09-02 in lang/code-identity.edn
  `:identity-implementations :measured-difference`. They are not
  `:definition-cid` values and must not travel as this unit."
  #{"bafyreihetwjs6fjj63z5zqnho7befbvw2h5igtmvujuaaiqfnjrg5uq7yq"
    "bafyreibsdyuxvctmdtocmrwidacdflmq7disy7h2oogvsf4u2hyye75gcq"})

(def source-unit-keys
  #{:source-tree-cid :source-bytes :source-text :source-cid :source-files :files})

(def authority-claim-keys
  #{:grants :eval :authority :admit :capability-grant :host-eval})

(defn- text? [x]
  (and (string? x) (not (str/blank? x))))

(defn- fail
  ([reason] {:ok? false :reason reason})
  ([reason extra] (merge {:ok? false :reason reason} extra)))

(defn- cid-problem
  [cid]
  (cond
    (not (identity/cid? cid)) :patch/cid-invalid
    (contains? parallel-hasher-cids cid) :patch/parallel-hasher-cid
    :else nil))

(defn- forbidden-keys [m]
  (let [ks (set (keys m))]
    (cond
      (seq (filter source-unit-keys ks)) :patch/source-tree-unit
      (seq (filter authority-claim-keys ks)) :patch/identity-is-not-authority
      (contains? ks :typed-code) :patch/parallel-hasher
      :else nil)))

(defn- validate-op [op]
  (cond
    (not (map? op)) (fail :patch/op-not-a-map)
    :else
    (let [forbidden (forbidden-keys op)
          op-name (:op op)
          nm (:name op)]
      (cond
        forbidden (fail forbidden)
        (not (text? nm)) (fail :patch/name-invalid)
        (not (#{:add :replace :remove} op-name)) (fail :patch/op-unknown {:op op-name})
        (= :replace op-name)
        (let [from-p (cid-problem (:from op))
              to-p (cid-problem (:to op))]
          (cond
            from-p (fail from-p {:field :from})
            to-p (fail to-p {:field :to})
            :else {:ok? true}))
        :else
        (let [p (cid-problem (:definition-cid op))]
          (if p
            (fail p {:field :definition-cid})
            {:ok? true}))))))

(defn validate-patch
  "Refuse a document that is not a definition-CID patch. Does not apply it."
  [patch]
  (cond
    (not (map? patch)) (fail :patch/not-a-map)
    :else
    (let [forbidden (forbidden-keys patch)
          hasher (get patch :hasher hasher-authority)
          version (or (:kotoba.definition-patch/version patch)
                      (:version patch)
                      patch-version)
          ops (:ops patch)]
      (cond
        forbidden (fail forbidden)
        (not= patch-version version) (fail :patch/version-unsupported)
        (= hasher parallel-hasher) (fail :patch/parallel-hasher)
        (not= hasher hasher-authority) (fail :patch/hasher-unsupported)
        (nil? ops) (fail :patch/ops-missing)
        (not (vector? ops)) (fail :patch/ops-not-a-vector)
        :else
        (or (some (fn [op]
                    (let [v (validate-op op)]
                      (when-not (:ok? v) v)))
                  ops)
            {:ok? true :patch patch})))))

(defn- apply-op [bindings op]
  (let [nm (:name op)]
    (case (:op op)
      :add
      (if (contains? bindings nm)
        (fail :patch/name-exists {:name nm})
        {:ok? true :bindings (assoc bindings nm (:definition-cid op))})
      :replace
      (cond
        (not (contains? bindings nm)) (fail :patch/name-missing {:name nm})
        (not= (get bindings nm) (:from op))
        (fail :patch/replace-from-mismatch
              {:name nm :expected (:from op) :actual (get bindings nm)})
        :else {:ok? true :bindings (assoc bindings nm (:to op))})
      :remove
      (cond
        (not (contains? bindings nm)) (fail :patch/name-missing {:name nm})
        (not= (get bindings nm) (:definition-cid op))
        (fail :patch/remove-mismatch
              {:name nm :expected (:definition-cid op) :actual (get bindings nm)})
        :else {:ok? true :bindings (dissoc bindings nm)}))))

(defn apply-patch
  "Apply a patch to a name→`:definition-cid` map. On failure the input
  bindings are unchanged. Success is name bindings only — not admission."
  [bindings patch]
  (let [v (validate-patch patch)]
    (if-not (:ok? v)
      v
      (reduce (fn [acc op]
                (if-not (:ok? acc)
                  acc
                  (apply-op (:bindings acc) op)))
              {:ok? true :bindings (if (map? bindings) bindings {})}
              (:ops patch)))))

(defn- verify-definition-payload [cid definition]
  (let [p (cid-problem cid)]
    (cond
      p (fail p {:field :definitions})
      (not (map? definition)) (fail :patch/share-definition-not-a-map {:cid cid})
      :else
      (try
        (let [actual (identity/definition-cid definition)]
          (if (= cid actual)
            {:ok? true}
            (fail :patch/share-hash-mismatch {:expected cid :actual actual})))
        (catch #?(:clj Throwable :cljs :default) e
          (fail :patch/share-definition-refused
                {:cid cid
                 :problem (or (:problem (ex-data e))
                              :definition/uncanonical-value)}))))))

(defn validate-share
  "A share is a patch plus optional definition payloads. Each payload is
  rehashed by the authority facade. The parallel hasher is never consulted."
  [share]
  (cond
    (not (map? share)) (fail :patch/share-not-a-map)
    :else
    (let [forbidden (forbidden-keys share)
          version (or (:kotoba.definition-share/version share)
                      (:share-version share)
                      share-version)
          patch (:patch share)
          definitions (:definitions share)]
      (cond
        forbidden (fail forbidden)
        (not= share-version version) (fail :patch/share-version-unsupported)
        (nil? patch) (fail :patch/share-patch-missing)
        (and (some? definitions) (not (map? definitions)))
        (fail :patch/share-definitions-not-a-map)
        :else
        (let [pv (validate-patch patch)]
          (if-not (:ok? pv)
            pv
            (or (some (fn [[cid definition]]
                        (let [v (verify-definition-payload cid definition)]
                          (when-not (:ok? v) v)))
                      definitions)
                {:ok? true :share share})))))))

(defn apply-share
  "Validate a share, rehash any payloads, then apply its patch. Never
  authorizes typed eval or host eval."
  [bindings share]
  (let [v (validate-share share)]
    (if-not (:ok? v)
      v
      (apply-patch bindings (:patch share)))))
