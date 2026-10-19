(ns mail.receipt)

(defn receipt
  ([effect provider result] (receipt effect provider result {}))
  ([effect provider result attrs]
   (let [message-id (:message-id result)]
     (when-not (string? message-id)
       (throw (ex-info "mail receipt requires provider message-id"
                       {:provider provider
                        :result result})))
     (merge {:mail.receipt/type :mail/receipt
             :mail.receipt/provider provider
             :mail.receipt/provider-message-id message-id
             :mail.receipt/draft-id (:mail.effect/draft-id effect)
             :mail.receipt/status (or (:status result) :accepted)}
            attrs))))

(defn receipt-fact [r]
  {:kotoba/type :mail/receipt
   :kotoba/id [:mail/receipt (:mail.receipt/provider r) (:mail.receipt/provider-message-id r)]
   :mail/receipt r})
