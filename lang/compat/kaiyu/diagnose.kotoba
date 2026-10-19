(ns kaiyu.diagnose
  "What a 回遊 report SAYS is wrong — the judgment half, kept pure and separate
  from both the measuring and the acting.

  `kaiyu.core` decides what may be recorded. This decides what the recording
  means, and it is deliberately the only place that does: an orchestrator that
  both fetches and judges ends up with its rules spread through its I/O, where
  they cannot be tested and quietly become whatever the last incident made
  them.

  Every finding here is a QUESTION a human can answer, not a verdict. The data
  is counts and dates with no visitor identity (that is the whole design), so
  it can locate where attention stops and where it never arrives — it cannot
  say why. A finding that claimed to know why would be inventing the part the
  measurement deliberately does not collect.

  ## The one rule that matters

  **Absence of rows is never evidence.** `kaiyu.core/measurement-state`
  distinguishes `:not-measured` / `:partial` / `:measured`, and every rule here
  refuses to fire on anything but `:measured`. The failure this prevents is the
  expensive one: on 2026-08-07 `/_metrics/audience` answered 200 with zeros for
  a day while the table held 595 pageviews, because every read threw and was
  swallowed. A loop that judged that report would have filed 『誰も来ていない』
  issues, and every one of them would have been about a broken read."
  (:require [clojure.string :as str]
            [kaiyu.core :as kaiyu]))

(def severities
  "Ordered. `:blocked` is not a severity of the site — it is a severity of the
  MEASUREMENT, and it outranks everything because a broken instrument makes the
  other findings meaningless rather than merely less certain."
  [:blocked :high :medium :low])

(def ^:private severity-rank (zipmap severities (range)))

(defn- finding
  [id severity title question evidence]
  {:id id :severity severity :title title :question question :evidence evidence})

;; ───────────────────────── rules ─────────────────────────

(defn measurement-findings
  "Rules about the instrument, checked before any rule about the site.

  A section that is `:not-measured` inside a window the site was live for is
  either a beacon that stopped or a read that throws — both are urgent, and
  neither is a fact about visitors."
  [{:keys [window sections site-live-since]}]
  (keep (fn [[section {:keys [state collected-since rows]}]]
          (cond
            (and (= :not-measured state)
                 (or (nil? site-live-since)
                     (<= (compare site-live-since (:from window)) 0)))
            (finding (keyword "kaiyu.measurement" (str (name section) "-not-measured"))
                     :blocked
                     (str (name section) " が測れていない")
                     (str "この window で " (name section)
                          " は 1 行も無く、収集開始日も記録されていない。"
                          "beacon が動いていないのか、読み取りが失敗しているのか、"
                          "本当に誰も来ていないのか——先にこれを決めないと、他の所見は読めない。")
                     {:section section :state state :collected-since collected-since
                      :window window})

            (and (= :measured state) (empty? rows))
            (finding (keyword "kaiyu.measurement" (str (name section) "-empty-while-measured"))
                     :high
                     (str (name section) " は測れているのに 0 行")
                     (str "収集は " collected-since " から続いているのに、この window の行が 0。"
                          "計測は生きていて到達が無い、という読みでよいか。")
                     {:section section :collected-since collected-since :window window})

            :else nil))
        sections))

(defn dead-end-findings
  "Routes people reach and never leave.

  A route with inbound edges and no outbound ones is where the journey stops.
  That is not automatically bad — a checkout confirmation SHOULD end it — so
  the finding names the route and asks, rather than asserting a defect."
  [{:keys [transitions min-visits]}]
  (let [min-visits (or min-visits 5)
        inbound (reduce (fn [m {:keys [to count]}] (update m to (fnil + 0) count)) {} transitions)
        outbound (reduce (fn [m {:keys [from count]}] (update m from (fnil + 0) count)) {} transitions)]
    (->> inbound
         (filter (fn [[route n]]
                   (and (>= n min-visits)
                        (zero? (get outbound route 0))
                        (not= route kaiyu/other-route))))
         (sort-by (comp - val))
         (map (fn [[route n]]
                (finding (keyword "kaiyu.journey" (str "dead-end-" route))
                         :medium
                         (str "「" route "」から先に進んだ人がいない")
                         (str n " 件の遷移がこの面に入り、出ていく辺が 1 件も無い。"
                              "ここで終わってよい面か（完了・退出が正しい面か）、"
                              "それとも次に行く先が見えていないのか。")
                         {:route route :inbound n :outbound 0})))
         vec)))

(defn attention-findings
  "Routes people reach and leave immediately.

  Reads the dwell distribution per route: if the shortest bucket dominates a
  route that is not a redirect-ish surface, the page is either not answering
  the question that brought them or not showing that it does."
  [{:keys [dwell min-samples short-share]}]
  (let [min-samples (or min-samples 10)
        threshold (or short-share 0.7)
        by-route (group-by :route dwell)]
    (->> by-route
         (keep (fn [[route rows]]
                 (let [total (reduce + 0 (map :count rows))
                       shortest (reduce + 0 (map :count (filter #(= "lt10" (:bucket %)) rows)))]
                   (when (and (>= total min-samples)
                              (>= (/ (double shortest) total) threshold))
                     (finding (keyword "kaiyu.attention" (str "bounce-" route))
                              :high
                              (str "「" route "」は " (Math/round (* 100.0 (/ (double shortest) total)))
                                   "% が 10 秒未満で離れている")
                              (str total " 件中 " shortest " 件が最短バケット。"
                                   "この面は来た人の問いに答えているか、"
                                   "答えていることが見えているか。")
                              {:route route :samples total :under-10s shortest})))))
         (sort-by (comp - :samples :evidence))
         vec)))

(defn reach-findings
  "Routes that exist and nobody arrives at.

  Named from the site's own vocabulary, so the absence is about a page that was
  built — not about a name nobody chose. Low severity on purpose: a page with
  no traffic may be perfectly deliberate."
  [{:keys [vocabulary transitions dwell]}]
  (let [seen (into #{} (concat (map :to transitions) (map :from transitions) (map :route dwell)))]
    (->> (sort (remove seen vocabulary))
         (map (fn [route]
                (finding (keyword "kaiyu.reach" (str "unreached-" route))
                         :low
                         (str "「" route "」に誰も来ていない")
                         (str "この window で " route " への遷移も滞在も 1 件も無い。"
                              "導線が無いのか、要らない面なのか。")
                         {:route route})))
         vec)))

(defn entry-concentration-findings
  "One arrival bucket carrying nearly everything.

  Not a defect — most sites have one dominant channel — but it is the single
  fact most worth knowing before spending on any other channel, and it changes
  what every other finding is worth."
  [{:keys [visits min-total dominant-share]}]
  (let [total (reduce + 0 (map :count visits))
        threshold (or dominant-share 0.9)]
    (when (>= total (or min-total 20))
      (when-let [{:keys [source count]} (->> visits (sort-by (comp - :count)) first)]
        (when (>= (/ (double count) total) threshold)
          [(finding (keyword "kaiyu.acquisition" (str "single-channel-" source))
                    :medium
                    (str "到達の " (Math/round (* 100.0 (/ (double count) total))) "% が「" source "」")
                    (str total " 件中 " count " 件が 1 つの入口。"
                         "この入口が止まったときに残るものがあるか。")
                    {:source source :visits count :total total})])))))

;; ───────────────────────── entry point ─────────────────────────

(defn diagnose
  "A kaiyu report → ranked findings.

  `report` is what a site's read face returns, normalized to:

    {:site \"shinshi.club\"
     :window {...}                       ; kaiyu.core/window
     :vocabulary #{...}                  ; the site's route set
     :site-live-since \"YYYY-MM-DD\"     ; optional
     :sections {:visits {...} :dwell {...} :transitions {...}}}

  where each section is a `kaiyu.core/section` map.

  **Measurement findings short-circuit everything.** If the instrument is in
  doubt, the site findings are not reported at all — not sorted below, not
  included. Reporting both would invite acting on the ones that are easier to
  act on, which are exactly the ones that might be artefacts."
  [{:keys [sections vocabulary] :as report}]
  (let [measurement (vec (measurement-findings report))
        blocked (filter #(= :blocked (:severity %)) measurement)]
    (if (seq blocked)
      {:site (:site report)
       :window (:window report)
       :findings (vec (sort-by (comp severity-rank :severity) measurement))
       :blocked? true}
      (let [rows (fn [k] (get-in sections [k :rows] []))
            findings (concat measurement
                             (attention-findings {:dwell (rows :dwell)})
                             (dead-end-findings {:transitions (rows :transitions)})
                             (entry-concentration-findings {:visits (rows :visits)})
                             (reach-findings {:vocabulary (or vocabulary #{})
                                              :transitions (rows :transitions)
                                              :dwell (rows :dwell)}))]
        {:site (:site report)
         :window (:window report)
         :findings (vec (sort-by (juxt (comp severity-rank :severity) :id) findings))
         :blocked? false}))))

(defn top-finding
  "The one thing to work on. Returns nil when there is nothing to say — which
  a loop must treat as a valid outcome and not as a reason to lower the bar.
  A loop that must produce an issue every round will produce noise on the
  rounds when the site is fine, and noise is what makes a queue unread."
  [diagnosis]
  (first (:findings diagnosis)))

(defn ->issue
  "A finding → the issue body a human reads in the queue.

  Deliberately not a fix. The data cannot say why, so an issue that proposed a
  remedy would be dressing a guess as an inference. It states what was
  measured, over what window, and the question to answer."
  [{:keys [site window]} {:keys [id severity title question evidence]}]
  {:kind :kaizen/open-issue
   :id (str "kaizen:" site ":" (name id) ":" (:from window) "_" (:to window))
   :severity severity
   :title (str "[" site "] " title)
   :body (str/join
          "\n"
          [(str "## 測ったこと")
           (str "- サイト: " site)
           (str "- 期間: " (:from window) " 〜 " (:to window) "（両端含む）")
           (str "- 根拠: " (pr-str evidence))
           ""
           "## 答えるべき問い"
           question
           ""
           "## この issue が言っていないこと"
           (str "回遊の計測は counts と dates だけで、訪問者を識別しない。"
                "どこで注意が止まり、どこに届いていないかは分かるが、"
                "**なぜ**は分からない。原因の推測はここには書かない。")])})
