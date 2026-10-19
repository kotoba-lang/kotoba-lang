(ns mime.header
  "RFC 5322 §2.2 header fields, RFC 2045 §5 parameters, RFC 2047
  encoded-words. Pure; input is a binary string (see `mime.codec`)."
  (:require [kotoba.lang.text :as str]
            [mime.codec :as codec]))

;; ------------------------------------------------------------ unfolding

(defn split-lines*
  "Split on CRLF or bare LF. Real mail arrives with both, sometimes in
  the same message."
  [s]
  (str/split s #"\r\n|\n" -1))

(defn unfold
  "RFC 5322 §2.2.3: a header field may be folded across lines, and every
  continuation line begins with whitespace. Join them back, keeping one
  space at the seam."
  [lines]
  (reduce (fn [acc line]
            (if (and (seq acc) (re-find #"^[ \t]" line))
              (conj (vec (butlast acc)) (str (last acc) " " (str/triml line)))
              (conj acc line)))
          []
          lines))

;; ------------------------------------------------------- encoded-words

(def ^:private encoded-word-re
  #"=\?([^?]+)\?([BbQq])\?([^?]*)\?=")

(defn decode-encoded-words
  "RFC 2047: `=?charset?B?…?=` / `=?charset?Q?…?=` inside a header.

  Whitespace *between* two adjacent encoded-words is deleted, per §6.2 —
  that is how a long subject split across several words rejoins without
  spurious spaces. Whitespace between an encoded-word and ordinary text
  is kept."
  ([s] (decode-encoded-words s nil))
  ([s decoder]
   (let [decode-one (fn [[_ charset enc text]]
                      (let [raw (if (contains? #{"B" "b"} enc)
                                  (codec/decode-base64 text)
                                  (codec/decode-quoted-printable text true))]
                        (codec/decode-charset raw charset decoder)))]
     (loop [rest- s out [] prev-was-word? false]
       (if-let [m (re-find encoded-word-re rest-)]
         (let [whole (first m)
               idx (str/index-of rest- whole)
               before (subs rest- 0 idx)
               gap-only? (and (seq before) (str/blank? before))
               after (subs rest- (+ idx (count whole)))]
           (recur after
                  (conj out
                        (if (and prev-was-word? gap-only?) "" before)
                        (decode-one m))
                  true))
         ;; No encoded-word left. Whatever remains is either ASCII, or raw
         ;; 8-bit bytes a sender put in a header that RFC 5322 says is
         ;; US-ASCII — `decode-8bit-header` reads those as UTF-8 when they
         ;; structurally are UTF-8, and leaves them alone when they are not.
         (codec/decode-8bit-header (apply str (conj out rest-))))))))

;; ------------------------------------------------------------- parsing

(defn parse-headers
  "Raw header block (already split from the body) -> ordered vector of
  `[lower-case-name raw-value]`.

  A vector, not a map: `Received:` legitimately repeats, and the order of
  those is the delivery path. `header`/`headers` read from it."
  [header-block]
  (->> (split-lines*  header-block)
       unfold
       (keep (fn [line]
               (when-let [i (str/index-of line ":")]
                 (let [name (str/lower (str/trim (subs line 0 i)))]
                   (when (seq name)
                     [name (str/triml (subs line (inc i)))])))))
       vec))

(defn header
  "First value for `name`, decoded. Header names are case-insensitive
  (RFC 5322 §1.2.2)."
  ([hs name] (header hs name nil))
  ([hs name decoder]
   (some (fn [[k v]] (when (= (str/lower name) k)
                       (decode-encoded-words v decoder)))
         hs)))

(defn headers
  "Every value for `name`, in order, decoded."
  ([hs name] (headers hs name nil))
  ([hs name decoder]
   (->> hs
        (filter (fn [[k _]] (= (str/lower name) k)))
        (mapv (fn [[_ v]] (decode-encoded-words v decoder))))))

(defn raw-header
  "First value for `name`, NOT decoded — for fields like Content-Type
  whose structure must be parsed before any encoded-word could apply."
  [hs name]
  (some (fn [[k v]] (when (= (str/lower name) k) v)) hs))

;; ---------------------------------------------------------- parameters

(defn parse-parameters
  "RFC 2045 §5.1 structured field: `value; name=val; name=\"quoted val\"`
  -> `{:value \"value\" :params {\"name\" \"val\"}}`.

  Splits on semicolons that are not inside a quoted string — a
  `name=\"a;b\"` parameter is legal and splitting naively corrupts it."
  [s]
  (if (str/blank? (or s ""))
    {:value "" :params {}}
    (let [n (count s)
          segments (loop [i 0 start 0 in-q? false esc? false out []]
                     (if (>= i n)
                       (conj out (subs s start))
                       (let [c (nth s i)]
                         (cond
                           esc? (recur (inc i) start in-q? false out)
                           (= \\ c) (recur (inc i) start in-q? true out)
                           (= \" c) (recur (inc i) start (not in-q?) false out)
                           (and (= \; c) (not in-q?))
                           (recur (inc i) (inc i) false false (conj out (subs s start i)))
                           :else (recur (inc i) start in-q? false out)))))
          unquote (fn [v] (let [v (str/trim v)]
                            (if (and (> (count v) 1) (str/starts-with? v "\"") (str/ends-with? v "\""))
                              (-> (subs v 1 (dec (count v))) (str/replace "\\\"" "\""))
                              v)))]
      {:value (str/trim (first segments))
       :params (into {}
                     (keep (fn [seg]
                             (when-let [i (str/index-of seg "=")]
                               [(str/lower (str/trim (subs seg 0 i)))
                                (unquote (subs seg (inc i)))])))
                     (rest segments))})))

;; ------------------------------------------------- RFC 2231 parameters

(defn- extended-value
  "RFC 2231 §4 `charset'language'percent-encoded` -> `{:charset :text}`.

  The language field is parsed and discarded: it says what human language
  the value is in, which nothing here renders differently. A value with
  neither apostrophe is a *continuation segment* rather than an initial
  one — only the first segment carries the charset — so it comes back
  with `:charset nil` and the caller supplies it."
  [v]
  (let [parts (str/split (str v) #"'" 3)]
    (if (= 3 (count parts))
      {:charset (not-empty (str/lower (first parts)))
       :text (codec/decode-percent (nth parts 2))}
      {:charset nil :text (codec/decode-percent (str v))})))

(defn assemble-parameters
  "RFC 2231 §3–4: fold continuations and extended values into plain ones.

  Three things happen in one pass, because they compose in one header:

  - **Continuations** (`name*0`, `name*1`, …) are concatenated in numeric
    order. Long filenames are split this way and a parser that reads only
    `name*0` truncates at the seam.
  - **Extended values** (`name*`, `name*0*`) are percent-decoded and
    charset-decoded. This is how a non-ASCII filename travels — RFC 2047
    encoded-words are *not* allowed in parameters, though senders emit
    them anyway, so `decode-encoded-words` runs as a fallback on values
    that were not extended.
  - **`name*` beats `name`** when both are present (§4). Senders include
    the plain one for old clients, usually mangled or truncated; taking
    it in preference is how a correct filename gets thrown away for a
    broken one."
  ([params] (assemble-parameters params nil))
  ([params decoder]
   (let [;; name, segment index, extended?
         split-key (fn [k]
                     (if-let [[_ base idx star] (re-matches #"([^*]+)\*(\d+)(\*?)" k)]
                       {:base base :index (parse-long idx) :extended? (= "*" star)}
                       (if-let [[_ base] (re-matches #"([^*]+)\*" k)]
                         {:base base :index nil :extended? true}
                         {:base k :index nil :extended? false})))
         grouped (reduce (fn [acc [k v]]
                           (let [{:keys [base index extended?]} (split-key k)]
                             (update acc base (fnil conj [])
                                     {:index index :extended? extended? :value v})))
                         {} params)]
     (into {}
           (map (fn [[base segments]]
                  (let [extended (filter :extended? segments)
                        plain (remove :extended? segments)
                        ;; §4: the extended form wins outright.
                        chosen (if (seq extended) extended plain)
                        ordered (sort-by #(or (:index %) -1) chosen)
                        any-extended? (boolean (seq extended))
                        charset (some #(:charset (extended-value (:value %)))
                                      (filter :extended? ordered))
                        text (apply str
                                    (map (fn [{:keys [value extended?]}]
                                           (if extended?
                                             (:text (extended-value value))
                                             value))
                                         ordered))]
                    [base (if any-extended?
                            (codec/decode-charset text charset decoder)
                            ;; Not legal in a parameter, but senders do it.
                            (decode-encoded-words text decoder))])))
           grouped))))

(defn content-type
  "-> `{:type \"text/plain\" :charset \"utf-8\" :boundary \"…\" :params {…}}`.
  Defaults to text/plain per RFC 2045 §5.2 when the field is absent.

  Parameters are RFC 2231-assembled, so a boundary split across
  continuations is one boundary here rather than a truncated one — which
  would mean finding no parts at all in a message that has them."
  ([hs] (content-type hs nil))
  ([hs decoder]
   (let [{:keys [value params]} (parse-parameters (or (raw-header hs "content-type") "text/plain"))
         params (assemble-parameters params decoder)]
     {:type (str/lower (if (str/blank? value) "text/plain" value))
      :charset (get params "charset")
      :boundary (get params "boundary")
      :params params})))

(defn content-disposition
  "-> `{:disposition \"attachment\" :filename \"…\" :params {…}}`, or nil.

  `filename` here is the RFC 2231-assembled one, so
  `filename*=UTF-8''%E8%AB%8B%E6%B1%82%E6%9B%B8.pdf` arrives as
  `請求書.pdf` rather than as its percent-encoding."
  ([hs] (content-disposition hs nil))
  ([hs decoder]
   (when-let [raw (raw-header hs "content-disposition")]
     (let [{:keys [value params]} (parse-parameters raw)
           params (assemble-parameters params decoder)]
       {:disposition (str/lower value)
        :filename (get params "filename")
        :params params}))))
