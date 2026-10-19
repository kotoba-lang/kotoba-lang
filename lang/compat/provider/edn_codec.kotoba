(ns provider.edn-codec
  "Host-side W4 guest codec and bounded canonical audit wire (ADR 0256–0284).

  Loads content-addressed typed wasm packages from the classpath registry and
  invokes pure request/reply EDN exports via Node + kotoba browser-host.

  This does **not** perform host I/O (network, spawn, store, CSPRNG). It only
  runs pure guest codecs so hosts can audit kit request/reply shapes without
  reimplementing EDN encode in Clojure. Audit events expose bounded
  `kotoba.value.v1` bytes; EDN fields remain compatibility evidence.

  ADR 0257–0259 wraps/factories; ADR 0260–0268 guest host surfaces + inject;
  ADR 0270 inject parity (git/entropy/fs-read); ADR 0264 ops W4 round-trips.

  Requires Node and a resolvable `browser-host.mjs` (sibling compiler checkout
  or `KOTOBA_BROWSER_HOST`). When unavailable, functions return
  `{:ok false :reason :browser-host-unavailable}`."
  (:require [json.data-json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [provider.git :as git]
            [provider.git-transport :as git-t]
            [provider.http-transport :as http-t]
            [provider.kit-package :as kit]
            [provider.process :as process]
            [provider.process-transport :as process-t]
            [provider.entropy :as entropy]
            [provider.entropy-transport :as entropy-t]
            [provider.scoped-fs-transport :as fs-t]
            [provider.secret-transport :as secret-t]
            [provider.value-codec :as value-codec])
  (:import (java.io File)))

(defn- browser-host-path
  "Absolute path to browser-host.mjs, or nil."
  []
  (or (System/getenv "KOTOBA_BROWSER_HOST")
      (let [cands [(io/file ".." "compiler" "runtime" "browser-host.mjs")
                   (io/file "/Users/junkawasaki/github/com-junkawasaki/orgs/kotoba-lang/compiler/runtime/browser-host.mjs")]]
        (some (fn [^File f]
                (when (.exists f) (.getAbsolutePath f)))
              cands))))

(defn- resource-file
  "Materialize a classpath wasm resource to a temp file (Node needs a path)."
  [resource]
  (let [url (io/resource resource)]
    (when-not url
      (throw (ex-info "wasm package resource missing"
                      {:phase :edn-codec :resource resource})))
    (let [tmp (File/createTempFile "kotoba-edn-codec-" ".wasm")]
      (.deleteOnExit tmp)
      (io/copy (io/input-stream url) tmp)
      (.getAbsolutePath tmp))))

(defn- js-quote [s]
  (str "'"
       (-> (str s)
           (str/replace "\\" "\\\\")
           (str/replace "'" "\\'"))
       "'"))

(defn- encode-arg [a]
  (cond
    (string? a) (js-quote a)
    (integer? a) (str a "n")
    (boolean? a) (if a "true" "false")
    :else (throw (ex-info "unsupported edn-codec arg type"
                          {:phase :edn-codec :arg a}))))

(defn- host-options-js
  "Emit instantiateKotoba options object (allowCapabilities + typedCapCall).

  inject-mode:
    nil           — no capability inject (pure exports only)
    :echo         — typedCapCall returns the request string (prove cap path)
    :ok-200       — HTTP fixed ok EDN arm (cap 4)
    :secret-value — secret fixed value arm (cap 21)
    :process-ok   — process fixed ok arm (cap 20)
    :git-ok       — git fixed ok arm (cap 22; same shape as process ok)
    :entropy-hex  — entropy fixed hex arm (cap 23)
    :fs-content   — scoped-fs fixed content arm (cap 19 read)
    :fs-written   — scoped-fs fixed written arm (cap 19 write)
  allow-capabilities — seq of integer cap ids (e.g. [4] http, [19] fs, [20] process, [21] secret).
  primary-cap-id — cap id matched in inject body (default first of allow)."
  [{:keys [allow-capabilities inject-mode primary-cap-id]}]
  (if (and (nil? inject-mode) (empty? allow-capabilities))
    "{}"
    (let [allow (or allow-capabilities [])
          cap-id (long (or primary-cap-id (first allow) 4))
          allow-js (str "[" (str/join "," (map str allow)) "]")
          inject-js (case inject-mode
                      :echo (str "typedCapCall(id,request){if(id===" cap-id
                                 ")return request;throw new Error('unimpl '+id);}")
                      :ok-200 (str "typedCapCall(id,request){if(id===" cap-id
                                   ")return "
                                   (js-quote "{:tag :ok :status 200 :body \"injected\"}")
                                   ";throw new Error('unimpl '+id);}")
                      :secret-value (str "typedCapCall(id,request){if(id===" cap-id
                                         ")return "
                                         (js-quote "{:tag :value :value \"s3cr3t\"}")
                                         ";throw new Error('unimpl '+id);}")
                      :process-ok (str "typedCapCall(id,request){if(id===" cap-id
                                       ")return "
                                       (js-quote "{:tag :ok :exit 0 :stdout \"ok\" :stderr \"\"}")
                                       ";throw new Error('unimpl '+id);}")
                      :git-ok (str "typedCapCall(id,request){if(id===" cap-id
                                   ")return "
                                   (js-quote "{:tag :ok :exit 0 :stdout \"ok\" :stderr \"\"}")
                                   ";throw new Error('unimpl '+id);}")
                      :entropy-hex (str "typedCapCall(id,request){if(id===" cap-id
                                        ")return "
                                        (js-quote "{:tag :hex :hex \"0123456789abcdef\"}")
                                        ";throw new Error('unimpl '+id);}")
                      :fs-content (str "typedCapCall(id,request){if(id===" cap-id
                                       ")return "
                                       (js-quote "{:tag :content :content \"payload\"}")
                                       ";throw new Error('unimpl '+id);}")
                      :fs-written (str "typedCapCall(id,request){if(id===" cap-id
                                       ")return "
                                       (js-quote "{:tag :written :written true}")
                                       ";throw new Error('unimpl '+id);}")
                      nil nil
                      (throw (ex-info "unknown inject-mode"
                                      {:phase :edn-codec :inject-mode inject-mode})))]
      (str "{"
           "allowCapabilities:" allow-js
           (when inject-js (str "," inject-js))
           "}"))))

(defn- invoke-export*
  "Internal: invoke export with optional host capability inject (ADR 0262)."
  [package-name export args host-opts]
  (let [host (browser-host-path)
        table (kit/load-wasm-packages-table)
        entry (kit/wasm-package-for table package-name)]
    (cond
      (nil? host)
      {:ok false :reason :browser-host-unavailable}

      (nil? entry)
      {:ok false :reason :unknown-package :detail (str package-name)}

      :else
      (try
        (let [wasm (resource-file (:resource entry))
              call (str "h.instance.exports[" (js-quote (name export)) "]("
                        (str/join "," (map encode-arg args)) ")")
              opts-js (host-options-js host-opts)
              script (str "import { readFileSync } from 'fs';"
                          "import { instantiateKotoba } from "
                          (pr-str (str "file://" host))
                          ";"
                          "const h=await instantiateKotoba(readFileSync("
                          (pr-str wasm) ")," opts-js ");"
                          "const v=" call ";"
                          "if(typeof v==='bigint'){console.log(JSON.stringify({t:'i64',v:v.toString()}));}"
                          "else if(typeof v==='string'){console.log(JSON.stringify({t:'s',v:v}));}"
                          "else {console.error('bad type',typeof v); process.exit(3);}")
              pb (doto (ProcessBuilder. ["node" "--input-type=module" "-e" script])
                   (.redirectErrorStream true))
              p (.start pb)
              out (slurp (.getInputStream p))
              code (.waitFor p)]
          (if (zero? code)
            (let [line (str/trim out)
                  ;; last JSON line only (cap debug may print earlier)
                  lines (str/split-lines line)
                  json-line (or (last (filter #(str/starts-with? % "{") lines)) line)
                  parsed (json/read-str json-line)]
              (case (get parsed "t")
                "s" {:ok true :value (get parsed "v")}
                "i64" {:ok true :value (Long/parseLong (get parsed "v"))}
                {:ok false :reason :bad-result :detail line}))
            {:ok false :reason :node-exit :detail out :code code}))
        (catch Exception e
          {:ok false :reason :exception :detail (ex-message e)})))))

(defn invoke-export
  "Invoke `export` on registry package `:name` with args (strings/i64).

  Returns `{:ok true :value string-or-long}` or
  `{:ok false :reason keyword :detail string}`."
  [package-name export args]
  (invoke-export* package-name export args {}))

;; High-level pure codec helpers (no host I/O)

(defn secret-request-edn
  "W4 secret request EDN via :secret-record-kv-edn."
  [name]
  (invoke-export :secret-record-kv-edn :secret_request_rec_kv_edn [name]))

(defn secret-reply-value-edn
  [value]
  (invoke-export :secret-record-kv-edn :secret_reply_value_rec_kv_edn [value]))

(defn secret-reply-error-edn
  [code message]
  (invoke-export :secret-record-kv-edn :secret_reply_error_rec_kv_edn [code message]))

(defn entropy-request-edn
  [n]
  (invoke-export :entropy-record-kv-edn :entropy_req_rec_kv_edn [(long n)]))

(defn process-request-edn
  "argv-edn is a prebuilt EDN vector string e.g. [\"echo\" \"hi\"]."
  [argv-edn max-stdout timeout-ms]
  (invoke-export :process-record-kv-edn :process_req_rec_kv_edn
                 [argv-edn (long max-stdout) (long timeout-ms)]))

(defn codec-aot-complete?
  "ADR 0256: pure request/result EDN codec packages landed for ops kits
  through W4 0255. Host authority remains outside guest AOT."
  []
  true)

;; --- ADR 0257: audit-hook integration (pure EDN only; no extra host I/O) ---

(defn- keyword-code [code]
  (cond
    (keyword? code) (name code)
    (string? code) code
    :else (str code)))

(defn- codec-value [result]
  (when (and (map? result) (:ok result) (string? (:value result)))
    (:value result)))

(defn- codec-audit-bytes [kit direction result]
  (when-let [text (codec-value result)]
    (try
      (value-codec/legacy-edn->audit-bytes kit direction text)
      (catch Exception _ nil))))

;; --- ADR 0285: guest-decided canonical bytes (secret kit first) -------------

(defn secret-request-value-hex
  "Canonical `kotoba.value.v1` audit bytes for a secret request, as hex, chosen
  by the guest rather than re-encoded by the host."
  [name]
  (invoke-export :secret-value-wire :secret_request_audit_hex [name]))

(defn secret-reply-value-value-hex
  [value]
  (invoke-export :secret-value-wire :secret_reply_value_audit_hex [value]))

(defn secret-reply-error-value-hex
  [code message]
  (invoke-export :secret-value-wire :secret_reply_error_audit_hex [code message]))

(defn- guest-audit-bytes
  "Admit guest-decided bytes. Returns nil when the guest rejected the input
  (empty hex) or when the bytes are not this envelope in canonical form."
  [kit direction result]
  (when-let [hex (codec-value result)]
    (when (seq hex)
      (try
        (value-codec/guest-hex->audit-bytes kit direction hex)
        (catch Exception _ nil)))))

(defn- codec-audit-fields [kit request-result reply-result]
  {:value-format value-codec/audit-format
   :request-value-bytes (codec-audit-bytes kit :request request-result)
   :reply-value-bytes (codec-audit-bytes kit :reply reply-result)})

(defn- complete-audit-wire? [request-edn reply-edn wire]
  (and request-edn reply-edn
       (:request-value-bytes wire)
       (:reply-value-bytes wire)))

(defn http-headers-list-edn
  "Build 2-header EDN list for recursive-record-kv (exactly two pairs)."
  [name1 value1 name2 value2]
  (invoke-export :recursive-record-kv-edn :headers_list_edn
                 [(str name1) (str value1) (str name2) (str value2)]))

(defn http-request-edn
  "W4 HTTP request EDN. headers-edn from `http-headers-list-edn`."
  [url body timeout-ms headers-edn]
  (invoke-export :recursive-record-kv-edn :request_rec_kv_edn
                 [(str url) (str body) (long timeout-ms) (str headers-edn)]))

(defn http-result-ok-edn
  [status body headers-edn]
  (invoke-export :recursive-record-kv-edn :result_ok_rec_kv_edn
                 [(long status) (str body) (str headers-edn)]))

(defn http-result-err-edn
  "retryable: 1 true, 0 false."
  [code message retryable]
  (invoke-export :recursive-record-kv-edn :result_err_rec_kv_edn
                 [(str code) (str message) (long retryable)]))

(defn- secret-audit-bytes
  "Prefer the bytes the guest decided; fall back to host re-encode of the EDN.

  Returns `[bytes source]` where source is `:guest` (ADR 0285), `:host-legacy`
  (ADR 0284), or nil when neither produced admitted bytes. The source travels
  with the event because the two paths are not the same claim: `:guest` means
  the guest chose the byte sequence, `:host-legacy` means the host did."
  [direction guest-result edn-result]
  (if-let [g (guest-audit-bytes :secret direction guest-result)]
    [g :guest]
    (if-let [h (codec-audit-bytes :secret direction edn-result)]
      [h :host-legacy]
      [nil nil])))

(defn wrap-secret-fetch
  "Wrap a secret `:fetch` transport so each call audits pure W4 request/reply.

  `on-call` receives a map:
  `{:kit :secret :op :get :name n :request-edn s :reply-edn s :reply-tag t
    :request-value-bytes b :reply-value-bytes b
    :request-value-source :guest|:host-legacy :reply-value-source …}`

  Costs four guest invocations per audited fetch (request/reply x EDN/value),
  up from two before ADR 0285. This is evidence machinery on the audit path,
  not the secret authority path; the underlying fetch is still the only secret
  authority exercised. Does not change fetch semantics. Codec/on-call failures
  are swallowed."
  ([fetch] (wrap-secret-fetch fetch (fn [_])))
  ([fetch on-call]
   (when-not (fn? fetch)
     (throw (ex-info "wrap-secret-fetch requires a fetch fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "wrap-secret-fetch requires an on-call fn"
                     {:phase :edn-codec})))
   (fn [{:keys [name] :as req}]
     (let [req-codec (secret-request-edn name)
           req-wire (secret-request-value-hex name)
           reply (fetch req)
           reply-value (str (:value reply))
           reply-code (keyword-code (:code reply))
           reply-message (str (or (:message reply) ""))
           reply-codec (case (:tag reply)
                         :value (secret-reply-value-edn reply-value)
                         :error (secret-reply-error-edn reply-code reply-message)
                         {:ok false})
           reply-wire (case (:tag reply)
                        :value (secret-reply-value-value-hex reply-value)
                        :error (secret-reply-error-value-hex reply-code reply-message)
                        {:ok false})
           [req-bytes req-source] (secret-audit-bytes :request req-wire req-codec)
           [rep-bytes rep-source] (secret-audit-bytes :reply reply-wire reply-codec)]
       (try
         (on-call {:kit :secret
                   :op :get
                   :name name
                   :request-edn (codec-value req-codec)
                   :reply-edn (codec-value reply-codec)
                   :value-format value-codec/audit-format
                   :request-value-bytes req-bytes
                   :reply-value-bytes rep-bytes
                   :request-value-source req-source
                   :reply-value-source rep-source
                   :reply-tag (:tag reply)})
         (catch Exception _))
       reply))))

(defn- headers-edn-from-map
  "Pick two headers for W4 headers_list (package is 2-header fixed arity).
  Prefer Accept/Host; fall back to first entries or synthetic placeholders."
  [headers]
  (let [m (into {}
                (map (fn [[k v]]
                       [(if (keyword? k) (name k) (str k)) (str v)]))
                (or headers {}))
        accept (or (get m "Accept") (get m "accept") "*/*")
        host (or (get m "Host") (get m "host") "audit.local")
        other (first (remove (fn [[k _]] (#{"Accept" "accept" "Host" "host"} k)) m))
        n1 "Accept"
        v1 accept
        n2 (if other (key other) "Host")
        v2 (if other (val other) host)
        r (http-headers-list-edn n1 v1 n2 v2)]
    (codec-value r)))

(defn wrap-http-post-transport
  "Wrap an HTTP post transport fn so each call audits pure W4 request EDN.

  Transport remains `(fn [{:keys [url headers body timeout-ms]}] -> result)`.
  `on-call` receives:
  `{:kit :http :op :post :url u :request-edn s :status n :error? bool :latency-ms n}`

  Does not perform network itself — only wraps the host-supplied transport."
  ([transport] (wrap-http-post-transport transport (fn [_])))
  ([transport on-call]
   (when-not (fn? transport)
     (throw (ex-info "wrap-http-post-transport requires a transport fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "wrap-http-post-transport requires an on-call fn"
                     {:phase :edn-codec})))
   (fn [{:keys [url headers body timeout-ms] :as req}]
     (let [started (System/currentTimeMillis)
           hdrs (or (headers-edn-from-map headers) "[]")
           req-codec (http-request-edn (str url)
                                       (str (or body ""))
                                       (long (or timeout-ms 5000))
                                       hdrs)
           result (transport req)
           status (or (:status result) -1)
           err? (boolean (or (:error result) (:error? result)))
           reply-codec (if err?
                         (http-result-err-edn
                          (keyword-code (or (:code result) :transport-error))
                          (str (or (:message result) (:error result) "error"))
                          (if (:retryable result) 1 0))
                         (http-result-ok-edn
                          (long status)
                          (str (or (:body result) ""))
                          hdrs))]
       (try
         (on-call {:kit :http
                   :op :post
                   :url url
                   :request-edn (codec-value req-codec)
                   :reply-edn (codec-value reply-codec)
                   :value-format value-codec/audit-format
                   :request-value-bytes (codec-audit-bytes :http :request req-codec)
                   :reply-value-bytes (codec-audit-bytes :http :reply reply-codec)
                   :status status
                   :error? err?
                   :latency-ms (- (System/currentTimeMillis) started)})
         (catch Exception _))
       result))))


;; --- ADR 0258: remaining ops-kit pure codecs + audit wraps ---

(defn process-reply-ok-edn
  [exit stdout stderr]
  (invoke-export :process-record-kv-edn :process_reply_ok_rec_kv_edn
                 [(long exit) (str stdout) (str stderr)]))

(defn process-reply-error-edn
  [code message]
  (invoke-export :process-record-kv-edn :process_reply_error_rec_kv_edn
                 [(str code) (str message)]))

(defn git-request-edn
  "args-edn is a prebuilt EDN vector string e.g. [\"status\"]."
  [args-edn max-stdout timeout-ms]
  (invoke-export :git-record-kv-edn :git_req_rec_kv_edn
                 [args-edn (long max-stdout) (long timeout-ms)]))

(defn git-reply-ok-edn
  [exit stdout stderr]
  (invoke-export :git-record-kv-edn :git_reply_ok_rec_kv_edn
                 [(long exit) (str stdout) (str stderr)]))

(defn git-reply-error-edn
  [code message]
  (invoke-export :git-record-kv-edn :git_reply_error_rec_kv_edn
                 [(str code) (str message)]))

(defn entropy-reply-hex-edn
  [hex]
  (invoke-export :entropy-record-kv-edn :entropy_reply_hex_rec_kv_edn [(str hex)]))

(defn entropy-reply-error-edn
  [code message]
  (invoke-export :entropy-record-kv-edn :entropy_reply_error_rec_kv_edn
                 [(str code) (str message)]))

(defn fs-req-read-edn
  [root path]
  (invoke-export :scoped-fs-record-kv-edn :fs_req_read_rec_kv_edn
                 [(str root) (str path)]))

(defn fs-req-write-edn
  [root path value]
  (invoke-export :scoped-fs-record-kv-edn :fs_req_write_rec_kv_edn
                 [(str root) (str path) (str value)]))

(defn fs-reply-content-edn
  [content]
  (invoke-export :scoped-fs-record-kv-edn :fs_reply_content_rec_kv_edn [(str content)]))

(defn fs-reply-written-edn
  "written: 1 true, 0 false."
  [written]
  (invoke-export :scoped-fs-record-kv-edn :fs_reply_written_rec_kv_edn [(long written)]))

(defn fs-reply-error-edn
  [code message]
  (invoke-export :scoped-fs-record-kv-edn :fs_reply_error_rec_kv_edn
                 [(str code) (str message)]))

(defn wrap-process-spawn
  "Wrap process spawn `(fn [{:keys [argv max-stdout-bytes timeout-ms]}] -> reply)`.
  Reply shapes: `{:tag :ok :exit n :stdout s :stderr s}` or
  `{:tag :error :code c :message m}`. Audits W4 request+reply EDN."
  ([spawn] (wrap-process-spawn spawn (fn [_])))
  ([spawn on-call]
   (when-not (fn? spawn)
     (throw (ex-info "wrap-process-spawn requires a spawn fn" {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "wrap-process-spawn requires an on-call fn" {:phase :edn-codec})))
   (fn [{:keys [argv max-stdout-bytes timeout-ms] :as req}]
     (let [argv-edn (cond
                      (string? argv) argv
                      (sequential? argv)
                      (str "[" (str/join " " (map #(str "\"" % "\"") argv)) "]")
                      :else "[]")
           req-codec (process-request-edn argv-edn
                                          (long (or max-stdout-bytes 4096))
                                          (long (or timeout-ms 5000)))
           reply (spawn req)
           reply-codec (case (:tag reply)
                         :ok (process-reply-ok-edn (long (or (:exit reply) 0))
                                                   (str (or (:stdout reply) ""))
                                                   (str (or (:stderr reply) "")))
                         :error (process-reply-error-edn
                                 (keyword-code (:code reply))
                                 (str (or (:message reply) "")))
                         {:ok false})]
       (try
         (on-call {:kit :process
                   :op :spawn
                   :request-edn (codec-value req-codec)
                   :reply-edn (codec-value reply-codec)
                   :value-format value-codec/audit-format
                   :request-value-bytes (codec-audit-bytes :process :request req-codec)
                   :reply-value-bytes (codec-audit-bytes :process :reply reply-codec)
                   :reply-tag (:tag reply)})
         (catch Exception _))
       reply))))

(defn wrap-git-run
  "Wrap git run `(fn [{:keys [args max-stdout-bytes timeout-ms]}] -> reply)`.
  Same reply shape as process. Audits W4 request+reply EDN."
  ([run] (wrap-git-run run (fn [_])))
  ([run on-call]
   (when-not (fn? run)
     (throw (ex-info "wrap-git-run requires a run fn" {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "wrap-git-run requires an on-call fn" {:phase :edn-codec})))
   (fn [{:keys [args max-stdout-bytes timeout-ms] :as req}]
     (let [args-edn (cond
                      (string? args) args
                      (sequential? args)
                      (str "[" (str/join " " (map #(str "\"" % "\"") args)) "]")
                      :else "[]")
           req-codec (git-request-edn args-edn
                                      (long (or max-stdout-bytes 4096))
                                      (long (or timeout-ms 5000)))
           reply (run req)
           reply-codec (case (:tag reply)
                         :ok (git-reply-ok-edn (long (or (:exit reply) 0))
                                               (str (or (:stdout reply) ""))
                                               (str (or (:stderr reply) "")))
                         :error (git-reply-error-edn
                                 (keyword-code (:code reply))
                                 (str (or (:message reply) "")))
                         {:ok false})]
       (try
         (on-call {:kit :git
                   :op :run
                   :request-edn (codec-value req-codec)
                   :reply-edn (codec-value reply-codec)
                   :value-format value-codec/audit-format
                   :request-value-bytes (codec-audit-bytes :git :request req-codec)
                   :reply-value-bytes (codec-audit-bytes :git :reply reply-codec)
                   :reply-tag (:tag reply)})
         (catch Exception _))
       reply))))

(defn- bytes->hex
  "Lowercase hex for byte seq (0–255 ints or signed bytes)."
  [bs]
  (when bs
    (apply str
           (map (fn [b]
                  (format "%02x" (bit-and (long b) 0xff)))
                bs))))

(defn wrap-entropy-draw
  "Wrap entropy draw `(fn [{:keys [n]}] -> reply)`.

  Transport reply shapes (provider.entropy):
  - `{:tag :bytes :bytes [0..255 ...]}` (os-draw / mem-draw)
  - `{:tag :hex :hex s}` (legacy/test)
  - `{:tag :error :code c :message m}`

  Pure W4 EDN reply uses hex arm; bytes are converted for audit only."
  ([draw] (wrap-entropy-draw draw (fn [_])))
  ([draw on-call]
   (when-not (fn? draw)
     (throw (ex-info "wrap-entropy-draw requires a draw fn" {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "wrap-entropy-draw requires an on-call fn" {:phase :edn-codec})))
   (fn [{:keys [n] :as req}]
     (let [req-codec (entropy-request-edn (long n))
           reply (draw req)
           reply-codec (case (:tag reply)
                         :hex (entropy-reply-hex-edn (str (:hex reply)))
                         :bytes (let [hx (bytes->hex (:bytes reply))]
                                  (if hx
                                    (entropy-reply-hex-edn hx)
                                    {:ok false}))
                         :error (entropy-reply-error-edn
                                 (keyword-code (:code reply))
                                 (str (or (:message reply) "")))
                         {:ok false})]
       (try
         (on-call {:kit :entropy
                   :op :draw
                   :n n
                   :request-edn (codec-value req-codec)
                   :reply-edn (codec-value reply-codec)
                   :value-format value-codec/audit-format
                   :request-value-bytes (codec-audit-bytes :entropy :request req-codec)
                   :reply-value-bytes (codec-audit-bytes :entropy :reply reply-codec)
                   :reply-tag (:tag reply)})
         (catch Exception _))
       reply))))

(defn wrap-scoped-fs-transact
  "Wrap scoped-fs op `(fn [{:keys [op root path value]}] -> reply)`.
  `op` is `:read` or `:write`. Reply tags: `:content`, `:written`, `:error`."
  ([tx] (wrap-scoped-fs-transact tx (fn [_])))
  ([tx on-call]
   (when-not (fn? tx)
     (throw (ex-info "wrap-scoped-fs-transact requires a tx fn" {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "wrap-scoped-fs-transact requires an on-call fn" {:phase :edn-codec})))
   (fn [{:keys [op root path value] :as req}]
     (let [req-codec (case op
                       :read (fs-req-read-edn root path)
                       :write (fs-req-write-edn root path (str (or value "")))
                       {:ok false})
           reply (tx req)
           reply-codec (case (:tag reply)
                         ;; store reply uses :value for content body (scoped-fs mem/os-store)
                         :content (fs-reply-content-edn
                                   (str (or (:value reply) (:content reply) "")))
                         :written (fs-reply-written-edn
                                   (if (or (true? (:written reply))
                                           (= 1 (:written reply))
                                           (nil? (:written reply)))
                                     1 0))
                         :error (fs-reply-error-edn
                                 (keyword-code (:code reply))
                                 (str (or (:message reply) "")))
                         {:ok false})]
       (try
         (on-call {:kit :scoped-fs
                   :op op
                   :root root
                   :path path
                   :request-edn (codec-value req-codec)
                   :reply-edn (codec-value reply-codec)
                   :value-format value-codec/audit-format
                   :request-value-bytes (codec-audit-bytes :scoped-fs :request req-codec)
                   :reply-value-bytes (codec-audit-bytes :scoped-fs :reply reply-codec)
                   :reply-tag (:tag reply)})
         (catch Exception _))
       reply))))

;; --- ADR 0259: production / test-double factories with EDN audit ---

(defn production-http-transport
  "Build `http-transport/production-transport` wrapped with pure W4 EDN audit.

  Options match `production-transport`. The base transport's internal
  `:on-call` is silenced; supply a single `:on-call` here to receive
  EDN-enriched events (`:request-edn` / `:reply-edn` / `:status` / `:latency-ms`).

  Does not flip wasm-aot — still host network authority."
  ([] (production-http-transport {}))
  ([opts]
   (let [user-on-call (:on-call opts (fn [_]))
         base (http-t/production-transport (assoc opts :on-call (fn [_])))]
     (wrap-http-post-transport base user-on-call))))

(defn secret-map-fetch-with-edn-audit
  "Secret map-fetch wrapped with pure W4 request/reply EDN audit."
  ([m] (secret-map-fetch-with-edn-audit m (fn [_])))
  ([m on-call]
   (wrap-secret-fetch (secret-t/map-fetch m) on-call)))

(defn secret-env-fetch-with-edn-audit
  "Secret env-fetch wrapped with pure W4 EDN audit."
  ([name->env] (secret-env-fetch-with-edn-audit name->env (fn [_])))
  ([name->env on-call]
   (wrap-secret-fetch (secret-t/env-fetch name->env) on-call)))

(defn process-echo-with-edn-audit
  "Process echo test double + pure W4 EDN audit (no OS spawn)."
  ([] (process-echo-with-edn-audit (fn [_])))
  ([on-call]
   (wrap-process-spawn (process/echo-transport) on-call)))

(defn git-echo-with-edn-audit
  "Git echo test double + pure W4 EDN audit (no OS git)."
  ([] (git-echo-with-edn-audit (fn [_])))
  ([on-call]
   (wrap-git-run (git/echo-transport) on-call)))

(defn process-os-spawn-with-edn-audit
  "JVM/cljs `process-transport/os-spawn` + pure W4 EDN audit.
  Pass the same opts as os-spawn (absolute bin, etc.). Optional :on-call."
  [opts]
  (let [on-call (:on-call opts (fn [_]))
        base (process-t/os-spawn (dissoc opts :on-call))]
    (wrap-process-spawn base on-call)))

(defn git-os-run-with-edn-audit
  "git-transport/os-run + pure W4 EDN audit. Optional :on-call in opts."
  [opts]
  (let [on-call (:on-call opts (fn [_]))
        base (git-t/os-run (dissoc opts :on-call))]
    (wrap-git-run base on-call)))

(defn scoped-fs-os-store-with-edn-audit
  "scoped-fs-transport/os-store + pure W4 EDN audit. Optional :on-call."
  [opts]
  (let [on-call (:on-call opts (fn [_]))
        base (fs-t/os-store (dissoc opts :on-call))]
    (wrap-scoped-fs-transact base on-call)))

;; --- ADR 0261: entropy production/test factories + :bytes reply honesty ---

(defn entropy-mem-draw-with-edn-audit
  "Deterministic mem-draw + pure W4 EDN audit (no CSPRNG)."
  ([seed-bytes] (entropy-mem-draw-with-edn-audit seed-bytes (fn [_])))
  ([seed-bytes on-call]
   (wrap-entropy-draw (entropy-t/mem-draw seed-bytes) on-call)))

(defn entropy-os-draw-with-edn-audit
  "CSPRNG `entropy-transport/os-draw` + pure W4 EDN audit.
  Optional :on-call and :random (JVM SecureRandom inject for tests)."
  ([] (entropy-os-draw-with-edn-audit {}))
  ([opts]
   (let [on-call (:on-call opts (fn [_]))
         base (entropy-t/os-draw (dissoc opts :on-call))]
     (wrap-entropy-draw base on-call))))

;; --- ADR 0262: live guest host_post_edn with typedCapCall inject ---

(defn http-w4-host-post-edn
  "Invoke guest `host_post_edn` (ADR 0260) with browser-host capability inject.

  Builds W4 request EDN in-guest then calls typed-cap-call :http/post (id 4).
  `inject-mode`:
    :echo   — host returns the request EDN string (cap path proof)
    :ok-200 — host returns fixed `{:tag :ok :status 200 :body \"injected\"}`

  Does not perform network itself — inject is the host authority boundary.
  Does not flip wasm-aot :implemented."
  ([url body timeout-ms headers-edn]
   (http-w4-host-post-edn url body timeout-ms headers-edn :echo))
  ([url body timeout-ms headers-edn inject-mode]
   (invoke-export* :http-w4-host-edn :host_post_edn
                   [(str url) (str body) (long timeout-ms) (str headers-edn)]
                   {:allow-capabilities [4]
                    :inject-mode inject-mode})))

(defn http-w4-host-post-denied
  "Prove deny-by-default: host_post without allowCapabilities fails closed."
  [url body timeout-ms headers-edn]
  (invoke-export* :http-w4-host-edn :host_post_edn
                  [(str url) (str body) (long timeout-ms) (str headers-edn)]
                  {}))

;; --- ADR 0263: production W4 round-trip (guest encode + host transport + guest reply) ---

(defn- headers-map->pairs
  "Normalize headers map to two name/value pairs for W4 headers_list arity."
  [headers]
  (let [m (into {}
                (map (fn [[k v]]
                       [(if (keyword? k) (name k) (str k)) (str v)]))
                (or headers {}))
        accept (or (get m "Accept") (get m "accept") "*/*")
        host (or (get m "Host") (get m "host") "audit.local")
        other (first (remove (fn [[k _]] (#{"Accept" "accept" "Host" "host"} k)) m))
        n1 "Accept"
        v1 accept
        n2 (if other (key other) "Host")
        v2 (if other (val other) host)]
    [n1 v1 n2 v2]))

(defn http-w4-roundtrip
  "Production path: guest pure W4 request EDN + host transport + guest pure reply EDN.

  Unlike `http-w4-host-post-edn` (guest typed-cap-call with inject stubs), this
  keeps **network authority** on the host transport and only uses guest packages
  as pure codecs — matching host-injected authority honesty.

  `transport` is `(fn [{:keys [url headers body timeout-ms]}] -> result)` where
  result is `{:status n :body s :headers m}` or `{:error {:code :message ...}
  :error? true}` / `{:error true ...}`.

  Returns:
  `{:ok true :request-edn s :reply-edn s :result transport-result}`
  or `{:ok false :reason ...}` if codecs fail hard.

  Does not flip wasm-aot :implemented."
  ([url headers body timeout-ms transport]
   (http-w4-roundtrip url headers body timeout-ms transport (fn [_])))
  ([url headers body timeout-ms transport on-call]
   (when-not (fn? transport)
     (throw (ex-info "http-w4-roundtrip requires a transport fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "http-w4-roundtrip requires an on-call fn"
                     {:phase :edn-codec})))
   (let [[n1 v1 n2 v2] (headers-map->pairs headers)
         hdrs-r (http-headers-list-edn n1 v1 n2 v2)
         hdrs (or (codec-value hdrs-r) "[]")
         req-r (http-request-edn (str url) (str (or body ""))
                                 (long (or timeout-ms 5000)) hdrs)
         req-edn (codec-value req-r)
         started (System/currentTimeMillis)
         result (transport {:url (str url)
                            :headers (into {}
                                           (map (fn [[k v]]
                                                  [(if (keyword? k) k (keyword k))
                                                   (str v)]))
                                           (or headers {}))
                            :body (str (or body ""))
                            :timeout-ms (long (or timeout-ms 5000))})
         status (long (or (:status result) -1))
         err? (boolean (or (:error result) (:error? result)))
         reply-r (if err?
                   (let [err (if (map? (:error result)) (:error result) {})]
                     (http-result-err-edn
                      (keyword-code (or (:code err) (:code result) :transport-error))
                      (str (or (:message err) (:message result) "error"))
                      (if (or (:retryable err) (:retryable result)) 1 0)))
                   (http-result-ok-edn status
                                       (str (or (:body result) ""))
                                       hdrs))
         reply-edn (codec-value reply-r)
         wire (codec-audit-fields :http req-r reply-r)
         event (merge {:kit :http
                       :op :w4-roundtrip
                       :url url
                       :request-edn req-edn
                       :reply-edn reply-edn
                       :status status
                       :error? err?
                       :latency-ms (- (System/currentTimeMillis) started)}
                      wire)]
     (try (on-call event) (catch Exception _))
     (if (complete-audit-wire? req-edn reply-edn wire)
       (merge {:ok true
               :request-edn req-edn
               :reply-edn reply-edn
               :result result}
              wire)
       {:ok false
        :reason :codec-failed
        :request-codec req-r
        :reply-codec reply-r
        :result result}))))

(defn http-w4-roundtrip-with-production
  "http-w4-roundtrip using production HTTP transport (allowed-origins required).

  opts: same as production-transport plus optional :on-call for EDN events."
  [url headers body timeout-ms opts]
  (let [on-call (:on-call opts (fn [_]))
        transport (http-t/production-transport
                   (assoc (dissoc opts :on-call) :on-call (fn [_])))]
    (http-w4-roundtrip url headers body timeout-ms transport on-call)))

;; --- ADR 0264: remaining ops-kit W4 round-trips (guest encode + host + guest reply) ---

(defn secret-w4-roundtrip
  "Guest pure secret request EDN + host fetch + guest pure reply EDN.

  `fetch` is `(fn [{:keys [name]}] -> {:tag :value/:error ...})`.
  Returns `{:ok true :request-edn s :reply-edn s :result reply}` or codec failure.
  Does not flip wasm-aot — secret authority remains host-injected."
  ([name fetch]
   (secret-w4-roundtrip name fetch (fn [_])))
  ([name fetch on-call]
   (when-not (fn? fetch)
     (throw (ex-info "secret-w4-roundtrip requires a fetch fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "secret-w4-roundtrip requires an on-call fn"
                     {:phase :edn-codec})))
   (let [req-r (secret-request-edn (str name))
         req-edn (codec-value req-r)
         started (System/currentTimeMillis)
         reply (fetch {:name (str name)})
         reply-r (case (:tag reply)
                   :value (secret-reply-value-edn (str (:value reply)))
                   :error (secret-reply-error-edn
                           (keyword-code (:code reply))
                           (str (or (:message reply) "")))
                   {:ok false})
         reply-edn (codec-value reply-r)
         wire (codec-audit-fields :secret req-r reply-r)
         event (merge {:kit :secret
                       :op :w4-roundtrip
                       :name name
                       :request-edn req-edn
                       :reply-edn reply-edn
                       :reply-tag (:tag reply)
                       :latency-ms (- (System/currentTimeMillis) started)}
                      wire)]
     (try (on-call event) (catch Exception _))
     (if (complete-audit-wire? req-edn reply-edn wire)
       (merge {:ok true :request-edn req-edn :reply-edn reply-edn :result reply}
              wire)
       {:ok false
        :reason :codec-failed
        :request-codec req-r
        :reply-codec reply-r
        :result reply}))))

(defn secret-w4-roundtrip-with-map
  "secret-w4-roundtrip over map-fetch (test/prod named map). Optional on-call."
  ([m name] (secret-w4-roundtrip-with-map m name (fn [_])))
  ([m name on-call]
   (secret-w4-roundtrip name (secret-t/map-fetch m) on-call)))

(defn process-w4-roundtrip
  "Guest pure process request EDN + host spawn + guest pure reply EDN.

  `spawn` is `(fn [{:keys [argv max-stdout-bytes timeout-ms]}] ->
  {:tag :ok/:error ...})`. Does not flip wasm-aot."
  ([argv max-stdout-bytes timeout-ms spawn]
   (process-w4-roundtrip argv max-stdout-bytes timeout-ms spawn (fn [_])))
  ([argv max-stdout-bytes timeout-ms spawn on-call]
   (when-not (fn? spawn)
     (throw (ex-info "process-w4-roundtrip requires a spawn fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "process-w4-roundtrip requires an on-call fn"
                     {:phase :edn-codec})))
   (let [argv-edn (cond
                    (string? argv) argv
                    (sequential? argv)
                    (str "[" (str/join " " (map #(str "\"" % "\"") argv)) "]")
                    :else "[]")
         req-r (process-request-edn argv-edn
                                    (long (or max-stdout-bytes 4096))
                                    (long (or timeout-ms 5000)))
         req-edn (codec-value req-r)
         started (System/currentTimeMillis)
         reply (spawn {:argv argv
                       :max-stdout-bytes (long (or max-stdout-bytes 4096))
                       :timeout-ms (long (or timeout-ms 5000))})
         reply-r (case (:tag reply)
                   :ok (process-reply-ok-edn (long (or (:exit reply) 0))
                                             (str (or (:stdout reply) ""))
                                             (str (or (:stderr reply) "")))
                   :error (process-reply-error-edn
                           (keyword-code (:code reply))
                           (str (or (:message reply) "")))
                   {:ok false})
         reply-edn (codec-value reply-r)
         wire (codec-audit-fields :process req-r reply-r)
         event (merge {:kit :process
                       :op :w4-roundtrip
                       :request-edn req-edn
                       :reply-edn reply-edn
                       :reply-tag (:tag reply)
                       :latency-ms (- (System/currentTimeMillis) started)}
                      wire)]
     (try (on-call event) (catch Exception _))
     (if (complete-audit-wire? req-edn reply-edn wire)
       (merge {:ok true :request-edn req-edn :reply-edn reply-edn :result reply}
              wire)
       {:ok false
        :reason :codec-failed
        :request-codec req-r
        :reply-codec reply-r
        :result reply}))))

(defn process-w4-roundtrip-echo
  "process-w4-roundtrip with process/echo-transport (no OS spawn)."
  ([argv] (process-w4-roundtrip-echo argv (fn [_])))
  ([argv on-call]
   (process-w4-roundtrip argv 4096 5000 (process/echo-transport) on-call)))

(defn git-w4-roundtrip
  "Guest pure git request EDN + host run + guest pure reply EDN.
  `run` is `(fn [{:keys [args max-stdout-bytes timeout-ms]}] -> reply)`.
  Does not flip wasm-aot."
  ([args max-stdout-bytes timeout-ms run]
   (git-w4-roundtrip args max-stdout-bytes timeout-ms run (fn [_])))
  ([args max-stdout-bytes timeout-ms run on-call]
   (when-not (fn? run)
     (throw (ex-info "git-w4-roundtrip requires a run fn" {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "git-w4-roundtrip requires an on-call fn" {:phase :edn-codec})))
   (let [args-edn (cond
                    (string? args) args
                    (sequential? args)
                    (str "[" (str/join " " (map #(str "\"" % "\"") args)) "]")
                    :else "[]")
         req-r (git-request-edn args-edn
                                (long (or max-stdout-bytes 4096))
                                (long (or timeout-ms 5000)))
         req-edn (codec-value req-r)
         started (System/currentTimeMillis)
         reply (run {:args args
                     :max-stdout-bytes (long (or max-stdout-bytes 4096))
                     :timeout-ms (long (or timeout-ms 5000))})
         reply-r (case (:tag reply)
                   :ok (git-reply-ok-edn (long (or (:exit reply) 0))
                                         (str (or (:stdout reply) ""))
                                         (str (or (:stderr reply) "")))
                   :error (git-reply-error-edn
                           (keyword-code (:code reply))
                           (str (or (:message reply) "")))
                   {:ok false})
         reply-edn (codec-value reply-r)
         wire (codec-audit-fields :git req-r reply-r)
         event (merge {:kit :git
                       :op :w4-roundtrip
                       :request-edn req-edn
                       :reply-edn reply-edn
                       :reply-tag (:tag reply)
                       :latency-ms (- (System/currentTimeMillis) started)}
                      wire)]
     (try (on-call event) (catch Exception _))
     (if (complete-audit-wire? req-edn reply-edn wire)
       (merge {:ok true :request-edn req-edn :reply-edn reply-edn :result reply}
              wire)
       {:ok false
        :reason :codec-failed
        :request-codec req-r
        :reply-codec reply-r
        :result reply}))))

(defn git-w4-roundtrip-echo
  "git-w4-roundtrip with git/echo-transport (no OS git)."
  ([args] (git-w4-roundtrip-echo args (fn [_])))
  ([args on-call]
   (git-w4-roundtrip args 8192 30000 (git/echo-transport) on-call)))

(defn entropy-w4-roundtrip
  "Guest pure entropy request EDN + host draw + guest pure hex/error reply EDN.

  `draw` is `(fn [{:keys [n]}] -> {:tag :bytes/:hex/:error ...})`.
  Bytes replies are hex-encoded for pure W4 reply arm (audit-only conversion).
  Does not flip wasm-aot — CSPRNG remains host-injected."
  ([n draw]
   (entropy-w4-roundtrip n draw (fn [_])))
  ([n draw on-call]
   (when-not (fn? draw)
     (throw (ex-info "entropy-w4-roundtrip requires a draw fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "entropy-w4-roundtrip requires an on-call fn"
                     {:phase :edn-codec})))
   (let [req-r (entropy-request-edn (long n))
         req-edn (codec-value req-r)
         started (System/currentTimeMillis)
         reply (draw {:n (long n)})
         reply-r (case (:tag reply)
                   :hex (entropy-reply-hex-edn (str (:hex reply)))
                   :bytes (let [hx (bytes->hex (:bytes reply))]
                            (if hx
                              (entropy-reply-hex-edn hx)
                              {:ok false}))
                   :error (entropy-reply-error-edn
                           (keyword-code (:code reply))
                           (str (or (:message reply) "")))
                   {:ok false})
         reply-edn (codec-value reply-r)
         wire (codec-audit-fields :entropy req-r reply-r)
         event (merge {:kit :entropy
                       :op :w4-roundtrip
                       :n n
                       :request-edn req-edn
                       :reply-edn reply-edn
                       :reply-tag (:tag reply)
                       :latency-ms (- (System/currentTimeMillis) started)}
                      wire)]
     (try (on-call event) (catch Exception _))
     (if (complete-audit-wire? req-edn reply-edn wire)
       (merge {:ok true :request-edn req-edn :reply-edn reply-edn :result reply}
              wire)
       {:ok false
        :reason :codec-failed
        :request-codec req-r
        :reply-codec reply-r
        :result reply}))))

(defn entropy-w4-roundtrip-mem
  "entropy-w4-roundtrip with mem-draw seed (no CSPRNG)."
  ([seed-bytes n] (entropy-w4-roundtrip-mem seed-bytes n (fn [_])))
  ([seed-bytes n on-call]
   (entropy-w4-roundtrip n (entropy-t/mem-draw seed-bytes) on-call)))

(defn scoped-fs-w4-roundtrip
  "Guest pure fs request EDN + host tx + guest pure reply EDN.

  `tx` is `(fn [{:keys [op root path value]}] ->
  {:tag :content/:written/:error ...})`. Does not flip wasm-aot."
  ([op root path value tx]
   (scoped-fs-w4-roundtrip op root path value tx (fn [_])))
  ([op root path value tx on-call]
   (when-not (fn? tx)
     (throw (ex-info "scoped-fs-w4-roundtrip requires a tx fn"
                     {:phase :edn-codec})))
   (when-not (fn? on-call)
     (throw (ex-info "scoped-fs-w4-roundtrip requires an on-call fn"
                     {:phase :edn-codec})))
   (let [req-r (case op
                 :read (fs-req-read-edn root path)
                 :write (fs-req-write-edn root path (str (or value "")))
                 {:ok false})
         req-edn (codec-value req-r)
         started (System/currentTimeMillis)
         reply (tx {:op op :root root :path path :value value})
         reply-r (case (:tag reply)
                   :content (fs-reply-content-edn
                             (str (or (:value reply) (:content reply) "")))
                   :written (fs-reply-written-edn
                             (if (or (true? (:written reply))
                                     (= 1 (:written reply))
                                     (nil? (:written reply)))
                               1 0))
                   :error (fs-reply-error-edn
                           (keyword-code (:code reply))
                           (str (or (:message reply) "")))
                   {:ok false})
         reply-edn (codec-value reply-r)
         wire (codec-audit-fields :scoped-fs req-r reply-r)
         event (merge {:kit :scoped-fs
                       :op :w4-roundtrip
                       :fs-op op
                       :root root
                       :path path
                       :request-edn req-edn
                       :reply-edn reply-edn
                       :reply-tag (:tag reply)
                       :latency-ms (- (System/currentTimeMillis) started)}
                      wire)]
     (try (on-call event) (catch Exception _))
     (if (complete-audit-wire? req-edn reply-edn wire)
       (merge {:ok true :request-edn req-edn :reply-edn reply-edn :result reply}
              wire)
       {:ok false
        :reason :codec-failed
        :request-codec req-r
        :reply-codec reply-r
        :result reply}))))

;; --- ADR 0265: guest secret W4 host_get_edn + live inject (wire id 21) ---

(defn secret-w4-host-get-edn
  "Invoke guest `host_get_edn` (ADR 0265) with browser-host capability inject.

  Guest builds W4 request EDN then typed-cap-call wire id 21 (secret/get).
  inject-mode:
    :echo         — host returns request EDN (cap path proof)
    :secret-value — host returns fixed `{:tag :value :value \"s3cr3t\"}`

  Does not read real secrets — inject is the host authority boundary.
  Does not flip wasm-aot :implemented."
  ([name] (secret-w4-host-get-edn name :secret-value))
  ([name inject-mode]
   (invoke-export* :secret-w4-host-edn :host_get_edn
                   [(str name)]
                   {:allow-capabilities [21]
                    :primary-cap-id 21
                    :inject-mode inject-mode})))

(defn secret-w4-host-get-denied
  "Prove deny-by-default: host_get without allowCapabilities fails closed."
  [name]
  (invoke-export* :secret-w4-host-edn :host_get_edn [(str name)] {}))

;; --- ADR 0266: guest process W4 host_spawn_edn + live inject (wire id 20) ---

(defn process-w4-host-spawn-edn
  "Invoke guest `host_spawn_edn` (ADR 0266) with browser-host capability inject.

  Builds W4 process request EDN then typed-cap-call wire id 20 (process/spawn).
  inject-mode:
    :echo       — host returns the request EDN string (cap path proof)
    :process-ok — host returns fixed `{:tag :ok :exit 0 :stdout \"ok\" :stderr \"\"}`

  argv-edn is a prebuilt EDN vector string e.g. `[\"echo\" \"hi\"]`.
  Does not perform OS spawn — inject is the host authority boundary."
  ([argv-edn max-stdout timeout-ms]
   (process-w4-host-spawn-edn argv-edn max-stdout timeout-ms :echo))
  ([argv-edn max-stdout timeout-ms inject-mode]
   (invoke-export* :process-w4-host-edn :host_spawn_edn
                   [(str argv-edn) (long max-stdout) (long timeout-ms)]
                   {:allow-capabilities [20]
                    :primary-cap-id 20
                    :inject-mode inject-mode})))

(defn process-w4-host-spawn-denied
  "Prove deny-by-default: host_spawn without allowCapabilities fails closed."
  [argv-edn max-stdout timeout-ms]
  (invoke-export* :process-w4-host-edn :host_spawn_edn
                  [(str argv-edn) (long max-stdout) (long timeout-ms)]
                  {}))

;; --- ADR 0267: git/entropy/fs guest host surfaces + inject helpers ---

(defn git-w4-host-run-edn
  "Guest host_run_edn (wire 22) with inject (ADR 0267 + 0270).

  inject-mode:
    :echo   — return request EDN (cap path proof)
    :git-ok — fixed `{:tag :ok :exit 0 :stdout \"ok\" :stderr \"\"}`
  Does not run OS git — inject is the host authority boundary."
  ([args-edn max-stdout timeout-ms]
   (git-w4-host-run-edn args-edn max-stdout timeout-ms :echo))
  ([args-edn max-stdout timeout-ms inject-mode]
   (invoke-export* :git-w4-host-edn :host_run_edn
                   [(str args-edn) (long max-stdout) (long timeout-ms)]
                   {:allow-capabilities [22]
                    :primary-cap-id 22
                    :inject-mode inject-mode})))

(defn git-w4-host-run-denied
  "Prove deny-by-default for host_run without allowCapabilities (ADR 0270)."
  [args-edn max-stdout timeout-ms]
  (invoke-export* :git-w4-host-edn :host_run_edn
                  [(str args-edn) (long max-stdout) (long timeout-ms)]
                  {}))

(defn entropy-w4-host-draw-edn
  "Guest host_draw_edn (wire 23) with inject (ADR 0267 + 0270).

  inject-mode:
    :echo        — return request EDN (cap path proof)
    :entropy-hex — fixed `{:tag :hex :hex \"0123456789abcdef\"}`
  Does not draw CSPRNG — inject is the host authority boundary."
  ([n] (entropy-w4-host-draw-edn n :echo))
  ([n inject-mode]
   (invoke-export* :entropy-w4-host-edn :host_draw_edn
                   [(long n)]
                   {:allow-capabilities [23]
                    :primary-cap-id 23
                    :inject-mode inject-mode})))

(defn entropy-w4-host-draw-denied
  "Prove deny-by-default for host_draw without allowCapabilities (ADR 0270)."
  [n]
  (invoke-export* :entropy-w4-host-edn :host_draw_edn [(long n)] {}))

(defn scoped-fs-w4-host-read-edn
  "Guest host_read_edn (wire 19) with inject (ADR 0267 + 0270).

  inject-mode:
    :echo       — return request EDN (cap path proof)
    :fs-content — fixed `{:tag :content :content \"payload\"}`
  Does not touch host store."
  ([root path] (scoped-fs-w4-host-read-edn root path :echo))
  ([root path inject-mode]
   (invoke-export* :scoped-fs-w4-host-edn :host_read_edn
                   [(str root) (str path)]
                   {:allow-capabilities [19]
                    :primary-cap-id 19
                    :inject-mode inject-mode})))

(defn scoped-fs-w4-host-read-denied
  "Prove deny-by-default for host_read without allowCapabilities (ADR 0270)."
  [root path]
  (invoke-export* :scoped-fs-w4-host-edn :host_read_edn
                  [(str root) (str path)]
                  {}))

(defn scoped-fs-w4-host-write-edn
  "Guest host_write_edn (ADR 0268, wire 19) with inject.

  inject-mode:
    :echo       — return request EDN (cap path proof)
    :fs-written — fixed `{:tag :written :written true}`
  Does not touch host store."
  ([root path value] (scoped-fs-w4-host-write-edn root path value :echo))
  ([root path value inject-mode]
   (invoke-export* :scoped-fs-w4-host-edn :host_write_edn
                   [(str root) (str path) (str value)]
                   {:allow-capabilities [19]
                    :primary-cap-id 19
                    :inject-mode inject-mode})))

(defn scoped-fs-w4-host-write-denied
  "Prove deny-by-default for host_write without allowCapabilities."
  [root path value]
  (invoke-export* :scoped-fs-w4-host-edn :host_write_edn
                  [(str root) (str path) (str value)]
                  {}))
