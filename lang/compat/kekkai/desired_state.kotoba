(ns kekkai.desired-state
  "Signed, content-addressed desired state shared by kekkai and its consumers.

  A mutable head is only discovery. Authority is the configured Ed25519 key;
  identity is a CIDv1 of the canonical statement bytes; ordering is a monotonic
  epoch plus the previous CID. Multiple mirrors may carry the same head, while
  equal-epoch disagreement fails closed as split brain."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kekkai.cacao :as cacao]
            [multiformats.core :as mf])
  (:import [java.nio.file Files StandardCopyOption]
           [java.security KeyFactory Signature]
           [java.security.spec X509EncodedKeySpec]
           [java.util Base64 UUID]))

(def schema "kekkai.desired-state/v1")
(def receipt-kind :kekkai/receipt)

(defn- canonical-compare [a b]
  (compare (pr-str a) (pr-str b)))

(defn canonical-value
  "Make unordered EDN collections byte-deterministic without changing their
  collection type. Sequential order remains meaningful."
  [value]
  (cond
    (map? value) (into (sorted-map-by canonical-compare)
                       (map (fn [[k v]] [(canonical-value k) (canonical-value v)]))
                       value)
    (set? value) (into (sorted-set-by canonical-compare) (map canonical-value) value)
    (vector? value) (mapv canonical-value value)
    (list? value) (apply list (map canonical-value value))
    (sequential? value) (doall (map canonical-value value))
    :else value))

(defn canonical-bytes ^bytes [value]
  (binding [*print-namespace-maps* false
            *print-length* nil
            *print-level* nil]
    (.getBytes (pr-str (canonical-value value)) "UTF-8")))

(defn- b64 [^bytes bs]
  (.encodeToString (Base64/getEncoder) bs))

(defn- unb64 ^bytes [s]
  (.decode (Base64/getDecoder) ^String s))

(defn- public-key [spki]
  (.generatePublic (KeyFactory/getInstance "Ed25519")
                   (X509EncodedKeySpec. (unb64 spki))))

(defn- sign ^bytes [private-key ^bytes bytes]
  (let [signer (doto (Signature/getInstance "Ed25519")
                 (.initSign private-key))]
    (.update signer bytes)
    (.sign signer)))

(defn- verify-signature? [public-key* ^bytes bytes signature]
  (let [verifier (doto (Signature/getInstance "Ed25519")
                   (.initVerify public-key*))]
    (.update verifier bytes)
    (.verify verifier (unb64 signature))))

(defn statement
  [{:keys [kind subject epoch previous-cid payload]} name]
  (when-not (keyword? kind)
    (throw (ex-info "desired state kind must be a keyword"
                    {:type :kekkai/invalid-desired-kind})))
  (when-not (and (string? subject) (not (str/blank? subject)))
    (throw (ex-info "desired state subject must be non-empty"
                    {:type :kekkai/invalid-desired-subject})))
  (when-not (and (integer? epoch) (pos? epoch))
    (throw (ex-info "desired state epoch must be a positive integer"
                    {:type :kekkai/invalid-desired-epoch})))
  (when-not (or (nil? previous-cid)
                (and (string? previous-cid) (str/starts-with? previous-cid "b")))
    (throw (ex-info "previous desired state must be a CID or nil"
                    {:type :kekkai/invalid-previous-cid})))
  (let [payload-bytes (canonical-bytes payload)]
    {:desired/schema schema
     :desired/kind kind
     :desired/subject subject
     :desired/name name
     :desired/epoch epoch
     :desired/previous-cid previous-cid
     :desired/payload-cid (mf/cidv1-raw payload-bytes)
     :desired/payload-b64 (b64 payload-bytes)}))

(defn seal
  "Create a signed desired-state envelope. The CID identifies the exact
  statement bytes; it never identifies a mirror URL or mutable head."
  [input {:keys [private-key public-key]}]
  (when-not (and private-key public-key)
    (throw (ex-info "desired state needs an Ed25519 identity"
                    {:type :kekkai/missing-signing-identity})))
  (let [name (cacao/ipns-name public-key)
        stmt (statement input name)
        bytes (canonical-bytes stmt)]
    {:desired/cid (mf/cidv1-raw bytes)
     :desired/statement-b64 (b64 bytes)
     :desired/signature-b64 (b64 (sign private-key bytes))
     :desired/signer-spki-b64 (b64 (.getEncoded public-key))}))

(defn verify
  "Verify signer, CID, signature, key-derived name, payload CID, and optional
  kind/subject/epoch/previous constraints. Returns statement plus :payload."
  ([envelope authority-spki] (verify envelope authority-spki {}))
  ([envelope authority-spki {:keys [kind subject min-epoch previous-cid]}]
   (let [{:desired/keys [cid statement-b64 signature-b64 signer-spki-b64]} envelope]
     (when-not (every? string? [cid statement-b64 signature-b64 signer-spki-b64])
       (throw (ex-info "desired state envelope is incomplete"
                       {:type :kekkai/invalid-desired-envelope})))
     (when-not (= authority-spki signer-spki-b64)
       (throw (ex-info "desired state signer is not the configured authority"
                       {:type :kekkai/untrusted-desired-signer})))
     (let [bytes (unb64 statement-b64)
           pub (public-key signer-spki-b64)]
       (when-not (= cid (mf/cidv1-raw bytes))
         (throw (ex-info "desired state CID mismatch"
                         {:type :kekkai/desired-cid-mismatch})))
       (when-not (verify-signature? pub bytes signature-b64)
         (throw (ex-info "desired state signature invalid"
                         {:type :kekkai/desired-signature-invalid})))
       (let [stmt (edn/read-string (String. bytes "UTF-8"))
             payload-bytes (unb64 (:desired/payload-b64 stmt))
             payload (edn/read-string (String. payload-bytes "UTF-8"))]
         (when-not (= schema (:desired/schema stmt))
           (throw (ex-info "desired state schema mismatch"
                           {:type :kekkai/desired-schema-mismatch})))
         (when-not (= (:desired/name stmt) (cacao/ipns-name pub))
           (throw (ex-info "desired state name is not derived from signer"
                           {:type :kekkai/desired-name-mismatch})))
         (when-not (= (:desired/payload-cid stmt) (mf/cidv1-raw payload-bytes))
           (throw (ex-info "desired state payload CID mismatch"
                           {:type :kekkai/desired-payload-cid-mismatch})))
         (when (and kind (not= kind (:desired/kind stmt)))
           (throw (ex-info "desired state kind mismatch"
                           {:type :kekkai/desired-kind-mismatch})))
         (when (and subject (not= subject (:desired/subject stmt)))
           (throw (ex-info "desired state subject mismatch"
                           {:type :kekkai/desired-subject-mismatch})))
         (when (and min-epoch (< (:desired/epoch stmt) min-epoch))
           (throw (ex-info "desired state epoch rollback"
                           {:type :kekkai/desired-epoch-rollback})))
         (when (and previous-cid
                    (not= previous-cid (:desired/previous-cid stmt)))
           (throw (ex-info "desired state does not extend expected head"
                           {:type :kekkai/desired-chain-mismatch})))
         (assoc stmt :desired/cid cid :desired/payload payload))))))

(defn authority-spki-b64 [identity]
  (b64 (.getEncoded (:public-key identity))))

(defn subject-key [subject]
  (mf/cidv1-raw (.getBytes ^String subject "UTF-8")))

(defn block-file [root cid]
  (io/file root "blocks" (str cid ".edn")))

(defn head-file [root subject]
  (io/file root "heads" (str (subject-key subject) ".edn")))

(defn- remote-root [root]
  (when (and (string? root) (str/starts-with? root "ssh://"))
    (let [[_ host path] (re-matches #"ssh://([^/]+)(/.*)" root)]
      (when-not (and host path
                     (re-matches #"(?:[A-Za-z0-9._-]+@)?[A-Za-z0-9._-]+" host)
                     (re-matches #"[A-Za-z0-9._/-]+" path)
                     (not (str/includes? path "..")))
        (throw (ex-info "invalid ssh desired-state mirror root"
                        {:type :kekkai/invalid-ssh-mirror :root root})))
      {:host host :path (str/replace path #"/+$" "")})))

(defn- relative-block [cid] (str "blocks/" cid ".edn"))
(defn- relative-head [subject] (str "heads/" (subject-key subject) ".edn"))

(declare atomic-spit!)

(defn- ssh-process [host command input]
  (let [process (.start (ProcessBuilder. ^java.util.List ["ssh" host command]))]
    (when (some? input)
      (with-open [out (.getOutputStream process)]
        (.write out (.getBytes ^String input "UTF-8"))))
    (let [stdout (slurp (.getInputStream process))
          stderr (slurp (.getErrorStream process))
          exit (.waitFor process)]
      (when-not (zero? exit)
        (throw (ex-info "ssh desired-state mirror operation failed"
                        {:type :kekkai/ssh-mirror-failed :host host
                         :exit exit :stderr stderr})))
      stdout)))

(defn- read-root [root relative]
  (if-let [{:keys [host path]} (remote-root root)]
    (ssh-process host (str "cat " path "/" relative) nil)
    (slurp (io/file root relative))))

(defn- write-root! [root relative text]
  (if-let [{:keys [host path]} (remote-root root)]
    (let [target (str path "/" relative)
          parent (subs target 0 (str/last-index-of target "/"))
          tmp (str target ".tmp-" (UUID/randomUUID))]
      (ssh-process host (str "mkdir -p " parent " && cat > " tmp " && mv " tmp " " target)
                   text))
    (atomic-spit! (io/file root relative) text)))

(defn- atomic-spit! [file text]
  (let [parent (.getParentFile file)
        _ (.mkdirs parent)
        tmp (java.io.File/createTempFile ".kekkai-" ".tmp" parent)]
    (spit tmp text)
    (try
      (Files/move (.toPath tmp) (.toPath file)
                  (into-array StandardCopyOption
                              [StandardCopyOption/ATOMIC_MOVE
                               StandardCopyOption/REPLACE_EXISTING]))
      (catch java.nio.file.AtomicMoveNotSupportedException _
        (Files/move (.toPath tmp) (.toPath file)
                    (into-array StandardCopyOption
                                [StandardCopyOption/REPLACE_EXISTING]))))))

(defn envelope-string [envelope]
  (String. (canonical-bytes envelope) "UTF-8"))

(defn- current-head [root subject]
  (try
    (edn/read-string (read-root root (relative-head subject)))
    (catch Exception e
      (if (or (instance? java.io.FileNotFoundException e)
              (= :kekkai/ssh-mirror-failed (:type (ex-data e))))
        nil
        (throw e)))))

(defn- assert-head-advance! [old-head new-head]
  (when old-head
    (let [old-epoch (:desired/epoch old-head)
          new-epoch (:desired/epoch new-head)]
      (when (< new-epoch old-epoch)
        (throw (ex-info "desired state head rollback refused"
                        {:type :kekkai/desired-head-rollback
                         :old old-head :new new-head})))
      (when (and (= new-epoch old-epoch)
                 (not= (:desired/cid old-head) (:desired/cid new-head)))
        (throw (ex-info "different desired state already occupies this epoch"
                        {:type :kekkai/desired-head-conflict
                         :old old-head :new new-head}))))))

(defn publish!
  "Write one verified block and mutable head to multiple independent roots.
  Success requires min-copies; each successful copy remains independently
  pullable."
  [roots min-copies envelope authority-spki]
  (when-not (and (integer? min-copies) (pos? min-copies)
                 (<= min-copies (count roots)))
    (throw (ex-info "mirror quorum must be between one and root count"
                    {:type :kekkai/invalid-mirror-quorum
                     :min-copies min-copies :root-count (count roots)})))
  (let [verified (verify envelope authority-spki)
        head {:desired/cid (:desired/cid verified)
              :desired/name (:desired/name verified)
              :desired/subject (:desired/subject verified)
              :desired/epoch (:desired/epoch verified)}
        results (mapv (fn [root]
                        (try
                          (assert-head-advance!
                           (current-head root (:desired/subject verified)) head)
                          (write-root! root (relative-block (:desired/cid verified))
                                       (envelope-string envelope))
                          (write-root! root (relative-head (:desired/subject verified))
                                       (String. (canonical-bytes head) "UTF-8"))
                          {:root root :ok? true}
                          (catch Exception e
                            {:root root :ok? false :error (.getMessage e)
                             :error-type (:type (ex-data e))})))
                      roots)
        copies (count (filter :ok? results))]
    (when (< copies min-copies)
      (throw (ex-info "desired state did not reach mirror quorum"
                      {:type :kekkai/desired-mirror-quorum
                       :required min-copies :copies copies :results results})))
    {:desired/cid (:desired/cid verified)
     :desired/epoch (:desired/epoch verified)
     :desired/name (:desired/name verified)
     :desired/copies copies
     :desired/results results}))

(defn pull
  "Read all available mirror heads, reject equal-epoch disagreement, then
  verify the highest epoch block against the pinned authority."
  [roots subject authority-spki opts]
  (let [heads (keep (fn [root]
                      (try
                        (let [head (edn/read-string (read-root root (relative-head subject)))]
                          (assoc head :root root))
                        (catch Exception _ nil)))
                    roots)]
    (when-not (seq heads)
      (throw (ex-info "no desired state mirror is readable"
                      {:type :kekkai/desired-unavailable :subject subject})))
    (let [epoch (apply max (map :desired/epoch heads))
          latest (filter #(= epoch (:desired/epoch %)) heads)
          cids (set (map :desired/cid latest))]
      (when-not (= 1 (count cids))
        (throw (ex-info "desired state mirrors disagree at the same epoch"
                        {:type :kekkai/desired-split-brain
                         :epoch epoch :cids cids})))
      (let [cid (first cids)
            candidates (keep (fn [{:keys [root]}]
                               (try
                                 (edn/read-string (read-root root (relative-block cid)))
                                 (catch Exception _ nil)))
                             latest)
            envelope (first candidates)]
        (when-not envelope
          (throw (ex-info "desired state head has no readable block"
                          {:type :kekkai/desired-block-unavailable :cid cid})))
        (verify envelope authority-spki
                (merge {:subject subject :min-epoch epoch} opts))))))

(defn receipt
  "A node receipt is itself signed/content-addressed desired-state data, so a
  collector cannot forge which node observed which desired CID."
  [{:keys [node desired-cid epoch status observed-at detail]} identity]
  (seal {:kind receipt-kind
         :subject (str desired-cid "@" node)
         :epoch epoch
         :previous-cid nil
         :payload {:receipt/node node
                   :receipt/desired-cid desired-cid
                   :receipt/status status
                   :receipt/observed-at observed-at
                   :receipt/detail detail}}
        identity))
