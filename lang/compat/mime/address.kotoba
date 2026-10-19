(ns mime.address
  "RFC 5322 §3.4 address lists.

  `To: Jun Kawasaki <jun@example.com>, \"Doe, John\" <j@example.com>`
  -> `[{:name \"Jun Kawasaki\" :address \"jun@example.com\"} …]`

  The whole difficulty is that a comma is both the list separator and a
  perfectly ordinary character inside a quoted display name, so
  `str/split` on commas is wrong for exactly the addresses whose display
  name is a person's surname-first name. Same for `<` `>` inside
  comments. So this walks the string with the quote/comment state
  machine RFC 5322 actually describes."
  (:require [kotoba.lang.text :as str]
            [mime.header :as header]))

(defn split-list
  "Split an address list on top-level commas — not those inside quoted
  strings, comments, or angle brackets."
  [s]
  (let [n (count (or s ""))]
    (loop [i 0 start 0 in-q? false in-c? 0 in-a? false esc? false out []]
      (if (>= i n)
        (->> (conj out (subs s start)) (map str/trim) (remove str/blank?) vec)
        (let [c (nth s i)]
          (cond
            esc? (recur (inc i) start in-q? in-c? in-a? false out)
            (= \\ c) (recur (inc i) start in-q? in-c? in-a? true out)
            (= \" c) (recur (inc i) start (not in-q?) in-c? in-a? false out)
            (and (= \( c) (not in-q?)) (recur (inc i) start in-q? (inc in-c?) in-a? false out)
            (and (= \) c) (not in-q?) (pos? in-c?)) (recur (inc i) start in-q? (dec in-c?) in-a? false out)
            (and (= \< c) (not in-q?) (zero? in-c?)) (recur (inc i) start in-q? in-c? true false out)
            (and (= \> c) (not in-q?) (zero? in-c?)) (recur (inc i) start in-q? in-c? false false out)
            (and (= \, c) (not in-q?) (zero? in-c?) (not in-a?))
            (recur (inc i) (inc i) false 0 false false (conj out (subs s start i)))
            :else (recur (inc i) start in-q? in-c? in-a? false out)))))))

(defn- strip-comments [s]
  (let [n (count s)]
    (loop [i 0 depth 0 in-q? false esc? false out []]
      (if (>= i n)
        (apply str out)
        (let [c (nth s i)]
          (cond
            esc? (recur (inc i) depth in-q? false (if (zero? depth) (conj out c) out))
            (= \\ c) (recur (inc i) depth in-q? true out)
            (= \" c) (recur (inc i) depth (not in-q?) false (conj out c))
            (and (= \( c) (not in-q?)) (recur (inc i) (inc depth) in-q? false out)
            (and (= \) c) (not in-q?) (pos? depth)) (recur (inc i) (dec depth) in-q? false out)
            :else (recur (inc i) depth in-q? false (if (zero? depth) (conj out c) out))))))))

(defn parse-one
  "One address -> `{:name … :address …}`. `:name` is nil when there is
  no display name. Never throws: an unparseable entry comes back with
  the raw text as `:address`, because dropping a recipient silently is
  worse than carrying a malformed one that a validator can reject."
  ([s] (parse-one s nil))
  ([s decoder]
   (let [s (str/trim (strip-comments s))]
     (if-let [open (str/last-index-of s "<")]
       (let [close (or (str/index-of s ">" open) (count s))
             addr (str/trim (subs s (inc open) close))
             raw-name (str/trim (subs s 0 open))
             name (-> raw-name
                      (str/replace #"^\"|\"$" "")
                      (str/replace "\\\"" "\"")
                      str/trim)]
         {:name (when (seq name) (header/decode-encoded-words name decoder))
          :address addr})
       {:name nil :address s}))))

(defn parse-list
  "Address-list header value -> vector of `{:name :address}`."
  ([s] (parse-list s nil))
  ([s decoder] (mapv #(parse-one % decoder) (split-list s))))

(defn addresses
  "Just the address strings — what `mail.message` wants for :to/:cc."
  [parsed]
  (mapv :address parsed))
