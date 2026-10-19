(ns mail.draft
  (:require [mail.message :as message]))

(defn draft
  ([id mail-message] (draft id mail-message {}))
  ([id mail-message attrs]
   (message/assert-valid-message mail-message)
   (merge {:mail.draft/type :mail/draft
           :mail.draft/id id
           :mail.draft/status :draft
           :mail.draft/message mail-message
           :mail.draft/approvals []}
          attrs)))

(defn approve [d approval]
  (-> d
      (update :mail.draft/approvals conj approval)
      (assoc :mail.draft/status :approved)))

(defn approved? [d]
  (= :approved (:mail.draft/status d)))

(defn send-effect
  ([d] (send-effect d {}))
  ([d attrs]
   (when-not (approved? d)
     (throw (ex-info "mail draft must be approved before send-effect"
                     {:mail.draft/id (:mail.draft/id d)
                      :mail.draft/status (:mail.draft/status d)})))
   (merge {:itonami.effect/type :external-send
           :mail.effect/type :mail/send
           :mail.effect/draft-id (:mail.draft/id d)
           :mail.effect/message (:mail.draft/message d)
           :mail.effect/status :proposed
           :mail.effect/required-capabilities #{"mail/send"}}
          attrs)))

(defn draft-fact [d]
  {:kotoba/type :mail/draft
   :kotoba/id [:mail/draft (:mail.draft/id d)]
   :mail/draft d})
