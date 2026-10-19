(ns paillier.core
  "Paillier additive homomorphic encryption for exact integer linear algebra.

  This JVM library evaluates plaintext-weight matrix/vector products over
  encrypted activations:

      Enc(Wx + b) = Enc(b) * product(Enc(x_i)^W_i)

  Paillier is additive/linear homomorphic encryption (PHE), not FHE. It cannot
  evaluate ciphertext-ciphertext products or Transformer nonlinearities. JVM
  BigInteger is not claimed constant-time."
  (:import (java.lang Integer)
           (java.math BigInteger)
           (java.nio.charset StandardCharsets)
           (java.security MessageDigest SecureRandom)))

(def ^:private zero BigInteger/ZERO)
(def ^:private one BigInteger/ONE)
(def ^:private two (BigInteger/valueOf 2))
(def ^:private production-key-bits 2048)

(defrecord PublicKey [n n-squared g bits key-id])
(defrecord PrivateKey [public-key lambda mu])
(defrecord Ciphertext [key-id value])

(defn public-key? [value] (instance? PublicKey value))
(defn private-key? [value] (instance? PrivateKey value))
(defn ciphertext? [value] (instance? Ciphertext value))

(defn- ->big-integer ^BigInteger [value]
  (cond
    (instance? BigInteger value) value
    (integer? value) (BigInteger. (str value))
    (string? value) (BigInteger. ^String value)
    :else (throw (ex-info "expected an integer" {:value value}))))

(defn- byte->hex [value]
  (let [hex (Integer/toHexString (bit-and 0xff value))]
    (if (= 1 (count hex)) (str "0" hex) hex)))

(defn- sha256-hex [^String value]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes value StandardCharsets/UTF_8))]
    (apply str (map byte->hex digest))))

(defn- key-id [^BigInteger n]
  (subs (sha256-hex (.toString n 16)) 0 32))

(defn- public-key [^BigInteger n bits]
  (->PublicKey n (.multiply n n) (.add n one) bits (key-id n)))

(defn- lcm ^BigInteger [^BigInteger a ^BigInteger b]
  (.divide (.multiply a b) (.gcd a b)))

(defn generate-keypair
  "Generate a Paillier keypair using `SecureRandom`.

  The default and production minimum are 2048 bits.  Smaller keys are accepted
  only with `:allow-insecure-test-key? true`, making an insecure benchmark key
  an explicit call-site decision rather than a silent production footgun."
  ([] (generate-keypair production-key-bits {}))
  ([bits] (generate-keypair bits {}))
  ([bits {:keys [allow-insecure-test-key? random]
          :or {allow-insecure-test-key? false}}]
   (when-not (and (integer? bits) (>= bits 256))
     (throw (ex-info "Paillier key size must be at least 256 bits"
                     {:bits bits})))
   (when (and (< bits production-key-bits) (not allow-insecure-test-key?))
     (throw (ex-info "Paillier production keys must be at least 2048 bits"
                     {:bits bits :minimum production-key-bits})))
   (let [rng (or random (SecureRandom.))
         p-bits (quot bits 2)
         q-bits (- bits p-bits)]
     (loop []
       (let [p (BigInteger/probablePrime p-bits rng)
             q (BigInteger/probablePrime q-bits rng)]
         (if (= p q)
           (recur)
           (let [n (.multiply p q)
                 pk (public-key n bits)
                 lambda (lcm (.subtract p one) (.subtract q one))
                 u (.modPow ^BigInteger (:g pk) lambda ^BigInteger (:n-squared pk))
                 l-value (.divide (.subtract u one) n)]
             ;; A rare unsuitable prime pair makes L(g^lambda) non-invertible.
             (if (not= one (.gcd l-value n))
               (recur)
               {:public-key pk
                :private-key (->PrivateKey pk lambda (.modInverse l-value n))}))))))))

(defn- signed-limit ^BigInteger [^PublicKey pk]
  (.divide ^BigInteger (:n pk) two))

(defn- validate-plaintext! ^BigInteger [^PublicKey pk value]
  (let [m (->big-integer value)
        limit (signed-limit pk)]
    (when (>= (.compareTo (.abs m) limit) 0)
      (throw (ex-info "Paillier signed plaintext is outside the unambiguous range"
                      {:key-id (:key-id pk)
                       :absolute-value (.toString (.abs m))
                       :exclusive-limit (.toString limit)})))
    m))

(defn- encode ^BigInteger [^PublicKey pk value]
  (.mod (validate-plaintext! pk value) ^BigInteger (:n pk)))

(defn- random-coprime ^BigInteger [^PublicKey pk ^SecureRandom rng]
  (let [n ^BigInteger (:n pk)]
    (loop []
      (let [candidate (BigInteger. (.bitLength n) rng)]
        (if (and (pos? (.signum candidate))
                 (neg? (.compareTo candidate n))
                 (= one (.gcd candidate n)))
          candidate
          (recur))))))

(defn- assert-public-key! [pk]
  (when-not (public-key? pk)
    (throw (ex-info "expected a Paillier public key" {:value-type (type pk)})))
  pk)

(defn- assert-ciphertext! [^PublicKey pk ciphertext]
  (when-not (and (ciphertext? ciphertext)
                 (= (:key-id pk) (:key-id ciphertext)))
    (throw (ex-info "ciphertext does not belong to this Paillier key"
                    {:expected-key-id (:key-id pk)
                     :actual-key-id (:key-id ciphertext)})))
  ciphertext)

(defn encrypt
  "Encrypt one signed integer with fresh randomness and the public key."
  ([pk plaintext] (encrypt pk plaintext {}))
  ([pk plaintext {:keys [random]}]
   (let [pk ^PublicKey (assert-public-key! pk)
         rng ^SecureRandom (or random (SecureRandom.))
         m (encode pk plaintext)
         r (random-coprime pk rng)
         n ^BigInteger (:n pk)
         n-squared ^BigInteger (:n-squared pk)
         value (-> (.modPow ^BigInteger (:g pk) m n-squared)
                   (.multiply (.modPow r n n-squared))
                   (.mod n-squared))]
     (->Ciphertext (:key-id pk) value))))

(defn decrypt
  "Decrypt one ciphertext and return an exact signed `BigInteger`."
  [private-key ciphertext]
  (when-not (private-key? private-key)
    (throw (ex-info "expected a Paillier private key"
                    {:value-type (type private-key)})))
  (let [pk ^PublicKey (:public-key private-key)
        ciphertext ^Ciphertext (assert-ciphertext! pk ciphertext)
        n ^BigInteger (:n pk)
        n-squared ^BigInteger (:n-squared pk)
        u (.modPow ^BigInteger (:value ciphertext)
                   ^BigInteger (:lambda private-key)
                   n-squared)
        l-value (.divide (.subtract u one) n)
        unsigned (.mod (.multiply l-value ^BigInteger (:mu private-key)) n)]
    (if (pos? (.compareTo unsigned (signed-limit pk)))
      (.subtract unsigned n)
      unsigned)))

(defn add
  "Homomorphically add two ciphertext plaintexts."
  [pk left right]
  (let [pk ^PublicKey (assert-public-key! pk)
        left ^Ciphertext (assert-ciphertext! pk left)
        right ^Ciphertext (assert-ciphertext! pk right)]
    (->Ciphertext (:key-id pk)
                  (.mod (.multiply ^BigInteger (:value left)
                                   ^BigInteger (:value right))
                        ^BigInteger (:n-squared pk)))))

(defn add-plaintext
  "Homomorphically add a public signed integer to a ciphertext."
  [pk ciphertext plaintext]
  (let [pk ^PublicKey (assert-public-key! pk)
        ciphertext ^Ciphertext (assert-ciphertext! pk ciphertext)
        factor (.modPow ^BigInteger (:g pk)
                        (encode pk plaintext)
                        ^BigInteger (:n-squared pk))]
    (->Ciphertext (:key-id pk)
                  (.mod (.multiply ^BigInteger (:value ciphertext) factor)
                        ^BigInteger (:n-squared pk)))))

(defn scale
  "Homomorphically multiply a ciphertext plaintext by a public signed integer."
  [pk ciphertext scalar]
  (let [pk ^PublicKey (assert-public-key! pk)
        ciphertext ^Ciphertext (assert-ciphertext! pk ciphertext)
        k (validate-plaintext! pk scalar)
        negative? (neg? (.signum k))
        base (if negative?
               (.modInverse ^BigInteger (:value ciphertext)
                            ^BigInteger (:n-squared pk))
               ^BigInteger (:value ciphertext))]
    (->Ciphertext (:key-id pk)
                  (.modPow ^BigInteger base (.abs k)
                           ^BigInteger (:n-squared pk)))))

(defn rerandomize
  "Refresh a ciphertext without changing its plaintext."
  ([pk ciphertext] (rerandomize pk ciphertext {}))
  ([pk ciphertext {:keys [random]}]
   (let [pk ^PublicKey (assert-public-key! pk)
         ciphertext ^Ciphertext (assert-ciphertext! pk ciphertext)
         rng ^SecureRandom (or random (SecureRandom.))
         n ^BigInteger (:n pk)
         n-squared ^BigInteger (:n-squared pk)
         r (random-coprime pk rng)]
     (->Ciphertext (:key-id pk)
                   (.mod (.multiply ^BigInteger (:value ciphertext)
                                    (.modPow r n n-squared))
                         n-squared)))))

(defn- non-negative-integer! [label value]
  (let [n (->big-integer value)]
    (when (neg? (.signum n))
      (throw (ex-info (str label " must be non-negative") {label value})))
    n))

(defn encrypted-matvec
  "Evaluate a plaintext integer matrix and bias over an encrypted vector.

  `:input-bound` is a public, data-independent absolute bound for every input
  element.  It is mandatory: a public range contract computes a worst-case output bound and rejects
  any row that could wrap modulo n.  The result contains ciphertexts and these
  public bounds; it never contains decrypted values.

  Outputs are rerandomized by default so identical linear results are not
  linkable by ciphertext equality."
  ([pk weights encrypted-input bias opts]
   (let [pk ^PublicKey (assert-public-key! pk)
         rows (mapv vec weights)
         encrypted-input (vec encrypted-input)
         bias (mapv ->big-integer bias)
         input-bound (non-negative-integer! :input-bound (:input-bound opts))
         rerandomize? (not= false (:rerandomize? opts))
         width (count encrypted-input)]
     (when-not (and (seq rows) (= (count rows) (count bias))
                    (every? #(= width (count %)) rows))
       (throw (ex-info "encrypted matvec dimensions are incompatible"
                       {:weight-shape [(count rows) (when (seq rows) (count (first rows)))]
                        :input-count width
                        :bias-count (count bias)})))
     (doseq [ciphertext encrypted-input]
       (assert-ciphertext! pk ciphertext))
     (let [limit (signed-limit pk)
           row-result
           (mapv
            (fn [row b]
              (let [row (mapv ->big-integer row)
                    bound (.add (.abs b)
                                (reduce #(.add ^BigInteger %1 ^BigInteger %2)
                                        zero
                                        (map #(.multiply (.abs ^BigInteger %)
                                                         input-bound)
                                             row)))]
                (when (>= (.compareTo bound limit) 0)
                  (throw (ex-info "encrypted matvec could wrap the Paillier plaintext modulus"
                                  {:output-bound (.toString bound)
                                   :exclusive-limit (.toString limit)})))
                (let [sum (reduce (fn [acc [weight ciphertext]]
                                    (add pk acc (scale pk ciphertext weight)))
                                  (encrypt pk zero)
                                  (map vector row encrypted-input))
                      biased (add-plaintext pk sum b)]
                  {:ciphertext (if rerandomize? (rerandomize pk biased) biased)
                   :bound bound})))
            rows bias)]
       {:ciphertexts (mapv :ciphertext row-result)
        :output-bounds (mapv :bound row-result)
        :shape [(count rows)]}))))

(defn public-key->data
  "Serialize only public key material to EDN-safe data."
  [pk]
  (let [pk ^PublicKey (assert-public-key! pk)]
    {:paillier/scheme :paillier-phe-v1
     :paillier/bits (:bits pk)
     :paillier/key-id (:key-id pk)
     :paillier/n (.toString ^BigInteger (:n pk) 16)}))

(defn data->public-key [data]
  (when-not (= :paillier-phe-v1 (:paillier/scheme data))
    (throw (ex-info "unsupported encrypted numerical scheme"
                    {:scheme (:paillier/scheme data)})))
  (let [n (BigInteger. ^String (:paillier/n data) 16)
        bits (:paillier/bits data)]
    (when-not (and (integer? bits)
                   (pos? (.signum n))
                   (.testBit n 0)
                   (<= (dec bits) (.bitLength n) bits))
      (throw (ex-info "invalid Paillier public modulus"
                      {:bits bits :modulus-bits (.bitLength n)})))
    (let [pk (public-key n bits)]
    (when-not (= (:key-id pk) (:paillier/key-id data))
      (throw (ex-info "Paillier public key fingerprint mismatch"
                      {:expected (:key-id pk)
                       :actual (:paillier/key-id data)})))
      pk)))

(defn ciphertext->data
  "Serialize one ciphertext without private key material or plaintext."
  [ciphertext]
  (when-not (ciphertext? ciphertext)
    (throw (ex-info "expected a Paillier ciphertext"
                    {:value-type (type ciphertext)})))
  {:paillier/key-id (:key-id ciphertext)
   :paillier/ciphertext (.toString ^BigInteger (:value ciphertext) 16)})

(defn data->ciphertext
  "Parse and validate one ciphertext against its public key."
  [pk data]
  (let [pk ^PublicKey (assert-public-key! pk)
        value (BigInteger. ^String (:paillier/ciphertext data) 16)
        n-squared ^BigInteger (:n-squared pk)]
    (when-not (and (= (:key-id pk) (:paillier/key-id data))
                   (pos? (.signum value))
                   (neg? (.compareTo value n-squared))
                   (= one (.gcd value n-squared)))
      (throw (ex-info "invalid Paillier ciphertext"
                      {:expected-key-id (:key-id pk)
                       :actual-key-id (:paillier/key-id data)})))
    (->Ciphertext (:key-id pk) value)))
