(ns murakumo.cluster.cosci
  "A co-scientist tournament over node-supervision and recovery designs for the
  murakumo edge fleet. Deterministic: no LLM in the loop, no network, no clock.

  Same shape as `cloud-murakumo.cosci` (Generation -> Reflection -> Ranking ->
  Proximity -> Evolution -> Meta-review), and the same discipline: **Reflection
  is hard gates, not soft scores.** A design that cannot survive a reboot does
  not get to be ranked slightly lower than one that can; it is disqualified and
  the gate that killed it is named.

  ## Why this exists

  Measured 2026-09-09 across the six reachable minis. The split was not by
  configuration, it was by uptime:

      benjamin  67d uptime  49,152 ports  48,298 TIME_WAIT  (98%)   dark
      simeon    67d uptime  49,152 ports  48,907 TIME_WAIT  (99.5%) dark
      joseph    67d uptime  16,384 ports  15,763 TIME_WAIT  (96%)   dark
      dan       24d uptime  16,384 ports       4 TIME_WAIT          live
      judah     24d uptime  16,384 ports      16 TIME_WAIT          live
      levi      24d uptime  16,384 ports       3 TIME_WAIT          live

  Two things follow, and they are the whole design problem:

  1. **The 2026-09-02 backoff contract works.** Nodes running it since their
     last reboot hold 3-16 TIME_WAIT after 24 days. It is not the cause of the
     three dark nodes.
  2. **The kernel does not reclaim TIME_WAIT on these hosts.** benjamin's count
     was byte-identical 45 s after its producer was killed. So damage taken
     before the fix is permanent for the life of the boot, and the only cure is
     a reboot.

  And then the trap. The edge worker is a **LaunchAgent**, and every node runs
  with no interactive login (`who` empty, /dev/console owned by root), so
  `~/Library/LaunchAgents` never loads and `launchctl` from ssh answers
  `125: Domain does not support specified action`. The workers that ARE running
  are orphans of a session that ended: 10d21h old on hosts that booted 24 days
  ago -- they did not start at boot, a human started them, two weeks late.

      The cure for the dark nodes is a reboot.
      A reboot is what the supervision cannot survive.

  That loop is the thing being designed away. `dark-node-days` below is not a
  metaphor for it; it is the measured 14-day gap between those two numbers."
  (:require [kotoba.lang.text :as str]))

;; ---------------------------------------------------------------------------
;; The measurements every gate stands on. Dated, so a gate cannot outlive its
;; evidence without somebody noticing the date.
;; ---------------------------------------------------------------------------

(def observations
  [{:id :no-login-session
    :on "2026-09-09"
    :what "All six reachable minis: `who` empty, /dev/console owned by root, and
           launchctl in the gui domain answers 125. LaunchAgents cannot load."
    :gates [:boot-survivable]}
   {:id :orphaned-workers
    :on "2026-09-09"
    :what "edge-join uptime 10d21h on hosts with 24d uptime: the workers did not
           start at boot. Recovery took a human ~14 days."
    :gates [:boot-survivable :human-free-recovery]}
   {:id :timewait-not-reclaimed
    :on "2026-09-09"
    :what "benjamin held 48,298 TIME_WAIT; 45 s after killing the producer the
           count was unchanged. Userland cannot recover the port range."
    ;; Supports two gates, and saying so is the point: it is why a bounded
    ;; transport is not enough on a node that already took the damage.
    :gates [:socket-bounded :human-free-recovery]}
   {:id :uptime-is-the-split
    :on "2026-09-09"
    :what "Dark nodes are the 67-day uptimes; live nodes are the 24-day ones,
           and they hold 3-16 TIME_WAIT. Exhaustion accumulates over a boot."
    :gates [:socket-bounded]}
   {:id :backoff-landed
    :on "2026-09-02"
    :what "ADR-2609021500: classified, bounded, jittered backoff shipped. Live
           nodes' 3-16 TIME_WAIT at 24 days is its effect, measured."
    :gates [:socket-bounded :backoff-contract]}
   {:id :observer-shared-fate
    :on "2026-09-02"
    :what "simeon's mesh watchdog probe failed from the node's own exhaustion
           and killed a healthy kotoba-server every two minutes."
    :gates [:observer-independent]}
   {:id :tier-drift
    :on "2026-09-09"
    :what "issachar enrolled trust-tier community while nine siblings are
           awai-secure; placement requires an exact match with no fallback."
    :gates [:enrollment-derived]}
   {:id :filevault-blocks-unattended-boot
    :on "2026-09-09"
    :what "`fdesetup status` on benjamin: FileVault is On. A headless mini does
           not complete a reboot without the pre-boot unlock, which is why the
           exhausted nodes have 67-day uptimes: nobody can restart them
           remotely, and the repair they need is a restart."
    :gates [:reboot-completes-unattended]}
   {:id :single-daemon-remote-access
    :on "2026-09-09"
    :what "levi went into swap death under a 27B load. The kernel still answers
           ping and macOS sshd still accepts TCP on 22, and the node has been
           unreachable since, because the only working way in was Tailscale SSH
           and tailscaled was what died. `ssh -vv` names the two servers: a
           tailnet connection answers `Tailscale`, a LAN connection to the same
           host answers `OpenSSH_10.0`. The second had never been exercised.
           The constrained recovery key could not help either -- its forced
           command kickstarted only the RPC worker."
    :gates [:remote-access-survives-daemon-loss]}
   {:id :fallback-path-provable
    :on "2026-09-09"
    :what "The fallback is real where it has been set up: `ssh -i ~/.ssh/murakumo
           -J dan judah@192.168.1.21` succeeded, and the head's constrained key
           reached judah over the LAN while the identical command against levi
           was refused. So the check is a control, not a belief."
    :gates [:remote-access-survives-daemon-loss]}
   {:id :silent-model-substitution
    :on "2026-09-09"
    :what "POST /v1/itonami-bots/responses with model murakumo-edge returned 200
           and served murakumo-main. A substitution, not a refusal."
    :gates [:swap-observable]}])

;; ---------------------------------------------------------------------------
;; Generation — a closed gene pool. Every option here is something this fleet
;; either runs today or could run without new hardware.
;; ---------------------------------------------------------------------------

(def dimensions
  {:supervision  [:launch-agent-gui        ; what the edge worker uses today
                  :launch-daemon-system    ; what the rpc worker already uses
                  :launch-agent-plus-autologin
                  :cron-reaper
                  :manual]
   :transport    [:connect-per-poll        ; what infer-join does today
                  :keep-alive-pool
                  :long-poll-keep-alive
                  :gateway-push]
   :backoff      [:none :fixed :classified-bounded-jittered]
   :port-recovery [:none
                   :headroom-sysctl        ; landed 2026-09-02
                   :guarded-self-reboot
                   :headroom-and-guarded-self-reboot]
   :watchdog     [:none :probe-only :proven-failure]
   :model-swap   [:in-place-restart :drain-then-swap :blue-green]
   :enrollment   [:hand-set-tier :derived-from-fleet-edn]
   ;; How the gateway can reach the node at all. Every mini sits behind a home
   ;; NAT with no inbound port, so this is not a preference -- it decides which
   ;; transports are even implementable.
   :reachability [:egress-only :kekkai-overlay]
   ;; Whether a restart finishes without a person at the machine. Measured, not
   ;; chosen: FileVault is on, so today this is :console-only on every mini.
   :unlock       [:console-only
                  :authrestart-stored-key   ; fdesetup authrestart, key on the node
                  :filevault-off]
   ;; How many independent ways in the node has. Measured, not chosen: on
   ;; 2026-09-09 every mini was :tailnet-only in practice, and one of them
   ;; proved what that means.
   :remote-access [:tailnet-only
                   :tailnet-plus-native-sshd
                   :tailnet-plus-oob-power]})

(defn generate-candidates
  ([] (generate-candidates dimensions))
  ([d]
   (for [s (:supervision d) t (:transport d) b (:backoff d)
         p (:port-recovery d) w (:watchdog d) m (:model-swap d)
         e (:enrollment d) r (:reachability d) u (:unlock d)
         ra (:remote-access d)]
     {:supervision s :transport t :backoff b :port-recovery p
      :watchdog w :model-swap m :enrollment e :reachability r :unlock u
      :remote-access ra})))

;; ---------------------------------------------------------------------------
;; Reflection — hard gates. Each returns nil when it passes, or the reason it
;; failed. Order matters only for which reason gets reported first.
;; ---------------------------------------------------------------------------

(def boot-survivable-supervision
  "Supervision that starts a worker with no interactive login.

  `:launch-agent-plus-autologin` is included and PASSES, because auto-login does
  make a LaunchAgent load. It ranks badly later for a different reason (it puts
  an unlocked GUI session on every node), which is exactly the separation this
  file is for: a security cost is a cost, not a gate."
  #{:launch-daemon-system :launch-agent-plus-autologin})

(def bounded-transports
  "Transports whose socket count does not grow without bound in uptime.

  This is the gate the kernel writes. Where TIME_WAIT is never reclaimed, a
  design that opens one connection per poll consumes the port range at a rate
  set by its cadence and can only ever end in exhaustion; the wait between polls
  changes when, not whether. Backoff is therefore not a substitute for reuse,
  and `:connect-per-poll` fails here even carrying the landed backoff."
  #{:keep-alive-pool :long-poll-keep-alive :gateway-push})

(defn- gate-boot-survivable [c]
  (when-not (contains? boot-survivable-supervision (:supervision c))
    "supervision does not start at boot without an interactive login (obs :no-login-session)"))

(defn- gate-socket-bounded [c]
  (when-not (contains? bounded-transports (:transport c))
    "connections grow with uptime on a host that never reclaims TIME_WAIT (obs :timewait-not-reclaimed)"))

(defn- gate-observer-independent [c]
  (when (= :probe-only (:watchdog c))
    "a watchdog that acts on its own failed probe shares fate with what it measures (obs :observer-shared-fate)"))

(defn- gate-human-free-recovery [c]
  ;; Userland cannot reclaim the port range, so the only recovery from a node
  ;; that has already taken the damage is a reboot. A design that cannot reboot
  ;; itself requires an operator, and the measured cost of requiring one was
  ;; 14 days. Headroom alone delays the wall; it does not cross it.
  (when-not (contains? #{:guarded-self-reboot :headroom-and-guarded-self-reboot}
                       (:port-recovery c))
    "cannot return a poisoned node to service without an operator (obs :timewait-not-reclaimed, :orphaned-workers)"))

(defn- gate-swap-observable [c]
  (when (= :in-place-restart (:model-swap c))
    "a restart-in-place leaves the node enrolled while it cannot serve the enrolled model (obs :silent-model-substitution)"))

(defn- gate-backoff-contract [c]
  ;; ADR-2609021500 decision 1 is unconditional and fleet-wide: no resident
  ;; poller ships without classification + bounded backoff + jitter. Every
  ;; transport here reconnects, including the pushed ones, so none of them is
  ;; exempt. It is a gate and not a cost because the fleet already decided it.
  (when-not (= :classified-bounded-jittered (:backoff c))
    "resident loops must classify and back off, bounded, with jitter (ADR-2609021500 decision 1, obs :backoff-landed)"))

(defn- gate-push-needs-overlay [c]
  ;; A mini behind a home NAT has no inbound port. `:gateway-push` is only
  ;; implementable when something gives the gateway an address for the node,
  ;; and in this fleet that something is the kekkai overlay. Without it, push
  ;; is not a slower design -- it is one that cannot be built, which is exactly
  ;; the difference between a gate and a cost.
  (when (and (= :gateway-push (:transport c))
             (not= :kekkai-overlay (:reachability c)))
    "gateway push needs an overlay address for a NAT-ed node (kekkai control plane)"))

(defn- gate-reboot-completes-unattended [c]
  ;; The other half of the loop, and the half nobody had measured. Supervision
  ;; that survives a reboot is worth nothing on a machine that cannot finish
  ;; one: with FileVault on and no console, `reboot` is indistinguishable from
  ;; `shutdown`. So a design that relies on rebooting to recover must also say
  ;; how the disk gets unlocked, and :console-only is not an answer a daemon
  ;; can execute.
  (when (and (contains? #{:guarded-self-reboot :headroom-and-guarded-self-reboot}
                        (:port-recovery c))
             (= :console-only (:unlock c)))
    "self-reboot on a FileVault node without an unattended unlock is a shutdown (obs :filevault-blocks-unattended-boot)"))

(defn- gate-remote-access-survives-daemon-loss [c]
  ;; The gate this tournament did not have when it ran the first time, and the
  ;; omission had a cost the same day: every design it admitted assumed the
  ;; operator could still reach the node, and none of them said why that would
  ;; be true.
  ;;
  ;; It binds any design that recovers by ACTING on the node -- self-reboot, a
  ;; watchdog that restarts something, a swap that has to be finished. All of
  ;; those are instructions somebody has to deliver, and a single userland
  ;; daemon carrying every instruction is not a recovery plan, it is the first
  ;; thing that fails. Note that it does not bind designs which recover by
  ;; doing nothing; that asymmetry is the real content.
  (when (and (or (contains? #{:guarded-self-reboot :headroom-and-guarded-self-reboot}
                            (:port-recovery c))
                 (not= :none (:watchdog c))
                 (not= :in-place-restart (:model-swap c)))
             (= :tailnet-only (:remote-access c)))
    "recovery has to reach the node, and one dead daemon takes every way in (obs :single-daemon-remote-access)"))

(defn- gate-enrollment-derived [c]
  (when (= :hand-set-tier (:enrollment c))
    "a hand-set trust tier drifts and placement has no fallback (obs :tier-drift)"))

(def gates
  [[:boot-survivable      gate-boot-survivable]
   [:socket-bounded       gate-socket-bounded]
   [:human-free-recovery  gate-human-free-recovery]
   [:observer-independent gate-observer-independent]
   [:swap-observable      gate-swap-observable]
   [:enrollment-derived   gate-enrollment-derived]
   [:backoff-contract     gate-backoff-contract]
   [:push-needs-overlay   gate-push-needs-overlay]
   [:reboot-completes-unattended gate-reboot-completes-unattended]
   [:remote-access-survives-daemon-loss gate-remote-access-survives-daemon-loss]])

(defn reflect
  "Hard pass/fail for one candidate. `:failed` is EVERY gate it fails, not the
  first: reporting only the first would make a design that fails one gate and a
  design that fails five look like the same distance from admissible."
  [candidate]
  (let [failed (vec (keep (fn [[id f]] (when-let [why (f candidate)] {:gate id :because why}))
                          gates))]
    {:candidate candidate
     :pass? (empty? failed)
     :failed failed}))

;; ---------------------------------------------------------------------------
;; Ranking — cost in node-days dark per node-year, plus operator actions.
;;
;; The rate inputs are derived from the measurement, not chosen: six nodes at
;; uptimes 24,24,24,67,67,67 days give a mean boot interval of 45.5 days, i.e.
;; about 8 reboots per node-year. That is what makes `:boot-survivable` the
;; dominant term rather than a tidy principle.
;; ---------------------------------------------------------------------------

(def cost-inputs
  {:reboots-per-node-year 8         ; derived: mean observed uptime 45.5 days
   :measured-operator-lag-days 14   ; measured: 24d uptime vs 10d21h worker age
   :operator-action-cost-days 0.5   ; a human touching a node is not free
   :exhaustion-events-per-node-year-per-unbounded-transport 1
   ;; NOT measured. The cost of operating a kekkai overlay control plane is the
   ;; one input here that is a guess, and the top two designs are separated by
   ;; it alone -- so `overlay-flip-point` exists to say where the guess stops
   ;; mattering, instead of letting a chosen number read as a finding.
   :overlay-operating-cost-days 0.08})

(defn dark-days
  "Expected node-days out of service per node-year for an admissible design.

  Only two things put a node in the dark once the gates are passed: a boot it
  does not come back from, and an exhaustion event it cannot clear itself.
  Designs that fail a gate are not costed at all -- they are not on the board."
  [{:keys [supervision transport port-recovery watchdog model-swap reachability
           unlock remote-access]
    :as _c}
   {:keys [reboots-per-node-year measured-operator-lag-days
           operator-action-cost-days] :as _inputs}]
  (let [;; A boot the supervision survives costs the reboot itself; one it does
        ;; not costs the operator lag, every time.
        per-boot (if (contains? boot-survivable-supervision supervision)
                   0.01
                   measured-operator-lag-days)
        boots (* reboots-per-node-year per-boot)
        ;; Auto-login pays a standing cost that is not downtime: an unlocked
        ;; session on every node. Priced as operator actions so it shows up in
        ;; the same column rather than being invisible.
        login-risk (if (= supervision :launch-agent-plus-autologin) 2.0 0.0)
        ;; A bounded transport still reconnects; a pooled one reconnects least.
        transport-cost (case transport
                         :gateway-push 0.0
                         :long-poll-keep-alive 0.05
                         :keep-alive-pool 0.1
                         0.0)
        ;; Self-reboot converts an exhaustion event from an operator visit into
        ;; a boot. Headroom on top delays reaching one at all.
        recovery-cost (case port-recovery
                        :headroom-and-guarded-self-reboot 0.02
                        :guarded-self-reboot 0.2
                        0.0)
        watchdog-cost (case watchdog :none 0.5 :proven-failure 0.05 0.0)
        swap-cost (case model-swap :blue-green 0.02 :drain-then-swap 0.1 0.0)
        ;; Running an overlay control plane is not free. Priced so that a design
        ;; only reaches for kekkai when the transport it unlocks pays for it.
        overlay-cost (if (= :kekkai-overlay reachability)
                       (:overlay-operating-cost-days _inputs 0.08) 0.0)
        ;; Both unattended unlocks weaken full-disk encryption, in different
        ;; amounts: a stored authrestart key is FDE that a root compromise can
        ;; use once, turning FileVault off is no FDE at all. Priced rather than
        ;; gated, because which is acceptable is the owner's call and not this
        ;; model's -- but it must not be free, or the tournament would
        ;; recommend disabling disk encryption to save a reboot.
        ;; A second SSH path costs a key in a file. Out-of-band power costs
        ;; hardware and a place to put it, and is priced above the thing it
        ;; would replace so the tournament does not buy a PDU to avoid writing
        ;; one line into authorized_keys.
        access-cost (case remote-access
                      :tailnet-plus-native-sshd 0.01
                      :tailnet-plus-oob-power 0.20
                      0.0)
        unlock-cost (case unlock
                      :authrestart-stored-key 0.15
                      :filevault-off 0.60
                      0.0)]
    (+ boots login-risk transport-cost recovery-cost watchdog-cost swap-cost
       overlay-cost unlock-cost access-cost
       (* 0 operator-action-cost-days))))

(defn- elo-update [ra rb score-a k]
  (let [ea (/ 1.0 (+ 1.0 (Math/pow 10 (/ (- rb ra) 400.0))))]
    [(+ ra (* k (- score-a ea)))
     (+ rb (* k (- (- 1 score-a) (- 1 ea))))]))

(defn rank
  "Round-robin Elo over cost. Elo rather than a plain sort because the cost
  model is a model: when two designs are within noise of each other the
  tournament should say so, and Elo's spread is that statement. Deterministic --
  the pairing order is the candidate order."
  ([admitted] (rank admitted cost-inputs))
  ([admitted inputs]
   (let [costed (mapv (fn [c] (assoc c :cost (dark-days c inputs))) admitted)
         n (count costed)
         init (vec (repeat n 1200.0))
         ratings (reduce
                  (fn [rs [i j]]
                    (let [ci (:cost (nth costed i)) cj (:cost (nth costed j))
                          score (cond (< ci cj) 1.0 (> ci cj) 0.0 :else 0.5)
                          [ri rj] (elo-update (nth rs i) (nth rs j) score 24)]
                      (assoc rs i ri j rj)))
                  init
                  (for [i (range n) j (range (inc i) n)] [i j]))]
     (->> (map-indexed (fn [i c] (assoc c :elo (nth ratings i))) costed)
          (sort-by (juxt :cost (comp - :elo)))
          vec))))

;; ---------------------------------------------------------------------------
;; Proximity — near-ties are one finding, not several. Without this the report
;; reads as if the tournament distinguished designs it did not.
;; ---------------------------------------------------------------------------

(defn cluster
  ([ranked] (cluster ranked 0.02))
  ([ranked tolerance]
   (reduce (fn [acc c]
             (let [head (peek acc)
                   lead (:cost (first head))]
               (if (and head (<= (Math/abs (- (:cost c) lead))
                                 (* tolerance (max 1e-9 (Math/abs lead)))))
                 (conj (pop acc) (conj head c))
                 (conj acc [c]))))
           []
           ranked)))

;; ---------------------------------------------------------------------------
;; Evolution — elitism + gene crossover. The pool is closed, so this cannot
;; invent an option; it recombines. Kept because a recombination can pass gates
;; that neither parent passed.
;; ---------------------------------------------------------------------------

(defn evolve
  "One generation: keep the elite, cross the top pair gene-wise, and return the
  distinct union. Deterministic."
  [ranked elite-n]
  (let [elite (vec (take elite-n ranked))
        [a b] (take 2 elite)
        crossed (when (and a b)
                  (for [k [:supervision :transport :backoff :port-recovery
                           :watchdog :model-swap :enrollment :reachability
                           :unlock :remote-access]]
                    (assoc (dissoc a :cost :elo) k (get b k))))]
    (vec (distinct (concat (map #(dissoc % :cost :elo) elite) crossed)))))

;; ---------------------------------------------------------------------------
;; Supervisor / meta-review
;; ---------------------------------------------------------------------------

(defn run-tournament
  ([] (run-tournament {}))
  ([{:keys [generations inputs] :or {generations 2}}]
   (let [inputs (or inputs cost-inputs)
         all (vec (generate-candidates))
         reflected (mapv reflect all)
         admitted (mapv :candidate (filter :pass? reflected))
         rejected (filterv (complement :pass?) reflected)
         final (loop [pool admitted g 0]
                 (let [r (rank pool inputs)]
                   (if (>= (inc g) generations)
                     r
                     (recur (mapv :candidate
                                  (filter :pass? (map reflect (evolve r 4))))
                            (inc g)))))]
     {:generated (count all)
      :admitted (count admitted)
      :rejected (count rejected)
      :gate-eliminations (->> rejected
                              (mapcat #(map :gate (:failed %)))
                              frequencies
                              (sort-by (comp - val))
                              vec)
      :ranked final
      :clusters (cluster final)
      :winner (first final)})))

(defn overlay-flip-point
  "The largest overlay operating cost at which a kekkai-overlay design still
  wins, to 0.005 node-days. Reported because the separation between the top two
  designs comes entirely from a number nobody measured: below this value the
  tournament recommends kekkai push, above it, egress long-poll. Anyone who
  measures the real cost of running the overlay can read the answer off this
  line without rerunning anything."
  []
  (->> (range 0 0.5 0.005)
       (filter (fn [x]
                 (= :kekkai-overlay
                    (:reachability (:winner (run-tournament
                                             {:inputs (assoc cost-inputs
                                                             :overlay-operating-cost-days x)}))))))
       last))

(defn report [{:keys [generated admitted rejected gate-eliminations ranked winner]}]
  (str/join
   "\n"
   (concat
    [(str "candidates " generated "  admitted " admitted "  rejected " rejected)
     ""
     "eliminated by gate (a candidate can fail several):"]
    (map (fn [[g n]] (str "  " (name g) " " n)) gate-eliminations)
    [""
     (str "winner  cost=" (:cost winner) " node-days dark / node-year")]
    (map (fn [k] (str "  " (name k) " = " (name (get winner k))))
         [:supervision :transport :backoff :port-recovery :watchdog
          :model-swap :enrollment :reachability :unlock :remote-access])
    [""
     "top admitted:"]
    (map-indexed
     (fn [i c] (str "  " (inc i) ". cost=" (:cost c) "  "
                    (name (:supervision c)) " / " (name (:transport c)) " / "
                    (name (:reachability c)) " / " (name (:port-recovery c))
                    " / " (name (:unlock c)) " / " (name (:remote-access c))))
     (take 5 ranked)))))
