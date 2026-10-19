(ns biscuit.token
  "A biscuit: a token anyone holding it can attenuate **offline**, and nobody
  can widen.

  Where a macaroon chains an HMAC — so only the holder of the root key can
  verify — a biscuit chains **public keys**. Each block carries the public
  key of the next block and is signed by the previous block's private key, so
  a verifier needs only the root *public* key. That is the difference that
  matters in a fleet: **verification needs no secret**, so any node, worker
  or browser can verify a token it was handed without being trusted with
  anything.

      authority block   signed by root key,     carries next-public-key
      block 1           signed by key 0,        carries next-public-key
      block n           signed by key n-1,      carries next-public-key
      proof             the final private key, or a signature over it

  ## Attenuation is monotone because of scoping, not because of etiquette

  A later block can add facts, rules and checks. What makes it unable to
  *widen* the token is that **a check is evaluated against the authority
  block's facts plus its own block's facts, and nothing later**. So a block
  cannot inject a fact that satisfies an earlier block's check.

  This is the rule an implementation gets wrong by simply pooling every fact
  into one set — which passes every ordinary test, because ordinary tokens
  have no reason to lie to themselves. There is a test here that only fails
  when the pool is shared.

  ## What is not here, by name

  - **The wire format.** Biscuit v3 serialises to protobuf with Ed25519
    signatures. This library holds the *decision core* and a canonical EDN
    encoding; it does not read or write biscuit protobuf, and does not claim
    interoperability with `biscuit-auth`. A partial decoder that guessed
    would produce plausible tokens, which is worse than none.
  - **Crypto.** `sign-fn` / `verify-fn` are injected, exactly like
    `tech-ipfs-specs-ipns` and `kad`. There is no default and no built-in
    Ed25519.
  - **Third-party blocks** (a block signed by an external authority).
    Refused by name in `append`."
  (:require [kotoba.lang.text :as str]))

(def version "biscuit/edn-v1")

(defn- block-payload
  "The bytes a block's signature covers: its own content **and** the next
  public key. Signing the content alone would let anyone splice a different
  continuation onto a valid block."
  [{:keys [index facts rules checks next-public-key]}]
  (pr-str [version index (vec facts) (vec rules) (vec checks) next-public-key]))

(defn authority
  "The first block, signed with the root key."
  [{:keys [facts rules checks next-public-key root-private-key sign-fn]}]
  (when-not (fn? sign-fn)
    (throw (ex-info "sign-fn is required — this library owns no crypto"
                    {:type :biscuit/no-signer})))
  (when (str/blank? (str next-public-key))
    (throw (ex-info "a block must name the key that may append after it"
                    {:type :biscuit/no-next-key})))
  (let [b {:block/index 0 :block/facts (vec facts) :block/rules (vec rules)
           :block/checks (vec checks) :block/next-public-key next-public-key}
        payload (block-payload {:index 0 :facts facts :rules rules :checks checks
                                :next-public-key next-public-key})]
    {:biscuit/version version
     :biscuit/blocks [(assoc b :block/signature (sign-fn root-private-key payload))]}))

(defn append
  "Attenuate: add a block, signed with the private key the previous block
  named. Needs no contact with the issuer and no secret the issuer holds."
  [t {:keys [facts rules checks next-public-key private-key sign-fn third-party?]}]
  (when third-party?
    (throw (ex-info "third-party blocks are not implemented — refused rather than approximated"
                    {:type :biscuit/unsupported})))
  (when (str/blank? (str next-public-key))
    (throw (ex-info "a block must name the key that may append after it"
                    {:type :biscuit/no-next-key})))
  (let [i (count (:biscuit/blocks t))
        payload (block-payload {:index i :facts facts :rules rules :checks checks
                                :next-public-key next-public-key})]
    (update t :biscuit/blocks conj
            {:block/index i :block/facts (vec facts) :block/rules (vec rules)
             :block/checks (vec checks) :block/next-public-key next-public-key
             :block/signature (sign-fn private-key payload)})))

(defn verify
  "Walk the key chain from `root-public-key`.

  -> `{:ok? true :blocks n}` or `{:ok? false :reason kw :index i}`.

  Each block is checked against the key **the previous block named**, so a
  block cannot choose the key that validates it. The root block is checked
  against the caller's root key, which is the only key the verifier has to
  know."
  [t root-public-key verify-fn]
  (cond
    (not= version (:biscuit/version t)) {:ok? false :reason :unknown-version}
    (empty? (:biscuit/blocks t)) {:ok? false :reason :no-blocks}
    :else
    (loop [[b & more] (:biscuit/blocks t) key root-public-key]
      (if (nil? b)
        {:ok? true :blocks (count (:biscuit/blocks t))}
        (let [payload (block-payload {:index (:block/index b)
                                      :facts (:block/facts b)
                                      :rules (:block/rules b)
                                      :checks (:block/checks b)
                                      :next-public-key (:block/next-public-key b)})]
          (if (verify-fn key payload (:block/signature b))
            (recur more (:block/next-public-key b))
            {:ok? false :reason :signature-mismatch :index (:block/index b)}))))))

(defn scope
  "The facts and rules a block at `index` may reason with: the authority
  block's, plus its own. **Not later blocks'** — that restriction is what
  makes attenuation monotone."
  [t index]
  (let [blocks (:biscuit/blocks t)
        a (first blocks)
        b (nth blocks index)
        pick (fn [k] (into [] (concat (get a k) (when (pos? index) (get b k)))))]
    {:facts (pick :block/facts) :rules (pick :block/rules)}))
