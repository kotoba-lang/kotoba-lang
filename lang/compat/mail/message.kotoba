(ns mail.message
  (:require [clojure.string :as str]))

(def email-pattern #"(?i)^[^@\s<>]+@[^@\s<>]+\.[^@\s<>]+$")

(defn normalize-address [address]
  (cond
    (string? address)
    {:mail.address/email (str/lower-case (str/trim address))}

    (map? address)
    (let [email (or (:mail.address/email address) (:email address))]
      (merge (dissoc address :email)
             {:mail.address/email (some-> email str/trim str/lower-case)}))

    :else
    {:mail.address/email nil}))

(defn valid-address? [address]
  (boolean (re-matches email-pattern (:mail.address/email (normalize-address address)))))

(defn normalize-addresses [addresses]
  (->> (if (sequential? addresses) addresses [addresses])
       (remove nil?)
       (mapv normalize-address)))

(defn body-part [content-type body]
  {:mail.part/content-type content-type
   :mail.part/body body})

(defn message
  "Build a provider-independent message.

  Required fields are :from, :to, :subject, and either :text or :html."
  [attrs]
  (let [from (:from attrs)
        to (:to attrs)
        cc (:cc attrs)
        bcc (:bcc attrs)
        text (:text attrs)
        html (:html attrs)
        parts (cond-> []
                text (conj (body-part "text/plain; charset=utf-8" text))
                html (conj (body-part "text/html; charset=utf-8" html)))]
    {:mail/type :mail/message
     :mail/from (normalize-address from)
     :mail/to (normalize-addresses to)
     :mail/cc (normalize-addresses cc)
     :mail/bcc (normalize-addresses bcc)
     :mail/reply-to (when-let [reply-to (:reply-to attrs)]
                      (normalize-address reply-to))
     :mail/subject (:subject attrs)
     :mail/parts parts
     :mail/headers (or (:headers attrs) {})
     :mail/tags (set (:tags attrs))
     :mail/metadata (or (:metadata attrs) {})}))

(defn recipients [m]
  (vec (concat (:mail/to m) (:mail/cc m) (:mail/bcc m))))

(defn validation-errors [m]
  (vec
   (concat
    (when-not (valid-address? (:mail/from m))
      [{:mail.error/code :invalid-from
        :mail.error/message "from must be a valid email address"}])
    (when (empty? (:mail/to m))
      [{:mail.error/code :missing-to
        :mail.error/message "at least one to recipient is required"}])
    (for [[idx address] (map-indexed vector (recipients m))
          :when (not (valid-address? address))]
      {:mail.error/code :invalid-recipient
       :mail.error/index idx
       :mail.error/address address})
    (when (str/blank? (str (:mail/subject m)))
      [{:mail.error/code :missing-subject
        :mail.error/message "subject is required"}])
    (when (empty? (:mail/parts m))
      [{:mail.error/code :missing-body
        :mail.error/message "text or html body is required"}]))))

(defn valid-message? [m]
  (empty? (validation-errors m)))

(defn assert-valid-message [m]
  (let [errors (validation-errors m)]
    (when (seq errors)
      (throw (ex-info "invalid mail message" {:mail/errors errors})))
    m))

(defn message-fact [m]
  {:kotoba/type :mail/message
   :kotoba/id [:mail/message (:mail/subject m) (mapv :mail.address/email (:mail/to m))]
   :mail/message m})
