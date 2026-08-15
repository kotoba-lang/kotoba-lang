(ns kotoba.lang.captp-runtime
  "Bounded CapTP 1.0 runtime for authenticated, in-order netlayers.

  This namespace owns session lifecycle, the CapTP/Syrup wire boundary,
  answer and resolver positions, and distributed reference bookkeeping.  It
  deliberately does not turn locator data into authority: resolving a
  sturdyref still requires a live resolver capability supplied by the host."
  (:require [clojure.string :as str]
            [kotoba.lang.capability-values :as capabilities]
            [kotoba.lang.trusted-admission :as trusted])
  #?(:clj (:import [java.io ByteArrayOutputStream]
                   [java.nio.charset StandardCharsets])))

(def profile "ocapn-captp-1.0-draft-2026-08-15")

(defn syrup-record
  [label fields]
  {:syrup/record label :syrup/fields (vec fields)})

(defn syrup-record?
  [x]
  (and (map? x)
       (= #{:syrup/record :syrup/fields} (set (keys x)))
       (symbol? (:syrup/record x))
       (vector? (:syrup/fields x))))

(defn- byte-array-value?
  [x]
  #?(:clj (= (class x) (Class/forName "[B"))
     :cljs (instance? js/Uint8Array x)))

(defn- utf8-bytes
  [s]
  #?(:clj (.getBytes ^String s StandardCharsets/UTF_8)
     :cljs (.encode (js/TextEncoder.) s)))

(defn- bytes->utf8
  [xs]
  #?(:clj (String. (byte-array xs) StandardCharsets/UTF_8)
     :cljs (.decode (js/TextDecoder.) (js/Uint8Array. (clj->js xs)))))

(defn- octets
  [xs]
  (mapv #(bit-and 0xff %) xs))

(defn- byte-count [xs]
  #?(:clj (alength ^bytes xs)
     :cljs (.-length xs)))

(defn- ->bytes
  [xs]
  #?(:clj (byte-array (map unchecked-byte xs))
     :cljs (js/Uint8Array. (clj->js xs))))

(defn- concat-bytes
  [& parts]
  #?(:clj
     (let [out (ByteArrayOutputStream.)]
       (doseq [part parts]
         (.write out ^bytes part 0 (alength ^bytes part)))
       (.toByteArray out))
     :cljs
     (let [size (reduce + (map #(.-length %) parts))
           out (js/Uint8Array. size)]
       (loop [offset 0, remaining parts]
         (if-let [part (first remaining)]
           (do (.set out part offset)
               (recur (+ offset (.-length part)) (next remaining)))
           out)))))

(defn- ascii
  [s]
  (utf8-bytes s))

(declare syrup-encode)

(defn- compare-octets
  [a b]
  (compare (octets a) (octets b)))

(defn syrup-encode
  "Encode the bounded Preserves data model used by CapTP to canonical Syrup.

  Supported values are booleans, integers, byte arrays, strings, symbols,
  Kotoba keywords (as colon-prefixed Syrup symbols), vectors, maps, sets, and
  syrup-record values. Unsupported runtime values fail closed."
  [value]
  (cond
    (true? value) (ascii "t")
    (false? value) (ascii "f")

    (integer? value)
    (ascii (if (neg? value)
             (str (- value) "-")
             (str value "+")))

    (byte-array-value? value)
    (concat-bytes (ascii (str (byte-count value) ":")) value)

    (string? value)
    (let [payload (utf8-bytes value)]
      (concat-bytes (ascii (str (byte-count payload) "\"")) payload))

    (keyword? value)
    (let [payload (utf8-bytes (str value))]
      (concat-bytes (ascii (str (byte-count payload) "'")) payload))

    (symbol? value)
    (let [text (str value)
          payload (utf8-bytes text)]
      (concat-bytes (ascii (str (byte-count payload) "'")) payload))

    (syrup-record? value)
    (apply concat-bytes
           (ascii "<")
           (syrup-encode (:syrup/record value))
           (concat (map syrup-encode (:syrup/fields value))
                   [(ascii ">")]))

    (vector? value)
    (apply concat-bytes (ascii "[")
           (concat (map syrup-encode value) [(ascii "]")]))

    (map? value)
    (let [pairs (sort-by (comp octets first)
                         (map (fn [[k v]] [(syrup-encode k) v]) value))]
      (apply concat-bytes (ascii "{")
             (concat (mapcat (fn [[encoded-key v]]
                               [encoded-key (syrup-encode v)])
                             pairs)
                     [(ascii "}")])))

    (set? value)
    (let [items (sort compare-octets (map syrup-encode value))]
      (apply concat-bytes (ascii "#") (concat items [(ascii "$")])))

    :else
    (throw (ex-info "value is not passable Syrup data"
                    {:problem :captp/syrup-value-unsupported
                     :value/type (str (type value))}))))

(defn- digit?
  [n]
  (<= 48 n 57))

(defn- parse-natural
  [data start]
  (loop [i start]
    (if (and (< i (count data)) (digit? (nth data i)))
      (recur (inc i))
      (when (> i start)
        [(parse-long (apply str (map char (subvec data start i)))) i]))))

(declare parse-value)

(defn- parse-many
  [data start terminal]
  (loop [i start, values []]
    (when (>= i (count data))
      (throw (ex-info "truncated Syrup container"
                      {:problem :captp/syrup-truncated})))
    (if (= terminal (nth data i))
      [values (inc i)]
      (let [[value next-i] (parse-value data i)]
        (recur next-i (conj values value))))))

(defn- parse-sized
  [data size delimiter]
  (let [payload-start (inc delimiter)
        end (+ payload-start size)]
    (when (> end (count data))
      (throw (ex-info "truncated Syrup scalar"
                      {:problem :captp/syrup-truncated})))
    [(subvec data payload-start end) end]))

(defn- parse-value
  [data start]
  (when (>= start (count data))
    (throw (ex-info "truncated Syrup value"
                    {:problem :captp/syrup-truncated})))
  (let [tag (nth data start)]
    (cond
      (= tag 116) [true (inc start)]
      (= tag 102) [false (inc start)]

      (= tag 91)
      (let [[values end] (parse-many data (inc start) 93)]
        [(vec values) end])

      (= tag 123)
      (let [[values end] (parse-many data (inc start) 125)]
        (when (odd? (count values))
          (throw (ex-info "Syrup dictionary has an unmatched key"
                          {:problem :captp/syrup-dictionary-arity})))
        [(into {} (map vec (partition 2 values))) end])

      (= tag 35)
      (let [[values end] (parse-many data (inc start) 36)]
        [(set values) end])

      (= tag 60)
      (let [[values end] (parse-many data (inc start) 62)
            label (first values)]
        (when-not (and (symbol? label) (seq values))
          (throw (ex-info "Syrup record label is not a symbol"
                          {:problem :captp/syrup-record-label})))
        [(syrup-record label (rest values)) end])

      (digit? tag)
      (let [[n delimiter] (or (parse-natural data start)
                              (throw (ex-info "invalid Syrup length"
                                              {:problem :captp/syrup-number})))
            marker (when (< delimiter (count data)) (nth data delimiter))]
        (case marker
          43 [n (inc delimiter)]
          45 (if (zero? n)
               (throw (ex-info "negative zero is not canonical Syrup"
                               {:problem :captp/syrup-negative-zero}))
               [(- n) (inc delimiter)])
          58 (let [[payload end] (parse-sized data n delimiter)]
               [(->bytes payload) end])
          34 (let [[payload end] (parse-sized data n delimiter)]
               [(bytes->utf8 payload) end])
          39 (let [[payload end] (parse-sized data n delimiter)
                   text (bytes->utf8 payload)
                   value (if (str/starts-with? text ":")
                           (keyword (subs text 1))
                           (symbol text))]
               [value end])
          (throw (ex-info "unknown Syrup scalar marker"
                          {:problem :captp/syrup-marker-invalid}))))

      :else
      (throw (ex-info "unknown Syrup tag"
                      {:problem :captp/syrup-tag-invalid :tag tag})))))

(defn syrup-decode
  "Decode one canonical Syrup value and reject trailing or non-canonical data."
  [encoded]
  (when-not (byte-array-value? encoded)
    (throw (ex-info "Syrup frame must be bytes"
                    {:problem :captp/syrup-bytes-required})))
  (let [data (octets encoded)
        [value end] (parse-value data 0)]
    (when-not (= end (count data))
      (throw (ex-info "Syrup frame has trailing bytes"
                      {:problem :captp/syrup-trailing-data})))
    (when-not (= data (octets (syrup-encode value)))
      (throw (ex-info "Syrup frame is not canonical"
                      {:problem :captp/syrup-noncanonical})))
    value))

(defn descriptor
  [kind position]
  (when-not (and (contains? #{'desc:export 'desc:answer
                              'desc:import-object 'desc:import-promise}
                            kind)
                 (int? position)
                 (not (neg? position)))
    (throw (ex-info "invalid CapTP descriptor"
                    {:problem :captp/descriptor-invalid})))
  (syrup-record kind [position]))

(defn start-session-frame
  [public-key acceptable-location signature]
  (syrup-record 'op:start-session
                [trusted/captp-version public-key acceptable-location signature]))

(defn- start-session-frame?
  [frame]
  (and (syrup-record? frame)
       (= 'op:start-session (:syrup/record frame))
       (= 4 (count (:syrup/fields frame)))
       (= trusted/captp-version (first (:syrup/fields frame)))))

(defn- target->descriptor
  [target]
  (descriptor (case (:ocapn/descriptor target)
                :desc/export 'desc:export
                :desc/answer 'desc:answer
                (throw (ex-info "unsupported CapTP target"
                                {:problem :captp/target-invalid})))
              (:ocapn/position target)))

(defn deliver-frame
  [target args answer-position resolve-me]
  (syrup-record 'op:deliver
                [(target->descriptor target)
                 (vec args)
                 (if (false? answer-position) false answer-position)
                 (if (false? resolve-me) false resolve-me)]))

(defn gc-answers-frame [positions]
  (syrup-record 'op:gc-answers [(vec positions)]))

(defn gc-exports-frame [positions deltas]
  (syrup-record 'op:gc-exports [(vec positions) (vec deltas)]))

(defn abort-frame [reason]
  (syrup-record 'op:abort [(str reason)]))

(defn listen-frame [target listener]
  (syrup-record 'op:listen [(target->descriptor target) listener]))

(defn get-frame [target field-name answer-position]
  (syrup-record 'op:get [(target->descriptor target) field-name answer-position]))

(defn index-frame [target index answer-position]
  (syrup-record 'op:index [(target->descriptor target) index answer-position]))

(defn untag-frame [target tag answer-position]
  (syrup-record 'op:untag [(target->descriptor target) tag answer-position]))

(defprotocol ^:private RuntimeValue
  (-runtime-state [runtime])
  (-runtime-session [runtime])
  (-runtime-request-capable? [runtime])
  (-runtime-registry [runtime])
  (-runtime-peer-key [runtime])
  (-runtime-write! [runtime frame])
  (-runtime-exchange! [runtime frame]))

(defprotocol ^:private SessionRegistryValue
  (-claim! [registry peer-key session-id])
  (-release! [registry peer-key session-id]))

(defprotocol ^:private DeferredAnswerValue
  (-answer-runtime [answer])
  (-answer-position [answer]))

(deftype ^:private DeferredAnswer [runtime position]
  DeferredAnswerValue
  (-answer-runtime [_] runtime)
  (-answer-position [_] position))

(defn deferred-answer? [x]
  (satisfies? DeferredAnswerValue x))

(deftype ^:private SessionRegistry [state]
  SessionRegistryValue
  (-claim! [_ peer-key session-id]
    (let [claimed? (atom false)]
      (swap! state
             (fn [sessions]
               (if (contains? sessions peer-key)
                 sessions
                 (do (reset! claimed? true)
                     (assoc sessions peer-key session-id)))))
      @claimed?))
  (-release! [_ peer-key session-id]
    (swap! state
           (fn [sessions]
             (if (= session-id (get sessions peer-key))
               (dissoc sessions peer-key)
               sessions)))))

(defn session-registry
  "Create a lexical registry enforcing one active CapTP session per peer."
  []
  (SessionRegistry. (atom {})))

(defn session-registry?
  [x]
  (satisfies? SessionRegistryValue x))

(deftype ^:private CapTPRuntime
  [state session registry peer-key write-frame! exchange-frame!]
  RuntimeValue
  (-runtime-state [_] state)
  (-runtime-session [_] session)
  (-runtime-request-capable? [_] (boolean exchange-frame!))
  (-runtime-registry [_] registry)
  (-runtime-peer-key [_] peer-key)
  (-runtime-write! [_ frame] (write-frame! frame))
  (-runtime-exchange! [_ frame]
    (when exchange-frame! (exchange-frame! frame))))

(defn runtime?
  [x]
  (satisfies? RuntimeValue x))

(defn- active!
  [runtime]
  (when-not (= :active (:phase @(-runtime-state runtime)))
    (throw (ex-info "CapTP session is not active"
                    {:problem :captp/session-not-active}))))

(defn- release-session!
  [runtime]
  (-release! (-runtime-registry runtime)
             (-runtime-peer-key runtime)
             (:session/id (trusted/session-description
                           (-runtime-session runtime)))))

(defn- abort-state!
  [runtime]
  (swap! (-runtime-state runtime)
         assoc :phase :aborted :answers {} :exports {} :imports {})
  (release-session! runtime))

(defn- accepted-result
  [result]
  (when-not (and (map? result)
                 (true? (:netlayer/accepted? result))
                 (capabilities/non-empty-string? (:netlayer/message-id result)))
    (throw (ex-info "netlayer did not accept the CapTP frame"
                    {:problem :captp/netlayer-unconfirmed})))
  {:ocapn/accepted? true
   :ocapn/message-id (:netlayer/message-id result)})

(defn- write-wire!
  [runtime frame]
  (let [result (try
                 (-runtime-write! runtime (syrup-encode frame))
                 (catch #?(:clj Exception :cljs :default) _
                   ::write-failed))]
    (when (= ::write-failed result)
      (abort-state! runtime)
      (throw (ex-info "CapTP netlayer write failed"
                      {:problem :captp/netlayer-write-failed})))
    (try
      (accepted-result result)
      (catch #?(:clj Exception :cljs :default) error
        (abort-state! runtime)
        (throw error)))))

(defn- allocate-request!
  [runtime]
  (let [allocated (atom nil)]
    (swap! (-runtime-state runtime)
           (fn [state]
             (let [answer (:next-answer state)
                   resolver (:next-export state)]
               (reset! allocated [answer resolver])
               (-> state
                   (assoc :next-answer (inc answer)
                          :next-export (inc resolver))
                   (assoc-in [:answers answer] {:status :pending
                                                :resolver resolver})
                   (assoc-in [:exports resolver] {:wire-count 1
                                                  :kind :resolver
                                                  :answer answer})))))
    @allocated))

(defn- allocate-answer!
  [runtime]
  (let [allocated (atom nil)]
    (swap! (-runtime-state runtime)
           (fn [state]
             (let [answer (:next-answer state)]
               (reset! allocated answer)
               (-> state
                   (assoc :next-answer (inc answer))
                   (assoc-in [:answers answer] {:status :pending})))))
    @allocated))

(defn- attach-listener!
  [runtime answer]
  (let [allocated (atom nil)]
    (swap! (-runtime-state runtime)
           (fn [state]
             (when-not (= :pending (get-in state [:answers answer :status]))
               (throw (ex-info "only a pending answer can be listened to"
                               {:problem :captp/listen-not-pending})))
             (when (get-in state [:answers answer :resolver])
               (throw (ex-info "answer already has a listener"
                               {:problem :captp/listener-already-attached})))
             (let [resolver (:next-export state)]
               (reset! allocated resolver)
               (-> state
                   (assoc :next-export (inc resolver))
                   (assoc-in [:answers answer :resolver] resolver)
                   (assoc-in [:exports resolver] {:wire-count 1
                                                  :kind :resolver
                                                  :answer answer})))))
    @allocated))

(defn- settle-resolver!
  [runtime position method value]
  (let [state (-runtime-state runtime)
        export (get-in @state [:exports position])]
    (when-not (= :resolver (:kind export))
      (throw (ex-info "delivery target is not an exported resolver"
                      {:problem :captp/resolver-unknown})))
    (let [answer (:answer export)
          status (case method
                   fulfill :fulfilled
                   break :broken
                   (throw (ex-info "resolver method is invalid"
                                   {:problem :captp/resolver-method})))]
      (when-not (= :pending (get-in @state [:answers answer :status]))
        (throw (ex-info "resolver is already settled"
                        {:problem :captp/resolver-already-settled})))
      (swap! state assoc-in [:answers answer]
             {:status status :value value :resolver position}))))

(defn- descriptor-positions
  [value labels]
  (letfn [(walk [x]
            (cond
              (syrup-record? x)
              (concat (when (contains? labels (:syrup/record x))
                        [(first (:syrup/fields x))])
                      (mapcat walk (:syrup/fields x)))

              (map? x) (mapcat walk (mapcat identity x))
              (or (vector? x) (set? x)) (mapcat walk x)
              :else []))]
    (vec (walk value))))

(defn- register-imports!
  [runtime value]
  (doseq [position (descriptor-positions
                    value #{'desc:import-object 'desc:import-promise})]
    (when-not (and (int? position) (not (neg? position)))
      (throw (ex-info "invalid imported CapTP descriptor"
                      {:problem :captp/import-descriptor-invalid})))
    (swap! (-runtime-state runtime)
           update-in [:imports position :wire-count] (fnil inc 0))
    (swap! (-runtime-state runtime)
           assoc-in [:imports position :kind] :remote-export)))

(defn- receive-frame!
  [runtime encoded]
  (active! runtime)
  (let [frame (syrup-decode encoded)
        label (:syrup/record frame)
        fields (:syrup/fields frame)]
    (case label
      op:deliver
      (let [[to args answer-pos resolve-me] fields
            [position] (:syrup/fields to)
            [method value] args]
        (when-not (and (= 4 (count fields))
                       (syrup-record? to)
                       (= 1 (count (:syrup/fields to)))
                       (= 'desc:export (:syrup/record to))
                       (= 2 (count args))
                       (symbol? method)
                       (false? answer-pos)
                       (false? resolve-me))
          (throw (ex-info "unsupported inbound CapTP delivery"
                          {:problem :captp/inbound-delivery-invalid})))
        (when (= 'fulfill method)
          (register-imports! runtime value))
        (settle-resolver! runtime position method value)
        :settled)

      op:gc-exports
      (let [[positions deltas] fields]
        (when-not (and (= 2 (count fields))
                       (vector? positions)
                       (vector? deltas)
                       (= (count positions) (count deltas))
                       (every? #(and (int? %) (not (neg? %))) positions)
                       (every? #(and (int? %) (pos? %)) deltas))
          (throw (ex-info "invalid CapTP export GC"
                          {:problem :captp/gc-exports-invalid})))
        (doseq [[position delta] (map vector positions deltas)]
          (let [old (get-in @(-runtime-state runtime)
                            [:exports position :wire-count])]
            (when-not (and old (<= delta old))
              (throw (ex-info "CapTP export GC exceeds wire count"
                              {:problem :captp/gc-exports-over-release})))
            (if (= old delta)
              (swap! (-runtime-state runtime) update :exports dissoc position)
              (swap! (-runtime-state runtime) update-in
                     [:exports position :wire-count] - delta))))
        :gc-exports)

      op:abort
      (do (when-not (and (= 1 (count fields))
                         (string? (first fields)))
            (throw (ex-info "invalid CapTP abort"
                            {:problem :captp/abort-invalid})))
          (abort-state! runtime)
          :aborted)

      (throw (ex-info "unsupported inbound CapTP operation"
                      {:problem :captp/operation-unsupported})))))

(defn- send-abstract!
  [runtime message]
  (active! runtime)
  (when-not (and (map? message)
                 (= :op/deliver (:ocapn/op message))
                 (= profile (:ocapn/profile message))
                 (false? (:ocapn/answer-position message))
                 (false? (:ocapn/resolve-me message)))
    (throw (ex-info "invalid one-way CapTP delivery"
                    {:problem :captp/outbound-delivery-invalid})))
  (write-wire! runtime
               (deliver-frame (:ocapn/to message) (:ocapn/args message)
                              false false)))

(defn- request-abstract!
  [runtime call]
  (active! runtime)
  (when-not (and (map? call)
                 (= profile (:ocapn/profile call))
                 (= :settled (:ocapn/result call)))
    (throw (ex-info "invalid CapTP request"
                    {:problem :captp/outbound-request-invalid})))
  (when-not (-runtime-request-capable? runtime)
    (throw (ex-info "CapTP runtime has no request exchange capability"
                    {:problem :captp/request-not-supported})))
  (let [[answer resolver] (allocate-request! runtime)
        frame (deliver-frame (:ocapn/to call) (:ocapn/args call)
                             answer (descriptor 'desc:import-object resolver))
        inbound (try
                  (-runtime-exchange! runtime (syrup-encode frame))
                  (catch #?(:clj Exception :cljs :default) _
                    ::exchange-failed))]
    (when (= ::exchange-failed inbound)
      (abort-state! runtime)
      (throw (ex-info "CapTP exchange failed"
                      {:problem :captp/exchange-failed})))
    (try
        (when-not (vector? inbound)
          (throw (ex-info "netlayer exchange did not return frames"
                          {:problem :captp/exchange-invalid})))
        (doseq [encoded inbound] (receive-frame! runtime encoded))
        (let [{:keys [status value]} (get-in @(-runtime-state runtime)
                                             [:answers answer])]
          (when (= :pending status)
            (throw (ex-info "CapTP answer remains unresolved"
                            {:problem :captp/answer-unresolved})))
          (write-wire! runtime (gc-answers-frame [answer]))
          (swap! (-runtime-state runtime) update :answers dissoc answer)
          (if (= :broken status)
            {:ocapn/status :broken}
            {:ocapn/status status :ocapn/value value}))
      (catch #?(:clj Exception :cljs :default) error
        (abort-state! runtime)
        (if (ex-data error)
          (throw error)
          (throw (ex-info "invalid inbound CapTP exchange"
                          {:problem :captp/inbound-exchange-invalid})))))))

(defn deferred-request!
  "Send a request whose answer can be pipelined before it settles.

  Resolution is driven by receive!. Merely printing or serializing the returned
  opaque value cannot forge an answer slot."
  [runtime {:keys [ocapn/to ocapn/args] :as call}]
  (active! runtime)
  (when-not (and (map? call)
                 (= #{:ocapn/to :ocapn/args} (set (keys call)))
                 (map? to) (vector? args))
    (throw (ex-info "invalid deferred CapTP request"
                    {:problem :captp/deferred-request-invalid})))
  (let [answer (allocate-answer! runtime)]
    (write-wire! runtime (deliver-frame to args answer false))
    (DeferredAnswer. runtime answer)))

(defn pipeline-request!
  "Pipeline a request to an unresolved remote answer, returning a new answer."
  [pending args]
  (when-not (and (deferred-answer? pending) (vector? args))
    (throw (ex-info "deferred answer and argument vector required"
                    {:problem :captp/pipeline-request-invalid})))
  (let [runtime (-answer-runtime pending)
        answer (allocate-answer! runtime)]
    (active! runtime)
    (write-wire! runtime
                 (deliver-frame {:ocapn/descriptor :desc/answer
                                 :ocapn/position (-answer-position pending)}
                                args answer false))
    (DeferredAnswer. runtime answer)))

(defn pipeline-send!
  "Pipeline a one-way message to an unresolved remote answer."
  [pending args]
  (when-not (and (deferred-answer? pending) (vector? args))
    (throw (ex-info "deferred answer and argument vector required"
                    {:problem :captp/pipeline-send-invalid})))
  (let [runtime (-answer-runtime pending)]
    (active! runtime)
    (write-wire! runtime
                 (deliver-frame {:ocapn/descriptor :desc/answer
                                 :ocapn/position (-answer-position pending)}
                                args false false))))

(defn listen!
  "Attach exactly one local resolver to a deferred answer."
  [pending]
  (when-not (deferred-answer? pending)
    (throw (ex-info "deferred answer required"
                    {:problem :captp/deferred-answer-required})))
  (let [runtime (-answer-runtime pending)
        answer (-answer-position pending)
        resolver (attach-listener! runtime answer)]
    (write-wire! runtime
                 (listen-frame {:ocapn/descriptor :desc/answer
                                :ocapn/position answer}
                               (descriptor 'desc:import-object resolver)))))

(defn- derive-answer!
  [pending value frame-fn problem]
  (when-not (deferred-answer? pending)
    (throw (ex-info "deferred answer required" {:problem problem})))
  (let [runtime (-answer-runtime pending)
        answer (allocate-answer! runtime)]
    (active! runtime)
    (write-wire! runtime
                 (frame-fn {:ocapn/descriptor :desc/answer
                            :ocapn/position (-answer-position pending)}
                           value answer))
    (DeferredAnswer. runtime answer)))

(defn get-answer! [pending field-name]
  (when-not (capabilities/non-empty-string? field-name)
    (throw (ex-info "CapTP field name required" {:problem :captp/get-field-invalid})))
  (derive-answer! pending field-name get-frame :captp/get-invalid))

(defn index-answer! [pending index]
  (when-not (and (int? index) (not (neg? index)))
    (throw (ex-info "CapTP index must be non-negative" {:problem :captp/index-invalid})))
  (derive-answer! pending index index-frame :captp/index-invalid))

(defn untag-answer! [pending tag]
  (when-not (capabilities/non-empty-string? tag)
    (throw (ex-info "CapTP tag required" {:problem :captp/untag-tag-invalid})))
  (derive-answer! pending tag untag-frame :captp/untag-invalid))

(defn settlement!
  "Consume a settled deferred answer and release its remote answer position."
  [pending]
  (when-not (deferred-answer? pending)
    (throw (ex-info "deferred answer required"
                    {:problem :captp/deferred-answer-required})))
  (let [runtime (-answer-runtime pending)
        answer (-answer-position pending)
        {:keys [status value]} (get-in @(-runtime-state runtime) [:answers answer])]
    (case status
      :pending (throw (ex-info "CapTP answer remains unresolved"
                               {:problem :captp/answer-unresolved}))
      :fulfilled (do (write-wire! runtime (gc-answers-frame [answer]))
                     (swap! (-runtime-state runtime) update :answers dissoc answer)
                     {:ocapn/status :fulfilled :ocapn/value value})
      :broken (do (write-wire! runtime (gc-answers-frame [answer]))
                  (swap! (-runtime-state runtime) update :answers dissoc answer)
                  {:ocapn/status :broken})
      (throw (ex-info "CapTP deferred answer is unknown or consumed"
                      {:problem :captp/answer-unknown})))))

(defn open-session!
  "Verify both start-session records and open a bounded CapTP runtime.

  WRITE-FRAME! receives canonical Syrup bytes. EXCHANGE-FRAME!, when present,
  receives request bytes and returns a vector of inbound canonical frames.
  The verifier is the cryptographic trust root and returns the closed session
  result required by trusted/authenticate-session!."
  [{:keys [verify! handshake local-start remote-start registry write-frame!
           exchange-frame!] :as opts}]
  (let [required #{:verify! :handshake :local-start :remote-start :registry
                   :write-frame!}
        allowed (conj required :exchange-frame!)]
    (when-not (and (map? opts)
                   (contains? #{required allowed} (set (keys opts))))
      (throw (ex-info "invalid CapTP open options"
                      {:problem :captp/open-fields})))
    (when-not (and (start-session-frame? local-start)
                   (start-session-frame? remote-start))
      (throw (ex-info "invalid CapTP start-session record"
                      {:problem :captp/start-session-invalid})))
    (when-not (fn? write-frame!)
      (throw (ex-info "CapTP netlayer writer is not live"
                      {:problem :captp/netlayer-writer-invalid})))
    (when-not (session-registry? registry)
      (throw (ex-info "CapTP session registry is required"
                      {:problem :captp/session-registry-invalid})))
    (when (and (some? exchange-frame!) (not (fn? exchange-frame!)))
      (throw (ex-info "CapTP exchange capability is not live"
                      {:problem :captp/exchange-invalid})))
    (let [state (atom {:phase :starting
                       :next-answer 1
                       :next-export 1
                       :answers {}
                       :exports {0 {:wire-count 1 :kind :bootstrap}}
                       :imports {0 {:wire-count 1 :kind :bootstrap}}})
          runtime-holder (atom nil)
          transport (cond->
                     {:send! #(send-abstract! @runtime-holder %)}
                      exchange-frame!
                      (assoc :request! #(request-abstract! @runtime-holder %)))
          session (trusted/authenticate-session!
                   verify!
                   {:handshake handshake
                    :local-start local-start
                    :remote-start remote-start}
                   transport)
          description (trusted/session-description session)
          peer-key (:session/peer description)
          session-id (:session/id description)
          runtime (CapTPRuntime. state session registry peer-key
                                  write-frame! exchange-frame!)]
      (reset! runtime-holder runtime)
      (when-not (-claim! registry peer-key session-id)
        (throw (ex-info "an active CapTP session already exists for peer"
                        {:problem :captp/duplicate-peer-session})))
      (try
        (accepted-result (write-frame! (syrup-encode local-start)))
        (swap! state assoc :phase :active)
        runtime
        (catch #?(:clj Exception :cljs :default) _
          (-release! registry peer-key session-id)
          (throw (ex-info "CapTP start-session send failed"
                          {:problem :captp/start-send-failed})))))))

(defn authenticated-session
  "Return the opaque trusted session bound to RUNTIME."
  [runtime]
  (when (runtime? runtime) (-runtime-session runtime)))

(defn runtime-description
  [runtime]
  (when (runtime? runtime)
    (let [state @(-runtime-state runtime)]
      (merge (trusted/session-description (-runtime-session runtime))
             {:captp/profile profile
              :captp/phase (:phase state)
              :captp/answers (count (:answers state))
              :captp/exports (count (:exports state))
              :captp/imports (count (:imports state))}))))

(defn receive!
  "Process one canonical inbound CapTP frame."
  [runtime encoded]
  (when-not (runtime? runtime)
    (throw (ex-info "CapTP runtime required"
                    {:problem :captp/runtime-required})))
  (try
    (receive-frame! runtime encoded)
    (catch #?(:clj Exception :cljs :default) error
      (when (= :active (:phase @(-runtime-state runtime)))
        (abort-state! runtime))
      (if (ex-data error)
        (throw error)
        (throw (ex-info "invalid inbound CapTP frame"
                        {:problem :captp/inbound-frame-invalid}))))))

(defn abort!
  [runtime reason]
  (active! runtime)
  (let [accepted (write-wire! runtime (abort-frame reason))]
    (abort-state! runtime)
    accepted))

(defn release-imports!
  "Release remote exports after local references are no longer reachable."
  [runtime positions]
  (active! runtime)
  (when-not (and (vector? positions)
                 (seq positions)
                 (every? #(contains? (:imports @(-runtime-state runtime)) %)
                         positions))
    (throw (ex-info "unknown CapTP import release"
                    {:problem :captp/import-release-invalid})))
  (let [deltas (mapv #(get-in @(-runtime-state runtime)
                              [:imports % :wire-count]) positions)
        accepted (write-wire! runtime (gc-exports-frame positions deltas))]
    (swap! (-runtime-state runtime) update :imports #(apply dissoc % positions))
    accepted))

(defn- uri-component?
  [s]
  (and (capabilities/non-empty-string? s)
       (boolean (re-matches #"(?:[A-Za-z0-9._~-]|%[0-9A-Fa-f]{2})+" s))))

(defn parse-locator
  "Parse an inert OCapN peer or sturdyref URI without dialing it."
  [uri]
  (when-not (capabilities/non-empty-string? uri)
    (throw (ex-info "OCapN locator must be a string"
                    {:problem :captp/locator-invalid})))
  (let [[_ authority swiss query]
        (re-matches #"^ocapn://([^/?#]+)(?:/s/([^/?#]+))?(?:\?([^#]+))?$" uri)
        split-at (when authority (str/last-index-of authority "."))]
    (when-not (and split-at (pos? split-at) (< split-at (dec (count authority))))
      (throw (ex-info "OCapN locator authority is invalid"
                      {:problem :captp/locator-invalid})))
    (let [designator (subs authority 0 split-at)
          transport (subs authority (inc split-at))
          hints (if query
                  (into (sorted-map)
                        (map (fn [part]
                               (let [[k v & more] (str/split part #"=" -1)]
                                 (when (or (str/blank? k) (nil? v) (seq more))
                                   (throw (ex-info "OCapN locator hint invalid"
                                                   {:problem :captp/locator-hint-invalid})))
                                 [k v])))
                        (str/split query #"&"))
                  {})]
      (when-not (and (uri-component? designator)
                     (boolean (re-matches #"[A-Za-z][A-Za-z0-9_-]*"
                                          transport))
                     (or (nil? swiss) (uri-component? swiss))
                     (every? (fn [[k v]]
                               (and (uri-component? k) (uri-component? v)))
                             hints))
        (throw (ex-info "OCapN locator component is invalid"
                        {:problem :captp/locator-component-invalid})))
      (cond-> {:locator/kind (if swiss :sturdyref :peer)
               :locator/transport (symbol transport)
               :locator/designator designator
               :locator/hints hints}
        swiss (assoc :locator/swiss-number swiss)))))

(defn resolve-sturdyref!
  "Resolve inert locator data only through a live host resolver capability.

  The resolver must return an opaque authenticated session and a remote export
  target. Merely parsing or possessing the URI cannot create a live reference."
  [resolve! uri]
  (when-not (fn? resolve!)
    (throw (ex-info "sturdyref resolver capability required"
                    {:problem :captp/locator-resolver-required})))
  (let [locator (parse-locator uri)]
    (when-not (= :sturdyref (:locator/kind locator))
      (throw (ex-info "peer locator does not identify an object"
                      {:problem :captp/sturdyref-required})))
    (let [result (resolve! locator)]
      (when-not (and (map? result)
                     (= #{:session :remote/target} (set (keys result)))
                     (trusted/authenticated-session? (:session result))
                     (= :desc/export (get-in result [:remote/target
                                                     :ocapn/descriptor]))
                     (int? (get-in result [:remote/target :ocapn/position]))
                     (not (neg? (get-in result [:remote/target
                                               :ocapn/position]))))
        (throw (ex-info "sturdyref resolver returned no live target"
                        {:problem :captp/locator-resolution-invalid})))
      result)))
