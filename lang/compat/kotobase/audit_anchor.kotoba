(ns kotobase.audit-anchor
  "Fail-closed qualification for signed, append-only audit receipt chains.

  Cryptographic signing is injected by the deployment key service; this module
  binds each receipt to its predecessor and an independently observed anchor."
  (:require [clojure.edn :as edn])
  (:import [java.math BigInteger]
           [java.security MessageDigest]))

(def policy-path "qualification/audit-anchor-policy.edn")
(def genesis "GENESIS")

(defn read-policy [] (edn/read-string (slurp policy-path)))
(defn digest [receipt]
  (format "%064x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256")
                                            (.getBytes (pr-str (dissoc receipt :receipt/signature))
                                                       "UTF-8")))))

(defn evaluate
  [policy {:keys [receipts anchor verify-signature]}]
  (let [chain-ok? (loop [previous genesis [receipt & more] receipts]
                    (if-not receipt true
                            (and (= previous (:receipt/previous-digest receipt))
                                 (recur (digest receipt) more))))
        signatures-ok? (every? #(boolean (verify-signature
                                           (dissoc % :receipt/signature)
                                           (:receipt/signature %))) receipts)
        head (when-let [last-receipt (last receipts)] (digest last-receipt))
        anchor-ok? (and (:anchor/external? anchor)
                        (:anchor/reconciled? anchor)
                        (= head (:anchor/receipt-digest anchor)))
        errors (cond-> []
                 (empty? receipts) (conj :audit/empty-chain)
                 (not chain-ok?) (conj :audit/chain)
                 (not signatures-ok?) (conj :audit/signature)
                 (not anchor-ok?) (conj :audit/anchor))]
    {:audit/anchored? (empty? errors) :audit/errors errors :audit/head head}))
