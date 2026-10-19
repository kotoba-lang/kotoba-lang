(ns mime.parse
  "Raw RFC 5322 message (binary string — see `mime.codec`) -> a part tree,
  then the flat shape `mail.inbound/from-parts` takes.

  This is the layer that did not exist: `kotoba-lang/mail` models a
  received message but explicitly does not parse one, and every host
  capability that could produce one (a Cloudflare Email Worker, an SMTP
  server, an IMAP poller) hands over raw bytes. ADR-2607263000 D8."
  (:require [kotoba.lang.text :as str]
            [mime.address :as address]
            [mime.codec :as codec]
            [mime.header :as header]))

;; ------------------------------------------------------- split & parts

(defn split-message
  "Separate the header block from the body at the first empty line
  (RFC 5322 §2.1). Returns `{:headers [[k v]…] :body binary-string}`."
  [raw]
  (let [i (or (some (fn [sep] (when-let [j (str/index-of raw sep)] [j (count sep)]))
                    ["\r\n\r\n" "\n\n"])
              [(count raw) 0])
        [at len] i]
    {:headers (header/parse-headers (subs raw 0 at))
     :body (subs raw (min (count raw) (+ at len)))}))

(defn- split-multipart
  "RFC 2046 §5.1.1. Parts sit between `--boundary` lines; the epilogue
  after `--boundary--` is discarded, as is the preamble before the first
  boundary (both are, by spec, to be ignored)."
  [body boundary]
  (let [delim (str "--" boundary)
        lines (header/split-lines* body)]
    (loop [[l & more] lines current nil parts []]
      (cond
        (nil? l)
        (cond-> parts current (conj (str/join "\r\n" current)))

        (= l (str delim "--"))
        (cond-> parts current (conj (str/join "\r\n" current)))

        (= l delim)
        (recur more [] (cond-> parts current (conj (str/join "\r\n" current))))

        :else
        (recur more (when current (conj current l)) parts)))))

(declare parse-part)

(defn- parse-multipart-children [body boundary opts]
  (mapv #(parse-part % opts) (split-multipart body boundary)))

(defn parse-part
  "One MIME entity (headers + body) -> a part map:

    {:headers […] :content-type \"text/plain\" :charset \"utf-8\"
     :disposition \"attachment\" :filename \"a.pdf\"
     :encoding \"base64\"
     :body   decoded text          ; text/* only
     :bytes  decoded binary string ; non-text
     :parts  [child …]}            ; multipart/* only"
  [raw {:keys [decoder] :as opts}]
  (let [{:keys [headers body]} (split-message raw)
        {:keys [type charset boundary]} (header/content-type headers decoder)
        {:keys [disposition filename]} (or (header/content-disposition headers decoder) {})
        encoding (header/raw-header headers "content-transfer-encoding")
        multipart? (str/starts-with? type "multipart/")
        text? (str/starts-with? type "text/")
        decoded (when-not multipart? (codec/decode-transfer encoding body))]
    (cond-> {:headers headers
             :content-type type
             :charset charset
             :encoding (some-> encoding str/trim str/lower)
             :disposition disposition
             :filename filename}
      multipart? (assoc :parts (if boundary
                                 (parse-multipart-children body boundary opts)
                                 ;; A multipart with no boundary is
                                 ;; malformed. Keep the raw body as one
                                 ;; text part rather than dropping the
                                 ;; message: the content is still there.
                                 [{:content-type "text/plain"
                                   :body (codec/decode-charset body charset decoder)
                                   :malformed :multipart-without-boundary}]))
      (and (not multipart?) text?) (assoc :body (codec/decode-charset decoded charset decoder))
      (and (not multipart?) (not text?)) (assoc :bytes decoded))))

(defn parse
  "Raw message -> root part."
  ([raw] (parse raw {}))
  ([raw opts] (parse-part raw opts)))

;; ------------------------------------------------------------ flatten

(defn walk-parts
  "Every part in the tree, depth first, root included."
  [part]
  (cons part (mapcat walk-parts (:parts part))))

(defn attachment?
  "A part is an attachment when it says so, or when it carries a filename
  and is not the message body. `inline` parts with a filename count:
  an inline image is still a file the user received."
  [{:keys [disposition filename content-type]}]
  (boolean (or (= "attachment" disposition)
               (and filename (not= "text/plain" content-type))
               (and (= "inline" disposition) filename))))

(defn- body-of
  "The best `type` body in the tree. `multipart/alternative` orders parts
  worst-to-best (RFC 2046 §5.1.4), so the LAST match wins — taking the
  first is the classic bug that shows a plain-text fallback in place of
  the message someone actually sent."
  [root type]
  (->> (walk-parts root)
       (filter #(and (= type (:content-type %))
                     (not (attachment? %))
                     (:body %)))
       last
       :body))

(defn- auth-result
  "Parse one mechanism out of Authentication-Results (RFC 8601).
  -> :pass / :fail / :neutral / :none / nil when not stated."
  [hs mechanism]
  (when-let [line (header/raw-header hs "authentication-results")]
    (when-let [m (re-find (re-pattern (str "(?i)\\b" mechanism "=(\\w+)")) line)]
      (keyword (str/lower (second m))))))

(defn message-parts
  "Root part + original headers -> exactly the map
  `mail.inbound/from-parts` takes. The `:provider` and
  `:provider-message-id` keys are the host's to supply; everything else
  comes off the wire.

  Deliberately not requiring `kotoba-lang/mail`: this library parses,
  that library models, and a parser that depends on a model it does not
  use is a dependency for nothing. The shape is the contract."
  ([root] (message-parts root {}))
  ([root {:keys [decoder]}]
   (let [hs (:headers root)
         addrs #(address/addresses (address/parse-list (or (header/raw-header hs %) "") decoder))]
     {:from (first (addrs "from"))
      :to (addrs "to")
      :cc (addrs "cc")
      :subject (header/header hs "subject" decoder)
      :message-id (some-> (header/raw-header hs "message-id") str/trim)
      :date (header/header hs "date" decoder)
      :text (body-of root "text/plain")
      :html (body-of root "text/html")
      :headers (into {} hs)
      :spf (auth-result hs "spf")
      :dkim (auth-result hs "dkim")
      :dmarc (auth-result hs "dmarc")
      :attachments (->> (walk-parts root)
                        (filter attachment?)
                        (mapv (fn [p]
                                {:filename (:filename p)
                                 :content-type (:content-type p)
                                 :size (count (or (:bytes p) (:body p) ""))
                                 :bytes (:bytes p)
                                 :body (:body p)})))})))
