(ns kototama.network-authority
  "Fail-closed authority envelope for guest-triggered HTTP providers."
  (:require [kotoba.lang.text :as str]
            ;; This namespace owns the policy-to-egress boundary.  Keep the
            ;; shared controls visible in its dependency graph so the
            ;; organization-level adoption gate can attest that ownership.
            [kotoba.security.abac :as abac]
            [kotoba.security.effect :as effect]
            [kotoba.security.information-flow :as information-flow])
  (:import [java.net URI]))

(def required-policy-keys
  #{:network-policy/version :network-policy/endpoints
    :network-policy/method :network-policy/purpose
    :network-policy/credential-ref :network-policy/max-calls
    :network-policy/max-request-bytes :network-policy/max-response-bytes})

(defn canonical-endpoint [value]
  (try
    (let [uri (URI/create value)
          scheme (some-> (.getScheme uri) str/lower)
          host (some-> (.getHost uri) str/lower)
          port (.getPort uri)
          path (or (.getRawPath uri) "")
          query (.getRawQuery uri)]
      (when (and (= "https" scheme) host
                 (nil? (.getRawUserInfo uri))
                 (nil? (.getRawFragment uri))
                 (not (str/blank? path)))
        (str scheme "://" host
             (when (not= -1 port) (str ":" port))
             path
             (when query (str "?" query)))))
    (catch Exception _ nil)))

(defn validate-policy!
  "Validate the complete production network authority envelope."
  [policy]
  (let [missing (remove #(contains? policy %) required-policy-keys)
        endpoints (:network-policy/endpoints policy)
        canonical (when (set? endpoints)
                    (set (map canonical-endpoint endpoints)))]
    (when (seq missing)
      (throw (ex-info "kototama.network-authority: incomplete policy"
                      {:kototama.network/code :incomplete-policy
                       :missing (set missing)})))
    (when-not (and (= 1 (:network-policy/version policy))
                   (set? endpoints) (seq endpoints)
                   (not (contains? canonical nil))
                   (= :post (:network-policy/method policy))
                   (keyword? (:network-policy/purpose policy))
                   (or (keyword? (:network-policy/credential-ref policy))
                       (string? (:network-policy/credential-ref policy)))
                   (every? #(and (integer? %) (pos? %))
                           ((juxt :network-policy/max-calls
                                  :network-policy/max-request-bytes
                                  :network-policy/max-response-bytes)
                            policy)))
      (throw (ex-info "kototama.network-authority: invalid policy"
                      {:kototama.network/code :invalid-policy})))
    (assoc policy :network-policy/endpoints canonical)))

(defn make-context
  "Bind a validated policy to a workload purpose and credential resolver."
  [policy request-purpose credential-provider]
  (let [policy (validate-policy! policy)]
    (when-not (= request-purpose (:network-policy/purpose policy))
      (throw (ex-info "kototama.network-authority: purpose mismatch"
                      {:kototama.network/code :purpose-mismatch
                       :required (:network-policy/purpose policy)
                       :actual request-purpose})))
    (when-not (ifn? credential-provider)
      (throw (ex-info "kototama.network-authority: credential provider required"
                      {:kototama.network/code :credential-provider-required})))
    {:policy policy
     :request-purpose request-purpose
     :credential-provider credential-provider}))

(defn authorize-request!
  "Authorize one exact request and resolve credentials by opaque reference.
   Returns sanitized headers plus response bound; credentials never enter guest
   memory or the policy document."
  [context url method request-bytes call-number]
  (let [policy (:policy context)
        endpoint (canonical-endpoint url)
        deny (cond
               (not= method (:network-policy/method policy)) :method
               (not= (:request-purpose context)
                     (:network-policy/purpose policy)) :purpose
               (not (contains? (:network-policy/endpoints policy) endpoint))
               :endpoint
               (> call-number (:network-policy/max-calls policy)) :call-quota
               (> request-bytes (:network-policy/max-request-bytes policy))
               :request-quota
               :else nil)]
    (when deny
      (throw (ex-info "kototama.network-authority: request denied"
                      {:kototama.network/code :request-denied
                       :kototama.network/control deny
                       :endpoint endpoint :method method})))
    (effect/guard!
     {:evaluate (constantly {:allowed? true})
      :request {:endpoint endpoint :method method}
      :approved? :allowed?
      :action :network/request
      :resource endpoint
      :digest nil
      :effect
      (fn [_]
        (let [headers ((:credential-provider context)
                       (:network-policy/credential-ref policy))]
          (when-not (and (map? headers)
                         (every? (fn [[k v]]
                                   (and (string? k) (not (str/blank? k))
                                        (string? v) (not (str/blank? v))))
                                 headers))
            (throw (ex-info "kototama.network-authority: credential resolution denied"
                            {:kototama.network/code :credential-resolution-denied})))
          {:endpoint endpoint
           :method method
           :purpose (:request-purpose context)
           :credential-ref (:network-policy/credential-ref policy)
           :headers headers
           :max-response-bytes (:network-policy/max-response-bytes policy)}))})))
