(ns kototama.bounded-stream
  "JVM implementation of the linear Task a / bounded Stream Bytes component
  resources. Providers supply InputStreams; this namespace owns byte budgets,
  pull bounds, single-consumer state, and cancellation."
  (:import [java.io InputStream]
           [java.net URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]
           [java.time Duration]))

(def max-pull-bytes 65536)

(defprotocol BytesStream
  (read-bytes! [stream maximum])
  (cancel-stream! [stream]))

(deftype BoundedInputStream [^InputStream input limit state]
  BytesStream
  (read-bytes! [_ maximum]
    (when-not (and (integer? maximum) (<= 1 maximum max-pull-bytes))
      (throw (ex-info "stream pull is outside the admitted bound"
                      {:phase :bounded-stream :maximum maximum})))
    (locking state
      (when (:cancelled? @state)
        (throw (ex-info "stream resource is cancelled"
                        {:phase :bounded-stream})))
      (let [buffer (byte-array (int maximum))
            n (.read input buffer)]
        (if (neg? n)
          (do (.close input)
              (swap! state assoc :cancelled? true)
              {:bytes (byte-array 0) :done true})
          (let [total (+ (:bytes @state) n)]
            (when (> total limit)
              (.close input)
              (swap! state assoc :cancelled? true)
              (throw (ex-info "stream exceeded its admitted byte budget"
                              {:phase :bounded-stream :limit limit})))
            (swap! state assoc :bytes total)
            {:bytes (java.util.Arrays/copyOf buffer n) :done false})))))
  (cancel-stream! [_]
    (locking state
      (when-not (:cancelled? @state) (.close input))
      (swap! state assoc :cancelled? true)
      nil)))

(defn bounded-input-stream [input limit]
  (when-not (and (instance? InputStream input)
                 (integer? limit) (pos? limit))
    (throw (ex-info "invalid bounded stream resource"
                    {:phase :bounded-stream :limit limit})))
  (BoundedInputStream. input limit (atom {:bytes 0 :cancelled? false})))

(defprotocol Task
  (poll! [task])
  (cancel-task! [task]))

(deftype ReadyTask [state]
  Task
  (poll! [_]
    (locking state
      (if-let [value (:value @state)]
        (do (reset! state {:cancelled? true})
            {:state :ready :value value})
        {:state :cancelled})))
  (cancel-task! [_] (reset! state {:cancelled? true}) nil))

(defn ready-task [value] (ReadyTask. (atom {:value value})))

(defn http-get-stream
  [{:keys [allowed-origins deadline-ms max-response-bytes]} url headers]
  (let [uri (URI. url)]
    (when-not (contains? (set allowed-origins)
                         (str (.getScheme uri) "://" (.getAuthority uri)))
      (throw (ex-info "HTTP origin is not admitted"
                      {:phase :bounded-stream :origin (.getAuthority uri)})))
    (let [builder (doto (HttpRequest/newBuilder uri)
                    (.GET)
                    (.timeout (Duration/ofMillis deadline-ms)))
          _ (doseq [[name value] headers] (.header builder name value))
          response (.send (HttpClient/newHttpClient) (.build builder)
                          (HttpResponse$BodyHandlers/ofInputStream))]
      {:status (.statusCode response)
       :task (ready-task
              (bounded-input-stream (.body response) max-response-bytes))})))

(defn object-get-stream
  "Adapt an admitted object provider returning {:input InputStream :etag ...}."
  [provider binding key maximum]
  (when-let [{:keys [input etag]} ((:get-stream provider) binding key)]
    {:etag etag :task (ready-task (bounded-input-stream input maximum))}))

(defn put-block! [provider binding digest bytes]
  ((:put-block provider) binding digest bytes))

(defn compare-and-set-ref!
  [provider binding key expected next]
  ((:compare-and-set-ref provider) binding key expected next))
