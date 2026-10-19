(ns kotobase.live-blocks
  "Opt-in acceptance for `kotobase.blocks` against the real `/ipld/` plane.

  The claim under test is a division of labour: the server stores bytes named
  by their own hash, and the client decides for itself whether what came back
  is what it asked for. So the run contributes one small block, reads it back
  through the ordinary public route, and re-derives the address from the bytes
  — the read half needs no credential at all, which is the half that matters.

  ## Why it contributes instead of reading something that is already there

  It first read a long-lived block from the archive plane
  (`ipfs.kotobase.net/ipfs/<cid>`). That plane answered 200 at 16:44 on
  2026-09-06 and 502 forty minutes later, so an acceptance built on it
  reports the archive plane's weather rather than this library's behaviour.
  A block this run wrote is a block this run can read.

  ## The negative cases are the acceptance

  A script that only wrote and read a block would pass against a server that
  echoes whatever it is handed. Four of the six checks are negative, and each
  is pinned to its own cause rather than to \"something failed\":

  - real bytes must be REFUSED under a foreign address
  - substituted bytes must be refused under the real address
  - an unstored address must read as absent, not as empty bytes
  - `GET /ipld/<cid>` must not be method-gated — a 405 there is exactly the
    state this surface was in until 2026-09-06, reachable and answering the
    wrong question

  It also refuses to report a pass it did not earn: fewer completed checks
  than expected exits non-zero instead of printing PASS on a short run."
  (:require ["@noble/curves/ed25519.js" :refer [ed25519]]
            [sha2.core :as sha2]
            [kotobase.cid :as cid]
            [kotobase.cacao :as cacao]
            [kotobase.blocks :as blocks]))

(def ^:private endpoint "https://kotobase.net")
(def ^:private operator-did "did:web:kotobase.net")
(def ^:private expected-checks 6)

(defn- cid-of
  "The CIDv1/raw/sha2-256 address of `bytes`, built with this library's own
   encoder — a derived address, not a copied one."
  [^js bytes]
  (let [digest (sha2/sha256 (vec (js/Array.from bytes)))
        header (js/Uint8Array. 36)]
    (aset header 0 0x01) (aset header 1 0x55)
    (aset header 2 0x12) (aset header 3 0x20)
    (dotimes [i 32] (aset header (+ 4 i) (nth digest i)))
    (str "b" (cid/base32-lower-no-pad header))))

(defn- utf8 [s] (.encode (js/TextEncoder.) s))

(defn- same? [^js a ^js b]
  (= (vec (js/Array.from a)) (vec (js/Array.from b))))

(defn- run-checks []
  (let [done (atom [])
        note (fn [label] (swap! done conj label) (js/console.log "  ok  " label))
        secret (.randomPrivateKey (.-utils ed25519))
        payload (utf8 (str "kotobase.blocks live acceptance " (.toISOString (js/Date.))))
        block-cid (cid-of payload)
        ;; An address nobody stored. Derived rather than mangled, so it stays
        ;; a well-formed CID and a 404 means "no such block", not "bad request".
        absent (cid-of (utf8 (str "absent probe " (.now js/Date))))
        auth (str "CACAO "
                  (:cacao-b64
                   (cacao/mint-cacao {:secret-key secret
                                      :aud operator-did
                                      :capability "kotobase:pin"
                                      :graph "kotobase/live-blocks"
                                      :statement "kotobase.blocks live acceptance"
                                      :ttl-sec 300})))
        {:keys [get-block put-block!]} (blocks/client {:endpoint endpoint})]
    (js/console.log (str "contributing " (.-length payload) " bytes as " block-cid))
    (-> (put-block! block-cid payload auth)
        (.then (fn [returned]
                 (when-not (= block-cid returned)
                   (throw (js/Error. (str "PUT reported a different CID: " returned))))
                 (note "PUT /ipld/<cid> accepted a self-signed contribution")))
        (.then
         (fn [_]
           (-> (get-block block-cid)
               (.then (fn [bytes]
                        (when-not bytes
                          (throw (js/Error. "block read back as absent immediately after PUT")))
                        (when-not (same? payload bytes)
                          (throw (js/Error. "read back different bytes than were written")))
                        (note (str "GET /ipld/<cid> returned the same " (.-length bytes)
                                   " bytes, verified against the address"))
                        bytes)))))
        (.then
         (fn [bytes]
           (when (blocks/verify-block absent bytes)
             (throw (js/Error. "verify-block accepted real bytes under a foreign CID")))
           (note "real bytes are refused under a different address")))
        (.then
         (fn [_]
           (when (blocks/verify-block block-cid (js/Uint8Array.from #js [1 2 3]))
             (throw (js/Error. "verify-block accepted substituted bytes")))
           (note "substituted bytes are refused under the real address")))
        (.then
         (fn [_]
           (-> (get-block absent)
               (.then (fn [bytes]
                        (when-not (nil? bytes)
                          (throw (js/Error. "the plane invented bytes for an unstored CID")))
                        (note "an unstored address reads as absent, not as empty bytes"))))))
        (.then
         (fn [_]
           (-> (js/fetch (str endpoint "/ipld/" absent) #js {:method "GET"})
               (.then (fn [^js resp]
                        (case (.-status resp)
                          405 (throw (js/Error. "GET /ipld/<cid> is method-gated — the block read is not live"))
                          404 (note "GET /ipld/<cid> is a live read, not a 405")
                          (throw (js/Error. (str "unexpected status for an absent block: "
                                                 (.-status resp))))))))))
        (.then
         (fn [_]
           (let [n (count @done)]
             (if (= expected-checks n)
               (js/console.log (str "PASS: " n " live block checks against " endpoint))
               (do (js/console.error
                    (str "REFUSING to report a pass: " n " of " expected-checks
                         " checks completed"))
                   (set! (.-exitCode js/process) 1))))))
        (.catch
         (fn [error]
           (js/console.error "kotobase.blocks live acceptance FAILED:" (str error))
           (js/console.error (str "  completed before failure: " (count @done)))
           (set! (.-exitCode js/process) 1))))))

(defn main []
  (if-not (= "1" (.. js/process -env -KOTOBASE_LIVE_BLOCKS))
    (js/console.log "SKIP: set KOTOBASE_LIVE_BLOCKS=1 to read and write the real plane")
    (run-checks)))
