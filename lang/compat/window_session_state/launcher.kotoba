(ns window-session-state.launcher
  "App launcher overlay — grid of installed apps. Restored from the
  legacy kami-engine/kami-os `launcher.rs` (deleted in
  kotoba-lang/kami-engine PR #82 'Remove Rust workspace from
  kami-engine') as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  Activated from the taskbar button or a keyboard shortcut (Super key);
  shows a searchable grid of app icons. This namespace ports the search
  filter / keyboard-navigation *state machine*; the actual grid draw
  (icons, layout) is GPU rendering and out of scope.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU."
  (:require [clojure.string :as str]))

(defn launcher-app
  [{:keys [app-id name icon description domain running]}]
  {:app-id app-id :name name :icon icon :description description
   :domain domain :running (boolean running)})

(defn launcher-state
  "Create an empty, hidden launcher."
  []
  {:visible false :search-query "" :apps [] :selected-index 0})

(defn toggle
  "Toggle launcher visibility, resetting search + selection on open."
  [state]
  (let [visible? (not (:visible state))]
    (if visible?
      (assoc state :visible true :search-query "" :selected-index 0)
      (assoc state :visible false))))

(defn- contains-ci? [s q]
  (str/includes? (str/lower-case s) q))

(defn filtered-apps
  "Filter `apps` by the current search query (case-insensitive
  substring match against name/description/domain)."
  [state]
  (let [q (:search-query state)]
    (if (empty? q)
      (:apps state)
      (let [q (str/lower-case q)]
        (filterv (fn [app]
                   (or (contains-ci? (:name app) q)
                       (contains-ci? (:description app) q)
                       (contains-ci? (:domain app) q)))
                 (:apps state))))))

(defn select-prev
  "Move selection up (toward index 0) in the grid."
  [state]
  (if (pos? (:selected-index state))
    (update state :selected-index dec)
    state))

(defn select-next
  "Move selection down in the grid, bounded by the filtered app count."
  [state]
  (let [max-idx (max 0 (dec (count (filtered-apps state))))]
    (if (< (:selected-index state) max-idx)
      (update state :selected-index inc)
      state)))
