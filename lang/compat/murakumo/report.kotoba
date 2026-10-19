;; murakumo.report — portable CLI report formatting.
;;
;; W6 product-shell + T6.4: pure string helpers + CSV join seps + cid-display max
;; + CSV fold steps + table/line builders require the shipped `:report-core` KIR
;; on **every** platform. Host pure mirrors are gone — cljs/nbb must preload
;; shipped KIR (resources/ via nbb cwd, register-kir!, or set-resource-loader!)
;; before requiring this ns (ADR-260731-w6-t64-report-mirror-delete).
;; Host remains: map/keyword projection, collection walks, and the
;; reconcile-lines mapcat structure over apps.

(ns murakumo.report
  (:require [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :report-core)

(defn- o
  "Call a pure export. Requires the shipped oracle on every platform (T6.4)."
  [export args]
  (oracle/require-ready! oid)
  (oracle/call oid export args))

(defn- o-record
  "T5.2: structural host map → call-record (requires shipped oracle)."
  [export host-map field-specs]
  (oracle/require-ready! oid)
  (oracle/call-record oid export host-map field-specs))

(def ^:private mesh-schema
  [:record :report/mesh [[:binary :string] [:launch :string]]])
(def ^:private pair-str-schema
  [:record :report/pair-str [[:a :string] [:b :string]]])
(def ^:private triple-str-schema
  [:record :report/triple-str [[:a :string] [:b :string] [:c :string]]])
(def ^:private pad-schema
  [:record :report/pad [[:s :string] [:pad :i64]]])
(def ^:private pad-to-schema
  [:record :report/pad-to [[:s :string] [:width :i64]]])
(def ^:private count-file-schema
  [:record :report/count-file [[:count :i64] [:peers-file :string]]])
(def ^:private name-ok-schema
  [:record :report/name-ok [[:name :string] [:ok :bool]]])
(def ^:private name-exit-schema
  [:record :report/name-exit [[:name :string] [:exit-str :string]]])
(def ^:private ports-schema
  [:record :report/ports [[:port :i64] [:interval :i64]]])
(def ^:private nodes-row-schema
  [:record :report/nodes-row
   [[:name :string] [:ip :string] [:online :bool] [:ssh-ok :bool] [:mesh :string]]])
(def ^:private title-schema
  [:record :report/title [[:fleet :string] [:ts :string]]])
(def ^:private cid-schema
  [:record :report/cid [[:cid :string] [:present :bool]]])
(def ^:private action-detail-schema
  [:record :report/action-detail
   [[:action :string] [:targets :string] [:running :string]
    [:running-empty :bool] [:reason :string]]])
(def ^:private app-row-schema
  [:record :report/app-row
   [[:app :string] [:cid :string] [:desired :i64] [:running-n :i64] [:action :string]]])
(def ^:private join-schema
  [:record :report/join [[:acc :string] [:sep :string] [:next :string]]])
(def ^:private status-row-schema
  [:record :report/status-row
   [[:name :string] [:health [:option :i64]]
    [:wasm :string] [:links :string] [:p2p-port :i64]]])
(def ^:private csv-schema
  [:record :report/csv [[:acc :string] [:next :string]]])

;; ── constants (oracle SSoT) ────────────────────────────────────────────

(def report-csv-sep
  "CSV join for reconcile targets/running/reach. Kotoba SSoT (required)."
  (o 'report-csv-sep []))

(def report-csv-spaced-sep
  "Spaced CSV join for deploy-observed where list. Kotoba SSoT (required)."
  (o 'report-csv-spaced-sep []))

(def mesh-status-sep
  "Separator between binary/launch status. Kotoba SSoT (required)."
  (o 'mesh-status-sep []))

(def cid-display-max-len
  "Max chars for reconcile CID display truncate. Kotoba SSoT (required)."
  (oracle/i64->host (o 'cid-display-max-len [])))

(defn join-append
  "CSV-style fold step: empty acc ⇒ next only. Kotoba (required).
   T5.2: native guest record wire."
  [acc sep next]
  (o-record 'join-append
            {:j (oracle/record join-schema
                               {:acc (or acc "") :sep sep :next next})}
            [[:j :raw]]))

(defn csv-append
  "Append one CSV cell (comma sep). Kotoba (required).
   T5.2: native guest record wire."
  [acc next]
  (o-record 'csv-append
            {:c (oracle/record csv-schema
                               {:acc (or acc "") :next next})}
            [[:c :raw]]))

(defn csv-spaced-append
  "Append one CSV cell (comma+space sep). Kotoba (required).
   T5.2: native guest record wire."
  [acc next]
  (o-record 'csv-spaced-append
            {:c (oracle/record csv-schema
                               {:acc (or acc "") :next next})}
            [[:c :raw]]))

(defn csv-join
  "Join items with report-csv-sep via csv-append fold."
  [items]
  (reduce (fn [acc x] (csv-append acc (str x)))
          ""
          (or items [])))

(defn csv-spaced-join
  "Join items with report-csv-spaced-sep via csv-spaced-append."
  [items]
  (reduce (fn [acc x] (csv-spaced-append acc (str x)))
          ""
          (or items [])))

;; ── dual-source public API → oracle-required ─────────────────────────

(defn nodes-header []
  (o 'nodes-header []))

(defn nodes-row
  "Format one `murakumo nodes` row (oracle nodes-row + host projection).
   T5.2: native guest record wire."
  [node ssh-ok mesh]
  (o-record 'nodes-row
            {:r (oracle/record nodes-row-schema
                               {:name (:name node)
                                :ip (or (:ip node) "?")
                                :online (boolean (:online? node))
                                :ssh-ok (boolean ssh-ok)
                                :mesh mesh})}
            [[:r :raw]]))

(defn mesh-status
  "Render installed/running probe outputs into a compact mesh status.
   T5.2: native guest record wire."
  [binary-status launch-status]
  (o-record 'mesh-status
            {:m (oracle/record mesh-schema
                               {:binary binary-status :launch launch-status})}
            [[:m :raw]]))

(defn status-header []
  (o 'status-header []))

(defn status-down-row
  "T5.2: structural map → call-record."
  [node]
  (o-record 'status-down-row
            {:name (:name node)}
            [[:name :string]]))

(defn status-row
  "Format one `murakumo status` row.
   Health presence via Product Value ABI optional i64.
   T5.2: native guest record wire (option health in-record)."
  [node health-json links p2p-port]
  (let [subsystems (:subsystems health-json)
        health? (when health-json 1)
        wasm (or (:wasm_executor subsystems) "?")
        links-str (if health-json (str links) "-")]
    (o-record 'status-row
              {:r (oracle/record status-row-schema
                                 {:name (str (:name node))
                                  :health health?
                                  :wasm (str wasm)
                                  :links (str links-str)
                                  :p2p-port p2p-port})}
              [[:r :raw]])))

(defn status-row* [{:keys [node health-json links p2p-port] :as row}]
  (status-row node health-json links p2p-port))

(defn deploy-observed-row
  "T5.2: native guest record wire for placed line."
  [where publish-node]
  (if (seq where)
    (o-record 'deploy-observed-placed-line
              {:p (oracle/record pair-str-schema
                                 {:a (csv-spaced-join where)
                                  :b (:name publish-node)})}
              [[:p :raw]])
    (o 'deploy-observed-empty-line [])))

(defn node-prefix
  "T5.2: structural map → call-record."
  [node]
  (o-record 'node-prefix
            {:name (:name node)}
            [[:name :string]]))

(def unreachable-skipped-line
  (o 'unreachable-skipped-line []))

(defn provision-result-line
  "T5.2: structural map → call-record."
  [peered?]
  (o-record 'provision-result-line
            {:peered? peered?}
            [[:peered? :bool]]))

(defn launch-result-line
  "Format one launchctl up/down result row.
   T5.2: native guest record wire."
  [node result]
  (o-record 'launch-result-line
            {:p (oracle/record name-exit-schema
                               {:name (:name node)
                                :exit-str (str (:exit result))})}
            [[:p :raw]]))

(defn missing-pinned-binaries-lines
  "T5.2: native guest record wire for line1."
  [build-manifest]
  [(o-record 'missing-pinned-binaries-line1
             {:p (oracle/record pair-str-schema
                                {:a (:version build-manifest)
                                 :b (:git-sha build-manifest)})}
             [[:p :raw]])
   (o 'missing-pinned-binaries-line2 [])])

(defn rollout-line
  "T5.2: native guest record wire."
  [build-manifest]
  (o-record 'rollout-line
            {:p (oracle/record triple-str-schema
                               {:a (:version build-manifest)
                                :b (:git-sha build-manifest)
                                :c (:features build-manifest)})}
            [[:p :raw]]))

(defn collected-peers-line
  "T5.2: native guest record wire."
  [count peers-file]
  (o-record 'collected-peers-line
            {:p (oracle/record count-file-schema
                               {:count count :peers-file peers-file})}
            [[:p :raw]]))

(def mesh-pass1-line
  (o 'mesh-pass1-line []))

(def mesh-wait-peerid-line
  (o 'mesh-wait-peerid-line []))

(def mesh-pass2-line
  (o 'mesh-pass2-line []))

(def mesh-forming-line
  (o 'mesh-forming-line []))

(defn artifact-node-status
  "T5.2: native guest record wire."
  [node result]
  (o-record 'artifact-node-status
            {:p (oracle/record name-ok-schema
                               {:name (:name node)
                                :ok (zero? (:exit result))})}
            [[:p :raw]]))

(defn deploy-start-line
  "T5.2: native guest record wire."
  [manifest cid]
  (o-record 'deploy-start-line
            {:p (oracle/record pair-str-schema
                               {:a manifest :b cid})}
            [[:p :raw]]))

(defn deploy-command-output
  "T5.2: native guest record wire."
  [out err]
  (o-record 'deploy-command-output
            {:p (oracle/record pair-str-schema
                               {:a (str out) :b (str err)})}
            [[:p :raw]]))

(defn pin-success-line
  "T5.2: native guest record wire."
  [src sha version]
  (o-record 'pin-success-line
            {:p (oracle/record triple-str-schema
                               {:a src :b sha :c version})}
            [[:p :raw]]))

(defn missing-binary-line
  "T5.2: structural map → call-record."
  [path]
  (o-record 'missing-binary-line
            {:path path}
            [[:path :string]]))

(def deploy-wait-placement-line
  (o 'deploy-wait-placement-line []))

(defn alert-line
  "T5.2: native guest record wire."
  [alert]
  (o-record 'alert-line
            {:p (oracle/record triple-str-schema
                               {:a (str (:level alert))
                                :b (str (:node alert))
                                :c (str (:msg alert))})}
            [[:p :raw]]))

(defn snapshot-error-line
  "T5.2: structural map → call-record."
  [message]
  (o-record 'snapshot-error-line
            {:message message}
            [[:message :string]]))

(defn reconcile-persist-error-line
  "T5.2: structural map → call-record."
  [message]
  (o-record 'reconcile-persist-error-line
            {:message message}
            [[:message :string]]))

(defn dashboard-start-line
  "T5.2: native guest record wire."
  [port interval]
  (o-record 'dashboard-start-line
            {:p (oracle/record ports-schema
                               {:port port :interval interval})}
            [[:p :raw]]))

(defn apply-target-line
  "T5.2: native guest record wire."
  [app target]
  (o-record 'apply-target-line
            {:p (oracle/record pair-str-schema
                               {:a (:app app) :b target})}
            [[:p :raw]]))

(defn watch-start-line
  "T5.2: structural map → call-record."
  [seconds]
  (o-record 'watch-start-line
            {:seconds seconds}
            [[:seconds :i64]]))

(def operator-seed-required-line
  (o 'operator-seed-required-line []))

(def operator-seed-hex-required-line
  (o 'operator-seed-hex-required-line []))

(def deploy-usage-line
  (o 'deploy-usage-line []))

(def reconcile-usage-line
  (o 'reconcile-usage-line []))

(defn command-error-line
  "Render a validation error keyword for a command.
   T5.2: native guest record wire."
  [command error]
  (o-record 'command-error-line
            {:p (oracle/record pair-str-schema
                               {:a (name command) :b (name error)})}
            [[:p :raw]]))

(def dashboard-no-persistence-line
  (o 'dashboard-no-persistence-line []))

(def reconcile-no-persistence-line
  (o 'reconcile-no-persistence-line []))

(def reconcile-converged-line
  (o 'reconcile-converged-line []))

(def reconcile-dry-run-line
  (o 'reconcile-dry-run-line []))

;; ── reconcile pure builders + host mapcat ────────────────────────────

(defn- pad-field
  "Left-align field via oracle pad-right; host supplies remaining pad from
  Clojure string count (not UTF-8 byte-length) so multi-byte glyphs like
  em-dash CID placeholder keep layout parity.
   T5.2: native guest record wire."
  [s width]
  (let [s (str s)
        pad (max 0 (- width (count s)))]
    (o-record 'pad-right
              {:p (oracle/record pad-schema {:s s :pad pad})}
              [[:p :raw]])))

(defn- fmt-cid
  "T5.2: native guest record wire."
  [cid]
  (if cid
    (o-record 'cid-display
              {:p (oracle/record cid-schema
                                 {:cid (subs cid 0 (min cid-display-max-len (count cid)))
                                  :present true})}
              [[:p :raw]])
    (o-record 'cid-display
              {:p (oracle/record cid-schema
                                 {:cid "" :present false})}
              [[:p :raw]])))

(defn reconcile-lines
  "Render a reconcile plan as operator table lines.
   Pure title/col/row/detail/reach/drift from oracle (required); host mapcats
   apps and CSV-joins targets/running/reach/misplaced via csv-append fold steps."
  [plan]
  (let [title (o-record 'reconcile-title
                        {:p (oracle/record title-schema
                                           {:fleet (or (:fleet plan) "fleet")
                                            :ts (:ts plan)})}
                        [[:p :raw]])
        col (o 'reconcile-col-header [])]
    (vec
     (concat
      [title col]
      (mapcat
       (fn [app]
         (let [action (name (:action app))
               targets-csv (csv-join (:targets app))
               running-csv (csv-join (:running app))
               running-empty (empty? (:running app))
               reason (str (or (:reason app) ""))
               detail (o-record 'action-detail
                                {:p (oracle/record action-detail-schema
                                                   {:action action
                                                    :targets targets-csv
                                                    :running running-csv
                                                    :running-empty running-empty
                                                    :reason reason})}
                                [[:p :raw]])
               app14 (pad-field (:app app) 14)
               cid10 (pad-field (fmt-cid (:cid app)) 10)
               act9 (pad-field action 9)
               front (o-record 'reconcile-app-row
                               {:r (oracle/record app-row-schema
                                                  {:app app14
                                                   :cid cid10
                                                   :desired (:desired app)
                                                   :running-n (count (:running app))
                                                   :action act9})}
                               [[:r :raw]])
               base [(o-record 'reconcile-app-line
                               {:p (oracle/record pair-str-schema
                                                  {:a front :b detail})}
                               [[:p :raw]])]
               reach (when (seq (:reach app))
                       [(o-record 'reach-line
                                  {:p (oracle/record pair-str-schema
                                                     {:a (csv-join (map str (:reach app)))
                                                      :b (csv-join (:eligible app))})}
                                  [[:p :raw]])])
               misplaced (when (seq (:misplaced app))
                           [(o-record 'drift-line
                                      {:misplaced (csv-join (:misplaced app))}
                                      [[:misplaced :string]])])]
           (concat base reach misplaced)))
       (:apps plan))))))

(defn command-help []
  (o 'command-help []))
