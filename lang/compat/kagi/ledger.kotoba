(ns kagi.ledger
  "改竄検知される append-only 監査台帳。各 fact を **ハッシュ鎖** で連結し、actor の
  **hybrid 署名(Ed25519 + ML-DSA-65)** を載せる(ADR-2606272330 の台帳要件)。

  entry = fact ＋ {:ledger/seq :ledger/prev-hash :ledger/hash :ledger/sig}
    - hash    = SHA-256(prev-hash ‖ canonical(fact))   ← 1 link でも壊すと以降全滅
    - sig     = hybrid-sign(hash)                       ← {:ed b64 :mldsa b64}（任意）
  canonical 化は sorted-map + pr-str で決定的にする(再計算と byte 一致させるため)。"
  (:require [kagi.crypto :as crypto]
            [kagi.key-registry :as key-registry])
  (:import [java.util Base64]))

(defn- b64 ^String [^bytes b] (.encodeToString (Base64/getEncoder) b))
(defn- unb64 ^bytes [^String s] (.decode (Base64/getDecoder) s))

(def ^:private meta-keys #{:ledger/seq :ledger/prev-hash :ledger/hash :ledger/sig})

(defn- fact-bytes ^bytes [fact]
  ;; 決定的シリアライズ: meta を除き、キー昇順で pr-str。
  (.getBytes (pr-str (into (sorted-map) (apply dissoc fact meta-keys))) "UTF-8"))

(defn- ba ^bytes [& xs] (byte-array (mapcat #(seq (or % (byte-array 0))) xs)))

(defn- link-hash ^bytes [^bytes prev-hash fact]
  (crypto/sha256 (ba prev-hash (fact-bytes fact))))

(defn make-entry
  "現 ledger に fact を継ぐ entry を作る。signer({:ed :mldsa} 秘密 bundle, provider 越し)が
  あれば hash に hybrid 署名する。"
  ([ledger fact provider signer]
   (make-entry ledger fact provider signer nil))
  ([ledger fact provider signer {:keys [key now]}]
   (when key (key-registry/authorize! key :sign now))
   (let [prev      (last ledger)
        prev-h-b64 (:ledger/hash prev)
        prev-h    (when prev-h-b64 (unb64 prev-h-b64))
        h         (link-hash prev-h fact)
        sig       (when signer
                    (let [{:keys [ed mldsa]} (crypto/sign-with provider signer h)]
                      {:ed (b64 ed) :mldsa (b64 mldsa)}))]
    (cond-> (assoc fact :ledger/seq (count ledger)
                   :ledger/prev-hash prev-h-b64
                   :ledger/hash (b64 h))
      sig (assoc :ledger/sig sig)))))

(defn verify-chain
  "ledger 全体を検証 → {:ok? :broken-at}。各 entry で (1)hash 再計算一致 (2)prev-hash 連結
  (3)sig があれば actor の公開鍵で hybrid verify。pub-of: actor-did → {:ed :mldsa} 公開 bundle。
  entry に :ledger/sig が無い場合、pub-of がその actor の公開鍵を解決できなければ
  (= 一度も署名鍵を持たなかった actor)未署名として許容するが、pub-of が鍵を返すなら
  (= 署名できる/した actor)欠落は「元は署名されていたが後から剥がされた」改竄とみなし
  検知する — 単なる欠落有無だけを見ると、entry 内容/hash/prev-hash に一切触れずに
  :ledger/sig だけ dissoc する改竄を素通りさせてしまうため。"
  [ledger provider pub-of]
  (loop [prev-h (byte-array 0) prev-b64 nil i 0 items ledger]
    (if-let [e (first items)]
      (let [h (link-hash prev-h e)
            hash-ok? (= (b64 h) (:ledger/hash e))
            link-ok? (= prev-b64 (:ledger/prev-hash e))
            sig-ok?  (if-let [{:keys [ed mldsa]} (:ledger/sig e)]
                       (let [pub (pub-of (:actor e))]
                         (boolean
                          (and pub
                               (try (crypto/verify* provider pub h
                                                    {:ed (unb64 ed) :mldsa (unb64 mldsa)})
                                    (catch Exception _ false)))))
                       (nil? (pub-of (:actor e))))]
        (if (and hash-ok? link-ok? sig-ok?)
          (recur h (:ledger/hash e) (inc i) (rest items))
          {:ok? false :broken-at i
           :why (cond (not hash-ok?) :hash (not link-ok?) :prev-hash :else :sig)}))
      {:ok? true :broken-at nil})))
