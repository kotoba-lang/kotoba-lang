(ns kagi.crypto.noble
  "kagi `Provider` のブラウザ実装 — **同期**、純 JS、Rust/WASM なし。

  `kagi.crypto/jvm-provider` と同じ hybrid 構成(X25519+ML-KEM-768 / Ed25519+ML-DSA-65 /
  AES-256-GCM / HKDF-SHA256 / Argon2id)を `@noble/*` で満たす。

  ## なぜ Web Crypto ではないか

  `kotoba-lang/org-signal` は JVM と CLJS を別ファイルに分け、その理由を
  「Web Crypto に同期 API が無いので、片方を無駄に async にするか CLJS に嘘の
  同期 API を与えるかの二択になる」と記録している。`@noble/*` は純 JS の**同期**
  実装なので、その二択自体が発生しない —— だから kagi は `Provider` を同期のまま
  1 つのプロトコルで通せる。Web Crypto は AES-GCM しか使わないのに Promise を
  全体に伝播させるので、ここでは採らない。

  ML-KEM-768(FIPS 203) と ML-DSA-65(FIPS 204) は `@noble/post-quantum` にあるため、
  ブラウザでも **hybrid を古典のみに縮退させない**。

  ## 鍵の符号化 —— DER prefix の付け外し

  JVM 側は JCA が返す X.509(SPKI) / PKCS#8 の **DER 符号化**バイト列をそのまま
  graph に載せる。noble は raw を扱う。kagi の KEM combiner はその符号化済み
  バイト列を transcript にハッシュするので(`kagi.crypto/combine-kem-shared`)、
  **符号化が一致しない限り runtime を跨いで同じ共有秘密にならない。**

  鍵長がすべて固定なので、DER prefix も 6 種すべて固定長定数になる。ここでは
  その定数を付け外しし、剥がす時は prefix 一致を検証して合わなければ throw する
  (fail closed)。定数は JDK 24 の実出力から採取し、`noble-interop-test` が
  JVM 生成の実ベクタで往復を検証する。"
  (:require [kagi.crypto :as c]
            ["@noble/ciphers/aes.js" :refer [gcm]]
            ["@noble/curves/ed25519.js" :refer [ed25519 x25519]]
            ;; `:as` であって `:refer` ではない —— `Provider` の method 名が
            ;; `argon2id` なので、refer するとメソッド本体の中で同名の import を
            ;; 呼んでいるのか自分を呼んでいるのか読めなくなる。
            ["@noble/hashes/argon2.js" :as argon2]
            ["@noble/hashes/hkdf.js" :refer [hkdf]]
            ["@noble/hashes/sha2.js" :refer [sha256]]
            ["@noble/post-quantum/ml-dsa.js" :refer [ml_dsa65]]
            ["@noble/post-quantum/ml-kem.js" :refer [ml_kem768]]))

;; ───────── DER prefix ─────────

(defn- hex->bytes [s]
  (let [n (/ (count s) 2)
        out (js/Uint8Array. n)]
    (dotimes [i n]
      (aset out i (js/parseInt (subs s (* 2 i) (+ 2 (* 2 i))) 16)))
    out))

(def ^:private der
  "JDK 24 が実際に出力した符号化から採取した固定 prefix と、その後に続く raw 鍵長。"
  {:x25519-spki    {:prefix (hex->bytes "302a300506032b656e032100") :raw 32}
   :x25519-pkcs8   {:prefix (hex->bytes "302e020100300506032b656e04220420") :raw 32}
   :ed25519-spki   {:prefix (hex->bytes "302a300506032b6570032100") :raw 32}
   :ed25519-pkcs8  {:prefix (hex->bytes "302e020100300506032b657004220420") :raw 32}
   :mlkem768-spki  {:prefix (hex->bytes "308204b2300b0609608648016503040402038204a100") :raw 1184}
   :mlkem768-pkcs8 {:prefix (hex->bytes "30820978020100300b06096086480165030404020482096404820960") :raw 2400}
   :mldsa65-spki   {:prefix (hex->bytes "308207b2300b0609608648016503040312038207a100") :raw 1952}
   :mldsa65-pkcs8  {:prefix (hex->bytes "30820fd8020100300b060960864801650304031204820fc404820fc0") :raw 4032}})

(defn wrap-der
  "raw 鍵 → JVM と同じ DER 符号化。"
  [kind raw]
  (let [{:keys [prefix]} (get der kind)]
    (when-not prefix
      (throw (ex-info "unknown DER key kind" {:kind kind})))
    (c/concat-bytes [prefix raw])))

(defn unwrap-der
  "DER 符号化 → raw 鍵。長さと prefix が一致しなければ throw(fail closed)。"
  [kind encoded]
  (let [{:keys [prefix raw]} (get der kind)
        _ (when-not prefix (throw (ex-info "unknown DER key kind" {:kind kind})))
        plen (.-length prefix)
        expected (+ plen raw)]
    (when-not (= expected (.-length encoded))
      (throw (ex-info "unexpected encoded key length"
                      {:kind kind :expected expected :actual (.-length encoded)})))
    (dotimes [i plen]
      (when-not (= (aget prefix i) (aget encoded i))
        (throw (ex-info "encoded key does not carry the expected DER prefix"
                        {:kind kind :offset i}))))
    (.slice encoded plen)))

;; ───────── プリミティブ ─────────

(defn- sha256* [b] (sha256 b))

(defn- hkdf-sha256
  "RFC 5869。kagi JVM 実装と同じく、空 salt は 32 バイトのゼロに置き換える
  (noble にそのまま空を渡すと 0 長 HMAC 鍵になり JVM と食い違う)。"
  [ikm salt info len]
  (when-not (and (int? len) (pos? len) (<= len (* 255 32)))
    (throw (ex-info "invalid HKDF-SHA256 output length"
                    {:length len :maximum (* 255 32)})))
  (hkdf sha256
        ikm
        (if (zero? (.-length salt)) (js/Uint8Array. 32) salt)
        info
        len))

(defn- aes-gcm [key nonce aad]
  (when-not (= 32 (.-length key))
    (throw (ex-info "AES-256-GCM requires a 32-byte key" {:length (.-length key)})))
  (when-not (= 12 (.-length nonce))
    (throw (ex-info "AES-GCM requires a 96-bit nonce" {:length (.-length nonce)})))
  (gcm key nonce aad))

;; ───────── provider ─────────

(defn noble-provider
  "@noble/* だけで `kagi.crypto/Provider` を満たす同期 provider。"
  []
  (reify c/Provider
    (rand-bytes [_ n]
      (js/crypto.getRandomValues (js/Uint8Array. n)))

    (aead-seal [_ key nonce pt aad] (.encrypt (aes-gcm key nonce aad) pt))
    (aead-open [_ key nonce ct aad] (.decrypt (aes-gcm key nonce aad) ct))
    (hkdf [_ ikm salt info len] (hkdf-sha256 ikm salt info len))

    ;; --- hybrid KEM: X25519(ECDH) + ML-KEM-768 ---
    (kem-keypair [_]
      (let [xk (.keygen x25519)
            pk (.keygen ml_kem768)
            x-pub  (wrap-der :x25519-spki (.-publicKey xk))
            pq-pub (wrap-der :mlkem768-spki (.-publicKey pk))]
        {:public {:x x-pub :pq pq-pub}
         :secret {:x (wrap-der :x25519-pkcs8 (.-secretKey xk))
                  :pq (wrap-der :mlkem768-pkcs8 (.-secretKey pk))
                  :x-pub x-pub :pq-pub pq-pub}}))

    (kem-encap [_ {:keys [x pq]}]
      (let [x-raw   (unwrap-der :x25519-spki x)
            pq-raw  (unwrap-der :mlkem768-spki pq)
            eph     (.keygen x25519)
            eph-pub (wrap-der :x25519-spki (.-publicKey eph))
            ss-x    (.getSharedSecret x25519 (.-secretKey eph) x-raw)
            encapsulated (.encapsulate ml_kem768 pq-raw)
            ss-pq   (.-sharedSecret encapsulated)
            ct-pq   (.-cipherText encapsulated)]
        (try
          {:ciphertext {:x eph-pub :pq ct-pq}
           :shared (c/combine-kem-shared sha256* hkdf-sha256 ss-x ss-pq
                                         x eph-pub pq ct-pq)}
          (finally
            (c/burn! ss-x)
            (c/burn! ss-pq)))))

    (kem-decap [_ {:keys [x pq x-pub pq-pub]} {ct-x :x ct-pq :pq}]
      (let [x-priv  (unwrap-der :x25519-pkcs8 x)
            pq-priv (unwrap-der :mlkem768-pkcs8 pq)
            eph-raw (unwrap-der :x25519-spki ct-x)
            ss-x    (.getSharedSecret x25519 x-priv eph-raw)
            ss-pq   (.decapsulate ml_kem768 ct-pq pq-priv)]
        (try
          (c/combine-kem-shared sha256* hkdf-sha256 ss-x ss-pq
                                x-pub ct-x pq-pub ct-pq)
          (finally
            (c/burn! ss-x)
            (c/burn! ss-pq)))))

    ;; --- hybrid 署名: Ed25519 + ML-DSA-65 ---
    (sign-keypair [_]
      (let [ed (.keygen ed25519)
            ml (.keygen ml_dsa65)]
        {:public {:ed    (wrap-der :ed25519-spki (.-publicKey ed))
                  :mldsa (wrap-der :mldsa65-spki (.-publicKey ml))}
         :secret {:ed    (wrap-der :ed25519-pkcs8 (.-secretKey ed))
                  :mldsa (wrap-der :mldsa65-pkcs8 (.-secretKey ml))}}))

    (sign* [_ {:keys [ed mldsa]} msg]
      {:ed    (.sign ed25519 msg (unwrap-der :ed25519-pkcs8 ed))
       :mldsa (.sign ml_dsa65 msg (unwrap-der :mldsa65-pkcs8 mldsa))})

    (verify* [_ {:keys [ed mldsa]} msg sig]
      (and (.verify ed25519 (:ed sig) msg (unwrap-der :ed25519-spki ed))
           (.verify ml_dsa65 (:mldsa sig) msg (unwrap-der :mldsa65-spki mldsa))))

    ;; --- real Argon2id; no silent downgrade ---
    (argon2id [_ pass salt {:keys [m-kb t p] :or {m-kb 262144 t 3 p 4}}]
      (when (or (< m-kb 8192) (< t 1) (< p 1))
        (throw (ex-info "unsafe Argon2id parameters"
                        {:m-kb m-kb :t t :p p :minimum {:m-kb 8192 :t 1 :p 1}})))
      (argon2/argon2id pass salt #js {:t t :m m-kb :p p :dkLen 32}))))
