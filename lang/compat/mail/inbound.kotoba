(ns mail.inbound
  "Provider-independent model for a received message.

  Mirrors mail.receipt (an outbound acknowledgement) but for the inbound
  direction: a host capability (SMTP server, Cloudflare Email Worker, IMAP
  poller, ...) parses the wire format and hands a plain map here. This
  namespace does not parse MIME or touch the network; see mail.README
  Non-goals."
  (:require [mail.message :as message]))

(defn inbound
  ([provider provider-message-id envelope] (inbound provider provider-message-id envelope {}))
  ([provider provider-message-id envelope attrs]
   (when-not (string? provider-message-id)
     (throw (ex-info "mail inbound requires a provider message-id"
                     {:provider provider})))
   (merge {:mail.inbound/type :mail/inbound
           :mail.inbound/provider provider
           :mail.inbound/provider-message-id provider-message-id
           :mail.inbound/message envelope
           :mail.inbound/received-at (:received-at attrs)}
          (dissoc attrs :received-at))))

(defn from-parts
  "Build an inbound record from the loosely-typed parts a host capability
  parses off the wire (e.g. a Cloudflare Email Worker's MIME parse)."
  [{:keys [provider provider-message-id from to cc subject text html headers
           received-at spf dkim dmarc attachments]}]
  (inbound provider provider-message-id
           (message/message {:from from :to to :cc cc :subject subject
                             :text text :html html :headers headers})
           {:received-at received-at
            :mail.inbound/spf spf
            :mail.inbound/dkim dkim
            :mail.inbound/dmarc dmarc
            :mail.inbound/attachments (vec attachments)}))

(defn authenticated?
  "True when both SPF and DKIM passed. Callers can use this to route
  unauthenticated mail to a stricter/held lane instead of :inbox."
  [i]
  (and (= :pass (:mail.inbound/spf i))
       (= :pass (:mail.inbound/dkim i))))

(defn inbound-fact [i]
  {:kotoba/type :mail/inbound
   :kotoba/id [:mail/inbound (:mail.inbound/provider i) (:mail.inbound/provider-message-id i)]
   :mail/inbound i})
