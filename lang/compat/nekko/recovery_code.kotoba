(ns nekko.recovery-code
  "The factor a person keeps on paper: 128 bits, written so a human can copy it
  back without the failure mode that makes recovery codes useless.

  That failure mode is worth naming, because it shapes everything here. A
  keyslot answers exactly one question — did this secret open it — and gives
  the same answer for a wrong factor, a tampered slot and a typo. So without a
  checksum a mistyped character is indistinguishable from \"your mailbox is
  gone\", at the worst possible moment, to the least technical user. The check
  character below is not security; it is the difference between \"you typed a
  character wrong\" and a false bereavement.

  Format: Crockford base32, 26 data characters plus one check character,
  grouped in fives.

    K7QM3-XZ9RB-VT2H8-NDJ4P-6WGSY-E

  Crockford rather than plain base32 because it is designed for exactly this:
  I, L and O are not in the alphabet, and on the way back in they are read as
  1, 1 and 0, so the classic transcription confusions decode to the right thing
  instead of failing. Case is ignored; hyphens and spaces are ignored.

  The check character is a position-weighted sum mod 37. Weighted, not a plain
  sum, so that transposing two characters — the other thing people do — does
  not cancel out.

  Portable `.cljc` with no host crypto and no host string classes: only
  `generate` needs randomness and the caller supplies it, so the JVM and the
  browser run this unchanged and tests are deterministic."
  (:require [kotoba.lang.text :as str]))

(def ^:private alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ")
(def ^:private check-alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ*~$=U")

(def ^:private data-chars 26)

(def secret-bytes
  "128 bits. Enough that guessing is not a threat, short enough that a person
  will actually write it down rather than photograph it and lose the photo."
  16)

(defn- at [s i] (subs s i (inc i)))

(defn- char->val
  "Crockford's read-back rules: I and L are 1, O is 0. nil for anything else,
  which `parse` reports as a bad character rather than a failed unlock."
  [c]
  (let [c (str/upper (str c))]
    (case c
      ("I" "L") 1
      "O" 0
      (str/index-of alphabet c))))

(defn- byte->bits [b]
  (apply str (map #(if (bit-test b %) "1" "0") (range 7 -1 -1))))

(defn- bits->int [s]
  (reduce (fn [acc c] (+ (* 2 acc) (if (= "1" (str c)) 1 0))) 0 (seq s)))

(defn- int->bits [v width]
  (apply str (map #(if (bit-test v %) "1" "0") (range (dec width) -1 -1))))

(defn- bits->vals
  "128 bits into 26 base32 characters: 26*5 = 130, so the tail is padded with
  two zero bits. Decoding drops them again."
  [bits]
  (let [padded (str bits "00")]
    (mapv #(bits->int (subs padded (* 5 %) (+ 5 (* 5 %)))) (range data-chars))))

(defn- vals->bytes [vs]
  (let [bits (apply str (map #(int->bits % 5) vs))
        useful (subs bits 0 (* 8 secret-bytes))]
    (mapv #(bits->int (subs useful (* 8 %) (+ 8 (* 8 %)))) (range secret-bytes))))

(defn- check-char [vs]
  (at check-alphabet
      (mod (reduce + (map-indexed (fn [i v] (* (inc i) v)) vs)) 37)))

(defn- group
  "Fives, hyphenated: long enough to keep your place in, short enough to read
  back aloud."
  [s]
  (str/join "-" (map #(apply str %) (partition-all 5 s))))

(defn format-secret
  "16 secret bytes -> the printable code. Pure, so a test can pin an exact
  string rather than only round-tripping."
  [bs]
  (let [vs (bits->vals (apply str (map byte->bits bs)))]
    (group (str (apply str (map #(at alphabet %) vs)) (check-char vs)))))

(defn normalize
  "Strip what people add, uppercase what they lowercase."
  [code]
  (-> (str code) str/upper (str/replace #"[\s\-_]" "")))

(defn parse
  "Printable code -> `{:secret [16 bytes]}` or `{:error …}`. The errors are
  deliberately distinguishable from each other and from \"this did not open the
  slot\", so a caller can say \"you mistyped\" instead of \"your mail is
  unrecoverable\"."
  [code]
  (let [s (normalize code)]
    (if (not= (inc data-chars) (count s))
      {:error :wrong-length :expected (inc data-chars) :got (count s)}
      (let [body (subs s 0 data-chars)
            given (at s data-chars)
            vs (mapv char->val (seq body))]
        (cond
          (some nil? vs)
          {:error :bad-character
           :at (first (keep-indexed (fn [i v] (when (nil? v) i)) vs))}

          (not= given (check-char vs))
          {:error :checksum}

          :else {:secret (vals->bytes vs)})))))

(defn generate
  "`random-16` is 16 random bytes from the caller's host — `js/crypto` in a
  browser, `SecureRandom` on the JVM. Injected rather than reached for, so this
  namespace stays portable and its tests deterministic.

  -> {:code printable :secret bytes}. Show the code once, never store it, and
  keep the secret only as long as it takes to wrap a keyslot with it."
  [random-16]
  {:code (format-secret random-16) :secret (vec random-16)})
