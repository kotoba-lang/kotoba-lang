(ns calendar.model)

(defn calendar
  ([id] (calendar id {}))
  ([id attrs]
   (merge {:calendar/id id
           :calendar/type :calendar
           :calendar/events {}}
          attrs)))

(defn event [id attrs]
  (merge {:calendar/id id
          :calendar/title id
          :calendar/start nil
          :calendar/end nil
          :calendar/organizer nil
          :calendar/attendees []
          :calendar/rsvp {}
          :calendar/links []}
         attrs))

(defn organized-by? [ev person-id]
  (and (some? person-id) (= person-id (:calendar/organizer ev))))

(defn add-event [cal ev]
  (assoc-in cal [:calendar/events (:calendar/id ev)] ev))

(defn event-by-id [cal id]
  (get-in cal [:calendar/events id]))

(defn add-attendee [cal event-id person-id]
  (update-in cal [:calendar/events event-id :calendar/attendees] (fnil conj []) person-id))

(defn link-resource [cal event-id resource]
  (update-in cal [:calendar/events event-id :calendar/links] (fnil conj []) resource))

(def rsvp-statuses
  "PARTSTAT values this model accepts (RFC 5545 §3.2.12, minus the
  delegation/in-process states no host here models). `:needs-action` is
  never stored — it is what `rsvp-status` returns for an invited attendee
  who has not answered, so an unanswered invite and a deleted answer are
  the same state."
  #{:accepted :declined :tentative})

(defn invited? [ev person-id]
  (boolean (some #{person-id} (:calendar/attendees ev))))

(defn respond
  "Record `person-id`'s answer to `event-id`. Returns `cal` UNCHANGED when
  the person is not on the event's attendee list, or when `status` is not
  one of `rsvp-statuses` -- an answer from someone who was never invited is
  not a smaller kind of answer, it is a different event entirely, and
  storing it would let any principal write into any event's rsvp map by
  naming an id. Callers that need to tell 'recorded' from 'ignored' apart
  must compare the returned calendar, or check `invited?` first; a silent
  no-op is deliberate here because the host endpoints already gate on
  identity and this is the model's last line of defence, not its first."
  [cal event-id person-id status]
  (let [ev (event-by-id cal event-id)]
    (if (and ev (invited? ev person-id) (contains? rsvp-statuses status))
      (assoc-in cal [:calendar/events event-id :calendar/rsvp person-id] status)
      cal)))

(defn rsvp-status
  "`person-id`'s answer, or `:needs-action` when they were invited and have
  not answered. `nil` when they were never invited at all -- callers that
  conflate the two end up showing 'awaiting reply' next to people who were
  never asked."
  [ev person-id]
  (cond
    (not (invited? ev person-id)) nil
    :else (get (:calendar/rsvp ev) person-id :needs-action)))

(defn attendee-responses
  "Dense {person-id -> status} over every invited attendee, including the
  `:needs-action` ones the sparse `:calendar/rsvp` map omits."
  [ev]
  (into {} (map (juxt identity #(rsvp-status ev %))) (:calendar/attendees ev)))

(defn attendees-with-status [ev status]
  (->> (:calendar/attendees ev)
       (filter #(= status (rsvp-status ev %)))
       vec))

(defn events-in-order [cal]
  (->> (vals (:calendar/events cal))
       (sort-by (juxt :calendar/start :calendar/id))
       vec))

(defn overlaps? [a b]
  (and (:calendar/start a) (:calendar/end a)
       (:calendar/start b) (:calendar/end b)
       (neg? (compare (:calendar/start a) (:calendar/end b)))
       (neg? (compare (:calendar/start b) (:calendar/end a)))))

(defn conflicts
  "Events that `person-id` has already accepted (or not yet answered) which
  overlap `candidate`, oldest first. `:declined` events are NOT conflicts --
  that is the whole point of declining -- and `candidate` never conflicts
  with itself, so this is safe to call while rescheduling an existing
  event rather than only when proposing a new one."
  [cal person-id candidate]
  (->> (events-in-order cal)
       (remove #(= (:calendar/id %) (:calendar/id candidate)))
       (filter #(contains? #{:accepted :needs-action :tentative} (rsvp-status % person-id)))
       (filter #(overlaps? % candidate))
       vec))

(defn free? [cal person-id candidate]
  (empty? (conflicts cal person-id candidate)))

(defn seed-calendar []
  (-> (calendar "gftd-calendar")
      (add-event (event "weekly-briefing" {:calendar/title "Weekly briefing"
                                           :calendar/start "2026-07-01T09:00:00Z"
                                           :calendar/end "2026-07-01T09:30:00Z"
                                           :calendar/links [{:kind :briefing :id "weekly"}]}))))
