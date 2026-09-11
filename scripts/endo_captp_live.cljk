(ns endo-captp-live
  (:require [kotoba.lang.captp-runtime :as captp]
            [kotoba.lang.incidence :as incidence]
            [kotoba.lang.trusted-admission :as trusted]))

(defonce ^:private immutable-bytes->u8 (atom nil))

(defn- u8 [bytes]
  (if (instance? js/Uint8Array bytes)
    bytes
    ((deref immutable-bytes->u8) bytes)))

(defn- immutable [bytes]
  (let [copy (js/Uint8Array. (.-length bytes))]
    (.set copy bytes)
    (.-buffer copy)))

(defn- public-key-component [public-key]
  ['public-key
   ['ecc ['curve 'Ed25519] ['flags 'eddsa] ['q (u8 (.-bytes public-key))]]])

(defn- public-key-q [component]
  (let [[_ [_ _ _ [_ q]]] component] q))

(defn- signature-component [signature]
  ['sig-val ['eddsa ['r (u8 (.-r signature))] ['s (u8 (.-s signature))]]])

(defn- signature-object [component]
  (let [[_ [_ [_ r] [_ s]]] component]
    #js {:type "sig-val" :scheme "eddsa"
         :r (immutable r) :s (immutable s)}))

(defn- location-record [location]
  (captp/syrup-record
   'ocapn-peer
   [(symbol (.-transport location))
    (.-designator location)
    (if (false? (.-hints location))
      false
      (js->clj (.-hints location)))]))

(defn- location-object [record]
  (let [[transport designator hints] (:syrup/fields record)]
    #js {:type "ocapn-peer"
         :transport (str transport)
         :designator designator
         :hints (if (false? hints) false (clj->js hints))}))

(defn- hex [bytes]
  (apply str (map #(let [x (.toString % 16)]
                     (if (= 1 (count x)) (str "0" x) x))
                  (u8 bytes))))

(defn- framed [payload]
  (let [prefix (.encode (js/TextEncoder.) (str (.-length payload) ":"))
        out (js/Uint8Array. (+ (.-length prefix) (.-length payload)))]
    (.set out prefix 0)
    (.set out payload (.-length prefix))
    out))

(defn- deframer [on-frame]
  (let [buffer (atom (js/Uint8Array. 0))]
    (fn [chunk]
      (let [incoming (u8 chunk)
            combined (js/Uint8Array. (+ (.-length @buffer)
                                        (.-length incoming)))]
        (.set combined @buffer 0)
        (.set combined incoming (.-length @buffer))
        (reset! buffer combined)
        (loop []
          (let [colon (first (filter #(= 58 (aget @buffer %))
                                     (range (.-length @buffer))))]
            (when colon
              (let [length-text (.decode (js/TextDecoder.)
                                         (.slice @buffer 0 colon))
                    length (js/Number length-text)
                    end (+ colon 1 length)]
                (when (<= end (.-length @buffer))
                  (let [payload (.slice @buffer (inc colon) end)]
                    (reset! buffer (.slice @buffer end))
                    (on-frame payload)
                    (recur)))))))))))

(defn- assert! [condition message data]
  (when-not condition (throw (ex-info message data))))

(defn -main []
  (-> (js/import "@endo/init")
      (.then
       (fn [_]
         (js/Promise.all
          #js [(js/import "node:net")
               (js/import "../node_modules/@endo/ocapn/src/cryptography.js")
               (js/import "@endo/ocapn")
               (js/import "@endo/ocapn/netlayer/tcp-testing")
               (js/import "@endo/bytes/from-immutable.js")])))
      (.then
       (fn [modules]
         (let [net-module (aget modules 0)
               crypto (aget modules 1)
               ocapn (aget modules 2)
               tcp (aget modules 3)
               bytes-module (aget modules 4)
               _ (reset! immutable-bytes->u8
                         (.-bytesFromImmutable bytes-module))
               local-keypair
               (.makeOcapnKeyPairFromPrivateKey
                crypto (js/Uint8Array. (clj->js (repeat 32 41))))
               runtime (atom nil)
               socket-holder (atom nil)
               server-holder (atom nil)
               closed (atom false)
               server (.createServer
                       (.-default net-module)
                       (fn [socket]
                         (reset! socket-holder socket)
                         (let [handle-frame
                               (fn [payload]
                                 (if @runtime
                                   (captp/receive! @runtime payload)
                                   (let [remote-start (captp/syrup-decode payload)
                                         [_ remote-key remote-location
                                          remote-signature]
                                         (:syrup/fields remote-start)
                                         address (.address @server-holder)
                                         local-location
                                         #js {:type "ocapn-peer"
                                              :transport "tcp-testing-only"
                                              :designator "kotoba-live"
                                              :hints #js {:host "127.0.0.1"
                                                          :port (str (.-port address))}}
                                         local-signature
                                         (.signLocation crypto local-location
                                                        local-keypair)
                                         local-start
                                         (captp/start-session-frame
                                          (public-key-component
                                           (.-publicKey local-keypair))
                                          (location-record local-location)
                                          (signature-component local-signature))
                                         registry (captp/session-registry)
                                         opened
                                         (captp/open-session!
                                          {:verify!
                                           (fn [{:keys [handshake local-start
                                                       remote-start]}]
                                             (assert! (= :tcp-testing-only handshake)
                                                      "wrong netlayer" {})
                                             (doseq [start [local-start remote-start]]
                                               (let [[version key location signature]
                                                     (:syrup/fields start)
                                                     public
                                                     (.makeOcapnPublicKey
                                                      crypto
                                                      (immutable
                                                       (public-key-q key)))]
                                                 (assert! (= trusted/captp-version
                                                             version)
                                                          "wrong CapTP version" {})
                                                 (.assertLocationSignatureValid
                                                  crypto (location-object location)
                                                  (signature-object signature)
                                                  public)))
                                             (let [remote-public
                                                   (.makeOcapnPublicKey
                                                    crypto
                                                    (immutable
                                                     (public-key-q remote-key)))
                                                   session-id
                                                   (.makeSessionId
                                                    crypto
                                                    (.. local-keypair -publicKey -id)
                                                    (.-id remote-public))
                                                   peer
                                                   (incidence/typed-ref
                                                    :did
                                                    (str "did:key:endo-"
                                                         (subs (hex (.-id remote-public))
                                                               0 24)))
                                                   transcript
                                                   (incidence/incidence
                                                    :session/transcript
                                                    {:session/peer #{peer}}
                                                    {})]
                                               {:session/valid? true
                                                :session/id (hex session-id)
                                                :session/version trusted/captp-version
                                                :session/peer peer
                                                :session/transcript-cid
                                                (incidence/incidence-cid transcript)}))
                                           :handshake :tcp-testing-only
                                           :local-start local-start
                                           :remote-start remote-start
                                           :registry registry
                                           :write-frame!
                                           (fn [encoded]
                                             (.write socket (framed encoded))
                                             {:netlayer/accepted? true
                                              :netlayer/message-id "tcp-live"})})]
                                     (reset! runtime opened))))]
                           (.on socket "data" (deframer handle-frame))
                           (.on socket "close" #(reset! closed true)))))
               server-ready (reset! server-holder server)
               listening
               (js/Promise.
                (fn [resolve reject]
                  (.once server "error" reject)
                  (.listen server 0 "127.0.0.1" resolve)))]
           (.then
            listening
            (fn []
              (let [address (.address server)
                    remote-location
                    #js {:type "ocapn-peer"
                         :transport "tcp-testing-only"
                         :designator "kotoba-live"
                         :hints #js {:host "127.0.0.1"
                                     :port (str (.-port address))}}
                    client (.makeClient ocapn #js {:debugLabel "endo-live"})]
                (-> (.registerNetlayer
                     client
                     (fn [handlers logger]
                       (.makeTcpNetLayer
                        tcp #js {:handlers handlers :logger logger
                                 :specifiedPort 0
                                 :specifiedDesignator "endo-live"
                                 :framing "syrup"})))
                    (.then
                     (fn [netlayer]
                       (-> (.provideSession client remote-location)
                           (.then
                            (fn [session]
                              (assert! (= :active
                                          (:captp/phase
                                           (captp/runtime-description @runtime)))
                                       "Kotoba runtime did not become active" {})
                              ;; Endo's public Session.abort is a local unplug;
                              ;; make Kotoba put op:abort on the actual wire so
                              ;; Endo must decode it and close the connection.
                              (captp/abort! @runtime "interop-complete")
                              (-> (js/Promise.
                                   (fn [resolve _]
                                     (js/setTimeout resolve 50)))
                                  (.then
                                   (fn []
                                     (assert! (= :aborted
                                                 (:captp/phase
                                                  (captp/runtime-description
                                                   @runtime)))
                                              "Kotoba abort did not commit" {})
                                     (assert! @closed
                                              "Endo did not close after wire abort"
                                              {})
                                     (.shutdown netlayer)
                                     (.close server)
                                     (assert! @socket-holder
                                              "no physical TCP socket was accepted"
                                              {})
                                     (println
                                      "Endo 1.1.1 <-> Kotoba live CapTP TCP session passed")))))))))))))))
           )
      (.catch
       (fn [error]
         (js/console.error error)
         (set! (.-exitCode js/process) 1)))))

(-main)
