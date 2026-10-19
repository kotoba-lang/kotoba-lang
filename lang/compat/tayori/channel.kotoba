(ns tayori.channel
  "Channel port — the ONLY place a reply actually leaves the building. A
  reply-LLM proposal is data (a `:draft` record) until a human approves
  sending it; `send-reply!` is called exactly once, after that approval, by
  `tayori.operation`'s commit step. Real channels (`tayori.channel.email`
  `tayori.channel.slack` `tayori.channel.whatsapp`) implement this protocol
  with injected I/O (`:http-fn` `:json-write` `:json-read` `:creds`), same
  contract as kekkai/gijiroku's ports. `mock-channel` is the default — a
  deterministic in-memory channel so the actor is runnable and testable with
  no network/creds."
  (:require [clojure.string :as str]))

(defprotocol Channel
  (fetch-thread [ch thread-id] "the thread's latest known state, or nil")
  (list-new-messages [ch thread-id] "inbound messages not yet ingested")
  (send-reply! [ch thread body] "send `body` as a reply on `thread` — the actuation"))

(defn mock-channel
  "A deterministic in-memory Channel: `sent` is an atom of
  [{:thread-id :body} ...] so tests/sim can assert on what WOULD have gone
  out, without any network call."
  ([] (mock-channel (atom [])))
  ([sent]
   (reify Channel
     (fetch-thread [_ thread-id] {:id thread-id})
     (list-new-messages [_ _thread-id] [])
     (send-reply! [_ thread body]
       (let [rec {:thread-id (:id thread) :body body}]
         (swap! sent conj rec)
         rec)))))

(defn trim-quote
  "A conservative reply-body helper: quote the prior inbound message the way a
  human MUA would, so a review shows the exchange, not just the new text."
  [prior-body new-text]
  (str new-text "\n\n> " (str/replace (or prior-body "") "\n" "\n> ")))
