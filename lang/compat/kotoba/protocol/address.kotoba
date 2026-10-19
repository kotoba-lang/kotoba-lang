(ns kotoba.protocol.address
  "Identity plane: output-addressed vs input-addressed (ADR-2608145200).

  Do not pick one as *the* protocol identity. Nix is the existence proof:
  NAR hash is output, drv hash is input, and the drv bytes themselves
  also have an output CID.

  Holochain's several hashes are *not* several naming schemes. They are
  several *of-what* on the same output-addressed CID grammar:

    EntryHash   → output of entry bytes
    ActionHash  → output of signed action bytes (the overlay's own CID)
    DnaHash     → output of app-definition bytes
    AgentPubKey → did:key (authorization), not a content hash"
  (:require [kotoba.protocol.vocab :as vocab]))

(defn output
  "hash(bytes). Default public identity (`ipfs://{cid}`)."
  [cid]
  (if (vocab/cid? cid)
    {:kind :output :cid cid :plane :identity}
    {:error :invalid-cid :value cid}))

(defn input
  "hash(recipe / call / drv). The recipe is itself bytes, so it also
  has an output CID — stored as `:recipe-cid`."
  [recipe-cid]
  (if (vocab/cid? recipe-cid)
    {:kind :input :recipe-cid recipe-cid :plane :identity}
    {:error :invalid-cid :value recipe-cid}))

(defn recipe-as-output
  "The recipe object, addressed as bytes. Not the eval result."
  [addr]
  (when (and (map? addr) (= :input (:kind addr)))
    (output (:recipe-cid addr))))

(def holochain-kinds
  "Holochain address kinds projected onto this protocol. Runtime
  (conductor, unordered DHT writes) is not adopted — ADR-2608038000."
  {:entry-hash {:plane :identity
                :address :output
                :of :entry
                :public-ref :cid}
   :action-hash {:plane :identity
                 :address :output
                 :of :signed-action
                 :public-ref :cid
                 :effect :action-link}
   :dna-hash {:plane :identity
              :address :output
              :of :app-definition
              :public-ref :cid}
   :agent-pub-key {:plane :authorization
                   :address :did-key
                   :of :agent
                   :public-ref :did}})

(defn holochain-kind
  [k]
  (or (holochain-kinds k) {:error :unknown-holochain-kind :value k}))

(defn classify
  "What this address is a hash *of*. `:of` is required for holochain kinds
  and optional for bare output/input."
  [addr]
  (cond
    (:error addr) addr
    (= :did-key (:address addr)) {:plane :authorization :kind :did-key}
    (= :input (:kind addr)) {:plane :identity :kind :input :of :recipe}
    (= :output (:kind addr)) {:plane :identity :kind :output :of (or (:of addr) :bytes)}
    :else {:error :not-an-address}))
