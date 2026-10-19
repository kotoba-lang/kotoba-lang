(ns game-production.audio
  "gameSpec -> the audio a build needs, as a request plan.

  A survivors game is unplayable in silence for a reason a screenshot never
  shows: the weapon cue is the only feedback that auto-fire is working, and
  the boss telegraph is the only warning a dodgeable attack is coming. Both
  are stated in the spec already — `:weapon/shape` says what a shot sounds
  like, `:move/telegraph-ms` says exactly how long the warning lasts — so the
  cue list is derivable rather than authored.

  This namespace produces **requests, not audio**: `{:cue/id :cue/prompt
  :cue/duration-ms :cue/loop?}` maps that a caller submits to whatever
  generation fleet it has. Keeping the fleet out of here is what lets the same
  plan be rendered by murakumo, by a local model, or by a human with a
  microphone, and lets this library be tested without one."
  (:require [clojure.string :as str]
            [game-production.spec :as spec]))

(defn- v [m & ks] (some #(get m %) ks))
(defn- l [x] (when (some? x) (long x)))

(defn- words [& xs]
  (->> xs (keep identity) (map #(str/replace (str %) #"[-_]+" " "))
       (remove str/blank?) (str/join ", ")))

(def shape-timbre
  "Weapon shape -> the sound it makes. The shapes are a closed vocabulary in
  the spec, so an unknown one is reported by `unmapped-shapes` rather than
  quietly rendered as a generic hit."
  {"bullet" "dry pistol crack, short tail"
   "bullet-spray" "rapid submachine gun burst, brass clatter"
   "arc" "heavy blade swing through air, wet impact"
   "sustained-arc" "chainsaw motor bite, continuous grind"
   "aoe-fire" "glass bottle shatter then fire whoosh"
   "aoe-fire-wall" "deep napalm ignition, sustained roar"
   "summon-turret" "mechanical deploy clank, servo spin-up"})

(def ^:const min-cue-ms 200)
(def ^:const max-cue-ms 4000)

(defn- clamp-ms [ms]
  (max min-cue-ms (min max-cue-ms (long (or ms min-cue-ms)))))

(defn unmapped-shapes
  "Weapon shapes with no timbre. Returned so a spec that invents a shape gets
  a cue authored for it instead of a silent weapon."
  [spec]
  (vec (distinct (keep (fn [w]
                         (let [s (v w :weapon/shape "weapon/shape")]
                           (when (and s (not (contains? shape-timbre s))) s)))
                       (spec/weapons spec)))))

(defn weapon-cues
  "One cue per weapon. Duration follows the cooldown — a 120 ms chainsaw tick
  and a 2400 ms molotov arc are different sounds, and a single fixed length
  makes one of them overlap itself and the other sit in silence."
  [spec]
  (vec (for [w (spec/weapons spec)
             :let [id (v w :weapon/id "weapon/id")
                   shape (v w :weapon/shape "weapon/shape")
                   cd (l (v w :weapon/cooldown-ms "weapon/cooldown-ms"))]
             :when id]
         {:cue/id (str "weapon-" id)
          :cue/kind :sfx
          :cue/source [:weapon id]
          :cue/duration-ms (clamp-ms (when cd (min cd 1500)))
          :cue/loop? (= shape "sustained-arc")
          :cue/prompt (words "game weapon sound effect"
                             (get shape-timbre shape shape)
                             (str id)
                             "dry, close-mic, no music, mono")})))

(defn boss-cues
  "Telegraph cues. `:move/telegraph-ms` is the dodge window, so the cue must
  be exactly that long — a warning that outlasts its window teaches the player
  the wrong timing."
  [spec]
  (vec (for [b (spec/bosses spec)
             m (spec/moves b)
             :let [bid (v b :boss/id "boss/id")
                   mid (v m :move/id "move/id")
                   tel (l (v m :move/telegraph-ms "move/telegraph-ms"))]
             :when (and bid mid tel)]
         {:cue/id (str "boss-" bid "-" mid "-telegraph")
          :cue/kind :sfx
          :cue/source [:boss bid mid]
          :cue/duration-ms (clamp-ms tel)
          :cue/loop? false
          :cue/prompt (words "boss attack telegraph warning sound"
                             (str mid " wind-up")
                             "rising tension, clearly audible over combat, no music")})))

(defn enemy-cues
  "Cues for enemies that do something the player must hear. A screamer that
  summons off-screen is the canonical case: its `:enemy/on-alive` is the only
  reason to turn around."
  [spec]
  (vec (for [e (spec/enemies spec)
             :let [id (v e :enemy/id "enemy/id")
                   on-alive (v e :enemy/on-alive "enemy/on-alive")
                   ranged (v e :enemy/ranged "enemy/ranged")]
             :when (and id (or on-alive ranged))]
         {:cue/id (str "enemy-" id "-" (if on-alive "signal" "ranged"))
          :cue/kind :sfx
          :cue/source [:enemy id]
          :cue/duration-ms (clamp-ms 900)
          :cue/loop? false
          :cue/prompt (words "enemy creature sound effect"
                             (str id)
                             (if on-alive "alarm call that carries across the map"
                                 "projectile spit, wet launch")
                             "no music")})))

(def ui-cues
  "The three cues the loop itself needs. Not derived from the spec because
  they belong to the runtime, not the design — every survivors build has a
  pickup, a level-up and a death."
  [{:cue/id "ui-xp-pickup" :cue/kind :sfx :cue/source [:ui :xp]
    :cue/duration-ms 250 :cue/loop? false
    :cue/prompt "short bright pickup chime, single note, game UI, no music"}
   {:cue/id "ui-level-up" :cue/kind :sfx :cue/source [:ui :level]
    :cue/duration-ms 900 :cue/loop? false
    :cue/prompt "level up flourish, ascending, triumphant, game UI, no music"}
   {:cue/id "ui-player-down" :cue/kind :sfx :cue/source [:ui :down]
    :cue/duration-ms 1200 :cue/loop? false
    :cue/prompt "player downed, heavy low impact with breath, game UI, no music"}])

(defn music-bed
  "The loop that plays under the whole run.

  `:cue/loop? true` matters more than the prompt: a run is fifteen minutes and
  a generated bed is seconds, so the request is for something that seams."
  [spec]
  (let [sc (spec/scene spec)
        genre (v spec :gamespec/genre "gamespec/genre")
        biome (v sc :scene/biome "scene/biome")
        palette (v sc :scene/palette "scene/palette")
        light (v sc :scene/lighting "scene/lighting")]
    {:cue/id "bed-main"
     :cue/kind :music
     :cue/source [:scene :bed]
     :cue/duration-ms 30000
     :cue/loop? true
     :cue/prompt (words "instrumental game background music loop"
                        genre biome palette light
                        "driving percussion, seamless loop, no vocals")}))

(defn night-bed
  "Night rage raises spawn and speed; a bed that does not change with it makes
  the hardest window feel identical to the easiest. Returns nil when the spec
  declares no night cycle, rather than inventing a second bed nobody plays."
  [spec]
  (let [dc (spec/day-cycle spec)
        windows (v dc :day-cycle/night-rage-windows-ms "day-cycle/night-rage-windows-ms")]
    (when (seq windows)
      (assoc (music-bed spec)
             :cue/id "bed-night-rage"
             :cue/source [:scene :bed-night]
             :cue/prompt (str (:cue/prompt (music-bed spec))
                              ", intensified, faster, higher threat")))))

(defn plan
  "Everything a build needs to stop being silent, in submission order:
  the bed first (it is the longest job), then the cues.

  `:unmapped-shapes` travels with the plan instead of being logged and lost —
  a caller that renders the plan and reports a clean run while three weapons
  had no timbre is reporting the wrong thing."
  [spec]
  (let [cues (vec (concat (weapon-cues spec) (boss-cues spec) (enemy-cues spec) ui-cues))
        beds (vec (keep identity [(music-bed spec) (night-bed spec)]))]
    {:beds beds
     :cues cues
     :unmapped-shapes (unmapped-shapes spec)
     :counts {:beds (count beds) :cues (count cues)}}))
