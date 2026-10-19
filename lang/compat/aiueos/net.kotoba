(ns aiueos.net
  "Surface-level network helpers for `:net/fetch` (ADR-0010).

  Surface providers are data in `aiueos.surface`; host adapters that actually
  perform HTTP must call through this namespace so every fetch path shares
  `aiueos.policy/net-url-allowed?` (security gap-close 2026-07-17). An empty
  net-allow fails closed at policy verification (`:net-allow-empty`); this
  namespace is the per-URL gate at call time."
  (:require [aiueos.policy :as policy]))

(defn allow-url?
  "Thin alias of `policy/net-url-allowed?` for host adapters."
  [policy url]
  (policy/net-url-allowed? policy url))

(defn deny-map
  [url]
  {:ok? false
   :aiueos.net/denied :net-url-not-allowed
   :aiueos.net/url (str url)})

(defn guarded-fetch
  "If POLICY allows URL, invoke FETCH-FN with URL and return its result
  wrapped as `{:ok? true :aiueos.net/result ...}`. Otherwise return
  `deny-map` without calling FETCH-FN (fail closed, no network I/O)."
  [policy url fetch-fn]
  (if (allow-url? policy url)
    {:ok? true :aiueos.net/result (fetch-fn url)}
    (deny-map url)))

(defn fixture-fetch
  "Deterministic fetch from a URL→body map (browser/cloud test fixtures).
  Still gated by net-allow — a fixture for an origin outside the allowlist
  is denied before lookup, so tests cannot accidentally prove the wrong
  property."
  [policy url->body url]
  (guarded-fetch policy url
                 (fn [u]
                   (if-let [body (get url->body u)]
                     {:status 200 :body body}
                     {:status 404 :body nil}))))
