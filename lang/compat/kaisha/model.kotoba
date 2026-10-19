(ns kaisha.model
  "EDN-native communication space (Slack/Teams 相当): one space per company
  (会社), channels, threaded messages, mentions, reactions, read markers.
  Pure data + pure functions; hosts render/persist/transport it elsewhere.")

(defn space
  ([id] (space id {}))
  ([id attrs]
   (merge {:kaisha/id id
           :kaisha/type :space
           :kaisha/name id
           :kaisha/members {}
           :kaisha/channels {}
           :kaisha/read {}}
          attrs)))

(defn member
  ([id] (member id {}))
  ([id attrs]
   (merge {:kaisha/id id
          :kaisha/handle id
           :kaisha/display-name id
           :kaisha/did nil}
          attrs)))

(defn channel
  ([id] (channel id {}))
  ([id attrs]
   (merge {:kaisha/id id
           :kaisha/name id
           :kaisha/topic nil
           :kaisha/visibility :public
           :kaisha/members #{}
           :kaisha/messages {}
           :kaisha/archived? false}
          attrs)))

(defn message
  ([id] (message id {}))
  ([id attrs]
   (merge {:kaisha/id id
           :kaisha/author nil
           :kaisha/body ""
           :kaisha/at nil
           :kaisha/thread nil
           :kaisha/reactions {}
           :kaisha/edited-at nil}
          attrs)))

(defn add-member [sp m]
  (assoc-in sp [:kaisha/members (:kaisha/id m)] m))

(defn member-by-id [sp id]
  (get-in sp [:kaisha/members id]))

(defn add-channel [sp ch]
  (assoc-in sp [:kaisha/channels (:kaisha/id ch)] ch))

(defn channel-by-id [sp id]
  (get-in sp [:kaisha/channels id]))

(defn join [sp channel-id member-id]
  (update-in sp [:kaisha/channels channel-id :kaisha/members] (fnil conj #{}) member-id))

(defn leave [sp channel-id member-id]
  (update-in sp [:kaisha/channels channel-id :kaisha/members] disj member-id))

(defn post [sp channel-id msg]
  (assoc-in sp [:kaisha/channels channel-id :kaisha/messages (:kaisha/id msg)] msg))

(defn reply [sp channel-id parent-id msg]
  (post sp channel-id (assoc msg :kaisha/thread parent-id)))

(defn message-by-id [sp channel-id msg-id]
  (get-in sp [:kaisha/channels channel-id :kaisha/messages msg-id]))

(defn react [sp channel-id msg-id emoji member-id]
  (update-in sp [:kaisha/channels channel-id :kaisha/messages msg-id :kaisha/reactions emoji]
             (fnil conj #{}) member-id))

(defn unreact
  "Withdraw `member-id`'s `emoji` reaction. Drops the emoji key entirely
  once the last reactor leaves, so a message never carries
  `{\"+1\" #{}}` -- an empty reactor set still renders as a visible
  zero-count badge in every host that iterates the reactions map."
  [sp channel-id msg-id emoji member-id]
  (let [path [:kaisha/channels channel-id :kaisha/messages msg-id :kaisha/reactions]
        remaining (disj (get-in sp (conj path emoji) #{}) member-id)]
    (if (seq remaining)
      (assoc-in sp (conj path emoji) remaining)
      (update-in sp path dissoc emoji))))

(defn edit
  "Replace a message body and stamp `:kaisha/edited-at`. The `at` argument
  is the host's clock -- this model never reads a clock itself, the same
  reason `message` takes `:kaisha/at` from its caller."
  [sp channel-id msg-id body at]
  (if (message-by-id sp channel-id msg-id)
    (update-in sp [:kaisha/channels channel-id :kaisha/messages msg-id]
               assoc :kaisha/body body :kaisha/edited-at at)
    sp))

(defn set-topic [sp channel-id topic]
  (if (channel-by-id sp channel-id)
    (assoc-in sp [:kaisha/channels channel-id :kaisha/topic] topic)
    sp))

(defn archive
  "Close a channel to new activity. Archiving is reversible and never
  deletes messages -- `messages-in-order`/`thread` keep working, so an
  archived channel stays readable exactly as Slack/Teams leave it."
  [sp channel-id]
  (if (channel-by-id sp channel-id)
    (assoc-in sp [:kaisha/channels channel-id :kaisha/archived?] true)
    sp))

(defn unarchive [sp channel-id]
  (if (channel-by-id sp channel-id)
    (assoc-in sp [:kaisha/channels channel-id :kaisha/archived?] false)
    sp))

(defn messages-in-order
  "Top-level messages of a channel (thread replies excluded), oldest first."
  [sp channel-id]
  (->> (vals (get-in sp [:kaisha/channels channel-id :kaisha/messages]))
       (remove :kaisha/thread)
       (sort-by (juxt :kaisha/at :kaisha/id))
       vec))

(defn thread
  "Parent message followed by its replies, oldest first."
  [sp channel-id parent-id]
  (let [parent (message-by-id sp channel-id parent-id)
        replies (->> (vals (get-in sp [:kaisha/channels channel-id :kaisha/messages]))
                     (filter #(= parent-id (:kaisha/thread %)))
                     (sort-by (juxt :kaisha/at :kaisha/id)))]
    (when parent (vec (cons parent replies)))))

(defn mentions
  "Handles mentioned as @handle in a message body."
  [body]
  (->> (re-seq #"@([A-Za-z0-9_-]+)" (or body ""))
       (map second)
       set))

(defn mentioned?
  "Does this message mention the member (by handle)?"
  [sp msg member-id]
  (let [handle (:kaisha/handle (member-by-id sp member-id))]
    (boolean (and handle (contains? (mentions (:kaisha/body msg)) handle)))))

(defn visible-channels
  "Channels the member can see: public ones plus private ones they belong to."
  [sp member-id]
  (->> (vals (:kaisha/channels sp))
       (filter #(or (= :public (:kaisha/visibility %))
                    (contains? (:kaisha/members %) member-id)))
       (sort-by :kaisha/id)
       vec))

(defn active-channels
  "`visible-channels` minus the archived ones -- the default sidebar list.
  Archived channels stay visible and readable through `visible-channels`;
  this is the narrower view a host shows by default, not an access rule."
  [sp member-id]
  (vec (remove :kaisha/archived? (visible-channels sp member-id))))

(defn mark-read [sp member-id channel-id msg-id]
  (assoc-in sp [:kaisha/read member-id channel-id] msg-id))

(defn unread
  "Top-level messages after the member's read marker, oldest first. If the
  marker doesn't match any top-level message (e.g. it points at a thread
  reply -- mark-read never validates that msg-id is top-level -- or the
  marked message was since deleted/moved), fails open and treats
  everything as unread rather than silently reporting nothing: with
  drop-while finding no match, it would otherwise consume the entire
  sequence and `rest` on the resulting empty seq stays empty, hiding
  genuinely unread messages. Under-reporting unread is a worse failure
  mode than over-reporting."
  [sp member-id channel-id]
  (let [marker (get-in sp [:kaisha/read member-id channel-id])
        msgs (messages-in-order sp channel-id)]
    (if (or (nil? marker) (not-any? #(= marker (:kaisha/id %)) msgs))
      msgs
      (->> msgs
           (drop-while #(not= marker (:kaisha/id %)))
           rest
           vec))))

(defn seed-space []
  (-> (space "gftd" {:kaisha/name "GFTD"})
      (add-member (member "jun" {:kaisha/display-name "Jun Kawasaki"}))
      (add-channel (channel "general" {:kaisha/topic "company-wide announcements"}))
      (join "general" "jun")
      (post "general" (message "m-1" {:kaisha/author "jun"
                                      :kaisha/body "Welcome to kaisha."
                                      :kaisha/at "2026-07-07T00:00:00Z"}))))
