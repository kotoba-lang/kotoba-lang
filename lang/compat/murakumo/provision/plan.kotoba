;; murakumo.provision.plan — portable provision/mesh planning helpers.
;;
;; Host effects stay in murakumo.core: SSH reachability, rsync, launchctl, local
;; kotoba DID derivation, and filesystem reads. This namespace owns deterministic
;; strings and defaults used by those effects.
;;
;; W6 product-shell + T6.4: constants + port/multiaddr path tokens + launchctl
;; shell tokens + peer/link shell + rsync argv + peer-entry + home-bin-path +
;; label/roles join seps + peer-id DID/body patterns + render-plist placeholder
;; tokens + fold steps + peer-id-from-log scan + write-plist-shell require the
;; shipped `:provision-plan` KIR on **every** platform. Host pure mirrors are
;; gone — cljs/nbb must preload shipped KIR (resources/ via nbb cwd,
;; register-kir!, or set-resource-loader!) before requiring this ns
;; (ADR-260731-w6-t64-provision-mirror-delete).
;; Host remains: collection walks, connect transport class checks, inventory
;; port lookup, map assembly / fold structure over nodes, host-rendered XML
;; plist body content.

(ns murakumo.provision.plan
  "Portable provision/mesh planning helpers.
   W6 product-shell: path/port + shell/rsync/peer-entry/plist/peer-id pure via provision_plan_core."
  (:require [kotoba.lang.text :as str]
            [murakumo.connect :as connect]
            [murakumo.fleet.inventory :as inv]
            [murakumo.kotoba.oracle :as oracle]))

(def ^:private oid :provision-plan)

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

(def ^:private multiaddr-schema
  [:record :provision/multiaddr [[:ip :string] [:port :i64]]])
(def ^:private bin-path-schema
  [:record :provision/bin-path [[:local-bin :string] [:bin :string]]])
(def ^:private remote-dest-schema
  [:record :provision/remote-dest [[:host :string] [:bin :string]]])
(def ^:private label-kv-schema
  [:record :provision/label-kv [[:k :string] [:v :string]]])
(def ^:private peer-entry-schema
  [:record :provision/peer-entry [[:peer-id :string] [:multiaddr :string]]])
(def ^:private join-schema
  [:record :provision/join [[:acc :string] [:sep :string] [:next :string]]])
(def ^:private bootstrap-schema
  [:record :provision/bootstrap [[:acc :string] [:entry :string]]])
(def ^:private labels-schema
  [:record :provision/labels [[:acc :string] [:pair :string]]])
(def ^:private roles-schema
  [:record :provision/roles [[:acc :string] [:role :string]]])
(def ^:private plist-replace-schema
  [:record :provision/plist-replace
   [[:tmpl :string] [:ph :string] [:val :string]]])
(def ^:private p2p-ports-schema
  [:record :provision/p2p-ports
   [[:node-port [:option :i64]] [:fleet-port [:option :i64]]]])
(def ^:private write-plist-schema
  [:record :provision/write-plist [[:label :string] [:body :string]]])

;; ── constants (oracle SSoT) ────────────────────────────────────────────

(def launchctl-print-prefix
  "sudo launchctl print system/ prefix. Kotoba SSoT (required)."
  (o 'launchctl-print-prefix []))

(def launchctl-bootout-prefix
  "sudo launchctl bootout system/ prefix. Kotoba SSoT (required)."
  (o 'launchctl-bootout-prefix []))

(def launchctl-bootstrap-sys
  "sudo launchctl bootstrap system  verb prefix. Kotoba SSoT (required)."
  (o 'launchctl-bootstrap-sys []))

(def launchd-daemons-dir
  "System LaunchDaemons directory. Kotoba SSoT (required)."
  (o 'launchd-daemons-dir []))

(def launchctl-bootstrap-prefix
  "bootstrap + LaunchDaemons path. Kotoba SSoT (required)."
  (o 'launchctl-bootstrap-prefix []))

(def launchctl-kickstart-prefix
  "sudo launchctl kickstart -k system/ prefix. Kotoba SSoT (required)."
  (o 'launchctl-kickstart-prefix []))

(def launchctl-status-suffix
  "running/stopped status probe suffix. Kotoba SSoT (required)."
  (o 'launchctl-status-suffix []))

(def launchctl-plist-quiet-semi
  ".plist 2>/dev/null; mid for launch-up. Kotoba SSoT (required)."
  (o 'launchctl-plist-quiet-semi []))

(def launchctl-quiet-true-sleep
  "quiet bootout + sleep mid for reprovision. Kotoba SSoT (required)."
  (o 'launchctl-quiet-true-sleep []))

(def launchctl-plist-quiet-true-semi
  ".plist quiet-true mid for reprovision. Kotoba SSoT (required)."
  (o 'launchctl-plist-quiet-true-semi []))

(def plist-ext
  "LaunchDaemon .plist extension. Kotoba SSoT (required)."
  (o 'plist-ext []))

(def multiaddr-ip4-prefix
  "libp2p multiaddr IP4 protocol prefix. Kotoba SSoT (required)."
  (o 'multiaddr-ip4-prefix []))

(def multiaddr-udp-mid
  "Between host and UDP port in multiaddr. Kotoba SSoT (required)."
  (o 'multiaddr-udp-mid []))

(def multiaddr-quic-suffix
  "Tailscale QUIC transport multiaddr suffix. Kotoba SSoT (required)."
  (o 'multiaddr-quic-suffix []))

(def plist-label
  (o 'plist-label []))

(def remote-bin
  (o 'remote-bin []))

(def remote-store
  (o 'remote-store []))

(def ssh-rsync-options
  (o 'ssh-rsync-options []))

(def peer-advertise-wait-ms
  (oracle/i64->host (o 'peer-advertise-wait-ms [])))

(def rsync-bin
  "rsync binary name. Kotoba SSoT (required)."
  (o 'rsync-bin []))

(def rsync-az-flag
  "rsync -az flag. Kotoba SSoT (required)."
  (o 'rsync-az-flag []))

(def rsync-e-flag
  "rsync -e flag. Kotoba SSoT (required)."
  (o 'rsync-e-flag []))

(def plist-heredoc-footer
  "Heredoc closer for write-plist. Kotoba SSoT (required)."
  (o 'plist-heredoc-footer []))

(def peer-at-sep
  "Separator between peer-id and multiaddr. Kotoba SSoT (required)."
  (o 'peer-at-sep []))

(def peer-join-sep
  "Comma separator for bootstrap peer list. Kotoba SSoT (required)."
  (o 'peer-join-sep []))

(def did-key-prefix
  "DID key URI prefix for mesh PeerIds. Kotoba SSoT (required)."
  (o 'did-key-prefix []))

(def peer-id-body-prefix
  "libp2p PeerId body prefix in mesh logs. Kotoba SSoT (required)."
  (o 'peer-id-body-prefix []))

(def peer-id-body-pattern
  "grep -o pattern for PeerId body. Kotoba SSoT (required)."
  (o 'peer-id-body-pattern []))

(def peer-id-did-pattern
  "grep -ho pattern for did:key:PeerId. Kotoba SSoT (required)."
  (o 'peer-id-did-pattern []))

(def home-bin-suffix
  "Path under node home for murakumo binaries ({{BIN}}). Kotoba SSoT (required)."
  (o 'home-bin-suffix []))

(def label-join-sep
  "Comma separator for labels-env. Kotoba SSoT (required)."
  (o 'label-join-sep []))

(def roles-join-sep
  "Comma separator for node roles CSV. Kotoba SSoT (required)."
  (o 'roles-join-sep []))

(def plist-ph-user
  "LaunchDaemon template placeholder {{USER}}. Kotoba SSoT (required)."
  (o 'plist-ph-user []))

(def plist-ph-bin
  "LaunchDaemon template placeholder {{BIN}}. Kotoba SSoT (required)."
  (o 'plist-ph-bin []))

(def plist-ph-port
  "LaunchDaemon template placeholder {{PORT}}. Kotoba SSoT (required)."
  (o 'plist-ph-port []))

(def plist-ph-roles
  "LaunchDaemon template placeholder {{ROLES}}. Kotoba SSoT (required)."
  (o 'plist-ph-roles []))

(def plist-ph-labels
  "LaunchDaemon template placeholder {{LABELS}}. Kotoba SSoT (required)."
  (o 'plist-ph-labels []))

(def plist-ph-home
  "LaunchDaemon template placeholder {{HOME}}. Kotoba SSoT (required)."
  (o 'plist-ph-home []))

(def plist-ph-ed25519
  "LaunchDaemon template placeholder {{ED25519}}. Kotoba SSoT (required)."
  (o 'plist-ph-ed25519 []))

(def plist-ph-x25519
  "LaunchDaemon template placeholder {{X25519}}. Kotoba SSoT (required)."
  (o 'plist-ph-x25519 []))

(def plist-ph-did
  "LaunchDaemon template placeholder {{DID}}. Kotoba SSoT (required)."
  (o 'plist-ph-did []))

(def plist-ph-p2pport
  "LaunchDaemon template placeholder {{P2PPORT}}. Kotoba SSoT (required)."
  (o 'plist-ph-p2pport []))

(def plist-ph-p2pseed
  "LaunchDaemon template placeholder {{P2PSEED}}. Kotoba SSoT (required)."
  (o 'plist-ph-p2pseed []))

(def plist-ph-extaddr
  "LaunchDaemon template placeholder {{EXTADDR}}. Kotoba SSoT (required)."
  (o 'plist-ph-extaddr []))

(def plist-ph-bootstrap
  "LaunchDaemon template placeholder {{BOOTSTRAP}}. Kotoba SSoT (required)."
  (o 'plist-ph-bootstrap []))

(def plist-ph-webrtc
  "LaunchDaemon template placeholder {{WEBRTC}}. Kotoba SSoT (required)."
  (o 'plist-ph-webrtc []))

(def watchdog-label
  (o 'watchdog-label []))

;; ── pure helpers → oracle-required ───────────────────────────────────

(defn local-bin-path
  "Local pin path for one binary. Kotoba (required).
   T5.2: native guest record wire."
  [local-bin bin]
  (o-record 'local-bin-path
            {:p (oracle/record bin-path-schema
                               {:local-bin local-bin :bin bin})}
            [[:p :raw]]))

(defn remote-bin-dest
  "Remote rsync dest for one binary. Kotoba (required).
   T5.2: native guest record wire."
  [host bin]
  (o-record 'remote-bin-dest
            {:p (oracle/record remote-dest-schema
                               {:host host :bin bin})}
            [[:p :raw]]))

(defn launchd-daemon-path
  "System LaunchDaemon path for label. Kotoba (required)."
  [label]
  (o-record 'launchd-daemon-path {:label label} [[:label :string]]))

(defn tee-plist-prefix
  "sudo tee … <<'PLIST'\\n prefix. Heredoc body stays host."
  [label]
  (o-record 'tee-plist-prefix {:label label} [[:label :string]]))

(defn label-kv
  "Single k=v label pair. Host joins with comma. Kotoba (required).
   T5.2: native guest record wire."
  [k v]
  (o-record 'label-kv
            {:p (oracle/record label-kv-schema {:k k :v v})}
            [[:p :raw]]))

(defn peer-entry
  "One bootstrap peer `peer-id@multiaddr`. Kotoba (required).
   T5.2: native guest record wire."
  [peer-id multiaddr]
  (o-record 'peer-entry
            {:p (oracle/record peer-entry-schema
                               {:peer-id peer-id :multiaddr multiaddr})}
            [[:p :raw]]))

(defn did-peer-id
  "DID URI for a mesh PeerId (`did:key:` + body). Kotoba (required)."
  [peer-id]
  (o-record 'did-peer-id {:peer-id peer-id} [[:peer-id :string]]))

(defn join-append
  "CSV-style fold step: empty acc ⇒ next only. Kotoba (required).
   T5.2: native guest record wire."
  [acc sep next]
  (o-record 'join-append
            {:j (oracle/record join-schema
                               {:acc (or acc "") :sep sep :next next})}
            [[:j :raw]]))

(defn bootstrap-append
  "Append one peer-entry to bootstrap-str acc. Kotoba (required).
   T5.2: native guest record wire."
  [acc entry]
  (o-record 'bootstrap-append
            {:b (oracle/record bootstrap-schema
                               {:acc (or acc "") :entry entry})}
            [[:b :raw]]))

(defn labels-append
  "Append one label-kv pair to labels-env acc. Kotoba (required).
   T5.2: native guest record wire."
  [acc pair]
  (o-record 'labels-append
            {:l (oracle/record labels-schema
                               {:acc (or acc "") :pair pair})}
            [[:l :raw]]))

(defn roles-append
  "Append one role name to roles CSV acc. Kotoba (required).
   T5.2: native guest record wire."
  [acc role]
  (o-record 'roles-append
            {:r (oracle/record roles-schema
                               {:acc (or acc "") :role role})}
            [[:r :raw]]))

(defn plist-replace
  "Substitute one placeholder token in a LaunchDaemon template. Kotoba (required).
   T5.2: native guest record wire."
  [tmpl ph val]
  (o-record 'plist-replace
            {:p (oracle/record plist-replace-schema
                               {:tmpl tmpl :ph ph :val (or val "")})}
            [[:p :raw]]))

(defn home-bin-path
  "Absolute `{{BIN}}` path under node home. Kotoba (required)."
  [home]
  (o-record 'home-bin-path {:home home} [[:home :string]]))

(defn operator-seed-missing?
  "True when a command requiring the fleet operator seed should fail.
   Kotoba (required)."
  [operator-seed]
  (oracle/bool->host
   (o-record 'operator-seed-missing? {:operator-seed operator-seed} [[:operator-seed :string]])))

(defn provision-command-error
  "Validation error keyword for provision, or nil. Host branch."
  [operator-seed]
  (when (operator-seed-missing? operator-seed)
    :missing-operator-seed-hex))

(defn mesh-command-error
  "Validation error keyword for mesh, or nil. Host branch."
  [operator-seed]
  (when (operator-seed-missing? operator-seed)
    :missing-operator-seed))

(defn node-p2p-port
  "Resolve a node's p2p QUIC port, defaulting to fleet p2p port, then 4001.
   Kotoba `resolve-p2p-port` with Product Value ABI optional ports (required).
   T5.2: native guest record wire."
  [fleet node]
  (oracle/i64->host
   (o-record 'resolve-p2p-port
             {:x (oracle/record p2p-ports-schema
                                {:node-port (:p2p-port node)
                                 :fleet-port (:fleet/p2p-port fleet)})}
             [[:x :raw]])))

(defn multiaddr
  "Tailscale QUIC multiaddr for a node ip/port. Kotoba (required).
   T5.2: native guest record wire."
  [ip port]
  (o-record 'multiaddr
            {:m (oracle/record multiaddr-schema
                               {:ip ip :port port})}
            [[:m :raw]]))

(defn node-webrtc-port
  "The /webrtc-direct UDP port for nodes whose class speaks :webrtc on :live.
   Offset +100 from the p2p port so it never clashes with the QUIC port.
   Host: connect class-transports gate; Kotoba: webrtc-port offset."
  [fleet connect-spec node]
  (when (and connect-spec
             (some #{:webrtc}
                   (set (connect/class-transports
                         connect-spec
                         (connect/node-class connect-spec node)
                         :live))))
    (let [p2p (node-p2p-port fleet node)]
      (oracle/i64->host (o-record 'webrtc-port {:p2p p2p} [[:p2p :i64]])))))

(defn bootstrap-str
  "Comma-list of `peerid@multiaddr` for every other node with a known PeerId.
   Pair format + join via oracle; node walk / peer lookup stay host."
  [fleet peers self]
  (reduce (fn [acc node]
            (if (= (:name node) (:name self))
              acc
              (if-let [peer-id (get peers (:name node))]
                (bootstrap-append
                 acc
                 (peer-entry peer-id (multiaddr (:ip node) (node-p2p-port fleet node))))
                acc)))
          ""
          (:nodes fleet)))

(defn peer-id-from-log
  "Extract the libp2p PeerId from kotoba mesh log output containing `did:key:<peerid>`.
   Kotoba pure scan (required); blank → nil."
  [out]
  (let [s (o-record 'peer-id-from-log {:out out} [[:out :string]])]
    (when-not (str/blank? (str s)) s)))

(defn collected-peers
  "Build the persisted node-name → PeerId map from node/peer pairs. Host fold."
  [node-peer-pairs]
  (into {}
        (keep (fn [[node peer-id]]
                (when peer-id
                  [(:name node) peer-id])))
        node-peer-pairs))

(defn peer-probe-targets
  "Nodes eligible for PeerId probing after mesh pass 1. Host filter + inject."
  [nodes reachable-node?]
  (filterv #(and (:ip %) (reachable-node? %)) nodes))

(defn peer-probe-plan
  "Nodes that should be probed for PeerIds after mesh pass 1. Host map."
  [nodes reachable-node?]
  (mapv (fn [node] {:node node :host (:host node)})
        (peer-probe-targets nodes reachable-node?)))

(defn peer-probe-results
  "Probe PeerIds for every eligible node using a caller-supplied host reader.
   Host fold + inject."
  [nodes reachable-node? read-peer-id]
  (mapv (fn [{:keys [node host]}]
          [node (read-peer-id host)])
        (peer-probe-plan nodes reachable-node?)))

(defn collected-peers-from-results
  "Build peers from peer-probe-plan results shaped as [node peer-id]."
  [node-peer-results]
  (collected-peers node-peer-results))

(defn mesh-binary-status-command
  "Kotoba (required)."
  []
  (o 'mesh-binary-status-command []))

(defn remote-store-command
  "Kotoba (required)."
  []
  (o 'remote-store-command []))

(defn rsync-binary-argv
  "argv for copying one pinned binary to a fleet node.
   Bin/flags + path fragments oracle SSoT; vector assembly host."
  [local-bin host bin]
  [rsync-bin rsync-az-flag rsync-e-flag ssh-rsync-options
   (local-bin-path local-bin bin)
   (remote-bin-dest host bin)])

(defn launch-status-command
  "Remote shell command that reports whether the resident launchd label is running.
   Kotoba (required)."
  []
  (o 'launch-status-command []))

(defn write-plist-shell
  "Assemble sudo tee … <<'PLIST' shell for a LaunchDaemon label + body.
   Kotoba (required).
   T5.2: native guest record wire."
  [label body]
  (o-record 'write-plist-shell
            {:w (oracle/record write-plist-schema
                               {:label label :body body})}
            [[:w :raw]]))

(defn write-plist-command
  "Remote shell command that writes plist content to the system LaunchDaemon path.
   Shell assembly via oracle; body content is host XML."
  [plist]
  (write-plist-shell plist-label plist))

(defn peer-id-log-command
  "Remote shell command that prints the latest node PeerId DID from mesh.log.
   Kotoba (required)."
  []
  (o 'peer-id-log-command []))

(defn live-link-count-command
  "Remote shell command that counts distinct connected libp2p peers.
   Kotoba (required)."
  []
  (o 'live-link-count-command []))

(defn live-link-count-output
  "Normalise the stdout from live-link-count-command. Kotoba (required)."
  [out]
  (o-record 'live-link-count-output {:out out} [[:out :string]]))

(defn labels-env
  "Render node labels as the launchd env string `k=v,k=v`.
   Pair format + join via oracle; map walk stays host."
  [labels]
  (reduce (fn [acc [k v]]
            (labels-append acc (label-kv (name k) v)))
          ""
          labels))

(defn- roles-csv
  "Comma-joined role names. Join step via oracle; walk host."
  [roles]
  (reduce (fn [acc role] (roles-append acc (str role)))
          ""
          (or roles [])))

(defn render-plist
  "Render the LaunchDaemon plist template for a node.

   `identity` supplies host-derived or crypto-derived values:
   :operator-seed, :x25519-seed, :did, and :p2p-seed.
   Placeholder tokens + replace + {{BIN}}/roles/labels via oracle;
   placeholder chain order stays host. Inventory port lookup host."
  [template fleet connect-spec peers node {:keys [user home operator-seed x25519-seed did p2p-seed]}]
  (-> template
      (plist-replace plist-ph-user user)
      (plist-replace plist-ph-bin (home-bin-path home))
      (plist-replace plist-ph-port (str (inv/node-port fleet node)))
      (plist-replace plist-ph-roles (roles-csv (:roles node)))
      (plist-replace plist-ph-labels (labels-env (:labels node)))
      (plist-replace plist-ph-home home)
      (plist-replace plist-ph-ed25519 operator-seed)
      (plist-replace plist-ph-x25519 x25519-seed)
      (plist-replace plist-ph-did did)
      (plist-replace plist-ph-p2pport (str (node-p2p-port fleet node)))
      (plist-replace plist-ph-p2pseed p2p-seed)
      (plist-replace plist-ph-extaddr (if (:ip node) (multiaddr (:ip node) (node-p2p-port fleet node)) ""))
      (plist-replace plist-ph-bootstrap (bootstrap-str fleet peers node))
      (plist-replace plist-ph-webrtc (str (node-webrtc-port fleet connect-spec node)))))

(defn launch-command
  "Shell command used to start or stop the resident LaunchDaemon.
   Kotoba up/down (required); host case dispatch."
  [action]
  (case action
    :up (o 'launch-up-command [])
    :down (o 'launch-down-command [])))

(defn launch-plan
  "Host command plan for changing one resident node state."
  [node action]
  {:node node
   :host (:host node)
   :command (launch-command action)})

(defn launch-plans
  "Host command plans for changing resident node state. Host fold."
  [nodes action]
  (mapv #(launch-plan % action) nodes))

(defn launch-results
  "Run launch plans with a caller-supplied host command runner. Host fold."
  [nodes action run-host-command]
  (mapv (fn [{:keys [node host command]}]
          [node (run-host-command host command)])
        (launch-plans nodes action)))

(defn reprovision-command
  "Shell command used after writing the plist to reload and kickstart it.
   Kotoba (required)."
  []
  (o 'reprovision-command []))

;; ── HTTP-wedge watchdog (com.murakumo.kotoba-mesh-watchdog) ─────────────────
;; kotoba-server can wedge its HTTP surface while libp2p stays alive (2026-07-02,
;; pin 4f38b74a): the process never exits, so the mesh daemon's KeepAlive cannot
;; heal it. A sibling StartInterval daemon probes /health and kills the server on
;; two consecutive failures — KeepAlive then restarts it.

(defn render-watchdog-plist
  "Substitute the watchdog template's placeholders for one node.
   Placeholder tokens + replace via oracle; chain order stays host."
  [tmpl fleet node {:keys [user home]}]
  (-> tmpl
      (plist-replace plist-ph-user user)
      (plist-replace plist-ph-home home)
      (plist-replace plist-ph-port (str (inv/node-port fleet node)))))

(defn write-watchdog-plist-command
  "Remote shell command that writes the watchdog plist to the system LaunchDaemon path.
   Shell assembly via oracle; body content is host XML."
  [plist]
  (write-plist-shell watchdog-label plist))

;; ── kernel network baseline (com.murakumo.sysctl-baseline) ─────────────────
;; ADR-260902-fleet-outbound-hygiene. A root LaunchDaemon (no UserName) that
;; widens the ephemeral port range at boot. The label is a host literal for
;; now — the plist family's labels live in kotoba/provision_plan_core.kotoba
;; and are parity-tested; moving this one there is the recorded follow-up
;; (it is a constant, not a decision).

(def sysctl-baseline-label "com.murakumo.sysctl-baseline")

(defn render-sysctl-baseline-plist
  "Substitute the sysctl-baseline template's placeholders for one node."
  [tmpl {:keys [home]}]
  (plist-replace tmpl plist-ph-home home))

(defn write-sysctl-baseline-plist-command
  "Remote shell command that writes the sysctl-baseline plist to the system
   LaunchDaemon path (same `sudo tee` shell as the other daemons)."
  [plist]
  (write-plist-shell sysctl-baseline-label plist))

(defn sysctl-baseline-reprovision-command
  "Load-or-reload the root daemon: bootout is tolerated when it was never
   loaded, bootstrap is tolerated when already loaded, kickstart applies the
   sysctl now rather than at next boot."
  []
  (str "sudo launchctl bootout system/" sysctl-baseline-label " 2>/dev/null; sleep 1; "
       "sudo launchctl bootstrap system /Library/LaunchDaemons/" sysctl-baseline-label ".plist 2>/dev/null || true; "
       "sudo launchctl kickstart -k system/" sysctl-baseline-label))

(defn watchdog-reprovision-command
  "Reload + kickstart the watchdog (same bootout-settle-bootstrap dance as the mesh).
   Kotoba (required)."
  []
  (o 'watchdog-reprovision-command []))
