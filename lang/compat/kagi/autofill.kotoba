(ns kagi.autofill
  "Type a secret straight into a browser field, the way a password manager
  extension does.

  Why this exists rather than a clipboard copy
  -------------------------------------------
  `kagi copy` is the best a CLI can do on its own, and it has two costs that
  no TTL removes. The clipboard is a machine-wide surface any process can
  read, so the secret is briefly readable by everything on the box; and the
  paste has to be performed by whoever is at the keyboard, which is the step
  that keeps stalling.

  Neither cost is inherent. A password manager does not put a login on the
  clipboard — it writes the value into the field. This does the same: the
  plaintext goes vault -> this JVM -> a CDP WebSocket frame -> the input
  element, and appears in no other place.

  What it deliberately does NOT do
  --------------------------------
  `agent-browser fill <selector> <text>` was the obvious route and is
  unusable for a secret: the value becomes a process argument, and argv is
  world-readable through `ps`. Any account on the machine could read the
  password out of the command line for as long as the process lived. So the
  browser CLI is used only to learn the debug endpoint, never to carry the
  value.

  The secret is also never interpolated into a JavaScript expression.
  `Runtime.evaluate` is used only to focus the element and to read back a
  LENGTH; the value itself travels as the `text` parameter of
  `Input.insertText`, where no escaping bug can turn it into code.

  Verification without disclosure
  -------------------------------
  A write nobody checked is not a write — but checking a password by reading
  it back would defeat the point. So the read-back is `value.length`, which
  distinguishes 'nothing landed', 'something truncated' and 'all of it
  arrived' without the caller ever learning a character of it.

  Secrets may enter this namespace. They must never be returned, printed or
  logged; every public result carries metadata only."
  (:require [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers
            WebSocket WebSocket$Listener]
           [java.util.concurrent CompletableFuture TimeUnit]))

(def ^:private timeout-sec 15)

;; ------------------------------------------------------------------ targets

(defn- http-get [url]
  (let [client (HttpClient/newHttpClient)
        req (-> (HttpRequest/newBuilder (URI/create url)) (.GET) (.build))]
    (.body (.send client req (HttpResponse$BodyHandlers/ofString)))))

(defn debug-base
  "http://host:port from a CDP WebSocket url.

  `agent-browser get cdp-url` hands back a browser-level ws:// endpoint; the
  per-page endpoints are listed over HTTP on the same authority."
  [ws-url]
  (let [u (URI/create ws-url)]
    (str "http://" (.getHost u) ":" (.getPort u))))

(defn page-targets
  "Debuggable pages, newest listing order preserved."
  [debug-base-url]
  (->> (json/read-str (http-get (str debug-base-url "/json/list")) :key-fn keyword)
       (filter #(= "page" (:type %)))
       vec))

(defn pick-page
  "The page to act on.

  With `url-substring`: the first page whose url contains it, or NIL. It does
  NOT fall back to another page — that fallback was here first and is exactly
  the failure the argument exists to prevent: a caller that named
  `prolific.com` and got 'whichever tab was open' would have a password typed
  into somebody else's form. A caught test, not a hypothetical.

  Without one: the first page that is not about:blank, which is a guess and
  only acceptable because the caller declined to say."
  [targets url-substring]
  (if (seq (str url-substring))
    (first (filter #(str/includes? (str (:url %)) url-substring) targets))
    (first (remove #(= "about:blank" (:url %)) targets))))

;; --------------------------------------------------------------- cdp client

(defn accumulate!
  "Append one `onText` chunk; publish to `inbox` only when `last?`.

  Extracted from the listener so the reassembly can be tested without a
  browser — it is the part that was wrong, and it was wrong in a way no
  fixture would reveal."
  [partial inbox data last?]
  (.append ^StringBuilder @partial (str data))
  (when last?
    (swap! inbox conj (str @partial))
    (reset! partial (StringBuilder.)))
  nil)

(defn length-verdict
  "Classify a write by LENGTH alone, never by comparing values.

  Checking a password by reading it back would defeat the point of not
  disclosing it, so the evidence is how many characters arrived. That still
  separates the four outcomes worth acting on differently."
  [expected observed]
  (cond
    (nil? observed) :absent
    (= observed expected) :match
    (zero? observed) :unchanged
    (< observed expected) :truncated
    :else :unexpected))

(defn- open-ws
  "Connect and return [websocket inbox], inbox being an atom of COMPLETE
  messages.

  The `last` flag is load-bearing. A WebSocket text message arrives in as many
  `onText` calls as the transport feels like, and an early version appended
  each chunk as though it were a whole message — so `json/read-str` failed with
  `EOF in object`. It passed against a tiny fixture page, whose frames happened
  to arrive intact, and failed on the real one. Chunking is a property of the
  traffic, not of the code, which is why a small test cannot establish that
  reassembly works."
  [ws-url]
  (let [inbox (atom [])
        partial (atom (StringBuilder.))
        latch (CompletableFuture.)
        listener
        (reify WebSocket$Listener
          (onOpen [_ ws] (.request ws 1) (.complete latch true) nil)
          (onText [_ ws data last?]
            (accumulate! partial inbox data last?)
            (.request ws 1)
            nil)
          (onError [_ _ws err] (.completeExceptionally latch err) nil))
        ws (-> (HttpClient/newHttpClient)
               (.newWebSocketBuilder)
               (.buildAsync (URI/create ws-url) listener)
               (.get timeout-sec TimeUnit/SECONDS))]
    (.get latch timeout-sec TimeUnit/SECONDS)
    [ws inbox]))

(defn- send-cmd!
  "One CDP command, awaiting the reply with the matching id.

  Polls the inbox rather than wiring a per-id promise: the exchange here is a
  handful of sequential commands, and a correlation table would be more
  machinery than the traffic justifies."
  [^WebSocket ws inbox id payload]
  (.sendText ws (json/write-str (assoc payload :id id)) true)
  (let [deadline (+ (System/currentTimeMillis) (* 1000 timeout-sec))
        ;; A frame this cannot parse is skipped rather than thrown from. CDP
        ;; interleaves unsolicited events with command replies, and one
        ;; unexpected shape must not abort a fill that is otherwise fine.
        parse (fn [s] (try (json/read-str s :key-fn keyword) (catch Exception _ nil)))]
    (loop []
      (if-let [hit (->> @inbox (keep parse) (filter #(= id (:id %))) first)]
        hit
        (if (< (System/currentTimeMillis) deadline)
          (do (Thread/sleep 40) (recur))
          (throw (ex-info "CDP command timed out"
                          {:id id :method (:method payload)
                           :frames-seen (count @inbox)})))))))

(defn- eval-js
  "Evaluate an expression that must NOT contain a secret, returning its value."
  [ws inbox id expr]
  (let [r (send-cmd! ws inbox id
                     {:method "Runtime.evaluate"
                      :params {:expression expr :returnByValue true}})]
    (get-in r [:result :result :value])))

;; ------------------------------------------------------------------- public

(defn- js-string
  "A JavaScript string literal for a NON-SECRET value (a CSS selector).

  json/write-str gives correct escaping; kept as its own function so the
  distinction between what may be interpolated and what may not is visible at
  every call site."
  [s]
  (json/write-str (str s)))

(defn fill-secret!
  "Focus `selector` on `page` and insert `secret` into it.

  Returns metadata only: `{:ok? :expected-length :observed-length :verdict}`.
  The verdict is derived from lengths, never from a comparison of values, so
  a caller learns whether the write landed and nothing else.

  `:verdict` is one of
    :match      the field holds exactly as many characters as were sent
    :absent     no element matched the selector
    :unchanged  the field is still empty — the insert did not take
    :truncated  fewer characters arrived than were sent
    :unexpected more characters than were sent, i.e. the field was not empty
                and this appended to somebody else's value"
  [{:keys [page-ws selector secret]}]
  (let [[ws inbox] (open-ws page-ws)
        sel (js-string selector)]
    (try
      (send-cmd! ws inbox 1 {:method "Runtime.enable"})
      (let [present? (eval-js ws inbox 2 (str "!!document.querySelector(" sel ")"))]
        (if-not present?
          {:ok? false :verdict :absent :selector selector}
          (let [_ (eval-js ws inbox 3
                           ;; Cleared here rather than by selecting-all and
                           ;; overtyping: :unexpected below can then only mean
                           ;; the clear failed, not that a stale value was
                           ;; still there.
                           (str "(() => { const e = document.querySelector(" sel ");"
                                " e.focus(); e.value = ''; return true; })()"))
                ;; The only frame carrying the secret. It is a CDP parameter,
                ;; not part of an expression, so no escaping mistake can make
                ;; it executable.
                _ (send-cmd! ws inbox 4 {:method "Input.insertText"
                                         :params {:text secret}})
                ;; Frameworks listen for these; a value set without them is
                ;; often ignored on submit.
                _ (eval-js ws inbox 5
                           (str "(() => { const e = document.querySelector(" sel ");"
                                " e.dispatchEvent(new Event('input', {bubbles:true}));"
                                " e.dispatchEvent(new Event('change', {bubbles:true}));"
                                " return true; })()"))
                observed (eval-js ws inbox 6
                                  (str "document.querySelector(" sel ").value.length"))
                expected (count secret)
                verdict (length-verdict expected observed)]
            {:ok? (= :match verdict)
             :verdict verdict
             :expected-length expected
             :observed-length observed
             :selector selector
             :secret? false})))
      (finally
        (try (.sendClose ws WebSocket/NORMAL_CLOSURE "done") (catch Throwable _ nil))))))
