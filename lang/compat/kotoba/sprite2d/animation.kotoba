(ns kotoba.sprite2d.animation
  "Frame-based animation clip playback — pure data, no canvas/GPU/timer dependency.

   A *clip* describes a sequence of atlas frames (frame-name/index, resolvable via
   `kotoba.sprite2d.atlas`) to play back over time:

     {:frames [:walk-0 :walk-1 :walk-2 :walk-3]
      :fps 8                 ;; OR :durations [0.1 0.1 0.2 0.1] (seconds per frame, same count
                              ;; as :frames — takes precedence over :fps when present)
      :mode :loop}           ;; :loop (default) | :once | :ping-pong

   A *clip-state* is the pure, serializable playback cursor for a clip:

     {:clip clip :index 0 :elapsed 0.0 :direction 1 :done? false}

   `advance` is the only stateful-feeling operation, and it is a pure function: given a
   clip-state and a dt (seconds), it returns the *new* clip-state. Callers own the reduce/atom;
   this namespace never mutates anything, matching the pure-data style of
   `kotoba.sprite2d.layout` in the sibling module.")

(defn- frame-duration
  [{:keys [durations fps] :as clip} idx]
  (if durations
    (nth durations idx)
    (/ 1.0 (or fps 12))))

(defn init
  "Build the initial clip-state for `clip`."
  [clip]
  {:clip clip :index 0 :elapsed 0.0 :direction 1 :done? false})

(defn current-frame
  "The atlas frame-name/index this clip-state is currently showing."
  [{:keys [clip index]}]
  (nth (:frames clip) index))

(defn done?
  [clip-state]
  (boolean (:done? clip-state)))

(defn advance
  "Pure step function: given a clip-state and elapsed dt (seconds), returns the new
   clip-state. Once a :once clip reaches its last frame it clamps there and sets :done? true
   (further advances are no-ops). :loop wraps to frame 0 forever. :ping-pong bounces
   direction at each end and never sets :done?."
  [{:keys [clip index elapsed direction done?] :as state} dt]
  (if done?
    state
    (let [frames (:frames clip)
          n (count frames)
          mode (or (:mode clip) :loop)]
      (loop [index index elapsed (+ elapsed dt) direction direction]
        (let [dur (frame-duration clip index)]
          (if (< elapsed dur)
            {:clip clip :index index :elapsed elapsed :direction direction :done? false}
            (let [remaining (- elapsed dur)]
              (case mode
                :once
                (if (>= (inc index) n)
                  {:clip clip :index (dec n) :elapsed 0.0 :direction 1 :done? true}
                  (recur (inc index) remaining direction))

                :ping-pong
                (let [next-idx (+ index direction)]
                  (if (or (>= next-idx n) (< next-idx 0))
                    (let [dir' (- direction)
                          idx' (+ index dir')
                          idx' (if (or (>= idx' n) (< idx' 0)) index idx')]
                      (recur idx' remaining dir'))
                    (recur next-idx remaining direction)))

                ;; :loop (default)
                (recur (mod (inc index) n) remaining direction)))))))))
