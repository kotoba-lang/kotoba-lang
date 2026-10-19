(ns kaiyu.session
  "The browser-side accounting that turns page time into one dwell bucket per
  view, as a pure state machine.

  A host owns the events (visibilitychange, focus/blur, pagehide, a router
  hook) and the transport (sendBeacon). It should not also own the rules,
  because the rules are where the mistakes are, and both existing
  implementations made the same ones in the same order:

  - **counting time in a background tab.** A page left open overnight reports
    a night of attention that nobody paid.
  - **losing the reading that happens after a tab return.** Leaving and coming
    back is two periods of attention. Both shipped implementations close the
    first out and start a fresh one; carrying the old seconds forward instead
    would double-count them into the next row. Which of the two a row MEANS is
    the `:on-hide` choice below — the one thing here a host may pick.
  - **double-reporting on close.** `pagehide` can fire after a route change
    already closed the view out, and fires more than once in some browsers.
  - **counting a re-entry as navigation.** Selecting the view already showing
    is not a transition.

  So this ns is a reducer: `(step state event) -> [state' emissions]`. No
  timers, no DOM, no network — a host feeds it wall-clock milliseconds and gets
  back the beacons to send. Which makes all four of the above testable without
  a browser, which is why they are pinned in the tests rather than in review
  comments."
  (:require [kaiyu.core :as kaiyu]))

(def hide-behaviours
  "What a tab-hide means for the dwell row, and therefore what one row IS.

  `:close` — hiding CLOSES the view out and emits its dwell; coming back starts
  a fresh segment that will emit its own row. One row = one uninterrupted
  period of attention. This is what club-shinshi-app and net-babiniku both
  shipped, and what ADR-2608060900 describes as 『新しい注視セグメントとして
  計り直す』.

  `:pause` — hiding only stops the clock; the accrued seconds carry over and
  one row is emitted when the view is finally left. One row = total attention
  on that view visit.

  Both are defensible and they answer different questions, so the library takes
  a side only in the default: `:close`, because that is what the existing rows
  in both products mean, and a library that silently redefined them would make
  historical and new rows incomparable — the exact failure the shared
  vocabulary exists to prevent."
  #{:close :pause})

(defn init
  "Initial state. `now-ms` is wall clock; `route` may be nil until the first
  navigation. `opts` takes `:on-hide` (see `hide-behaviours`, default
  `:close`)."
  ([now-ms route] (init now-ms route {}))
  ([now-ms route {:keys [on-hide] :or {on-hide :close}}]
   {:route route
    :active-seconds 0
    :since now-ms          ; when the current attention segment started, nil when paused
    :on-hide (if (contains? hide-behaviours on-hide) on-hide :close)
    :closed? (nil? route)}))

(defn- accrue
  "Fold elapsed wall time into the active total and stop the clock."
  [{:keys [since active-seconds] :as state} now-ms]
  (if since
    (assoc state
           :active-seconds (+ active-seconds (max 0 (/ (- now-ms since) 1000.0)))
           :since nil)
    state))

(defn- close-view
  "Emit the dwell bucket for the view being left, once. Idempotent by design:
  `:closed?` is set before the emission is produced, so a second close (a
  pagehide after a route change) yields nothing rather than a duplicate row."
  [{:keys [route closed?] :as state} now-ms]
  (let [state (accrue state now-ms)]
    (if (or closed? (nil? route))
      [state []]
      [(assoc state :closed? true)
       [{:t :dwell :route route :bucket (kaiyu/dwell-bucket (:active-seconds state))}]])))

(defn step
  "Advance the machine. Returns `[state' emissions]`, where each emission is
  `{:t :dwell :route r :bucket b}` or `{:t :nav :from f :to t}`.

  Events:
    `{:type :navigate :to r :now n}`  — the app moved to view `r`
    `{:type :hide :now n}`            — tab hidden (see `hide-behaviours`)
    `{:type :show :now n}`            — tab visible again
    `{:type :pause :now n}`           — clock off, no emission (window blurred)
    `{:type :resume :now n}`          — clock on again
    `{:type :end :now n}`             — pagehide / unload

  Time never accrues while hidden, under either `:on-hide`. What differs is
  whether hiding also EMITS: see `hide-behaviours`."
  [state {:keys [type to now vocabulary]}]
  (case type
    :navigate
    (let [edge (when (:route state) (kaiyu/transition (or vocabulary []) (:route state) to))]
      (if (and (:route state) (nil? edge))
        ;; Re-entering the view already showing (or another item in the same
        ;; section): not a navigation, and not a reason to close out dwell.
        [state []]
        (let [[state closes] (close-view state now)]
          [(assoc state :route to :active-seconds 0 :since now :closed? false)
           (into (vec closes) (when edge [{:t :nav :from (:from edge) :to (:to edge)}]))])))

    ;; `:close` emits the segment now; `:pause` only stops the clock. See
    ;; hide-behaviours for why this is a choice and not a bug.
    :hide  (if (= :pause (:on-hide state))
             [(accrue state now) []]
             (close-view state now))

    ;; After a `:close`, coming back must start a fresh segment — carrying the
    ;; old seconds forward would double-count them into the next row.
    :show  (if (:closed? state)
             [(assoc state :since now :active-seconds 0 :closed? false) []]
             [(assoc state :since now) []])

    ;; Clock-only, no emission. A window that loses focus while still visible
    ;; is not attention, but it is also not the END of a period of attention —
    ;; the reader is still looking at the page, they are just typing somewhere
    ;; else. Emitting there would split one read into a row per glance away.
    :pause  [(accrue state now) []]
    :resume [(if (:closed? state) state (assoc state :since now)) []]

    :end   (close-view state now)

    [state []]))

(defn drive
  "Run a sequence of events, returning `[state' all-emissions]`. Exists so a
  host can replay a real session in a test, and so this ns's own tests read as
  scenarios instead of as a chain of `step` calls."
  [state events]
  (reduce (fn [[st acc] ev]
            (let [[st' out] (step st ev)]
              [st' (into acc out)]))
          [state []]
          events))
