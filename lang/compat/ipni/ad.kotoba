(ns ipni.ad
  "Advertisement and EntryChunk as logical IPLD values (ipni/specs IPNI.md).

  An advertisement does not change the content CID. Putting a provider is
  not a merkle rewrite. `:mutates-cid?` is always false.

  `Entries` is a link to an EntryChunk (or a HAMT). This namespace holds
  the chunk as a value; hashing that value into a CID is `hash-fn`,
  injected, because this library does not own a hasher.

  Signature bytes are `sign-fn` of the unsigned advertisement. There is
  no default sign-fn. An indexer that requires a signature will 400 —
  that is a failure, not a silent pass."
  (:require [ipni.metadata :as metadata]
            [ipni.octets :as oct]))

(def ^:private octets oct/->octets)

(defn entry-chunk
  "One EntryChunk. `entries` are multihashes (octet vectors), not CIDs.
  `next` is an optional CID string of the next chunk."
  [{:keys [entries next]}]
  (cond
    (not (sequential? entries)) {:error :invalid-entries :value entries}
    (some (fn [e] (or (not (sequential? e)) (empty? e))) entries)
    {:error :invalid-multihash}
    :else
    (cond-> {:schema :ipni.entry-chunk
             :entries (mapv octets entries)}
      next (assoc :next next))))

(defn advertisement
  "Build an unsigned advertisement. `:cid` is the **content** CID this
  advertisement claims to provide. It is recorded and never rewritten.

  `:peer` is the provider libp2p peer ID.
  `:addrs` are retrieval multiaddrs (gateway), not publisher HTTP addrs.
  `:context-id` identifies the pin/tenant. Unpin is the same id + `:is-rm`.
  `:entries` is an EntryChunk value or a CID string already stored."
  [{:keys [cid peer addrs context-id is-rm previous-id entries metadata
           extended-provider]
    :or {is-rm false}}]
  (cond
    (not (string? cid)) {:error :invalid-cid :value cid}
    (not (pos? (count cid))) {:error :invalid-cid :value cid}
    (not (string? peer)) {:error :invalid-peer :value peer}
    (not (sequential? addrs)) {:error :invalid-addrs :value addrs}
    (let [ctx (octets context-id)]
      (or (= :invalid ctx) (empty? ctx)))
    {:error :invalid-context-id :value context-id}
    (and (not is-rm)
         (or (nil? entries)
             (and (map? entries) (:error entries))
             (and (map? entries) (empty? (:entries entries)))
             (and (sequential? entries) (empty? entries))))
    {:error :empty-entries}
    :else
    (let [chunk (cond
                  (nil? entries) nil
                  (and (map? entries) (contains? entries :entries)) entries
                  (and (map? entries) (= :ipni.entry-chunk (:schema entries))) entries
                  (string? entries) entries
                  (sequential? entries) (entry-chunk {:entries entries})
                  :else {:error :invalid-entries :value entries})
          md (or metadata (metadata/gateway-http-bytes))]
      (cond
        (and (map? chunk) (:error chunk)) chunk
        (and (map? md) (:error md)) md
        :else
        {:schema :ipni.advertisement
         :cid cid
         :provider peer
         :addresses (vec addrs)
         :context-id (octets context-id)
         :metadata (vec md)
         :is-rm (boolean is-rm)
         :previous-id previous-id
         :entries chunk
         :extended-provider extended-provider
         :mutates-cid? false}))))

(defn from-discover
  "Lift a kotoba.protocol.discover record (by convention, not by require)
  into an advertisement. Forces `:cid` to stay the asked content CID."
  [rec opts]
  (if (:error rec)
    rec
    (advertisement (merge {:cid (:cid rec)
                           :peer (:peer rec)
                           :addrs (:addrs rec)}
                          opts))))

(defn unsigned-fields
  "The map a sign-fn sees. Content CID is not a wire field — identity
  stays off the advertisement bytes. Callers that put the content CID
  into ContextID do that explicitly."
  [ad]
  (when (and (map? ad) (= :ipni.advertisement (:schema ad)))
    (cond-> {:Provider (:provider ad)
             :Addresses (:addresses ad)
             :Entries (:entries ad)
             :ContextID (:context-id ad)
             :Metadata (:metadata ad)
             :IsRm (:is-rm ad)}
      (:previous-id ad) (assoc :PreviousID (:previous-id ad))
      (:extended-provider ad) (assoc :ExtendedProvider (:extended-provider ad)))))

(defn sign
  "Attach signature bytes. `sign-fn` is `(fn [unsigned-fields] octets)`.
  Missing sign-fn is not authenticity — the advertisement is returned
  unsigned so a later 400 can still speak."
  [ad sign-fn]
  (cond
    (:error ad) ad
    (not (ifn? sign-fn)) (assoc ad :signature nil :signed? false)
    :else
    (let [sig (sign-fn (unsigned-fields ad))]
      (assoc ad :signature (octets sig) :signed? true))))
