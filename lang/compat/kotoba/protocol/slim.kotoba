(ns kotoba.protocol.slim
  "Authority-free application envelope carried by AGNTCY SLIM.

  SLIM owns routing, sessions, reconnect and group delivery. This pure model
  owns what a Kotoba host is willing to place inside that transport. It does
  not pretend that constructing an envelope establishes a SLIM session."
  (:require [kotoba.lang.text :as str]))

(def profile "org.kotoba.a2a-over-slim/1")

(def authority-keys
  #{"grant" "grants" "approval" "approval-receipt" "credential"
    "credentials" "cookie" "cookies" "private-key" "wallet-authority"
    "replay-permission"})

(defn- canonical-key [value]
  (-> (if (keyword? value) (name value) (str value))
      (str/replace #"_" "-")
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      str/lower))

(defn- forbidden-path
  ([value] (forbidden-path [] value))
  ([path value]
   (cond
     (map? value)
     (some (fn [[k v]]
             (let [canonical (canonical-key k)
                   next-path (conj path k)]
               (if (contains? authority-keys canonical)
                 next-path
                 (forbidden-path next-path v))))
           value)

     (sequential? value)
     (some identity
           (map-indexed (fn [i v] (forbidden-path (conj path i) v)) value))

     :else nil)))

(defn authority-free?
  "True when no authority-bearing field appears anywhere in VALUE. This is a
  structural denylist at the portable seam; hosts still apply their own
  positive admission policy before delivery."
  [value]
  (nil? (forbidden-path value)))

(defn name-parts?
  "SLIM names are hierarchical. Keep the common library independent of a
  binding by representing the organization/namespace/service coordinates as
  three non-blank strings."
  [parts]
  (and (vector? parts)
       (= 3 (count parts))
       (every? #(and (string? %) (not (str/blank? %))) parts)))

(defn envelope
  "Build one idempotent A2A-over-SLIM application envelope. `delivery-id` is
  minted by the durable host before transport submission and is the receiver's
  duplicate-delivery key."
  [{:keys [delivery-id from to channel payload]}]
  (cond
    (not (and (string? delivery-id) (not (str/blank? delivery-id))))
    {:error :delivery-id-required}
    (not (name-parts? from)) {:error :invalid-from}
    (not (name-parts? to)) {:error :invalid-to}
    (and channel (not (name-parts? channel))) {:error :invalid-channel}
    (not (map? payload)) {:error :payload-object-required}
    :else
    (if-let [path (forbidden-path payload)]
      {:error :authority-field-refused :path path}
      (cond-> {:profile profile
               :deliveryId delivery-id
               :from from
               :to to
               :payload payload}
        channel (assoc :channel channel)))))

(defn delivery-key [envelope]
  (when (and (= profile (:profile envelope))
             (string? (:deliveryId envelope)))
    [(:from envelope) (:deliveryId envelope)]))
