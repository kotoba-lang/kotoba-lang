(ns kami.ical
  "iCalendar (RFC 5545) as data — 'hiccup for calendars'. An .ics file is a tree of BEGIN/END
   components carrying NAME:value properties, so it maps onto EDN directly — an event / todo / calendar
   is composable data you fork and diff. A fresh calendar/scheduling domain in the kami.* family.
   `.cljc`. Lines are CRLF-terminated per the RFC; TEXT properties (summary/description/location/…) are
   escaped (\\ ; , and newline), structural ones (dtstart/rrule/…) pass through.

   Builders return line sequences; `vcalendar` joins them into the .ics string:
     (vevent {:uid \"1@x\" :dtstart \"20260625T100000Z\" :dtend \"20260625T110000Z\"
              :summary \"Meeting; A,B\" :rrule \"FREQ=WEEKLY;COUNT=10\"})
     (vcalendar {:prodid \"-//me//EN\"} event…)  ⇒  BEGIN:VCALENDAR … END:VCALENDAR"
  (:require [clojure.string :as str]))

(def ^:private text-props #{:summary :description :location :comment :categories :name :contact})

(defn- esc-text [s]
  (-> (str s) (str/replace "\\" "\\\\") (str/replace ";" "\\;")
      (str/replace "," "\\,") (str/replace "\n" "\\n")))

(defn- prop [k v]
  (str (str/upper-case (name k)) ":" (if (text-props k) (esc-text v) (str v))))

(defn- fold
  "RFC 5545 §3.1 line folding: a content line longer than 75 octets is split, continuation lines
   beginning with a single space (≈75 chars; ASCII-octet approximation)."
  [line]
  (if (<= (count line) 75)
    line
    (apply str (subs line 0 75)
           (for [i (range 75 (count line) 74)]
             (str "\r\n " (subs line i (min (count line) (+ i 74))))))))

(defn- lines
  "Flat line seq for a component: BEGIN, its properties, nested children (line seqs), END."
  [cname props children]
  (concat [(str "BEGIN:" (str/upper-case (name cname)))]
          (for [[k v] props] (prop k v))
          (apply concat children)
          [(str "END:" (str/upper-case (name cname)))]))

(defn vevent   "A VEVENT component (line seq)."   [props] (lines :vevent props nil))
(defn vtodo    "A VTODO component (line seq)."    [props] (lines :vtodo props nil))
(defn valarm   "A VALARM component (line seq)."   [props] (lines :valarm props nil))
(defn vjournal "A VJOURNAL component (line seq)." [props] (lines :vjournal props nil))

(defn vcalendar
  "Compile a VCALENDAR document to an .ics string (CRLF line endings). opts merge over VERSION:2.0 and a
   default PRODID; components are line seqs from vevent/vtodo/…."
  [opts & components]
  (str/join "\r\n"
            (map fold
            (lines :vcalendar
                   (into [[:version (or (:version opts) "2.0")]
                          [:prodid  (or (:prodid opts) "-//kami//kami.ical//EN")]]
                         (for [[k v] (dissoc opts :version :prodid)] [k v]))
                   components))))
