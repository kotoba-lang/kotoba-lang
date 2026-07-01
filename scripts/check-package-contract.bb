#!/usr/bin/env bb
(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.set :as set]
         '[clojure.string :as str])

(def root (io/file "."))
(def manifest-path "lang/package-conformance/manifest.edn")

(defn read-edn [path]
  (edn/read-string (slurp (io/file root path))))

(defn fail [msg data]
  (throw (ex-info msg data)))

(defn non-empty-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn cid? [x]
  (and (non-empty-string? x) (str/starts-with? x "bafy")))

(defn require-keys [m keys msg]
  (doseq [k keys]
    (when-not (contains? m k)
      (fail msg {:missing k}))))

(def manifest-required
  [:kotoba.package/name
   :kotoba.package/version
   :kotoba.package/repo-rid
   :kotoba.package/source
   :kotoba.package/capabilities
   :kotoba.package/signatures])

(def lock-required
  [:dep/name
   :dep/version
   :dep/repo-rid
   :dep/commit
   :dep/tree-cid
   :dep/manifest-cid
   :dep/signers
   :dep/capabilities])

(def allowed-package-kinds
  #{:library :adapter :schema-contract :tool :component})

(defn contract-keyword? [x]
  (and (keyword? x)
       (let [s (subs (str x) 1)]
         (or (str/starts-with? s "app.kotoba.")
             (str/starts-with? s "wire.kotoba.")))))

(defn check-package-kind! [kind data]
  (when (and kind (not (contains? allowed-package-kinds kind)))
    (fail "unknown package kind" (assoc data :allowed allowed-package-kinds))))

(defn check-contract-vector! [value field]
  (when (and (some? value) (not (vector? value)))
    (fail "contract surface vector required" {:field field :value value}))
  (doseq [contract value]
    (when-not (contract-keyword? contract)
      (fail "contract surface keyword required"
            {:field field :value contract}))))

(defn check-contract-surfaces! [m prefix]
  (check-contract-vector! (get m (keyword prefix "provides"))
                          (keyword prefix "provides"))
  (check-contract-vector! (get m (keyword prefix "consumes"))
                          (keyword prefix "consumes")))

(defn check-signatures! [sigs]
  (when-not (seq sigs)
    (fail "signature required" {}))
  (doseq [sig sigs]
    (require-keys sig [:did :alg :sig] "signature missing required field")
    (when-not (non-empty-string? (:did sig))
      (fail "signature did required" {:signature sig}))
    (when-not (= :ed25519 (:alg sig))
      (fail "signature alg unsupported" {:signature sig}))
    (when-not (non-empty-string? (:sig sig))
      (fail "signature bytes required" {:signature sig}))))

(defn check-package-manifest! [m _case]
  (require-keys m manifest-required "missing required package field")
  (check-package-kind! (:kotoba.package/kind m) {:package (:kotoba.package/name m)})
  (check-contract-surfaces! m "kotoba.package")
  (when (and (= :adapter (:kotoba.package/kind m))
             (empty? (:kotoba.package/consumes m)))
    (fail "adapter consumes required" {:package (:kotoba.package/name m)}))
  (when (and (= :schema-contract (:kotoba.package/kind m))
             (empty? (:kotoba.package/provides m)))
    (fail "schema-contract provides required" {:package (:kotoba.package/name m)}))
  (when-not (cid? (:kotoba.package/repo-rid m))
    (fail "repo-rid cid required" {:value (:kotoba.package/repo-rid m)}))
  (let [source (:kotoba.package/source m)]
    (require-keys source [:git-commit :tree-cid :manifest-cid] "missing required source field")
    (when-not (cid? (:tree-cid source))
      (fail "tree cid required" {:source source}))
    (when-not (cid? (:manifest-cid source))
      (fail "manifest cid required" {:source source})))
  (when-not (vector? (:kotoba.package/capabilities m))
    (fail "capabilities vector required" {:value (:kotoba.package/capabilities m)}))
  (check-signatures! (:kotoba.package/signatures m))
  true)

(defn check-lockfile! [m tc]
  (when-not (= 1 (:kotoba.lock/version m))
    (fail "lock version 1 required" {:value (:kotoba.lock/version m)}))
  (when-not (seq (:deps m))
    (fail "lock deps required" {}))
  (let [declared (set (:declared-capabilities tc))]
    (doseq [dep (:deps m)]
      (require-keys dep lock-required "missing required lock field")
      (check-package-kind! (:dep/kind dep) {:dependency (:dep/name dep)})
      (check-contract-surfaces! dep "dep")
      (doseq [k [:dep/repo-rid :dep/tree-cid :dep/manifest-cid]]
        (when-not (cid? (get dep k))
          (fail "cid required" {:field k :value (get dep k)})))
      (when-not (seq (:dep/signers dep))
        (fail "signer required" {:dep dep}))
      (let [signers (set (:dep/signers dep))
            blocked (set/union (set (:revoked-signers tc))
                               (set (:expired-signers tc))
                               (set (:compromised-signers tc)))
            bad (seq (set/intersection signers blocked))]
        (when bad
          (fail "signer not currently trusted"
                {:signers (vec bad)
                 :dependency (:dep/name dep)})))
      (when-not (set/subset? (set (:dep/capabilities dep)) declared)
        (fail "capability grant exceeds package declaration"
              {:grant (:dep/capabilities dep)
               :declared (:declared-capabilities tc)}))))
  true)

(defn check-case! [tc]
  (let [path (str "lang/package-conformance/" (:file tc))
        data (read-edn path)
        check! (case (:type tc)
                 :package-manifest check-package-manifest!
                 :lockfile check-lockfile!
                 (fail "unknown case type" tc))]
    (case (:kind tc)
      :accept
      (do (check! data tc)
          (println "ok" (:id tc)))
      :expect-error
      (try
        (check! data tc)
        (fail "expected error" tc)
        (catch clojure.lang.ExceptionInfo e
          (let [msg (.getMessage e)]
            (when-not (str/includes? msg (:error-contains tc))
              (fail "unexpected error"
                    {:case (:id tc)
                     :expected (:error-contains tc)
                     :actual msg}))
            (println "ok" (:id tc) "->" msg))))
      (fail "unknown case kind" tc))))

(let [manifest (read-edn manifest-path)]
  (when-not (= 1 (:kotoba.lang.package.conformance/version manifest))
    (fail "package conformance version 1 required" manifest))
  (doseq [tc (:cases manifest)]
    (check-case! tc)))
