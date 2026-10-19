(ns kami.host
  "Browser host for compiled kami games (the CLJS twin of native kami-script-runtime).

   A game `logic.clj` is compiled to wasm by kototama, then WebAssembly.instantiate'd
   against this import object; its `kami:engine/*` calls read/write a small ECS, and
   its `*-tick` exports run each frame. This is what makes 'edit CLJ → live game' work.

   The ECS is a plain atom of {id → entity} (verified ABI-correct off-GPU in node +
   in-browser). A DataScript/Datomic query+as-of projection is the next layer (it was
   tried in the hot path first but caused subtle read bugs — kept out of the per-call
   path for now). ABI: imports are typed (i32 ptr/len, i64 eid, f32 coords); wasm i64
   crosses to JS as BigInt — eids are BigInt on the wire, keyed by Number here."
  (:require [clojure.string :as str]
            [kami.physics :as phys]))

(defn new-state []
  (atom {:ents {} :next 1 :tick 0 :rng 0x2545F491
         :keys #{} :axes {} :mem nil :cursors {} :next-cur 1}))

(defn- mem-str [st ptr len]
  (if-let [mem (:mem @st)]
    (.decode (js/TextDecoder. "utf-8") (js/Uint8Array. (.-buffer mem) ptr len))
    ""))

(defn- ent [st id] (get-in @st [:ents (js/Number id)]))

;; --- import object: the kami:engine/* world over the atom ECS -----------------

(defn import-object [st]
  (let [bid #(js/BigInt %) n #(js/Number %)
        scene
        #js {:spawn (fn [ptr len]
                      (let [id (:next @st)]
                        (swap! st #(-> %
                                       (assoc-in [:ents id] {:tag (mem-str st ptr len) :x 0 :y 0 :z 0 :vx 0 :vy 0 :vz 0})
                                       (update :next inc)))
                        (bid id)))
             :despawn (fn [eid] (swap! st update :ents dissoc (n eid)) nil)
             :get-x (fn [eid] (or (:x (ent st eid)) 0))
             :get-y (fn [eid] (or (:y (ent st eid)) 0))
             :get-z (fn [eid] (or (:z (ent st eid)) 0))
             :set-position (fn [eid x y z]
                             (when (ent st eid)
                               (swap! st update-in [:ents (n eid)] assoc :x x :y y :z z)) nil)
             :get-vx (fn [eid] (or (:vx (ent st eid)) 0))
             :get-vy (fn [eid] (or (:vy (ent st eid)) 0))
             :get-vz (fn [eid] (or (:vz (ent st eid)) 0))
             :set-velocity (fn [eid vx vy vz]
                             (when (ent st eid)
                               (swap! st update-in [:ents (n eid)] assoc :vx vx :vy vy :vz vz)) nil)
             :get-rx (fn [_] 0) :get-ry (fn [_] 0) :get-rz (fn [_] 0) :get-rw (fn [_] 1)
             :set-rotation (fn [_ _ _ _ _] nil)
             :count-tagged (fn [ptr len]
                             (let [t (mem-str st ptr len)]
                               (bid (count (filter #(= (:tag (val %)) t) (:ents @st))))))
             :query-begin (fn [ptr len]
                            (let [t (mem-str st ptr len)
                                  ids (vec (keep (fn [[id e]] (when (= (:tag e) t) id)) (:ents @st)))
                                  h (:next-cur @st)]
                              (swap! st #(-> % (assoc-in [:cursors h] ids) (update :next-cur inc)))
                              (bid h)))
             :query-next (fn [handle]
                           (let [h (n handle) ids (get-in @st [:cursors h])]
                             (if (seq ids)
                               (do (swap! st assoc-in [:cursors h] (subvec ids 1)) (bid (first ids)))
                               (bid -1))))
             :nearest (fn [ptr len x y maxd]
                        (let [t (mem-str st ptr len)
                              best (->> (:ents @st)
                                        (filter (fn [[_ e]] (= (:tag e) t)))
                                        (map (fn [[id e]] [id (js/Math.hypot (- (:x e) x) (- (:y e) y))]))
                                        (filter (fn [[_ d]] (<= d maxd)))
                                        (sort-by second) ffirst)]
                          (bid (or best -1))))
             :move-toward (fn [eid target speed]
                            (let [e (ent st eid) tg (ent st (n target))]
                              (when (and e tg)
                                (let [dx (- (:x tg) (:x e)) dy (- (:y tg) (:y e)) d (js/Math.hypot dx dy)]
                                  (when (> d 1e-4)
                                    (swap! st update-in [:ents (n eid)] assoc
                                           :vx (* (/ dx d) speed) :vy (* (/ dy d) speed)))))) nil)}
        random #js {:int (fn [bound]
                           (let [s (bit-and (+ (* 1103515245 (:rng @st)) 12345) 0x7fffffff)]
                             (swap! st assoc :rng s)
                             (bid (if (pos? (n bound)) (mod s (n bound)) 0))))}
        physics #js {:apply-impulse (fn [_ _ _ _] nil)}
        input #js {:key-down    (fn [ptr len] (if (contains? (:keys @st) (mem-str st ptr len)) 1 0))
                   :key-pressed (fn [ptr len] (if (contains? (:keys @st) (mem-str st ptr len)) 1 0))
                   :axis        (fn [ptr len] (get (:axes @st) (mem-str st ptr len) 0.0))
                   :pointer-x   (fn [] 0.0) :pointer-y (fn [] 0.0)}
        render #js {:draw-mesh (fn [_ _ _ _ _] nil) :spawn-particle (fn [_ _ _ _ _] nil)
                    :draw-line (fn [_ _ _ _ _ _ _ _] nil)}
        audio  #js {:play (fn [_ _] nil) :stop (fn [_ _] nil) :play-at (fn [_ _ _ _ _] nil)}
        time   #js {:tick (fn [] (bid (:tick @st))) :elapsed-ms (fn [] (bid (* 16 (:tick @st))))}]
    (doto (js-obj)
      (aset "kami:engine/scene@1.0.0" scene)
      (aset "kami:engine/random@1.0.0" random)
      (aset "kami:engine/physics@1.0.0" physics)
      (aset "kami:engine/input@1.0.0" input)
      (aset "kami:engine/render@1.0.0" render)
      (aset "kami:engine/audio@1.0.0" audio)
      (aset "kami:engine/time@1.0.0" time))))

;; --- run a compiled game module ---------------------------------------------

(defn instantiate! [st wasm-bytes]
  (-> (js/WebAssembly.instantiate wasm-bytes (import-object st))
      (.then (fn [res]
               (let [inst (.-instance res) mdl (.-module res) exps (.-exports inst)]
                 (swap! st assoc :mem (aget exps "memory"))
                 (let [names (->> (js/WebAssembly.Module.exports mdl)
                                  (map #(.-name %)) (filter #(str/ends-with? % "-tick")) vec)]
                   (when-let [init (aget exps "init")] (init))
                   {:instance inst :exports exps :systems names :state st}))))))

(defn- resolve-collisions!
  "Push apart overlapping entities whose layers (tags) collide, per the EDN collision
   config (kami.physics). Layer = entity tag; the matrix decides who separates from whom."
  [state cfg]
  (let [ents (:ents @state)
        pts (mapv (fn [[id e]] {:id id :layer (keyword (:tag e)) :x (:x e) :y (:y e)}) ents)
        deltas (phys/separate cfg pts)]
    (when (seq deltas)
      (swap! state update :ents
             (fn [es]
               (reduce (fn [m [id [dx dy]]]
                         (if (get m id)
                           (-> m (update-in [id :x] + dx) (update-in [id :y] + dy))
                           m))
                       es deltas))))))

(defn- support-top
  "Side-scroller ground/platform support: the highest solid top under x that a *falling*
   (vy<=0) entity crosses from above (prevy→ny). solids: [[x0 x1 ytop …] …] (extra slots —
   thickness/colour — are the renderer's). One-way: you pass UP through a platform, land on
   top. Returns the y to rest on, or nil (airborne / over a pit)."
  [solids x prevy ny vy]
  (when (and solids (<= vy 0.0))
    (reduce (fn [best s]
              (let [x0 (nth s 0) x1 (nth s 1) yt (nth s 2)]
                (if (and (>= x x0) (<= x x1) (>= prevy (- yt 1.0)) (<= ny yt)
                         (or (nil? best) (> yt best)))
                  yt best)))
            nil solids)))

(defn tick!
  "Run each *-tick system (dt ms as i64/BigInt) in export order, integrate velocities into
   positions, then resolve EDN-configured layer collisions (host physics).

   Optional 2D platformer physics (when the state holds a :platformer config — set by the
   host app from the scene EDN): entities whose tag is in :tags fall under :gravity (capped
   at :terminal), and land on :solids (ground + one-way platforms). This keeps the *guest*
   pure-constant (it only sets jump/float velocities + reads positions; gravity & collision
   live here, since the compiled guest can't do f32 arithmetic). No config → unchanged."
  [{:keys [exports systems state cfg]} dt-ms]
  (let [dt (js/BigInt dt-ms) dts (/ dt-ms 1000.0)
        pf     (:platformer @state)
        g      (if pf (or (:gravity pf) 0.0) 0.0)
        term   (if pf (or (:terminal pf) -1e9) -1e9)
        gtags  (if pf (set (:tags pf)) #{})
        solids (when pf (:solids pf))]
    (doseq [s systems] ((aget exports s) dt))
    (swap! state update :tick inc)
    (swap! state update :ents
           (fn [ents]
             (persistent!
               (reduce-kv
                 (fn [m id e]
                   (let [nx (+ (:x e) (* (:vx e) dts))
                         nz (+ (:z e) (* (:vz e) dts))]
                     (if (contains? gtags (:tag e))
                       (let [vy    (max (- (:vy e) (* g dts)) term)   ;; host gravity
                             prevy (:y e)
                             ny    (+ prevy (* vy dts))
                             sup   (support-top solids nx prevy ny vy)]
                         (if sup
                           (assoc! m id (assoc e :x nx :z nz :y sup :vy 0.0 :grounded true))
                           (assoc! m id (assoc e :x nx :z nz :y ny :vy vy :grounded false))))
                       (assoc! m id (assoc e :x nx :z nz :y (+ (:y e) (* (:vy e) dts)))))))
                 (transient {}) ents))))
    (resolve-collisions! state (or cfg phys/default-layers))))

(defn snapshot
  "Current entities as [{:id :tag :pos [x y z]} …]."
  [st]
  (mapv (fn [[id e]] {:id id :tag (:tag e) :pos [(:x e) (:y e) (:z e)]}) (:ents @st)))

(defn globals
  "Read the game's exported `defatom` cells (mutable WASM globals) as a {name → number} map —
   the host's window into lives/score/etc. without off-map marker entities. Empty if the game
   declares none (or on any read error — the HUD then falls back to marker counts)."
  [{:keys [exports]}]
  (let [out (atom {})]
    (try
      (when exports
        (doseq [k (array-seq (js/Object.keys exports))]
          (let [e (aget exports k)]
            (when (instance? js/WebAssembly.Global e)
              (swap! out assoc k (js/Number (.-value e)))))))
      (catch :default _ nil))
    @out))
