(ns kotoba.lang.code-identity
  "Compatibility facade for DefCID.

  The canonical implementation and authority live in
  `kotoba.kir.definition-identity`. These aliases preserve the language
  repository's public namespace while ensuring there is only one algorithm,
  payload version, and lock-admission implementation."
  (:require [kotoba.kir.definition-identity :as identity]))

(def payload-version identity/payload-version)
(def definition-required identity/definition-required)

(def cid? identity/cid?)
(def f64 identity/f64)
(def i64 identity/i64)
(def normalize identity/normalize)
(def definition-error identity/definition-error)
(def identity-payload identity/identity-payload)
(def canonical-bytes identity/canonical-bytes)
(def canonical-hex identity/canonical-hex)
(def definition-cid identity/definition-cid)
(def verify-locked-definitions identity/verify-locked-definitions)
(def admit-build identity/admit-build)
(def check-case identity/check-case)
