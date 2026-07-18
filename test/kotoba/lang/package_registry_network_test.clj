(ns kotoba.lang.package-registry-network-test
  "Real HTTP round-trip tests via the JDK's built-in
  com.sun.net.httpserver.HttpServer (no new test dependency, no live
  Kubo/IPFS daemon or external network needed -- a real loopback HTTP
  server on an ephemeral port, serving real bytes over a real socket).
  This is the first I/O-touching test in this repo; the pattern here
  (spin up a real local server per test, real client against it, no
  mocking of HttpClient itself) is the convention to follow for any
  future network-adapter test in this namespace family."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [multiformats.core :as mf]
            [kotoba.lang.package-registry-network :as net]
            [kotoba.lang.package-registry :as registry])
  (:import (com.sun.net.httpserver HttpServer HttpHandler HttpExchange)
           (java.net InetSocketAddress)))

(defn- ^:private write-response!
  "Isolated from the reify body so `handle`'s method body is a single,
  unambiguous void-returning call -- reify's Java interop return-type
  checker gets confused by a `let`/`with-open` chain ending in a Java
  void call inline in the method body."
  [^HttpExchange exchange handler-fn]
  (let [result (handler-fn (.getPath (.getRequestURI exchange)))
        status (int (nth result 0))
        ^bytes body (nth result 1)]
    (.sendResponseHeaders exchange status (alength body))
    (with-open [os (.getResponseBody exchange)]
      (.write os body)))
  nil)

(defn- start-server!
  "Real loopback HTTP server on an ephemeral port. HANDLER-FN receives the
  request path and returns [status ^bytes body]."
  [handler-fn]
  (let [server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext server "/"
      (reify HttpHandler
        (handle [_ exchange] (write-response! exchange handler-fn))))
    (.setExecutor server nil)
    (.start server)
    server))

(defn- server-port [^HttpServer server] (.getPort (.getAddress server)))

(defn- registry-bytes [registry-edn]
  (.getBytes (pr-str registry-edn) "UTF-8"))

;; Real CIDv1 strings (not the classic "bafy..." placeholder-sniff strings
;; this ecosystem's package-contract/cid? explicitly no longer accepts --
;; see that fn's own docstring) so record-problems doesn't reject this
;; fixture as :registry/cid-invalid before resolution ever runs.
(defn- fake-cid [seed] (mf/cidv1-raw (.getBytes ^String seed "UTF-8")))

(def sample-registry
  {:kotoba.registry/version 1
   :records
   [{:registry/name "kotoba-lang/json"
     :registry/version "0.1.0"
     :registry/repo-rid (fake-cid "repo-rid")
     :registry/commit "abc123"
     :registry/tree-cid (fake-cid "tree-cid")
     :registry/manifest-cid (fake-cid "manifest-cid")
     :registry/signers ["did:key:zSigner1"]
     :registry/capabilities []}]})

(deftest fetch-bytes-real-http-round-trip
  (let [body (registry-bytes sample-registry)
        server (start-server! (fn [_path] [200 body]))
        port (server-port server)]
    (try
      (let [result (net/fetch-bytes "anything"
                                    {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
        (is (:ok? result))
        (is (= (seq body) (seq (:bytes result)))))
      (finally (.stop server 0)))))

(deftest fetch-bytes-reports-non-200-as-problem
  (let [server (start-server! (fn [_path] [404 (byte-array 0)]))
        port (server-port server)]
    (try
      (let [result (net/fetch-bytes "missing-cid"
                                    {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
        (is (not (:ok? result)))
        (is (= :registry/fetch-http-status (:problem (first (:problems result))))))
      (finally (.stop server 0)))))

(deftest verify-cid-matches-real-bytes-rejects-tampered-bytes
  (let [body (registry-bytes sample-registry)
        real-cid (mf/cidv1-raw body)]
    (is (net/verify-cid real-cid body))
    (is (not (net/verify-cid real-cid (registry-bytes (assoc sample-registry :tampered true)))))))

(deftest fetch-and-parse-registry-full-round-trip-matches-direct-normalize
  (let [body (registry-bytes sample-registry)
        real-cid (mf/cidv1-raw body)
        server (start-server! (fn [_path] [200 body]))
        port (server-port server)]
    (try
      (let [result (net/fetch-and-parse-registry
                    real-cid {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
        (is (:ok? result))
        (is (= (registry/normalize sample-registry) (:registry result))))
      (finally (.stop server 0)))))

(deftest fetch-and-parse-registry-rejects-cid-mismatch
  (testing "gateway serving bytes that don't hash to the requested CID is rejected, not trusted"
    (let [real-body (registry-bytes sample-registry)
          wrong-body (registry-bytes (assoc sample-registry :swapped true))
          real-cid (mf/cidv1-raw real-body)
          ;; server dishonestly serves DIFFERENT bytes than what real-cid names
          server (start-server! (fn [_path] [200 wrong-body]))
          port (server-port server)]
      (try
        (let [result (net/fetch-and-parse-registry
                      real-cid {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
          (is (not (:ok? result)))
          (is (= :registry/cid-mismatch (:problem (first (:problems result))))))
        (finally (.stop server 0))))))

(deftest fetch-and-parse-registry-rejects-malformed-edn
  (let [garbage (.getBytes "{:unterminated-map 1" "UTF-8")
        real-cid (mf/cidv1-raw garbage)
        server (start-server! (fn [_path] [200 garbage]))
        port (server-port server)]
    (try
      (let [result (net/fetch-and-parse-registry
                    real-cid {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
        (is (not (:ok? result)))
        (is (= :registry/edn-parse-failed (:problem (first (:problems result))))))
      (finally (.stop server 0)))))

(deftest resolve-record-network-resolves-through-real-fetch
  (let [body (registry-bytes sample-registry)
        real-cid (mf/cidv1-raw body)
        server (start-server! (fn [_path] [200 body]))
        port (server-port server)]
    (try
      (let [result (net/resolve-record-network
                    real-cid "kotoba-lang/json" "0.1.0"
                    {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
        (is (:ok? result))
        (is (= "kotoba-lang/json" (:registry/name (:record result)))))
      (finally (.stop server 0)))))

(deftest lock-from-requests-network-resolves-through-real-fetch
  (let [body (registry-bytes sample-registry)
        real-cid (mf/cidv1-raw body)
        server (start-server! (fn [_path] [200 body]))
        port (server-port server)]
    (try
      (let [result (net/lock-from-requests-network
                    real-cid [{:name "kotoba-lang/json" :version "0.1.0"}]
                    {:gateway-base (str "http://127.0.0.1:" port "/ipfs/")})]
        (is (:ok? result))
        (is (= 1 (count (:deps result)))))
      (finally (.stop server 0)))))
