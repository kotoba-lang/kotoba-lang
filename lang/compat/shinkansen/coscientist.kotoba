(ns shinkansen.coscientist
  "The framework's UI/UX kaizen loop — Generate → Reflect → Rank (Elo) →
  Evolve → Meta — with `shinkansen.audit` as the judge.

  Ported from the workspace's Co-Scientist pattern (keiei-arbor engine
  ADR-2606141500; isekai.ux ADR-0007; 90-docs/design-quality/coscientist.cljk)
  and made a FRAMEWORK module so every shinkansen consumer runs the same
  loop over its own emitted documents. The judge is deterministic: ranking
  is reproducible and never an LLM debate. The founding rule of that lineage
  holds here verbatim — an unmeasured metric is theater — which is why the
  loop refuses to converge on an audit that has unmeasured axes.

  Generate is the offline/heuristic path: one grounded hypothesis per
  aggregated finding, from a table that names WHO owns the fix
  (:consumer — the app's view / CSS; :deploy — the publish step; :framework
  — this repo) and its effort. An LLM Generate (proposing fixes beyond the
  audit's axes) is a follow-up; the heuristic path already demonstrates the
  full loop end-to-end and is what a gate can run.

  Pure .cljc, deterministic, no I/O: the caller renders documents, calls
  `kaizen-cycle`, and persists the iteration (append-only measurements)."
  (:require [clojure.string :as str]
            [shinkansen.audit :as audit]))

;; --- Generate: one grounded hypothesis per finding -----------------------

(def hypothesis-table
  "axis → the change that closes it, who owns it, effort. Every row was
   written against a failure measured on kotoba.cloud/account (2026-09-15)."
  {:assets-resolve
   {:owner :deploy :effort :S
    :change "publish from a tree in which every referenced same-origin asset exists; gate the deploy on this audit with the asset set (a document whose script 404s never hydrates — every panel stays 'loading…' for every visitor)"}
   :unique-ids
   {:owner :consumer :effort :S
    :change "give each id one element: page-level actions (refresh) render once, per-panel controls get per-panel ids"}
   :nav-one-home
   {:owner :consumer :effort :S
    :change "one nav entry per destination and one data-current; a second label for the same page is a duplicate fact, not a shortcut"}
   :nav-before-content
   {:owner :consumer :effort :M
    :change "below the lg band collapse the nav into a <details> disclosure (summary = menu) so the first screen is content; keep it as a fixed column on desktop"}
   :skip-link
   {:owner :consumer :effort :S
    :change "emit a[href=#main] as the first focusable element of every document that puts a nav before main"}
   :idle-pending
   {:owner :consumer :effort :M
    :change "render the idle document as one role=status line plus the one next action (sign in); keep the console body hidden until the session is known and reveal it with each cell already mapped to a state"}
   :idle-disabled
   {:owner :consumer :effort :S
    :change "hide a control until its state is known, or enable it with the reason beside it — never a bare disabled button in the idle document"}
   :repeated-actions
   {:owner :consumer :effort :S
    :change "one page-level action for refresh/retry; per-panel repeats are removed"}
   :plain-labels
   {:owner :consumer :effort :S
    :change "headings and labels name the person's task (本人確認 / 組織 / チーム); protocol names, provider hosts and field identifiers move under <details> or the guide"}
   :note-density
   {:owner :consumer :effort :S
    :change "keep one sentence beside each action; move explanation under <details> or link the guide"}
   :locale-path-links
   {:owner :consumer :effort :S
    :change "emit locale-free hrefs (/#…, /billing/) and let the edge negotiate by cookie (shinkansen.locale)"}
   :fixed-anchor
   {:owner :consumer :effort :S
    :change "declare inset-inline-start:0 on the fixed nav and offset the content once (body padding OR main margin, not both)"}
   :viewport
   {:owner :consumer :effort :S
    :change "carry shinkansen.viewport/viewport-meta and a media band ≤ 480px"}
   :links-resolve
   {:owner :consumer :effort :S
    :change "emit the document the nav links (or point the link at one that exists); generate the nav from the route tree so an unpublished node cannot be linked"}
   :csp-allows-assets
   {:owner :deploy :effort :S
    :change "the host's Content-Security-Policy carries 'self' in style-src / script-src for the document's own assets; pin the header in the worker smoke"}
   :pre-overflow
   {:owner :consumer :effort :S
    :change "pre{overflow-x:auto} in the shared stylesheet (a code block that clips mid-line on a phone is unreadable, not just ugly)"}
   :code-language
   {:owner :consumer :effort :S
    :change "emit every code block with cloud-kotoba-dds.code/block (server-side tokens, data-lang + language-<x> markers, a copy control) instead of a bare <pre> — one colour for everything is unreadable, and a block that names no language cannot be tokenized or announced"}
   :chrome-layers
   {:owner :consumer :effort :S
    :change "use the shell pattern (cloud-kotoba-dds.shell): the top bar carries data-chrome=top with position:sticky;top:0;z-index, the account menu carries data-chrome=float with position:absolute;z-index — chrome that scrolls away or reflows the page is not chrome"}
   :classes-styled
   {:owner :consumer :effort :S
    :change "emit the document through the shared shell (site-layout / docs-page) so it ships the stylesheet its markup was written against, or inline that CSS; a page that hand-builds its <head> with only the token bridge renders its header as a bare list of links"}
   :behaviors-delivered
   {:owner :consumer :effort :S
    :change "ship jp-go-dds.behavior/script as one file on every document that carries a data-behavior marker, and emit the marked markup through the jp-go-dds wrappers (menu-list-box / modal-dialog / tab / combobox / toast-region) so the opener, the floating popup, the roles and the aria wiring the runtime selects are all there"}})

(defn- pad2 [n] (if (< n 10) (str "0" n) (str n)))

(defn- title-case [k]
  (let [s (name k)]
    (str (str/upper-case (subs s 0 1)) (subs s 1))))

(defn generate
  "One hypothesis per aggregated finding (from `audit/audit` :findings),
   offline/heuristic — no LLM call. Unknown axes get a generic hypothesis
   owned by :framework so the gap is visible, never dropped."
  [findings]
  (vec (map-indexed
        (fn [i {:keys [axis weight files finding headroom worst-score]}]
          (let [{:keys [owner effort change]}
                (get hypothesis-table axis
                     {:owner :framework :effort :M
                      :change (str "no hypothesis row for " (name axis) " — add one to shinkansen.coscientist/hypothesis-table: " finding)})]
            {:id (str "sk-h" (inc i))
             :axis axis :title (title-case axis)
             :change change :files files :finding finding
             :weight weight :predicted-gain headroom :worst-score worst-score
             :owner owner :effort effort}))
        findings)))

;; --- Reflect ---------------------------------------------------------------

(defn reflect
  "Annotate each hypothesis with risk. :consumer + :S = low (additive view /
   CSS change inside the app, no shared library edit); :consumer + :M =
   medium (structure changes, needs the browser walk-through); :deploy and
   :framework = medium (blast radius beyond one app)."
  [hyps]
  (mapv (fn [{:keys [owner effort] :as h}]
          (assoc h :reflection
                 {:risk (cond (and (= owner :consumer) (= effort :S)) :low
                              :else :medium)
                  :note (case owner
                          :consumer "app-level view / CSS change; verify with the audit and one real-browser pass at 390 / 768 / 1440"
                          :deploy "publish-step change; verify by fetching every referenced asset from the live origin after deploy"
                          :framework "shinkansen change; lands upstream first, then the consumer pin advances")}))
        hyps))

;; --- Rank: an Elo tournament seeded by measured headroom --------------------

(defn- expected [ra rb] (/ 1.0 (+ 1.0 (Math/pow 10.0 (/ (- rb ra) 400.0)))))

(defn- bout
  "Deterministic pairwise comparison: more predicted gain wins; ties go to
   lower risk, then lower effort, then the stable id order."
  [a b]
  (let [ga (:predicted-gain a) gb (:predicted-gain b)
        risk {:low 0 :medium 1 :high 2}
        effort {:S 0 :M 1 :L 2}]
    (cond (> ga gb) 1.0
          (< ga gb) 0.0
          (< (risk (get-in a [:reflection :risk])) (risk (get-in b [:reflection :risk]))) 1.0
          (> (risk (get-in a [:reflection :risk])) (risk (get-in b [:reflection :risk]))) 0.0
          (< (effort (:effort a)) (effort (:effort b))) 1.0
          (> (effort (:effort a)) (effort (:effort b))) 0.0
          (neg? (compare (:id a) (:id b))) 1.0
          :else 0.0)))

(defn rank
  "Round-robin Elo (K=32) over the reflected hypotheses, seeded at 1200.
   Returns hypotheses sorted by :elo desc with :gain-points (predicted gain
   in overall points, weight-normalised over the whole rubric)."
  [hyps]
  (let [k 32
        ids (mapv :id hyps)
        by-id (into {} (map (juxt :id identity) hyps))
        elo (reduce (fn [elo [ia ib]]
                      (let [a (by-id ia) b (by-id ib)
                            ra (elo ia) rb (elo ib)
                            sa (bout a b) sb (- 1.0 sa)]
                        (-> elo
                            (assoc ia (+ ra (* k (- sa (expected ra rb)))))
                            (assoc ib (+ rb (* k (- sb (expected rb ra))))))))
                    (zipmap ids (repeat 1200.0))
                    (for [i (range (count ids)) j (range (inc i) (count ids))]
                      [(ids i) (ids j)]))
        total-weight (reduce + (map :weight audit/axes))]
    (->> hyps
         (map (fn [h] (assoc h
                             :elo (Math/round (elo (:id h)))
                             :gain-points (* 100.0 (/ (:predicted-gain h) total-weight)))))
         (sort-by :elo >)
         vec)))

;; --- Evolve: the batch worth shipping this iteration ------------------------

(defn evolve
  "The kaizen batch: every low-risk :consumer hypothesis, plus any :deploy
   hypothesis (a missing asset is a production outage, never deferred).
   Everything else is the roadmap for the next iteration."
  [ranked]
  (let [members (filter (fn [h] (or (= :low (get-in h [:reflection :risk]))
                                    (= :deploy (:owner h))))
                        ranked)]
    {:batch-id "sk-kaizen-batch"
     :members (mapv :id members)
     :predicted-gain-points (reduce + 0.0 (map :gain-points members))
     :deferred (mapv :id (remove (set members) ranked))}))

;; --- Meta ---------------------------------------------------------------------

(defn meta-review
  [before ranked evolved]
  (let [overall (:overall before)
        n-docs (count (:documents before))
        projected (min 100.0 (+ overall (/ (:predicted-gain-points evolved) (max 1 n-docs))))]
    {:overall overall
     :documents n-docs
     :findings (count (:findings before))
     :unmeasured (count (:unmeasured before))
     :roadmap (mapv #(select-keys % [:id :axis :owner :effort :elo :gain-points :change :files]) ranked)
     :batch evolved
     :projected-overall projected
     :converged? (and (empty? (:findings before)) (empty? (:unmeasured before)) (not (:empty? before)))}))

(defn delta
  "The kaizen proof: per-axis change between two audits of the same
   documents. Returns {:overall-before :overall-after :delta :axes [{:axis
   :before :after :delta}] :closed [...] :opened [...]} — the roadmap is a
   prediction; this is the measurement."
  [before after]
  (let [axis-mean (fn [a id]
                    (let [xs (keep (fn [[_ rep]]
                                     (let [ax (first (filter #(= (:id %) id) (:axes rep)))]
                                       (:score ax)))
                                   (:documents a))]
                      (when (seq xs) (/ (reduce + xs) (count xs)))))
        axes (vec (for [{:keys [id]} audit/axes
                        :let [b (axis-mean before id) a (axis-mean after id)]
                        :when (or b a)]
                    {:axis id :before b :after a
                     :delta (when (and a b) (- a b))}))
        f-before (set (map :axis (:findings before)))
        f-after (set (map :axis (:findings after)))]
    {:overall-before (:overall before)
     :overall-after (:overall after)
     :delta (- (:overall after) (:overall before))
     :axes axes
     :closed (vec (sort (remove f-after f-before)))
     :opened (vec (sort (remove f-before f-after)))}))

;; --- the iteration document ---------------------------------------------------

(defn- pct [x] (str (/ (Math/round (* 10.0 x)) 10.0)))

(defn iteration-md
  [n seed before meta]
  (let [{:keys [overall roadmap batch projected-overall converged?]} meta
        findings (:findings before)
        unmeasured (:unmeasured before)]
    (str
     "# shinkansen Co-Scientist Iteration " (pad2 n) " — UI/UX kaizen\n\n"
     "> Seed: " seed "\n>\n"
     "> Judge: `shinkansen.audit` (deterministic, no LLM, no browser) over "
     (count (:documents before)) " emitted document(s). Overall **" (pct overall) " / 100**.\n\n"
     (when (seq unmeasured)
       (str "## Unmeasured — the loop does not converge on these\n\n"
            (str/join "\n" (map (fn [{:keys [file axis why]}] (str "- `" file "` `" (name axis) "`: " why)) unmeasured))
            "\n\n"))
     (if converged?
       "## Converged — nothing to build on the current rubric\n\n0 open findings, 0 unmeasured axes. The next gain needs a new axis (a WCAG contrast-ratio axis over resolved tokens; a real-browser layout pass) or a new document.\n"
       (str
        "## Findings (weight × shortfall, heaviest first)\n\n"
        "| axis | documents | weight | finding |\n|---|---|---|---|\n"
        (str/join "\n" (map (fn [f] (str "| `" (name (:axis f)) "` | " (count (:files f))
                                         " | " (:weight f) " | " (:finding f) " |")) findings))
        "\n\n## Roadmap (Elo-ranked)\n\n"
        "| # | id | owner | effort | Elo | +pts | change |\n|---|---|---|---|---|---|---|\n"
        (str/join "\n" (map-indexed
                        (fn [i h] (str "| " (inc i) " | `" (:id h) "` | " (name (:owner h)) " | "
                                       (name (:effort h)) " | " (:elo h) " | +" (pct (:gain-points h))
                                       " | " (:change h) " |"))
                        roadmap))
        "\n\n## Batch shipped this iteration\n\n"
        "`" (:batch-id batch) "` = " (str/join ", " (map #(str "`" % "`") (:members batch)))
        " — projected " (pct overall) " → **" (pct projected-overall) " / 100**. "
        "Deferred: " (if (seq (:deferred batch)) (str/join ", " (map #(str "`" % "`") (:deferred batch))) "none") ".\n\n"
        "## Seed for next iteration\n\n"
        "Re-run the audit after the batch lands and record `shinkansen.coscientist/delta` — the roadmap is a prediction, the delta is the proof. Then the deferred rows.\n")))))

(defn kaizen-cycle
  "Run one iteration over already-audited documents.
     (kaizen-cycle (audit/audit docs ctx) {:n 1 :seed \"…\"})
   → {:before :hypotheses :ranked :evolved :meta :doc}"
  [before {:keys [n seed] :or {n 1 seed "shinkansen UI/UX kaizen pass"}}]
  (let [hyps (generate (:findings before))
        reflected (reflect hyps)
        ranked (rank reflected)
        evolved (evolve ranked)
        meta (meta-review before ranked evolved)]
    {:before before :hypotheses reflected :ranked ranked :evolved evolved
     :meta meta :doc (iteration-md n seed before meta)}))
