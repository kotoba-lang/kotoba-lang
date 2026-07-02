(ns kotoba.lang.capability-cacao
  "Crypto-free mapping from a VERIFIED CACAO delegation-chain result to
  capability grants (ADR-safe-capability-language, S4b; see the \"CACAO
  chains\" section of docs/lang/capability-values.md).

  This namespace NEVER sees a cacao_b64 or a signature: chain verification is
  owned by the crypto layer (kotoba-lang/cacao, `cacao.core/verify-chain`),
  which hands over only its result map

    {:chain/valid? bool :chain/problems [..] :chain/root-iss did
     :chain/holder did :chain/resources #{uri ..} :chain/expires ts-or-nil
     :chain/depth n}

  `grants-from-chain` maps that shape to kotoba.lang.capability-values grants
  ({:grant/kind kw :grant/resources #{str-or-:any} :grant/expires date-or-nil
  :grant/id str}) ready for `intersect-grants` / guard-call. An unverified
  chain (:chain/valid? false, or anything that is not a valid-chain map)
  NEVER yields grants — the result is {:grants [] :problems [..]}.

  Resource URI convention (`/`-to-slash keyword):

    kotoba://cap/<kind>/<resource>

  where <kind> is the capability kind keyword printed without the colon — a
  bare kind maps to a bare keyword (\"graph-read\" -> :graph-read) and a
  namespaced kind keeps its namespace as a path segment
  (\"host/clipboard-read\" -> :host/clipboard-read). <resource> is the
  resource string, or `*` for the :any wildcard scope. Only kinds registered
  in kotoba.lang.capability-values/effect-for-kind are granted; every other
  resource URI is SKIPPED (reported under :skipped), never silently granted."
  (:require [clojure.string :as str]
            [kotoba.lang.capability-values :as values]))

(def cap-uri-prefix "kotoba://cap/")

(defn kind->segment
  "URI path segment for a capability kind keyword: :graph-read ->
  \"graph-read\", :host/clipboard-read -> \"host/clipboard-read\"."
  [kind]
  (if (namespace kind)
    (str (namespace kind) "/" (name kind))
    (name kind)))

(def ^:private kinds-longest-first
  "Registered kinds ordered longest URI segment first, so a namespaced kind
  (\"host/ledger-append\") is matched before any shorter prefix could."
  (sort-by #(- (count (kind->segment %))) (keys values/effect-for-kind)))

(defn parse-cap-uri
  "Parses a `kotoba://cap/<kind>/<resource>` URI against the registered
  capability kinds. Returns {:kind kw :resource str-or-:any} for a registered
  kind (`*` maps to :any), otherwise {:skip <note>} where <note> is one of
  :not-a-string, :not-a-cap-uri, :unknown-kind, :empty-resource."
  [uri]
  (cond
    (not (string? uri)) {:skip :not-a-string}
    (not (str/starts-with? uri cap-uri-prefix)) {:skip :not-a-cap-uri}
    :else
    (let [tail (subs uri (count cap-uri-prefix))
          [kind resource] (some (fn [kind]
                                  (let [seg (str (kind->segment kind) "/")]
                                    (when (str/starts-with? tail seg)
                                      [kind (subs tail (count seg))])))
                                kinds-longest-first)]
      (cond
        (nil? kind) {:skip :unknown-kind}
        (str/blank? resource) {:skip :empty-resource}
        (= "*" resource) {:kind kind :resource :any}
        :else {:kind kind :resource resource}))))

(defn- chain-expires->grant-expires
  "Chain expiry (an ISO-8601 instant such as \"2026-07-10T00:00:00Z\", or a
  plain date) -> the grant-shape date string, by truncating to the date part.
  Returns nil for nil and :invalid for a non-conforming value (fail closed:
  an unintelligible expiry must not widen into \"never expires\")."
  [x]
  (cond
    (nil? x) nil
    (not (string? x)) :invalid
    :else (let [date (first (str/split x #"T"))]
            (if (values/date-string? date) date :invalid))))

(defn grants-from-chain
  "Maps a VERIFIED chain result (the cacao.core/verify-chain shape — this
  namespace never sees b64/crypto) to capability grants.

  On success returns {:grants [<grant> ..] :skipped [{:resource uri
  :note kw} ..]}: one grant per registered `kotoba://cap/<kind>/<resource>`
  URI in :chain/resources (sorted for determinism), with
  :grant/expires = :chain/expires (truncated to its date part) and
  :grant/id = \"cacao:<root-iss>:<index>\" so every grant is traceable to the
  delegating root issuer. Unknown kinds and non-cap URIs are SKIPPED with a
  note — never granted.

  Fails closed with {:grants [] :problems [..]} when the chain result is not
  a map, :chain/valid? is not true (:chain/not-verified — grants are never
  derived from an unverified chain), :chain/root-iss is missing, or
  :chain/expires is present but unintelligible."
  [chain-result]
  (cond
    (not (map? chain-result))
    {:grants [] :problems [{:problem :chain/not-a-map :value chain-result}]}

    (not (true? (:chain/valid? chain-result)))
    {:grants []
     :problems (into [{:problem :chain/not-verified}]
                     (filter map? (:chain/problems chain-result)))}

    (not (values/non-empty-string? (:chain/root-iss chain-result)))
    {:grants [] :problems [{:problem :chain/root-iss-missing
                            :value (:chain/root-iss chain-result)}]}

    :else
    (let [expires (chain-expires->grant-expires (:chain/expires chain-result))]
      (if (= :invalid expires)
        {:grants [] :problems [{:problem :chain/expires-invalid
                                :value (:chain/expires chain-result)}]}
        (let [root-iss (:chain/root-iss chain-result)
              parsed (map (fn [uri] [uri (parse-cap-uri uri)])
                          (sort (map str (:chain/resources chain-result))))
              granted (filter (comp :kind second) parsed)
              skipped (remove (comp :kind second) parsed)]
          {:grants (vec (map-indexed
                         (fn [i [_uri {:keys [kind resource]}]]
                           {:grant/kind kind
                            :grant/resources #{resource}
                            :grant/expires expires
                            :grant/id (str "cacao:" root-iss ":" i)})
                         granted))
           :skipped (vec (for [[uri {:keys [skip]}] skipped]
                           {:resource uri :note skip}))})))))

;; ---------------------------------------------------------------------------
;; Conformance-case runner (shared by the test suite and the bb gate)

(defn run-case
  "Runs one :cacao-grants conformance fixture. DATA is a chain result map."
  [_tc data]
  (grants-from-chain data))

(defn check-case
  "Runs a :cacao-grants conformance case and compares the outcome with the
  expectations declared in TC. Returns {:ok? bool :case id :actual ...}."
  [tc data]
  (let [actual (run-case tc data)
        ok?
        (case (:kind tc)
          :accept
          (and (not (contains? actual :problems))
               (or (not (contains? tc :expected-grants))
                   (= (:expected-grants tc) (:grants actual)))
               (or (not (contains? tc :expected-skipped))
                   (= (:expected-skipped tc)
                      (mapv :resource (:skipped actual)))))

          :expect-error
          (and (empty? (:grants actual))
               (boolean (seq (:problems actual)))
               (or (not (contains? tc :problem-contains))
                   (boolean (some #(= (:problem-contains tc) (:problem %))
                                  (:problems actual)))))

          false)]
    {:ok? ok? :case (:id tc) :actual actual}))
