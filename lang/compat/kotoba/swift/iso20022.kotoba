(ns kotoba.swift.iso20022
  "Real ISO 20022 XML generation and parsing for two message definitions —
  pain.001.001.09 (CustomerCreditTransferInitiation) and pacs.008.001.08
  (FIToFICustomerCreditTransfer). No network, no I/O.

  Wire format is genuinely emitted XML (via kotoba-lang/xml's dependency-
  free Hiccup->XML emitter), not an EDN stand-in, and parsed back losslessly
  by a minimal recursive-descent parser scoped to this library's own
  generated documents (single default xmlns, no CDATA/comments/processing-
  instructions beyond a leading `<?xml ...?>` declaration, no mixed
  text+element content) — see parse-xml docstring. This is not a
  general-purpose validating XML parser and does not check documents
  against the real ISO 20022 XSD.

  Scope, stated honestly: only pain.001.001.09 and pacs.008.001.08 are
  modeled, out of the full ISO 20022 message catalogue. Within those two
  message definitions, only the element subset documented on pain001-doc /
  pacs008-doc is modeled — party postal address (PstlAdr), ultimate
  debtor/creditor, payment-type/purpose/category codes, structured
  remittance, and most other optional branches of the real schema are not
  modeled. See README.md for the verified sources.

  Portable (.cljc) across JVM / ClojureScript / SCI / GraalVM."
  (:require [clojure.string :as str]
            [xml.core :as xmlc]
            [kotoba.swift :as swift]))

;; ---------------------------------------------------------------------------
;; Message-definition namespaces (verified — see README.md "Verified
;; against")
;; ---------------------------------------------------------------------------

(def pain-001-namespace "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09")
(def pacs-008-namespace "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08")

;; ---------------------------------------------------------------------------
;; Field-format validators
;; ---------------------------------------------------------------------------

(defn iso-date-valid?
  "True for a YYYY-MM-DD ISO 8601 calendar date string (field shape only,
  as with kotoba.swift/mt-date-valid?)."
  [s]
  (boolean (and (string? s) (re-matches #"\d{4}-\d{2}-\d{2}" s))))

(defn iso-datetime-valid?
  "True for a YYYY-MM-DDTHH:MM:SS[.fff](Z|+HH:MM|-HH:MM) ISO 8601 date-time
  string, the shape used by CreDtTm."
  [s]
  (boolean (and (string? s)
                (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})" s))))

(def chrg-br-codes
  "ChargeBearerType1Code — verified external code-list values: DEBT (all
  charges borne by debtor), CRED (borne by creditor), SHAR (shared), SLEV
  (per agreed service level)."
  #{"DEBT" "CRED" "SHAR" "SLEV"})

;; ---------------------------------------------------------------------------
;; Amount formatting — mirrors kotoba.swift/format-mt-amount but with a
;; period decimal separator (plain XML xs:decimal), not a comma.
;; ---------------------------------------------------------------------------

(defn- ipow10 [n] (reduce * 1 (repeat n 10)))
(defn- pad-left [n width c]
  (let [s (str n)]
    (if (>= (count s) width) s (str (apply str (repeat (- width (count s)) c)) s))))

(defn format-xml-decimal
  "Render an integer amount in minor units as a plain XML decimal string
  (period separator, no thousands separator)."
  ([minor] (format-xml-decimal minor 2))
  ([minor decimals]
   (when (integer? minor)
     (let [neg?* (neg? minor)
           m      (if neg?* (- minor) minor)
           factor (ipow10 decimals)
           whole  (quot m factor)
           frac   (rem m factor)]
       (str (when neg?* "-") whole (when (pos? decimals) (str "." (pad-left frac decimals \0))))))))

;; ---------------------------------------------------------------------------
;; Hiccup element helper — drops nil children so optional ISO 20022
;; elements (e.g. RmtInf, InstrId) can be omitted with (when cond (el ...)).
;; ---------------------------------------------------------------------------

(defn- el
  ([tag] [tag])
  ([tag a & more]
   (if (map? a)
     (into [tag a] (remove nil? more))
     (into [tag] (remove nil? (cons a more))))))

;; ---------------------------------------------------------------------------
;; pain.001.001.09 — CustomerCreditTransferInitiation
;;   Document/CstmrCdtTrfInitn/
;;     GrpHdr/(MsgId,CreDtTm,NbOfTxs,CtrlSum,InitgPty/Nm)
;;     PmtInf/(PmtInfId,PmtMtd,NbOfTxs,CtrlSum,ReqdExctnDt,
;;             Dbtr/Nm,DbtrAcct/Id/IBAN,DbtrAgt/FinInstnId/BICFI,ChrgBr,
;;             CdtTrfTxInf*/(PmtId/(InstrId?,EndToEndId,UETR?),
;;                           Amt/InstdAmt[@Ccy],
;;                           CdtrAgt/FinInstnId/BICFI,Cdtr/Nm,
;;                           CdtrAcct/Id/IBAN,RmtInf/Ustrd?))
;;   Verified against the real element hierarchy and namespace — see
;;   README.md "Verified against".
;; ---------------------------------------------------------------------------

(defn pain001-doc
  "Build a pain.001.001.09 document as a Hiccup XML tree: one GrpHdr + one
  PmtInf containing 1..n CdtTrfTxInf. `transactions` is a seq of
  {:end-to-end-id :instruction-id? :uetr? :amount-minor :decimals?
  :currency :creditor-name :creditor-iban :creditor-bic
  :remittance-information?}. Returns nil when a required field is
  missing/invalid (mirrors mt103/mt202's own-validate-on-construct style)."
  [{:keys [msg-id creation-date-time initiating-party-name
           payment-info-id requested-execution-date charge-bearer
           debtor-name debtor-iban debtor-bic
           transactions]
    :or   {charge-bearer "SLEV"}}]
  (when (and (seq msg-id)
             (iso-datetime-valid? creation-date-time)
             (seq payment-info-id)
             (iso-date-valid? requested-execution-date)
             (swift/bic-valid? debtor-bic)
             (seq debtor-iban)
             (seq debtor-name)
             (contains? chrg-br-codes charge-bearer)
             (seq transactions)
             (every? (fn [{:keys [end-to-end-id amount-minor currency creditor-bic creditor-iban creditor-name]}]
                       (and (seq end-to-end-id)
                            (integer? amount-minor) (pos? amount-minor)
                            (re-matches #"[A-Z]{3}" (or currency ""))
                            (swift/bic-valid? creditor-bic)
                            (seq creditor-iban)
                            (seq creditor-name)))
                     transactions))
    (let [n         (count transactions)
          ctrl-sum  (reduce + (map :amount-minor transactions))
          tx-els    (for [{:keys [instruction-id end-to-end-id uetr amount-minor decimals currency
                                   creditor-name creditor-iban creditor-bic remittance-information]}
                          transactions]
                      (el :CdtTrfTxInf
                          (el :PmtId
                              (when instruction-id (el :InstrId instruction-id))
                              (el :EndToEndId end-to-end-id)
                              (when uetr (el :UETR uetr)))
                          (el :Amt (el :InstdAmt {:Ccy currency} (format-xml-decimal amount-minor (or decimals 2))))
                          (el :CdtrAgt (el :FinInstnId (el :BICFI creditor-bic)))
                          (el :Cdtr (el :Nm creditor-name))
                          (el :CdtrAcct (el :Id (el :IBAN creditor-iban)))
                          (when remittance-information (el :RmtInf (el :Ustrd remittance-information)))))]
      (el :Document {:xmlns pain-001-namespace}
          (el :CstmrCdtTrfInitn
              (el :GrpHdr
                  (el :MsgId msg-id)
                  (el :CreDtTm creation-date-time)
                  (el :NbOfTxs (str n))
                  (el :CtrlSum (format-xml-decimal ctrl-sum 2))
                  (el :InitgPty (el :Nm initiating-party-name)))
              (apply el :PmtInf
                     (el :PmtInfId payment-info-id)
                     (el :PmtMtd "TRF")
                     (el :NbOfTxs (str n))
                     (el :CtrlSum (format-xml-decimal ctrl-sum 2))
                     (el :ReqdExctnDt requested-execution-date)
                     (el :Dbtr (el :Nm debtor-name))
                     (el :DbtrAcct (el :Id (el :IBAN debtor-iban)))
                     (el :DbtrAgt (el :FinInstnId (el :BICFI debtor-bic)))
                     (el :ChrgBr charge-bearer)
                     tx-els))))))

;; ---------------------------------------------------------------------------
;; pacs.008.001.08 — FIToFICustomerCreditTransfer
;;   Document/FIToFICstmrCdtTrf/
;;     GrpHdr/(MsgId,CreDtTm,NbOfTxs,SttlmInf/SttlmMtd)
;;     CdtTrfTxInf*/(PmtId/(InstrId?,EndToEndId,UETR?),
;;                   IntrBkSttlmAmt[@Ccy],ChrgBr,
;;                   DbtrAgt/FinInstnId/BICFI,Dbtr/Nm,DbtrAcct/Id/IBAN,
;;                   CdtrAgt/FinInstnId/BICFI,Cdtr/Nm,CdtrAcct/Id/IBAN)
;;   Unlike pain.001 (one shared PmtInf), pacs.008 carries debtor/creditor
;;   per-transaction inside each CdtTrfTxInf — verified against a real
;;   pacs.008.001.08 example. SttlmMtd is verified to exist and to accept
;;   at least the value \"INDA\"; the full SettlementMethod1Code external
;;   code-list enumeration was NOT independently verified, so it is
;;   validated only by shape (4!a), not by a fixed value set.
;; ---------------------------------------------------------------------------

(defn pacs008-doc
  "Build a pacs.008.001.08 document as a Hiccup XML tree: one GrpHdr + 1..n
  CdtTrfTxInf. `transactions` is a seq of {:end-to-end-id :instruction-id?
  :uetr? :amount-minor :decimals? :currency :debtor-name :debtor-iban
  :debtor-bic :creditor-agent-bic :creditor-name :creditor-iban
  :charge-bearer?}. Returns nil when a required field is missing/invalid."
  [{:keys [msg-id creation-date-time settlement-method transactions]
    :or   {settlement-method "INDA"}}]
  (when (and (seq msg-id)
             (iso-datetime-valid? creation-date-time)
             (re-matches #"[A-Z]{4}" settlement-method)
             (seq transactions)
             (every? (fn [{:keys [end-to-end-id amount-minor currency
                                   debtor-bic debtor-iban debtor-name
                                   creditor-agent-bic creditor-iban creditor-name
                                   charge-bearer]}]
                       (and (seq end-to-end-id)
                            (integer? amount-minor) (pos? amount-minor)
                            (re-matches #"[A-Z]{3}" (or currency ""))
                            (swift/bic-valid? debtor-bic) (seq debtor-iban) (seq debtor-name)
                            (swift/bic-valid? creditor-agent-bic)
                            (seq creditor-iban) (seq creditor-name)
                            (contains? chrg-br-codes (or charge-bearer "SHAR"))))
                     transactions))
    (let [n      (count transactions)
          tx-els (for [{:keys [instruction-id end-to-end-id uetr amount-minor decimals currency
                                debtor-bic debtor-iban debtor-name
                                creditor-agent-bic creditor-iban creditor-name
                                charge-bearer]}
                       transactions]
                   (el :CdtTrfTxInf
                       (el :PmtId
                           (when instruction-id (el :InstrId instruction-id))
                           (el :EndToEndId end-to-end-id)
                           (when uetr (el :UETR uetr)))
                       (el :IntrBkSttlmAmt {:Ccy currency} (format-xml-decimal amount-minor (or decimals 2)))
                       (el :ChrgBr (or charge-bearer "SHAR"))
                       (el :DbtrAgt (el :FinInstnId (el :BICFI debtor-bic)))
                       (el :Dbtr (el :Nm debtor-name))
                       (el :DbtrAcct (el :Id (el :IBAN debtor-iban)))
                       (el :CdtrAgt (el :FinInstnId (el :BICFI creditor-agent-bic)))
                       (el :Cdtr (el :Nm creditor-name))
                       (el :CdtrAcct (el :Id (el :IBAN creditor-iban)))))]
      (el :Document {:xmlns pacs-008-namespace}
          (apply el :FIToFICstmrCdtTrf
                 (el :GrpHdr
                     (el :MsgId msg-id)
                     (el :CreDtTm creation-date-time)
                     (el :NbOfTxs (str n))
                     (el :SttlmInf (el :SttlmMtd settlement-method)))
                 tx-els)))))

;; ---------------------------------------------------------------------------
;; XML rendering (kotoba-lang/xml's Hiccup->XML emitter) + parsing back
;; ---------------------------------------------------------------------------

(defn xml->str
  "Render a Hiccup XML tree (as built by pain001-doc / pacs008-doc) to a
  well-formed XML string with a UTF-8 declaration."
  [hiccup]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (xmlc/xml hiccup)))

(def ^:private xml-entity->char
  {"amp" \& "lt" \< "gt" \> "quot" \" "apos" \'})

(defn- xml-unescape [s]
  (str/replace s #"&(amp|lt|gt|quot|apos);" (fn [[_ n]] (str (xml-entity->char n)))))

(defn- xml-tokenize [s] (re-seq #"<[^>]+>|[^<]+" s))
(defn- xml-tag-name [tok] (second (re-find #"^</?([A-Za-z_][\w.\-]*)" tok)))
(defn- xml-tag-attrs [tok]
  (into {} (for [[_ k v] (re-seq #"([A-Za-z_][\w.\-]*)=\"([^\"]*)\"" tok)] [(keyword k) (xml-unescape v)])))

(defn parse-xml
  "Parse a well-formed XML string produced by this namespace's own
  generators (pain001-doc / pacs008-doc via xml->str) back into the
  equivalent Hiccup form. A minimal recursive-descent tokenizer scoped to
  this library's own generated documents — see namespace docstring for the
  exact scope limits. Not a general-purpose validating XML parser."
  [s]
  (let [s      (str/replace s #"(?s)^\s*<\?xml[^>]*\?>\s*" "")
        tokens (xml-tokenize s)]
    (loop [toks tokens stack [] root nil]
      (if (empty? toks)
        root
        (let [tok (first toks)
              rst (rest toks)]
          (cond
            (str/starts-with? tok "</")
            (let [{:keys [tag attrs children]} (peek stack)
                  stack*  (pop stack)
                  node    (into (if (seq attrs) [tag attrs] [tag]) children)]
              (if (seq stack*)
                (recur rst (conj (pop stack*) (update (peek stack*) :children conj node)) root)
                (recur rst stack* node)))

            (str/ends-with? tok "/>")
            (let [tag   (keyword (xml-tag-name tok))
                  attrs (xml-tag-attrs tok)
                  node  (if (seq attrs) [tag attrs] [tag])]
              (if (seq stack)
                (recur rst (conj (pop stack) (update (peek stack) :children conj node)) root)
                (recur rst stack node)))

            (str/starts-with? tok "<")
            (let [tag   (keyword (xml-tag-name tok))
                  attrs (xml-tag-attrs tok)]
              (recur rst (conj stack {:tag tag :attrs attrs :children []}) root))

            :else
            (let [text (str/trim tok)]
              (if (and (seq text) (seq stack))
                (recur rst (conj (pop stack) (update (peek stack) :children conj (xml-unescape text))) root)
                (recur rst stack root)))))))))

;; ---------------------------------------------------------------------------
;; Hiccup XML query helpers (used by validation, export, and the console UI)
;; ---------------------------------------------------------------------------

(defn xml-tag [el] (first el))
(defn- xml-attrs? [el] (map? (second el)))
(defn xml-attrs [el] (if (xml-attrs? el) (second el) {}))
(defn xml-children [el] (if (xml-attrs? el) (drop 2 el) (rest el)))

(defn xml-find
  "First direct child element of el with the given tag keyword."
  [el tag]
  (first (filter #(and (vector? %) (= tag (xml-tag %))) (xml-children el))))

(defn xml-find-all
  "All direct child elements of el with the given tag keyword."
  [el tag]
  (filter #(and (vector? %) (= tag (xml-tag %))) (xml-children el)))

(defn xml-text
  "Concatenated direct text-node children of el (its element's own text
  content, not descendants')."
  [el]
  (apply str (filter string? (xml-children el))))

;; ---------------------------------------------------------------------------
;; Structural validation of a parsed ISO 20022 document
;; ---------------------------------------------------------------------------

(defn validate-iso20022
  "Structural validation of a parsed ISO 20022 Hiccup document (as returned
  by parse-xml, or self-built by pain001-doc/pacs008-doc). Checks the
  namespace matches a supported message definition and that the mandatory
  top-level elements this library models are present."
  [doc]
  (cond
    (not (vector? doc))          {:swift/valid? false :swift/error :not-xml}
    (not= :Document (xml-tag doc)) {:swift/valid? false :swift/error :missing-document-root}
    :else
    (let [xmlns (:xmlns (xml-attrs doc))]
      (cond
        (= xmlns pain-001-namespace)
        (let [body (xml-find doc :CstmrCdtTrfInitn)
              grp  (some-> body (xml-find :GrpHdr))
              pmt  (some-> body (xml-find :PmtInf))]
          (if (and grp pmt (xml-find grp :MsgId) (xml-find pmt :PmtInfId) (seq (xml-find-all pmt :CdtTrfTxInf)))
            {:swift/valid? true :swift/message-type "pain.001.001.09"}
            {:swift/valid? false :swift/error :missing-mandatory-elements}))

        (= xmlns pacs-008-namespace)
        (let [body (xml-find doc :FIToFICstmrCdtTrf)
              grp  (some-> body (xml-find :GrpHdr))
              txs  (some-> body (xml-find-all :CdtTrfTxInf))]
          (if (and grp (seq txs) (xml-find grp :MsgId))
            {:swift/valid? true :swift/message-type "pacs.008.001.08"}
            {:swift/valid? false :swift/error :missing-mandatory-elements}))

        :else {:swift/valid? false :swift/error :unsupported-namespace}))))
