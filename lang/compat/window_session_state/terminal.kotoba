(ns window-session-state.terminal
  "Embedded XRPC terminal — command-history buffer + scrollback. Restored
  from the legacy kami-engine/kami-os `terminal.rs` (deleted in
  kotoba-lang/kami-engine PR #82 'Remove Rust workspace from
  kami-engine') as part of the clj-wgsl migration (ADR-2607010930,
  com-junkawasaki/root).

  The original rendered scrollback via kami-text and routed submitted
  commands through an XRPC client to atproto.etzhayyim.com; both the
  SDF-text rendering and the actual XRPC network call are out of scope
  here (GPU rendering / network IO respectively) — this namespace owns
  only the portable input/history/scrollback *state machine*: submit,
  append-output, history up/down navigation, and scrollback trimming.

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU."
  (:require [clojure.string :as str]))

(def line-kinds #{:command :output :error :system})

(def default-max-scrollback 10000)

(def welcome-message "etzhayyim OS Terminal — XRPC shell. Type `help` for commands.")

(defn terminal-line [text kind] {:text text :kind kind})

(defn terminal-state
  "Create a new terminal with the welcome message in scrollback."
  []
  {:history []
   :output [(terminal-line welcome-message :system)]
   :input ""
   :cursor 0
   :history-index nil
   :scroll-offset 0
   :max-scrollback default-max-scrollback})

(defn- trim-scrollback
  "Trim output to `max-scrollback` lines, dropping oldest first."
  [state]
  (let [max-sb (:max-scrollback state)
        n (count (:output state))]
    (if (> n max-sb)
      (update state :output (fn [o] (vec (drop (- n max-sb) o))))
      state)))

(defn submit
  "Submit the current input line. Returns `[state' cmd]` where `cmd` is
  the submitted command string."
  [state]
  (let [cmd (:input state)
        state (update state :output conj (terminal-line (str "> " cmd) :command))
        state (if (not-empty cmd) (update state :history conj cmd) state)
        state (-> state
                  (assoc :input "" :cursor 0 :history-index nil)
                  trim-scrollback)]
    [state cmd]))

(defn append-output
  "Append output lines from an XRPC response, splitting on newlines."
  [state text kind]
  (-> state
      (update :output into (map #(terminal-line % kind) (str/split-lines text)))
      trim-scrollback))

(defn history-up
  "Navigate command history up (toward older commands)."
  [state]
  (if (empty? (:history state))
    state
    (let [idx (case (:history-index state)
                nil (dec (count (:history state)))
                0 0
                (dec (:history-index state)))]
      (assoc state
             :history-index idx
             :input (nth (:history state) idx)
             :cursor (count (nth (:history state) idx))))))

(defn history-down
  "Navigate command history down (toward newer commands / back to the
  in-progress input)."
  [state]
  (let [idx (:history-index state)
        n (count (:history state))]
    (cond
      (nil? idx) state

      (>= idx (dec n))
      (assoc state :history-index nil :input "" :cursor 0)

      :else
      (let [idx' (inc idx)]
        (assoc state
               :history-index idx'
               :input (nth (:history state) idx')
               :cursor (count (nth (:history state) idx')))))))
