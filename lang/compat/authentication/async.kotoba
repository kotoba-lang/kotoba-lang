(ns authentication.async
  "Promise-returning mirror of `authentication.core`'s verify/decide flow,
  for hosts whose factor verifiers are asynchronous (every WebCrypto-backed
  edge verifier: `crypto.subtle` returns Promises).

  Only the *orchestration* is duplicated -- the decision itself is
  `authentication.core/decide`, unchanged, so a Worker and a JVM host reach
  the same decision from the same factors. A verifier map may mix
  `IFactorVerifier` (sync) and `IAsyncFactorVerifier` (async) entries; sync
  results are lifted with `js/Promise.resolve`.

  Matches `authentication.identity-service`'s existing style (raw Promises,
  no core.async), so a host wires both without a second async idiom."
  (:require [authentication.core :as core]
            [authentication.ports :as p]))

(defn verify-factor!
  "Promise of one verified factor. Rejects on the same invalid-request and
  missing-verifier conditions `core/verify-factor` throws on."
  [verifiers factor-request response]
  (try
    (when-let [ps (seq (core/factor-request-problems factor-request))]
      (throw (ex-info "invalid authentication factor request" {:authn/problems ps})))
    (let [factor-type (:authn.factor-request/type factor-request)
          verifier (get verifiers factor-type)]
      (cond
        (nil? verifier)
        (throw (ex-info "missing factor verifier" {:authn.factor/type factor-type}))

        (satisfies? p/IAsyncFactorVerifier verifier)
        (js/Promise.resolve (p/verify-factor-async! verifier factor-request response))

        :else
        (js/Promise.resolve (p/verify-factor! verifier factor-request response))))
    (catch :default error
      (js/Promise.reject error))))

(defn authenticate!
  "Promise of the authentication decision. Factor verification runs
  concurrently -- the factors of one request are independent ceremonies,
  and serializing them would make a multi-factor sign-in as slow as the sum
  of its round trips instead of its slowest one."
  [verifiers request factor-requests responses]
  (let [response-by-id (into {} (map (juxt :authn.factor-response/request-id identity) responses))]
    (-> (js/Promise.all
         (clj->js (mapv (fn [fr]
                          (verify-factor! verifiers fr (get response-by-id (:authn.factor-request/id fr))))
                        factor-requests)))
        (.then (fn [factors]
                 (core/decide request (vec factors)))))))
