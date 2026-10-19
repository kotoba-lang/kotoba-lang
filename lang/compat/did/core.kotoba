(ns did.core
  "EDN-first helpers for DID identifiers and DID Documents."
  (:require [clojure.string :as str]))

(def did-context "https://www.w3.org/ns/did/v1")
(def context-key (keyword "@context"))

(def did-re
  #"^did:([a-z0-9]+):([^/?#]+)(?:/([^?#]*))?(?:\?([^#]*))?(?:#(.*))?$")

(defn parse
  "Parse a DID or DID URL into EDN. Throws on invalid input."
  [s]
  (let [[_ method method-id path query fragment] (re-matches did-re s)]
    (when-not method
      (throw (ex-info "invalid DID or DID URL" {:input s})))
    (cond-> {:did (str "did:" method ":" method-id)
             :method method
             :method-id method-id}
      path (assoc :path path)
      query (assoc :query query)
      fragment (assoc :fragment fragment))))

(defn did? [s]
  (boolean (and (string? s) (re-matches #"^did:[a-z0-9]+:.+" s))))

(defn document
  "Construct a DID Document map. Keys use EDN keywords but render directly to
  DID-core JSON key names when stringified by a JSON library."
  [id opts]
  (merge {context-key [did-context]
          :id id
          :verificationMethod []
          :authentication []
          :assertionMethod []
          :service []}
         opts))

(def ^:private b58-alphabet
  "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(def ^:private b58-idx
  (into {} (map-indexed (fn [i c] [c i]) b58-alphabet)))

(defn base58btc-decode
  "Decode base58btc to an int vector. Portable .cljc implementation."
  [s]
  (let [bytes (reduce
               (fn [bs c]
                 (let [idx (or (b58-idx c)
                               (throw (ex-info "bad base58btc character" {:char c})))
                       [bs carry]
                       (reduce (fn [[acc carry] d]
                                 (let [v (+ (* d 58) carry)]
                                   [(conj acc (rem v 256)) (quot v 256)]))
                               [[] idx] bs)]
                   (loop [bs bs carry carry]
                     (if (pos? carry)
                       (recur (conj bs (rem carry 256)) (quot carry 256))
                       bs))))
               [] (seq s))
        nzeros (count (take-while #(= \1 %) s))]
    (vec (concat (repeat nzeros 0) (rseq bytes)))))

(defn base58btc
  "Encode byte ints to base58btc."
  [data]
  (let [in (map #(bit-and (int %) 0xff) (seq data))
        digits (reduce
                (fn [digits b]
                  (let [[digits carry]
                        (reduce (fn [[ds carry] d]
                                  (let [v (+ (* d 256) carry)]
                                    [(conj ds (rem v 58)) (quot v 58)]))
                                [[] b] digits)]
                    (loop [digits digits carry carry]
                      (if (pos? carry)
                        (recur (conj digits (rem carry 58)) (quot carry 58))
                        digits))))
                [] in)
        nzeros (count (take-while zero? in))]
    (str (apply str (repeat nzeros \1))
         (apply str (map #(nth b58-alphabet %) (rseq digits))))))

(defn did-key-ed25519? [did]
  (str/starts-with? did "did:key:z6Mk"))

(defn did-key->public-key
  "Parse did:key Ed25519 (multicodec 0xed01) to an int vector public key."
  [did]
  (let [{:keys [method method-id]} (parse did)]
    (when-not (= "key" method)
      (throw (ex-info "expected did:key" {:did did})))
    (when-not (str/starts-with? method-id "z")
      (throw (ex-info "expected base58btc z multibase did:key" {:did did})))
    (let [bytes (base58btc-decode (subs method-id 1))]
      (when-not (and (= 34 (count bytes))
                     (= 0xed (first bytes))
                     (= 0x01 (second bytes)))
        (throw (ex-info "expected Ed25519 did:key multicodec 0xed01" {:did did})))
      (vec (drop 2 bytes)))))

(defn public-key->did-key
  "Raw Ed25519 public key byte ints -> did:key."
  [pub]
  (when-not (= 32 (count pub))
    (throw (ex-info "Ed25519 public key must be 32 bytes" {:got (count pub)})))
  (str "did:key:z" (base58btc (concat [0xed 0x01] pub))))

(defn did-key-document
  "Resolve an Ed25519 did:key locally to a DID Document."
  [did]
  (let [pub (did-key->public-key did)
        kid (str did "#" (subs did (count "did:key:")))]
    (document did
              {:verificationMethod
               [{:id kid
                 :type "Ed25519VerificationKey2020"
                 :controller did
                 :publicKeyMultibase (str "z" (base58btc (concat [0xed 0x01] pub)))}]
               :authentication [kid]
               :assertionMethod [kid]})))

(defn did-web
  "Build a did:web id from a host and optional path segments."
  [host & path]
  (str "did:web:" (str/join ":" (cons host path))))

(defn did-web-url
  "Return the HTTPS URL where a did:web DID Document is expected."
  [did]
  (let [{:keys [method method-id]} (parse did)]
    (when-not (= "web" method)
      (throw (ex-info "expected did:web" {:did did})))
    (let [[host & path] (str/split method-id #":")]
      (if (seq path)
        (str "https://" host "/" (str/join "/" path) "/did.json")
        (str "https://" host "/.well-known/did.json")))))

(defn did-web-document
  "Construct a local did:web DID Document skeleton."
  [host {:keys [path] :or {path []} :as opts}]
  (let [id (apply did-web host path)]
    (document id (dissoc opts :path))))

(defn resolve-local
  "Local, no-network resolution for methods this library can derive."
  [did]
  (let [{:keys [method method-id]} (parse did)]
    (case method
      "key" (did-key-document did)
      "web" (let [[host & path] (str/split method-id #":")]
              (did-web-document host {:path path}))
      (throw (ex-info "unsupported local DID method" {:did did :method method})))))
