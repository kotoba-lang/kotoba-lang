(ns storj.node.pem
  "PEM, because an identity that exists is one on disk.

  `storj.node.mint` makes an identity; almost nobody needs that. A node
  operator ran `identity create` months ago and has four files, and the only
  useful thing to do with them is read them. That is what this is for.

  ## Strict where it matters, lenient where Go is

  `encoding/pem` skips anything before a `-----BEGIN` line, and so does this:
  identity files in the wild carry comments, and refusing them would refuse
  real identities. But base64 is decoded strictly, a closing label has to
  match its opening one, and **a `BEGIN` with no `END` is an error rather than
  a block quietly skipped** — Go's `pem.Decode` returns the rest of the input
  in that case, which for a caller reading a certificate chain means silently
  getting fewer certificates than the file contains.

  ## Why base64 is here

  Nothing in this workspace's byte libraries has it: `io-multiformats` carries
  base58 and base32 because those are what multihash and CID need. Sixty lines
  of table lookup is cheaper than a dependency, and it is checked against
  RFC 4648's vectors and against a PEM file Go wrote."
  (:require [clojure.string :as str]))

(def line-length
  "Characters of base64 per line — what `encoding/pem` emits, and what makes
  the output of this byte-identical to Go's."
  64)

(defn- fail [msg data]
  (throw (ex-info (str "storj.node.pem: " msg) data)))

;; ── base64 ──────────────────────────────────────────────────────────────────

(def ^:private alphabet
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

(def ^:private char->value
  ;; Built from the alphabet rather than written out, so the two cannot drift.
  ;; The key type differs per runtime — a Character on the JVM, a one-character
  ;; string in JavaScript — but both sides of every lookup come from the same
  ;; `nth`, so the map never has to know which it is.
  (into {} (map-indexed (fn [i c] [c i]) alphabet)))

(defn base64-encode [bs]
  (let [v (vec bs)
        n (count v)]
    (str/join
     (for [i (range 0 n 3)]
       (let [b0 (nth v i)
             b1 (when (< (+ i 1) n) (nth v (+ i 1)))
             b2 (when (< (+ i 2) n) (nth v (+ i 2)))
             x  (+ (bit-shift-left b0 16)
                   (bit-shift-left (or b1 0) 8)
                   (or b2 0))
             c  (fn [shift] (nth alphabet (bit-and (bit-shift-right x shift) 0x3f)))]
         (str (c 18) (c 12)
              (if b1 (c 6) "=")
              (if b2 (c 0) "=")))))))

(defn base64-decode
  "Decode strictly: whitespace is dropped, anything else outside the alphabet
  is an error rather than a character to skip."
  [s]
  (let [t (str/replace s #"\s" "")]
    (when-not (zero? (mod (count t) 4))
      (fail "base64 length is not a multiple of four" {:length (count t)}))
    (let [padding (count (take-while #(= \= %) (reverse t)))]
      (when (> padding 2)
        (fail "more than two padding characters" {:padding padding}))
      (->> (partition 4 t)
           (mapcat (fn [[a b c d]]
                     (let [val (fn [ch]
                                 (if (= \= ch)
                                   0
                                   (or (char->value ch)
                                       (fail "character outside the base64 alphabet"
                                             {:char (str ch)}))))
                           x (+ (bit-shift-left (val a) 18)
                                (bit-shift-left (val b) 12)
                                (bit-shift-left (val c) 6)
                                (val d))]
                       [(bit-and (bit-shift-right x 16) 0xff)
                        (bit-and (bit-shift-right x 8) 0xff)
                        (bit-and x 0xff)])))
           vec
           (#(subvec % 0 (- (count %) padding)))))))

;; ── blocks ──────────────────────────────────────────────────────────────────

(defn encode
  "One PEM block, ending in a newline."
  [label der]
  (str "-----BEGIN " label "-----\n"
       (str/join (for [chunk (partition-all line-length (base64-encode der))]
                   (str (str/join chunk) "\n")))
       "-----END " label "-----\n"))

(defn encode-all
  "Several blocks, concatenated — which is all a certificate chain file is."
  [blocks]
  (str/join (map (fn [{:keys [label der]}] (encode label der)) blocks)))

(defn decode-all
  "Every block in `text`, as `{:label :der}`, in the order they appear.

  Scanned with `index-of` rather than a multiline regex: `(?m)` is a JVM
  inline flag and does not survive into a JavaScript RegExp, so an anchored
  pattern would quietly match different things on the two runtimes."
  [text]
  (loop [from 0, out []]
    (let [begin (str/index-of text "-----BEGIN " from)]
      (if (nil? begin)
        out
        (let [line-end (or (str/index-of text "\n" begin) (count text))
              header   (str/trimr (subs text begin line-end))]
          (when-not (str/ends-with? header "-----")
            (fail "malformed BEGIN line" {:line header}))
          (let [label      (subs header (count "-----BEGIN ") (- (count header) 5))
                end-marker (str "-----END " label "-----")
                end        (str/index-of text end-marker line-end)]
            (when (nil? end)
              ;; Go returns the remaining input here and the caller sees one
              ;; certificate fewer than the file has. A chain quietly short by
              ;; a certificate is exactly what this library exists to avoid.
              (fail "a BEGIN block has no matching END" {:label label}))
            (recur (+ end (count end-marker))
                   (conj out {:label label
                              :der (base64-decode (subs text line-end end))}))))))))

(defn decode-one
  "The single block in `text`, refusing a file that holds more than one.

  Used for key files, where a second block would mean the file holds a key
  this code is not using and the caller has no way to find out."
  [text expected-label]
  (let [blocks (decode-all text)]
    (when (not= 1 (count blocks))
      (fail "expected exactly one PEM block"
            {:found (count blocks) :labels (mapv :label blocks)}))
    (when (not= expected-label (:label (first blocks)))
      (fail "unexpected PEM label"
            {:expected expected-label :found (:label (first blocks))}))
    (:der (first blocks))))
