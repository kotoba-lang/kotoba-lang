(ns game-production.balance
  "Derived balance quantities — the arithmetic a designer would otherwise do by
  hand for every spec revision.

  Everything here is **integer, in milli-units**: `dps-milli` is damage per
  second times 1000. The runtime plays in fixed point and the spec forbids
  fractional literals, so producing a float here would introduce exactly the
  drift the spec's integer rule exists to prevent. Read `12340` as 12.34 dps.

  What this namespace deliberately does NOT do is model the wave curve.
  `:waves/density-curve \"exp\"` and `:waves/escalation \"per-minute\"` name a
  behaviour the runtime owns; writing a second interpolation here would create
  a number that looks authoritative and disagrees with the game. The functions
  below use only quantities the spec states outright — the interval bounds, the
  alive cap, the night-rage multipliers — so a pressure figure is a bound the
  spec guarantees, not a guess about the curve between them."
  (:require [game-production.spec :as spec]))

(defn- v [m & ks] (some #(get m %) ks))
(defn- l [x] (when (some? x) (long x)))

(def ^:const milli 1000)

(defn ceil-div [a b] (if (zero? (long b)) 0 (quot (+ (long a) (dec (long b))) (long b))))

;; ---------------------------------------------------------------------------
;; weapons

(defn weapon-dps-milli
  "Sustained single-target damage per second x1000, ignoring travel time.

  Pierce is reported separately rather than multiplied in: a pierce-3 bullet is
  3x only when three enemies are actually lined up, and folding that into one
  number makes crowd weapons look strictly better than they play."
  [w]
  (let [dmg (l (v w :weapon/base-dmg "weapon/base-dmg"))
        cd (l (v w :weapon/cooldown-ms "weapon/cooldown-ms"))]
    (if (and dmg cd (pos? cd)) (quot (* dmg milli milli) cd) 0)))

(defn weapon-dot-dps-milli
  "Damage-over-time weapons state `:weapon/dot-ms` — the burn keeps ticking
  while the weapon is on cooldown, so its contribution is the full hit spread
  over the longer of (cooldown, dot duration)."
  [w]
  (let [dmg (l (v w :weapon/base-dmg "weapon/base-dmg"))
        cd (l (v w :weapon/cooldown-ms "weapon/cooldown-ms"))
        dot (l (v w :weapon/dot-ms "weapon/dot-ms"))]
    (if (and dmg cd dot (pos? dot))
      (quot (* dmg milli milli) (max cd dot))
      0)))

(defn crit-adjusted-dps-milli
  "Weapon dps with the player's crit line applied. Both crit fields are
  permille, so this stays integer: chance/1000 of dealing mult/1000 damage."
  [w player]
  (let [base (weapon-dps-milli w)
        chance (or (l (v player :player/crit-chance-permille "player/crit-chance-permille")) 0)
        mult (or (l (v player :player/crit-mult-permille "player/crit-mult-permille")) 1000)]
    (quot (* base (+ 1000 (quot (* chance (- mult 1000)) 1000))) 1000)))

(defn weapon-table
  "Every weapon with its derived numbers, sorted by crit-adjusted dps."
  [spec]
  (let [p (spec/player spec)]
    (->> (spec/weapons spec)
         (map (fn [w]
                {:id (v w :weapon/id "weapon/id")
                 :shape (v w :weapon/shape "weapon/shape")
                 :dps-milli (weapon-dps-milli w)
                 :dot-dps-milli (weapon-dot-dps-milli w)
                 :crit-dps-milli (crit-adjusted-dps-milli w p)
                 :pierce (or (l (v w :weapon/pierce "weapon/pierce")) 1)
                 :evolves-to (v w :weapon/evolves-to "weapon/evolves-to")}))
         (sort-by (comp - :crit-dps-milli))
         vec)))

(defn evolution-gain-permille
  "How much a weapon's evolution actually improves it, in permille of the base
  weapon's dps. An evolution that gains under ~200 permille is a rename with
  extra steps — the pair costs the player a whole passive slot to reach."
  [spec]
  (let [by-id (into {} (map (juxt :id identity)) (weapon-table spec))]
    (vec (for [{:keys [id evolves-to dps-milli]} (weapon-table spec)
               :when (and evolves-to (get by-id evolves-to) (pos? dps-milli))
               :let [to (get by-id evolves-to)]]
           {:from id :to evolves-to
            :gain-permille (- (quot (* (:dps-milli to) 1000) dps-milli) 1000)}))))

;; ---------------------------------------------------------------------------
;; enemies and time-to-kill

(defn ttk-ms
  "Milliseconds for one weapon to kill one enemy, counting the first hit as
  immediate. Returns nil when the weapon cannot damage the enemy at all."
  [w enemy]
  (let [dmg (l (v w :weapon/base-dmg "weapon/base-dmg"))
        cd (l (v w :weapon/cooldown-ms "weapon/cooldown-ms"))
        hp (l (v enemy :enemy/hp "enemy/hp"))]
    (when (and dmg cd hp (pos? dmg))
      (* (dec (ceil-div hp dmg)) cd))))

(defn enemy-contact-dps-milli
  "Contact damage the player actually takes: one hit per invulnerability
  window, not per frame. Without the iframe divisor a shambler reads as lethal
  and every balance conclusion drawn from it is wrong."
  [enemy player]
  (let [dmg (l (v enemy :enemy/dmg "enemy/dmg"))
        iframe (or (l (v player :player/contact-iframe-ms "player/contact-iframe-ms")) 1000)]
    (if (and dmg (pos? iframe)) (quot (* dmg milli milli) iframe) 0)))

(defn touch-death-ms
  "How long the player survives standing in contact with one enemy, taking no
  action. A floor, not a prediction — it assumes no movement, which is the one
  thing the genre's loop is entirely about."
  [enemy spec]
  (let [p (spec/player spec)
        hp (l (v p :player/max-hp "player/max-hp"))
        dps (enemy-contact-dps-milli enemy p)]
    (when (and hp (pos? dps)) (quot (* hp milli milli) dps))))

(defn enemy-table
  [spec]
  (let [p (spec/player spec)]
    (vec (for [e (spec/enemies spec)]
           {:id (v e :enemy/id "enemy/id")
            :hp (l (v e :enemy/hp "enemy/hp"))
            :speed (l (v e :enemy/speed "enemy/speed"))
            :contact-dps-milli (enemy-contact-dps-milli e p)
            :touch-death-ms (touch-death-ms e spec)
            :xp (l (v e :enemy/xp "enemy/xp"))
            :spawn-weight (l (v e :enemy/spawn-weight "enemy/spawn-weight"))
            :min-ms (l (v e :enemy/min-ms "enemy/min-ms"))
            :outruns-player? (let [s (l (v e :enemy/speed "enemy/speed"))
                                   ps (l (v p :player/move-speed-px "player/move-speed-px"))]
                               (boolean (and s ps (> s ps))))}))))

;; ---------------------------------------------------------------------------
;; pressure — stated bounds only

(defn spawns-per-min
  "Spawns per minute at a given interval."
  [interval-ms]
  (if (and interval-ms (pos? (long interval-ms))) (quot 60000 (long interval-ms)) 0))

(defn pressure-bounds
  "The spawn pressure the spec states outright: the opening rate, the terminal
  rate at the interval floor, the same floor under night rage, and the alive
  cap. Nothing between them is modelled — that is the curve's job."
  [spec]
  (let [w (spec/waves spec)
        dc (spec/day-cycle spec)
        base (l (v w :waves/base-spawn-interval-ms "waves/base-spawn-interval-ms"))
        floor (l (v w :waves/min-spawn-interval-ms "waves/min-spawn-interval-ms"))
        rage (or (l (v dc :day-cycle/night-rage-spawn-permille
                       "day-cycle/night-rage-spawn-permille")) 1000)]
    {:opening-per-min (spawns-per-min base)
     :terminal-per-min (spawns-per-min floor)
     :night-rage-terminal-per-min (quot (* (spawns-per-min floor) rage) 1000)
     :max-alive (l (v w :waves/max-alive "waves/max-alive"))}))

(defn clear-rate-required-milli
  "Sustained dps x1000 the player needs so that the alive count stops growing
  at the terminal spawn rate, against the given enemy's hp.

  This is the number that decides whether a build can hold the endgame: below
  it the field fills to `:waves/max-alive` and the run ends by attrition
  regardless of how the player moves."
  [spec enemy-id]
  (let [{:keys [terminal-per-min night-rage-terminal-per-min]} (pressure-bounds spec)
        e (get (spec/by-id (spec/enemies spec) :enemy/id) enemy-id)
        hp (some-> e (v :enemy/hp "enemy/hp") l)]
    (when hp
      {:enemy enemy-id
       :required-dps-milli (quot (* terminal-per-min hp milli) 60)
       :night-rage-required-dps-milli (quot (* night-rage-terminal-per-min hp milli) 60)})))

;; ---------------------------------------------------------------------------
;; bosses

(defn boss-window-ms
  "How long a boss has before the next boss arrives, or the run ends. A boss
  whose ttk exceeds its window is one the player meets while still fighting
  the last one."
  [spec boss-id]
  (let [bs (->> (spec/bosses spec)
                (keep (fn [b] (when-let [at (l (v b :boss/at-ms "boss/at-ms"))]
                                [(v b :boss/id "boss/id") at])))
                (sort-by second)
                vec)
        end (spec/survive-ms spec)
        idx (first (keep-indexed (fn [i [id _]] (when (= id boss-id) i)) bs))]
    (when idx
      (let [[_ at] (nth bs idx)
            next-at (or (second (get bs (inc idx))) end)]
        (when next-at (- (long next-at) (long at)))))))

(defn boss-check
  "Per boss: time to kill at the player's best sustained weapon, the window it
  has, and whether the fight fits. `:fits? false` is a design finding, not an
  error — a boss that outlives its window may be intentional (the finale)."
  [spec]
  (let [best (first (weapon-table spec))
        best-dps (or (:crit-dps-milli best) 0)]
    (vec (for [b (spec/bosses spec)
               :let [id (v b :boss/id "boss/id")
                     hp (l (v b :boss/hp "boss/hp"))
                     window (boss-window-ms spec id)
                     ttk (when (and hp (pos? best-dps))
                           (quot (* hp milli milli) best-dps))]
               :when hp]
           {:boss id
            :hp hp
            :best-weapon (:id best)
            :ttk-ms ttk
            :window-ms window
            :fits? (boolean (and ttk window (<= ttk window)))}))))

(defn report
  "One map holding every derived view — what a design review reads."
  [spec]
  {:weapons (weapon-table spec)
   :evolutions (evolution-gain-permille spec)
   :enemies (enemy-table spec)
   :pressure (pressure-bounds spec)
   :bosses (boss-check spec)})
