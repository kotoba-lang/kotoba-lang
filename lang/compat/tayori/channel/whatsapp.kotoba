(ns tayori.channel.whatsapp
  "WhatsApp Channel via the WhatsApp Business Cloud API (Meta Graph API) —
  `send-reply!` posts a text message to `/{phone-number-id}/messages`.
  Inbound only ever arrives via Meta's webhook push (there is no
  conversation-history polling endpoint), so `list-new-messages` is honestly
  empty here — a real deployment ingests via a webhook handler that calls
  `tayori.operation`'s `:message/ingest` directly, not by polling this port.

  I/O injected (`:http-fn` `:json-write` `:json-read`
  `:creds {:phone-number-id :access-token}`), same contract as
  `tayori.channel.email`/`slack`. Real binding is untested (needs Meta
  Business verification) — see `tayori.channel/mock-channel` for the runnable
  default."
  (:require [tayori.channel :as ch]))

(defn whatsapp-channel [{:keys [http-fn json-write creds]}]
  (let [{:keys [phone-number-id access-token]} creds
        messages-url (str "https://graph.facebook.com/v20.0/" phone-number-id "/messages")]
    (reify ch/Channel
      (fetch-thread [_ thread-id] {:id thread-id})
      (list-new-messages [_ _thread-id] [])
      (send-reply! [_ thread body]
        (http-fn {:url messages-url :method :post
                  :headers {"Authorization" (str "Bearer " access-token)
                            "Content-Type" "application/json"}
                  :body (json-write {:messaging_product "whatsapp"
                                     :to (:external-id thread)
                                     :type "text"
                                     :text {:body body}})})))))
