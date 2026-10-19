(ns game-production.knobs
  "gameSpec -> runtime tuning constants.

  The shared survivors runtime is one program; a spec becomes a game by
  supplying its constants. Those constants are in **ticks and fixed-point
  units**, while the spec is in **milliseconds and pixels**, so this
  translation is where a spec stops being a document and starts being the
  thing that plays.

  On the tick rate: the runtime template does not state one. Rather than bake
  in a number that would be silently wrong against a runtime built at another
  rate, `ticks-per-second` is an argument with a 60 default, and every derived
  period is reported alongside the millisecond figure it came from so a
  mismatch is visible in the output instead of showing up as a game that runs
  at the wrong speed."
  (:require [clojure.string :as str]
            [game-production.spec :as spec]))

(defn- v [m & ks] (some #(get m %) ks))
(defn- l [x] (when (some? x) (long x)))

(def ^:const default-ticks-per-second 60)

(defn ms->ticks
  "Milliseconds -> whole ticks, never zero: a period of 0 makes `(mod n 0)`
  divide by zero in the runtime's spawn gate."
  ([ms] (ms->ticks ms default-ticks-per-second))
  ([ms tps]
   (max 1 (quot (* (long (or ms 0)) (long tps)) 1000))))

(defn fastest-weapon
  "The weapon that sets the fire cadence — the runtime has one auto-fire
  period, and the shortest cooldown is the one a player notices missing."
  [spec]
  (->> (spec/weapons spec)
       (keep (fn [w] (when-let [cd (l (v w :weapon/cooldown-ms "weapon/cooldown-ms"))]
                       (assoc w ::cd cd))))
       (sort-by ::cd)
       first))

(defn longest-reach-px
  "Reach the runtime should use for its contact/attack test: the longest
  stated weapon range or radius. Weapons that state neither are projectile
  shapes whose reach is the projectile's, not the player's."
  [spec]
  (->> (spec/weapons spec)
       (keep (fn [w] (or (l (v w :weapon/range-px "weapon/range-px"))
                         (l (v w :weapon/radius-px "weapon/radius-px")))))
       (reduce max 0)))

(defn knobs
  "spec -> the tuning map the runtime template is filled from.

  Every entry carries its provenance: `:from` names the spec path the number
  came from, so a surprising constant can be traced back to the design
  decision that produced it rather than to this function."
  ([spec] (knobs spec default-ticks-per-second))
  ([spec tps]
   (let [w (spec/waves spec)
         p (spec/player spec)
         fast (fastest-weapon spec)
         spawn-ms (l (v w :waves/base-spawn-interval-ms "waves/base-spawn-interval-ms"))
         fire-ms (some-> fast ::cd)
         speeds (keep #(l (v % :enemy/speed "enemy/speed")) (spec/enemies spec))
         reach (longest-reach-px spec)]
     {:ticks-per-second (long tps)
      :max-alive {:value (or (l (v w :waves/max-alive "waves/max-alive")) 0)
                  :from :waves/max-alive}
      :spawn-period {:value (ms->ticks spawn-ms tps) :ms spawn-ms
                     :from :waves/base-spawn-interval-ms}
      :fire-period {:value (ms->ticks fire-ms tps) :ms fire-ms
                    :from (some-> fast (v :weapon/id "weapon/id"))}
      :enemy-speed {:value (if (seq speeds) (long (/ (reduce + speeds) (count speeds))) 0)
                    :from :enemy/speed :note "spawn-weighted mix is the runtime's job; this is the plain mean"}
      :weapon-range {:value reach :from :weapon/range-px|radius-px}
      :contact-range {:value (or (l (v p :player/pickup-radius-px "player/pickup-radius-px")) 0)
                      :from :player/pickup-radius-px}
      :spawn-radius {:value (or (some-> (v (spec/scene spec) :scene/fog-radius-px "scene/fog-radius-px") long)
                                0)
                     :from :scene/fog-radius-px
                     :note "enemies enter at the edge of vision, so the fog radius is the spawn ring"}})))

(defn- ns-safe [s]
  (-> (str (or s "game")) str/lower-case (str/replace #"[^a-z0-9]+" "_")
      (str/replace #"(^_|_$)" "")))

(defn substitutions
  "knobs -> the flat {placeholder value} map a template is rendered with."
  [spec k]
  {"title" (str (or (v spec :gamespec/title "gamespec/title") "UNTITLED"))
   "slug" (str (or (v spec :gamespec/slug "gamespec/slug") "game"))
   "ns" (ns-safe (or (v spec :gamespec/slug "gamespec/slug") "game"))
   "max_alive" (str (get-in k [:max-alive :value]))
   "spawn_period" (str (get-in k [:spawn-period :value]))
   "fire_period" (str (get-in k [:fire-period :value]))
   "enemy_speed" (str (get-in k [:enemy-speed :value]))
   "weapon_range" (str (get-in k [:weapon-range :value]))
   "contact_range" (str (get-in k [:contact-range :value]))
   "spawn_radius" (str (get-in k [:spawn-radius :value]))})

(defn render
  "Fill a `{{placeholder}}` template. Unknown placeholders are left in place
  and returned, so a template that gained a knob this code does not supply is
  reported rather than silently emitted with a literal `{{...}}` in it."
  [template subs]
  (let [out (reduce (fn [s [k val]] (str/replace s (str "{{" k "}}") (str val)))
                    (str template) subs)
        left (vec (distinct (map second (re-seq #"\{\{([a-zA-Z0-9_]+)\}\}" out))))]
    {:text out :unfilled left :complete? (empty? left)}))
