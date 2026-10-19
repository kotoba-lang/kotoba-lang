(ns playwright-clj.core
  "Idiomatic Clojure wrapper over Playwright for Java — launch real browsers
   (Chromium/Firefox/WebKit), drive pages, evaluate JS, capture console/network,
   screenshot. Built for browser-debugging + E2E of WASM/ClojureScript apps
   (e.g. the kotoba office layer: real Service Worker + IndexedDB + WebCrypto).

   Quick start:
     (require '[playwright-clj.core :as pw])
     (pw/with-page [page {:headless true}]
       (pw/goto page \"https://example.com\")
       (println (pw/eval-js page \"document.title\")))

   Browsers must be installed once: `clojure -M:install chromium`."
  (:import [com.microsoft.playwright Playwright Browser BrowserType BrowserType$LaunchOptions
            BrowserContext Page Page$NavigateOptions ConsoleMessage]
           [com.microsoft.playwright.options WaitForSelectorState]
           [java.util.function Consumer]
           [java.nio.file Paths]))

(declare ->clj)

;; ── lifecycle ───────────────────────────────────────────────────────────────

(defn launch
  "Launch a browser. Returns {:pw Playwright :browser Browser}.
   opts: {:browser :chromium|:firefox|:webkit (default :chromium)
          :channel  String — an installed browser channel (\"chrome\", \"msedge\",
                    \"chrome-beta\" …) instead of Playwright's bundled build
          :headless bool (default true)
          :slow-mo  ms   (debug pacing, optional)
          :args     [String] extra browser args (e.g. virtual-authenticator flags)}

   `:channel` matters for more than version coverage: **the bundled Chromium and
   an installed Chrome do not expose the same GPU**. Measured on macOS
   2026-08-06, over http://127.0.0.1 (WebGPU needs a secure context, so
   about:blank reports no `navigator.gpu` at all and is useless for this):

     bundled chromium, headless -> SwiftShader, isFallbackAdapter=true
     channel \"chrome\", headless -> Apple M4, metal-3, isFallbackAdapter=false

   So a gate that demands a physical adapter cannot be satisfied by the bundled
   build, no matter which GPU flags it passes."
  ([] (launch {}))
  ([{:keys [browser channel headless slow-mo args]
     :or   {browser :chromium headless true}}]
   (let [pw (Playwright/create)
         ^BrowserType bt (case browser
                           :chromium (.chromium pw)
                           :firefox  (.firefox pw)
                           :webkit   (.webkit pw))
         lo (doto (BrowserType$LaunchOptions.)
              (.setHeadless (boolean headless)))
         _  (when (and channel (seq (str channel))) (.setChannel lo (str channel)))
         _  (when slow-mo (.setSlowMo lo (double slow-mo)))
         _  (when (seq args) (.setArgs lo (vec args)))
         b  (.launch bt lo)]
     {:pw pw :browser b})))

(defn close
  "Close a launched {:pw :browser} (and everything under it)."
  [{:keys [^Browser browser ^Playwright pw]}]
  (when browser (.close browser))
  (when pw (.close pw)))

(defn new-context ^BrowserContext [{:keys [^Browser browser]}] (.newContext browser))
(defn new-page ^Page [{:keys [^Browser browser]}] (.newPage browser))

(defmacro with-browser
  "(with-browser [b opts] body…) — launch, run, always close. `b` binds {:pw :browser}."
  [[b opts] & body]
  `(let [~b (launch ~opts)]
     (try ~@body (finally (close ~b)))))

(defmacro with-page
  "(with-page [page opts] body…) — launch a browser + one page, run, always close.
   `page` binds a Playwright Page."
  [[page opts] & body]
  `(let [b# (launch ~opts)
         ~page (new-page b#)]
     (try ~@body (finally (close b#)))))

;; ── navigation + interaction ────────────────────────────────────────────────

(defn goto
  "Navigate the page to `url`. opts {:wait-until \"load\"|\"domcontentloaded\"|\"networkidle\"
   :timeout ms}."
  ([^Page page url] (goto page url {}))
  ([^Page page url {:keys [wait-until timeout]}]
   (let [o (Page$NavigateOptions.)]
     (when wait-until
       (.setWaitUntil o (com.microsoft.playwright.options.WaitUntilState/valueOf
                         (.toUpperCase ^String wait-until))))
     (when timeout (.setTimeout o (double timeout)))
     (.navigate page url o))))

(defn click   [^Page page sel] (.click page sel))
(defn fill    [^Page page sel v] (.fill page sel v))
(defn type-in [^Page page sel v] (.type page sel v))
(defn text    [^Page page sel] (.textContent page sel))
(defn inner-text [^Page page sel] (.innerText page sel))
(defn content [^Page page] (.content page))
(defn title   [^Page page] (.title page))
(defn url     [^Page page] (.url page))

(defn wait-for
  "Wait until `sel` reaches `state` (:visible default | :attached | :hidden | :detached).
   opts {:timeout ms}."
  ([^Page page sel] (wait-for page sel :visible {}))
  ([^Page page sel state] (wait-for page sel state {}))
  ([^Page page sel state {:keys [timeout]}]
   (let [o (com.microsoft.playwright.Page$WaitForSelectorOptions.)]
     (.setState o (case state
                    :visible WaitForSelectorState/VISIBLE
                    :attached WaitForSelectorState/ATTACHED
                    :hidden WaitForSelectorState/HIDDEN
                    :detached WaitForSelectorState/DETACHED))
     (when timeout (.setTimeout o (double timeout)))
     (.waitForSelector page sel o))))

;; ── JavaScript evaluation (the workhorse for WASM/CLJS debugging) ────────────

(defn eval-js
  "Evaluate a JS expression/function in the page and return the (JSON-able) result
   marshalled to Clojure data. `arg` is passed to the JS function as its argument.
     (eval-js page \"() => 1 + 1\")             ;=> 2
     (eval-js page \"x => x.length\" \"abc\")    ;=> 3"
  ([^Page page js] (->clj (.evaluate page js)))
  ([^Page page js arg] (->clj (.evaluate page js arg))))

(defn wait-for-fn
  "Poll a JS predicate function until it returns truthy (e.g. wait for WASM ready).
   opts {:timeout ms :polling ms}. NOTE: the default polling is requestAnimationFrame,
   which is throttled/paused for backgrounded headless pages — pass a numeric :polling
   (e.g. 200) when waiting on conditions that change during page idle (timers, Service
   Worker messages, IndexedDB), or the predicate may never be re-evaluated."
  ([^Page page js] (wait-for-fn page js {}))
  ([^Page page js {:keys [timeout polling]}]
   (let [o (com.microsoft.playwright.Page$WaitForFunctionOptions.)]
     (when timeout (.setTimeout o (double timeout)))
     (when polling (.setPollingInterval o (double polling)))
     (.waitForFunction page js nil o))))

;; ── console / diagnostics ────────────────────────────────────────────────────

(defn capture-console
  "Attach a console listener; appends {:type :text} maps to the returned atom (a vector).
   Use for browser-debug: read it after the run to see console.log/error output."
  [^Page page]
  (let [log (atom [])]
    (.onConsoleMessage page
      (reify Consumer
        (accept [_ msg]
          (let [^ConsoleMessage m msg]
            (swap! log conj {:type (.type m) :text (.text m)})))))
    log))

(defn set-offline
  "Toggle the page's browser context offline/online (fires the DOM online/offline
   events). Use to E2E offline-first sync queues."
  [^Page page offline?]
  (.setOffline (.context page) (boolean offline?)))

(defn screenshot
  "Save a full-page PNG to `path`. opts {:timeout ms} — Playwright's own default (30000ms)
   applies when omitted; pass a longer timeout for capture pipelines that may run under
   real machine load (many concurrent CPU-bound compositor/rasterize passes push a cold
   full-page screenshot past 30s well before the page itself is actually stuck — this
   showed up as spurious TimeoutErrors in network-isekai's playtest-coscientist under
   heavy parallel load, not a hang in the captured page)."
  ([^Page page path] (screenshot page path {}))
  ([^Page page path {:keys [timeout]}]
   (.screenshot page
     (doto (com.microsoft.playwright.Page$ScreenshotOptions.)
       (.setPath (Paths/get path (make-array String 0)))
       (.setFullPage true)
       (cond-> timeout (.setTimeout (double timeout)))))
   path))

;; ── CDP (Chrome DevTools Protocol): WebAuthn virtual authenticator, etc. ─────

(defn- clj->gson
  "Clojure map/vector/scalar → gson JsonElement (for CDP params)."
  [x]
  (cond
    (map? x)     (let [o (com.google.gson.JsonObject.)]
                   (doseq [[k v] x] (.add o (name k) (clj->gson v))) o)
    (sequential? x) (let [a (com.google.gson.JsonArray.)]
                      (doseq [v x] (.add a (clj->gson v))) a)
    (string? x)  (com.google.gson.JsonPrimitive. ^String x)
    (boolean? x) (com.google.gson.JsonPrimitive. ^Boolean (boolean x))
    (number? x)  (com.google.gson.JsonPrimitive. ^Number x)
    (nil? x)     com.google.gson.JsonNull/INSTANCE
    :else        (com.google.gson.JsonPrimitive. (str x))))

(defn- gson->clj [^com.google.gson.JsonElement e]
  (cond
    (nil? e) nil
    (.isJsonNull e) nil
    (.isJsonObject e) (persistent!
                       (reduce (fn [m en] (assoc! m (key en) (gson->clj (val en))))
                               (transient {}) (.entrySet (.getAsJsonObject e))))
    (.isJsonArray e) (mapv gson->clj (.getAsJsonArray e))
    (.isJsonPrimitive e) (let [p (.getAsJsonPrimitive e)]
                           (cond (.isBoolean p) (.getAsBoolean p)
                                 (.isNumber p)  (.getAsDouble p)
                                 :else          (.getAsString p)))
    :else (str e)))

(defn cdp-session
  "Open a CDP session bound to `page` (for protocol commands Playwright doesn't wrap)."
  ^com.microsoft.playwright.CDPSession [^Page page]
  (.newCDPSession (.context page) page))

(defn cdp-send
  "Send a raw CDP command. `params` is a Clojure map. Returns the result as clj data."
  ([^com.microsoft.playwright.CDPSession sess method] (cdp-send sess method {}))
  ([^com.microsoft.playwright.CDPSession sess method params]
   (gson->clj (.send sess method (clj->gson params)))))

(defn add-virtual-authenticator
  "Install a WebAuthn VIRTUAL AUTHENTICATOR so passkey ceremonies
   (navigator.credentials.create/get) work headlessly. Returns {authenticatorId …}.
   opts default to an internal ctap2 platform authenticator with resident keys +
   user verification auto-satisfied (i.e. a passkey)."
  ([^Page page] (add-virtual-authenticator page {}))
  ([^Page page {:keys [protocol transport has-resident-key has-user-verification
                       is-user-verified automatic-presence-simulation has-prf]
                :or {protocol "ctap2" transport "internal" has-resident-key true
                     has-user-verification true is-user-verified true
                     automatic-presence-simulation true has-prf true}}]
   (let [s (cdp-session page)]
     (cdp-send s "WebAuthn.enable")
     (cdp-send s "WebAuthn.addVirtualAuthenticator"
               {:options (cond-> {:protocol protocol
                                  :transport transport
                                  :hasResidentKey has-resident-key
                                  :hasUserVerification has-user-verification
                                  :isUserVerified is-user-verified
                                  :automaticPresenceSimulation automatic-presence-simulation}
                           ;; PRF (CTAP hmac-secret) — needed for passkey key-wrapping.
                           ;; Supported by Chrome's virtual authenticator (recent builds).
                           has-prf (assoc :hasPrf true))}))))

;; ── Java<->Clojure marshalling for evaluate results ──────────────────────────

(defn ->clj
  "Convert Playwright/Java evaluate results (Map/List/Number/Boolean/String/nil)
   into Clojure data."
  [x]
  (cond
    (instance? java.util.Map x)  (persistent!
                                  (reduce (fn [m e] (assoc! m (key e) (->clj (val e))))
                                          (transient {}) x))
    (instance? java.util.List x) (mapv ->clj x)
    :else x))
