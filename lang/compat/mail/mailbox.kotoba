(ns mail.mailbox
  "Portable mailbox/thread/label state. Transport and persistence are injected."
  (:refer-clojure :exclude [deliver])
  (:require [clojure.string :as str]))

(def system-labels #{:inbox :sent :drafts :spam :trash :starred :important})

(defn mailbox [id address]
  {:mailbox/id id
   :mailbox/address (str/lower-case address)
   :mailbox/messages {}
   :mailbox/threads {}
   :mailbox/labels system-labels
   :mailbox/used-bytes 0})

(defn- body-bytes [message]
  (reduce + 0 (map #(count (str (:mail.part/body %))) (:mail/parts message))))

(defn message-entry [id thread-id message attrs]
  {:mailbox.message/id id
   :mailbox.message/thread-id thread-id
   :mailbox.message/message message
   :mailbox.message/attachments (vec (:attachments attrs))
   :mailbox.message/labels (set (or (:labels attrs) #{:inbox}))
   :mailbox.message/read? (boolean (:read? attrs))
   :mailbox.message/received-at (:received-at attrs)
   :mailbox.message/size-bytes (or (:size-bytes attrs) (body-bytes message))})

(defn deliver
  "Idempotently add a message and attach it to a thread."
  [box entry]
  (let [id (:mailbox.message/id entry)
        thread-id (:mailbox.message/thread-id entry)]
    (if (get-in box [:mailbox/messages id])
      box
      (-> box
          (assoc-in [:mailbox/messages id] entry)
          (update-in [:mailbox/threads thread-id]
                     (fn [thread]
                       (-> (or thread {:mailbox.thread/id thread-id
                                       :mailbox.thread/message-ids []})
                           (update :mailbox.thread/message-ids conj id))))
          (update :mailbox/used-bytes + (:mailbox.message/size-bytes entry))))))

(defn set-read [box message-id read?]
  (assoc-in box [:mailbox/messages message-id :mailbox.message/read?] (boolean read?)))

(defn add-label [box message-id label]
  (-> box
      (update :mailbox/labels conj label)
      (update-in [:mailbox/messages message-id :mailbox.message/labels] conj label)))

(defn remove-label [box message-id label]
  (update-in box [:mailbox/messages message-id :mailbox.message/labels] disj label))

(defn trash [box message-id]
  (-> box
      (remove-label message-id :inbox)
      (add-label message-id :trash)))

(defn thread [box thread-id]
  (let [ids (get-in box [:mailbox/threads thread-id :mailbox.thread/message-ids])]
    (mapv #(get-in box [:mailbox/messages %]) ids)))

(defn messages-with-label [box label]
  (->> (:mailbox/messages box) vals
       (filter #(contains? (:mailbox.message/labels %) label)) vec))

(defn unread-count [box]
  (count (filter #(and (contains? (:mailbox.message/labels %) :inbox)
                       (not (:mailbox.message/read? %)))
                 (vals (:mailbox/messages box)))))

(defn search
  "Case-insensitive search over envelope, subject and body with optional filters."
  [box query {:keys [label unread?]}]
  (let [needle (str/lower-case (or query ""))
        matches? (fn [entry]
                   (let [m (:mailbox.message/message entry)
                         haystack (str/lower-case
                                   (str (:mail/from m) " " (:mail/to m) " " (:mail/cc m) " "
                                        (:mail/subject m) " "
                                        (str/join " " (map :mail.part/body (:mail/parts m)))))]
                     (and (or (str/blank? needle) (str/includes? haystack needle))
                          (or (nil? label) (contains? (:mailbox.message/labels entry) label))
                          (or (nil? unread?) (= unread? (not (:mailbox.message/read? entry)))))))]
    (->> (:mailbox/messages box) vals (filter matches?)
         (sort-by :mailbox.message/received-at #(compare %2 %1)) vec)))
