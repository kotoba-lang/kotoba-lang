(ns identity.adapters.evm
  "Pinned, read-only JVM host for EAS and Human Passport.

  This namespace owns transport and ABI decoding. The portable adapters remain
  pure policy boundaries. Every contract read verifies the remote chain ID
  before issuing eth_call."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [identity.adapters.eas :as eas]
            [identity.adapters.erc8004 :as erc8004]
            [identity.adapters.human-passport :as passport])
  (:import (java.math BigInteger)
           (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
                          HttpResponse$BodyHandlers)
           (java.nio.charset StandardCharsets)
           (java.time Duration)
           (java.util Base64)))

(def ^:private get-attestation-selector "a3112a64")
(def ^:private get-schema-selector "a2ea7c6e")
(def ^:private owner-of-selector "6352211e")
(def ^:private token-uri-selector "c87b56dd")
(def ^:private get-agent-wallet-selector "00339509")
(def ^:private get-identity-registry-selector "bc4d861b")
(def ^:private reputation-summary-selector "81bbba58")
(def ^:private validation-summary-selector "1b7cabd6")
(def ^:private max-rpc-response-bytes (* 2 1024 1024))
(def ^:private max-document-response-bytes (* 1024 1024))

(defn- fail! [code details]
  (throw (ex-info "EVM trust reader rejected input"
                  (assoc details :identity.evm/problem code))))

(defn- strip-0x [x]
  (if (and (string? x) (str/starts-with? x "0x")) (subs x 2) x))

(defn- hex-string? [x]
  (boolean (and (string? x)
                (even? (count (strip-0x x)))
                (re-matches #"[0-9a-fA-F]*" (strip-0x x)))))

(defn- bytes-length [hex]
  (quot (count (strip-0x hex)) 2))

(defn- ensure-range! [hex offset length label]
  (when-not (and (hex-string? hex)
                 (integer? offset) (not (neg? offset))
                 (integer? length) (not (neg? length))
                 (<= (+ offset length) (bytes-length hex)))
    (fail! :abi/out-of-bounds {:field label :offset offset :length length
                               :available (when (hex-string? hex)
                                            (bytes-length hex))})))

(defn- slice-bytes [hex offset length label]
  (ensure-range! hex offset length label)
  (subs (strip-0x hex) (* 2 offset) (* 2 (+ offset length))))

(defn- uint-at [hex offset label]
  (BigInteger. (slice-bytes hex offset 32 label) 16))

(defn- exact-long [^BigInteger value label]
  (try (.longValueExact value)
       (catch ArithmeticException _
         (fail! :abi/integer-overflow {:field label :value (str value)}))))

(defn- offset-at [hex offset label]
  (let [n (exact-long (uint-at hex offset label) label)]
    (when (neg? n) (fail! :abi/negative-offset {:field label :value n}))
    n))

(defn- bytes32-at [hex offset label]
  (str "0x" (str/lower-case (slice-bytes hex offset 32 label))))

(defn- address-at [hex offset label]
  (let [word (slice-bytes hex offset 32 label)]
    (when-not (re-matches #"0{24}[0-9a-fA-F]{40}" word)
      (fail! :abi/address-padding {:field label}))
    (str "0x" (str/lower-case (subs word 24)))))

(defn- bool-at [hex offset label]
  (case (exact-long (uint-at hex offset label) label)
    0 false
    1 true
    (fail! :abi/bool {:field label})))

(defn- bytes-at [hex offset label]
  (let [length (exact-long (uint-at hex offset (str label ".length"))
                           (str label ".length"))]
    (slice-bytes hex (+ offset 32) length label)))

(defn- utf8-at [hex offset label]
  (let [raw (bytes-at hex offset label)
        bytes (byte-array (map #(unchecked-byte (Integer/parseInt % 16))
                               (map (partial apply str) (partition 2 raw))))]
    (String. bytes StandardCharsets/UTF_8)))

(defn- uint-word [n label]
  (when-not (and (integer? n) (not (neg? n)))
    (fail! :abi/uint {:field label :value n}))
  (let [raw (.toString (BigInteger. (str n)) 16)]
    (when (> (count raw) 64)
      (fail! :abi/uint-overflow {:field label :value (str n)}))
    (str (apply str (repeat (- 64 (count raw)) "0")) raw)))

(defn- address-word [address label]
  (when-not (and (string? address)
                 (re-matches #"0x[0-9a-fA-F]{40}" address))
    (fail! :abi/address {:field label :value address}))
  (str (apply str (repeat 24 "0")) (str/lower-case (subs address 2))))

(defn- empty-string-abi [] (uint-word 0 "string.length"))

(defn- address-array-abi [addresses]
  (str (uint-word (count addresses) "addresses.length")
       (apply str (map-indexed #(address-word %2 (str "addresses[" %1 "]"))
                               addresses))))

(defn- single-uint-call-data [selector n]
  (str "0x" selector (uint-word n "agent-id")))

(defn- reputation-call-data [agent-id clients]
  (when-not (seq clients)
    (fail! :erc8004/clients-empty {}))
  (let [client-data (address-array-abi clients)
        client-offset (* 4 32)
        tag1-offset (+ client-offset (quot (count client-data) 2))
        tag2-offset (+ tag1-offset 32)]
    (str "0x" reputation-summary-selector
         (uint-word agent-id "agent-id")
         (uint-word client-offset "clients.offset")
         (uint-word tag1-offset "tag1.offset")
         (uint-word tag2-offset "tag2.offset")
         client-data (empty-string-abi) (empty-string-abi))))

(defn- validation-call-data [agent-id validators]
  (when-not (seq validators)
    (fail! :erc8004/validators-empty {}))
  (let [validator-data (address-array-abi validators)
        validator-offset (* 3 32)
        tag-offset (+ validator-offset (quot (count validator-data) 2))]
    (str "0x" validation-summary-selector
         (uint-word agent-id "agent-id")
         (uint-word validator-offset "validators.offset")
         (uint-word tag-offset "tag.offset")
         validator-data (empty-string-abi))))

(defn- decode-address-result [hex label]
  (address-at hex 0 label))

(defn- decode-string-result [hex label]
  (utf8-at hex (offset-at hex 0 (str label ".offset")) label))

(defn- signed-int128-at [hex offset label]
  (let [word (slice-bytes hex offset 32 label)
        padding (subs word 0 32)
        raw (BigInteger. (subs word 32) 16)
        negative? (.testBit raw 127)]
    (when-not (= padding (apply str (repeat 32 (if negative? "f" "0"))))
      (fail! :abi/int128-padding {:field label}))
    (if negative? (.subtract raw (.shiftLeft BigInteger/ONE 128)) raw)))

(defn- scaled-number [value decimals]
  (if (zero? decimals) value (/ value (.pow BigInteger/TEN decimals))))

(defn decode-reputation-summary-result [hex]
  (let [count (exact-long (uint-at hex 0 "reputation.count") "reputation.count")
        value (signed-int128-at hex 32 "reputation.value")
        decimals (exact-long (uint-at hex 64 "reputation.decimals")
                             "reputation.decimals")]
    (when (> decimals 18)
      (fail! :abi/decimals {:field "reputation.decimals" :value decimals}))
    {:count count :score (scaled-number value decimals)
     :value value :value-decimals decimals}))

(defn decode-validation-summary-result [hex]
  (let [summary {:count (exact-long (uint-at hex 0 "validation.count")
                                    "validation.count")
                 :score (exact-long (uint-at hex 32 "validation.average-response")
                                    "validation.average-response")}]
    (when (> (:score summary) 100)
      (fail! :abi/validation-response {:value (:score summary)}))
    summary))

(defn decode-eas-attestation-result
  "Decode EAS getAttestation(bytes32). Rejects malformed offsets and values."
  [hex]
  (let [base (offset-at hex 0 "attestation.tuple")
        data-offset (offset-at hex (+ base (* 9 32)) "attestation.data-offset")]
    {:uid (bytes32-at hex base "attestation.uid")
     :schema-uid (bytes32-at hex (+ base 32) "attestation.schema")
     :time (exact-long (uint-at hex (+ base (* 2 32)) "attestation.time")
                       "attestation.time")
     :expiration-time (exact-long
                       (uint-at hex (+ base (* 3 32)) "attestation.expiration-time")
                       "attestation.expiration-time")
     :revocation-time (exact-long
                       (uint-at hex (+ base (* 4 32)) "attestation.revocation-time")
                       "attestation.revocation-time")
     :reference-uid (bytes32-at hex (+ base (* 5 32)) "attestation.ref-uid")
     :recipient (address-at hex (+ base (* 6 32)) "attestation.recipient")
     :attester (address-at hex (+ base (* 7 32)) "attestation.attester")
     :revocable? (bool-at hex (+ base (* 8 32)) "attestation.revocable")
     :revoked? (pos? (exact-long
                      (uint-at hex (+ base (* 4 32)) "attestation.revocation-time")
                      "attestation.revocation-time"))
     :data (str "0x" (bytes-at hex (+ base data-offset) "attestation.data"))}))

(defn decode-eas-schema-result
  "Decode EAS SchemaRegistry getSchema(bytes32)."
  [hex]
  (let [base (offset-at hex 0 "schema.tuple")
        schema-offset (offset-at hex (+ base (* 3 32)) "schema.schema-offset")]
    {:uid (bytes32-at hex base "schema.uid")
     :resolver (address-at hex (+ base 32) "schema.resolver")
     :revocable? (bool-at hex (+ base (* 2 32)) "schema.revocable")
     :schema (utf8-at hex (+ base schema-offset) "schema.schema")}))

(defn decode-human-passport-data
  "Decode the current Human Passport EAS score payload."
  [hex]
  (let [base 0
        stamps-offset (offset-at hex (+ base (* 5 32)) "passport.stamps-offset")
        array-base (+ base stamps-offset)
        n (exact-long (uint-at hex array-base "passport.stamps.length")
                      "passport.stamps.length")]
    (when (> n 4096)
      (fail! :abi/array-too-large {:field "passport.stamps" :count n}))
    {:passing-score (bool-at hex base "passport.passing-score")
     :score-decimals (exact-long (uint-at hex (+ base 32) "passport.score-decimals")
                                 "passport.score-decimals")
     :scorer-id (uint-at hex (+ base (* 2 32)) "passport.scorer-id")
     :score (exact-long (uint-at hex (+ base (* 3 32)) "passport.score")
                        "passport.score")
     :threshold (exact-long (uint-at hex (+ base (* 4 32)) "passport.threshold")
                            "passport.threshold")
     :stamps
     (mapv (fn [i]
             ;; For a dynamic array of dynamic tuples, element offsets are
             ;; relative to the word immediately after the array length.
             (let [offsets-base (+ array-base 32)
                   tuple-offset (offset-at hex (+ offsets-base (* i 32))
                                           (str "passport.stamps[" i "].offset"))
                   tuple-base (+ offsets-base tuple-offset)
                   provider-offset (offset-at hex tuple-base
                                              (str "passport.stamps[" i "].provider-offset"))]
               {:provider (utf8-at hex (+ tuple-base provider-offset)
                                   (str "passport.stamps[" i "].provider"))
                :score (uint-at hex (+ tuple-base 32)
                                (str "passport.stamps[" i "].score"))}))
           (range n))}))

(defn- bounded-http-body [response limit kind]
  (with-open [stream (.body response)]
    (let [bytes (.readNBytes stream (inc limit))]
      (when (> (alength bytes) limit)
        (fail! kind {:limit-bytes limit}))
      (String. bytes StandardCharsets/UTF_8))))

(defn- default-post! [^HttpClient client rpc-url body]
  (let [request (-> (HttpRequest/newBuilder (URI/create rpc-url))
                    (.timeout (Duration/ofSeconds 15))
                    (.header "content-type" "application/json")
                    (.POST (HttpRequest$BodyPublishers/ofString body))
                    (.build))
        response (.send client request (HttpResponse$BodyHandlers/ofInputStream))]
    {:status (.statusCode response)
     :body (bounded-http-body response max-rpc-response-bytes
                              :rpc/response-too-large)}))

(defn- default-get! [^HttpClient client url]
  (let [request (-> (HttpRequest/newBuilder (URI/create url))
                    (.timeout (Duration/ofSeconds 15))
                    (.header "accept" "application/json")
                    (.GET)
                    (.build))
        response (.send client request (HttpResponse$BodyHandlers/ofInputStream))]
    {:status (.statusCode response)
     :body (bounded-http-body response max-document-response-bytes
                              :document/too-large)}))

(defrecord JsonRpc [rpc-url post-fn client request-id])

(defn json-rpc
  "Construct a pinned JSON-RPC transport. `post-fn` is injectable for tests and
  receives [rpc-url JSON-body], returning {:status n :body string}."
  [{:keys [rpc-url post-fn]}]
  (when-not (and (string? rpc-url) (re-matches #"https://\S+" rpc-url))
    (fail! :rpc/url {:rpc-url rpc-url}))
  (->JsonRpc rpc-url post-fn
             (-> (HttpClient/newBuilder)
                 (.connectTimeout (Duration/ofSeconds 10))
                 (.build))
             (atom 0)))

(defn- rpc-call! [{:keys [rpc-url post-fn client request-id]} method params]
  (let [id (swap! request-id inc)
        request {:jsonrpc "2.0" :id id :method method :params params}
        body (json/write-str request)
        {:keys [status body]} ((or post-fn #(default-post! client %1 %2)) rpc-url body)]
    (when-not (= 200 status)
      (fail! :rpc/http {:status status :method method}))
    (when-not (string? body)
      (fail! :rpc/body {:method method}))
    (when (> (count (.getBytes ^String body StandardCharsets/UTF_8))
             max-rpc-response-bytes)
      (fail! :rpc/response-too-large {:method method}))
    (let [response (try (json/read-str body :key-fn keyword)
                        (catch Exception e
                          (fail! :rpc/json {:method method :cause (.getMessage e)})))]
      (when-not (= id (:id response))
        (fail! :rpc/id {:expected id :actual (:id response)}))
      (when-let [error (:error response)]
        (fail! :rpc/error {:method method :error error}))
      (when-not (contains? response :result)
        (fail! :rpc/no-result {:method method}))
      (:result response))))

(defn- assert-chain! [rpc expected-chain-id]
  (let [result (rpc-call! rpc "eth_chainId" [])]
    (when-not (and (string? result) (re-matches #"0x[0-9a-fA-F]+" result))
      (fail! :rpc/chain-id-result {:result result}))
    (let [actual (.longValueExact (BigInteger. (subs result 2) 16))]
      (when-not (= expected-chain-id actual)
        (fail! :rpc/chain-mismatch {:expected expected-chain-id :actual actual})))))

(defn- call-data [selector bytes32]
  (when-not (and (string? bytes32) (re-matches #"0x[0-9a-fA-F]{64}" bytes32))
    (fail! :abi/bytes32 {:value bytes32}))
  (str "0x" selector (subs bytes32 2)))

(defn- eth-call! [rpc coordinate to data]
  (assert-chain! rpc (:chain-id coordinate))
  (rpc-call! rpc "eth_call" [{:to to :data data} "latest"]))

(defrecord EvmEASReader [rpc]
  eas/IEASReader
  (read-schema! [_ coordinate schema-uid]
    (decode-eas-schema-result
     (eth-call! rpc coordinate (:schema-registry-address coordinate)
                (call-data get-schema-selector schema-uid))))
  (read-attestation! [_ coordinate attestation-uid]
    (decode-eas-attestation-result
     (eth-call! rpc coordinate (:eas-address coordinate)
                (call-data get-attestation-selector attestation-uid)))))

(defn eas-reader [rpc-options]
  (->EvmEASReader (json-rpc rpc-options)))

(defrecord EvmHumanPassportDecoder []
  passport/IHumanPassportDecoder
  (decode-score! [_ schema raw-data]
    (when-not (= passport/score-schema schema)
      (fail! :passport/schema {:schema schema}))
    (decode-human-passport-data raw-data)))

(defn human-passport-decoder [] (->EvmHumanPassportDecoder))

(defn- registration-key [k]
  (case k
    "x402Support" :x402-support
    "supportedTrust" :supported-trust
    "agentId" :agent-id
    "agentRegistry" :agent-registry
    (keyword k)))

(defn- parse-registration [body]
  (try (json/read-str body :key-fn registration-key)
       (catch Exception e
         (fail! :document/json {:cause (.getMessage e)}))))

(defn- resolve-document-url [{:keys [ipfs-gateway allowed-https-hosts]} uri]
  (cond
    (str/starts-with? uri "https://")
    (let [parsed (try (URI/create uri)
                      (catch IllegalArgumentException e
                        (fail! :document/uri {:uri uri :cause (.getMessage e)})))
          host (some-> parsed .getHost str/lower-case)
          allowed (set (map str/lower-case allowed-https-hosts))]
      (when-not (and host (contains? allowed host))
        (fail! :document/host-not-allowed {:host host :uri uri}))
      uri)
    (str/starts-with? uri "ipfs://")
    (do
      (when-not (and (string? ipfs-gateway)
                     (re-matches #"https://\S+" ipfs-gateway))
        (fail! :document/ipfs-gateway {:uri uri}))
      (str (str/replace ipfs-gateway #"/+$" "") "/ipfs/" (subs uri 7)))
    :else nil))

(defn- read-registration! [^JsonRpc rpc {:keys [get-fn] :as options} uri]
  (when-not (and (string? uri) (not-empty uri))
    (fail! :document/uri {:uri uri}))
  (if (str/starts-with? uri "data:application/json;base64,")
    (let [encoded (subs uri (count "data:application/json;base64,"))
          decoded (try (String. (.decode (Base64/getDecoder) encoded)
                                StandardCharsets/UTF_8)
                       (catch IllegalArgumentException e
                         (fail! :document/base64 {:cause (.getMessage e)})))]
      (when (> (count (.getBytes decoded StandardCharsets/UTF_8))
               max-document-response-bytes)
        (fail! :document/too-large {:uri-scheme "data"}))
      (parse-registration decoded))
    (let [url (resolve-document-url options uri)]
      (when-not url
        (fail! :document/scheme {:uri uri}))
      (let [{:keys [status body]} ((or get-fn #(default-get! (:client rpc) %)) url)]
        (when-not (= 200 status)
          (fail! :document/http {:status status :url url}))
        (when-not (string? body)
          (fail! :document/body {:url url}))
        (when (> (count (.getBytes ^String body StandardCharsets/UTF_8))
                 max-document-response-bytes)
          (fail! :document/too-large {:url url}))
        (parse-registration body)))))

(def ^:private zero-address "0x0000000000000000000000000000000000000000")

(defrecord EvmERC8004Reader [rpc document-options]
  erc8004/IERC8004Reader
  (read-registry-bindings! [_ coordinate]
    {:reputation-identity-registry
     (decode-address-result
      (eth-call! rpc coordinate (:reputation-registry coordinate)
                 (str "0x" get-identity-registry-selector))
      "reputation.identity-registry")
     :validation-identity-registry
     (decode-address-result
      (eth-call! rpc coordinate (:validation-registry coordinate)
                 (str "0x" get-identity-registry-selector))
      "validation.identity-registry")})
  (read-agent! [_ coordinate agent-id]
    (let [identity-registry (:identity-registry coordinate)
          owner (decode-address-result
                 (eth-call! rpc coordinate identity-registry
                            (single-uint-call-data owner-of-selector agent-id))
                 "agent.owner")
          agent-uri (decode-string-result
                     (eth-call! rpc coordinate identity-registry
                                (single-uint-call-data token-uri-selector agent-id))
                     "agent.uri")
          wallet (decode-address-result
                  (eth-call! rpc coordinate identity-registry
                             (single-uint-call-data get-agent-wallet-selector agent-id))
                  "agent.wallet")]
      {:owner owner
       :agent-uri agent-uri
       :registration (read-registration! rpc document-options agent-uri)
       :agent-wallet (when-not (= zero-address wallet) wallet)
       :wallet-verified? (not= zero-address wallet)}))
  (read-reputation! [_ coordinate agent-id clients]
    (decode-reputation-summary-result
     (eth-call! rpc coordinate (:reputation-registry coordinate)
                (reputation-call-data agent-id clients))))
  (read-validations! [_ coordinate agent-id validators]
    (decode-validation-summary-result
     (eth-call! rpc coordinate (:validation-registry coordinate)
                (validation-call-data agent-id validators)))))

(defn erc8004-reader
  "Construct a coordinate-driven ERC-8004 reader. No deployment address is
  inferred. Options additionally accept `allowed-https-hosts`, `ipfs-gateway`,
  and injectable `get-fn`. Direct HTTPS registration hosts are denied unless
  explicitly allowlisted."
  [options]
  (->EvmERC8004Reader (json-rpc options)
                      (select-keys options [:allowed-https-hosts :ipfs-gateway :get-fn])))
