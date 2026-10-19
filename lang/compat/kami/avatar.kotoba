(ns kami.avatar
  "Portable M3 avatar quality-sweep and RTC presence contract.

  The SDK selects deterministic quality bands and maintains signalling state;
  hosts remain responsible for VRM animation, audio, WebRTC, and rendering.")

(def quality-order [:hero :animated :impostor :hidden])

(defn avatar-sweep
  "Assign avatar quality by distance and per-band capacity. `bands` contains
  `:hero-distance`, `:animated-distance`, and `:impostor-distance`; `capacity`
  maps quality to maximum counts. Ties are broken by stable printed id."
  [avatars observer bands capacity]
  (let [{:keys [hero-distance animated-distance impostor-distance]} bands]
    (when-not (and (vector? observer) (= 3 (count observer))
                   (every? number? observer))
      (throw (ex-info "avatar: observer must be a numeric vec3" {:observer observer})))
    (when-not (every? #(nat-int? (get capacity %)) [:hero :animated :impostor])
      (throw (ex-info "avatar: capacity values must be non-negative integers"
                      {:capacity capacity})))
    (when-not (and (every? #(and (number? %) (not (neg? %)))
                            [hero-distance animated-distance impostor-distance])
                   (<= hero-distance animated-distance impostor-distance))
      (throw (ex-info "avatar: distances must be non-negative and ordered" {:bands bands})))
    (let [ranked (sort-by (juxt :distance-squared (comp pr-str :avatar/id))
                          (map (fn [{:avatar/keys [position] :as avatar}]
                                 (when-not (and (some? (:avatar/id avatar))
                                                (vector? position) (= 3 (count position))
                                                (every? number? position))
                                   (throw (ex-info "avatar: invalid avatar" {:avatar avatar})))
                                 (assoc avatar :distance-squared
                                        (reduce + (map (fn [x y] (let [d (- x y)] (* d d)))
                                                       position observer))))
                               avatars))]
      (:assigned
       (reduce (fn [{:keys [counts] :as result} avatar]
                 (let [d2 (:distance-squared avatar)
                       eligible (cond
                                  (<= d2 (* hero-distance hero-distance)) [:hero :animated :impostor]
                                  (<= d2 (* animated-distance animated-distance)) [:animated :impostor]
                                  (<= d2 (* impostor-distance impostor-distance)) [:impostor]
                                  :else [])
                       quality (or (first (filter #(< (get counts % 0)
                                                      (get capacity % 0)) eligible))
                                   :hidden)]
                   (-> result
                       (update-in [:counts quality] (fnil inc 0))
                       (update :assigned conj {:avatar/id (:avatar/id avatar)
                                               :avatar/quality quality
                                               :avatar/distance-squared d2}))))
               {:counts {} :assigned []} ranked)))))

(def rtc-states #{:offline :present :negotiating :connected :degraded})

(defn rtc-room [] {:rtc/peers {} :rtc/revision 0})

(def rtc-transitions
  {:offline #{:present}
   :present #{:negotiating :offline}
   :negotiating #{:connected :degraded :offline}
   :connected #{:degraded :offline}
   :degraded #{:negotiating :connected :offline}})

(defn update-peer
  "Apply an ordered peer-state observation. Revision must strictly increase per
  peer, making duplicate/out-of-order signalling a deterministic no-op."
  [room {:peer/keys [id revision state muted?] :as observation}]
  (when-not (and (some? id) (nat-int? revision) (rtc-states state)
                 (or (nil? muted?) (boolean? muted?)))
    (throw (ex-info "avatar: invalid RTC observation" {:observation observation})))
  (let [previous (get-in room [:rtc/peers id] {:peer/revision -1 :peer/state :offline})]
    (if (<= revision (:peer/revision previous))
      room
      (do
        (when-not (contains? (get rtc-transitions (:peer/state previous) #{}) state)
          (throw (ex-info "avatar: illegal RTC transition"
                          {:peer/id id :from (:peer/state previous) :to state})))
        (-> room
            (assoc-in [:rtc/peers id] {:peer/revision revision :peer/state state
                                      :peer/muted? (boolean muted?)})
            (update :rtc/revision inc))))))

(defn connected-peers [room]
  (->> (:rtc/peers room)
       (keep (fn [[id peer]] (when (= :connected (:peer/state peer)) id)))
       (sort-by pr-str)
       vec))
