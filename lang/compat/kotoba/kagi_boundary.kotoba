(ns kotoba.kagi-boundary
  "Reference-only boundary to kagi/kagitaba. Kotoba facts and capabilities must
  never carry secret values. Resolution belongs to an injected kagi runtime adapter."
  (:require [clojure.string :as str]))

(def allowed-schemes #{"kagi" "keychain" "pkcs11" "passkey"})

(defn secret-ref? [x]
  (and (string? x)
       (when-let [[_ scheme body] (re-matches #"^([a-z][a-z0-9+.-]*)://(.+)$" x)]
         (and (contains? allowed-schemes scheme) (not (str/blank? body))))))

(defn reference-record
  "Canonical Kotoba datom-safe pointer to a kagi item and kagitaba category."
  [{:keys [ref category purpose key-epoch]}]
  (when-not (and (secret-ref? ref) (keyword? category) (keyword? purpose)
                 (nat-int? key-epoch))
    (throw (ex-info "invalid kagi secret reference" {:ref-valid? (boolean (secret-ref? ref))
                                                       :category category :purpose purpose
                                                       :key-epoch key-epoch})))
  #:kotoba.secret{:ref ref :category category :purpose purpose :key-epoch key-epoch})

(defn assert-reference-only! [record]
  (when (some #(contains? record %) [:secret :value :plaintext :private-key :password :token])
    (throw (ex-info "secret value crossed the kotoba/kagi boundary"
                    {:keys (vec (keys record))})))
  (when-not (secret-ref? (:kotoba.secret/ref record))
    (throw (ex-info "missing kagi secret reference" {})))
  record)
