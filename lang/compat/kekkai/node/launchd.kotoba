(ns kekkai.node.launchd
  "Residency on macOS: the launchd job that keeps the agent running.

  Pure plist generation — the installer (`bin/install.cljs`) writes what this
  returns. Being a function of a config map rather than a template file means the
  paths in the plist and the paths the agent actually uses cannot drift apart.

  **A LaunchDaemon, not a LaunchAgent** (`/Library/LaunchDaemons`, `RunAtLoad`,
  `KeepAlive`), for the reason murakumo's README already documents the hard way:
  a user LaunchAgent dies with the login session, and `launchctl list` over SSH
  shows only the user domain, so a job installed as an agent looks *absent* to
  every remote check. An overlay data plane has to survive logout."
  (:require [clojure.string :as str]))

(def ^:const default-label "cloud.kekkai.node")

(defn- xml-escape [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- plist-array [items]
  (str "  <array>\n"
       (str/join "\n" (map #(str "    <string>" (xml-escape %) "</string>") items))
       "\n  </array>"))

(defn plist
  "The LaunchDaemon plist for the agent.

   `:label`       job label (default `cloud.kekkai.node`)
   `:nbb`         absolute path to the nbb executable
   `:classpath`   the agent's classpath
   `:script`      absolute path to `bin/agent.cljs`
   `:config-file` absolute path to the agent's EDN config
   `:working-dir` where to run
   `:log-dir`     stdout/stderr destination"
  [{:keys [label nbb classpath script config-file working-dir log-dir]
    :or {label default-label log-dir "/var/log"}}]
  (let [args [nbb "--classpath" classpath script config-file]]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\""
         " \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
         "<plist version=\"1.0\">\n"
         "<dict>\n"
         "  <key>Label</key>\n  <string>" (xml-escape label) "</string>\n"
         "  <key>ProgramArguments</key>\n" (plist-array args) "\n"
         "  <key>RunAtLoad</key>\n  <true/>\n"
         "  <key>KeepAlive</key>\n  <true/>\n"
         ;; launchd restarts a crash-looping job immediately by default and then
         ;; throttles it; make the interval explicit so a misconfigured agent is
         ;; not a busy loop.
         "  <key>ThrottleInterval</key>\n  <integer>10</integer>\n"
         (when working-dir
           (str "  <key>WorkingDirectory</key>\n  <string>"
                (xml-escape working-dir) "</string>\n"))
         "  <key>StandardOutPath</key>\n  <string>"
         (xml-escape (str log-dir "/" label ".log")) "</string>\n"
         "  <key>StandardErrorPath</key>\n  <string>"
         (xml-escape (str log-dir "/" label ".err.log")) "</string>\n"
         "  <key>ProcessType</key>\n  <string>Background</string>\n"
         "</dict>\n</plist>\n")))

(defn plist-path [label] (str "/Library/LaunchDaemons/" (or label default-label) ".plist"))

(defn install-commands
  "The privileged steps, returned as data rather than executed: an installer
   should print exactly what it is about to run as root."
  [label]
  [["launchctl" "bootout" (str "system/" label)]        ; ignore failure if absent
   ["launchctl" "bootstrap" "system" (plist-path label)]
   ["launchctl" "enable" (str "system/" label)]])

(defn resolver-file
  "macOS split-DNS: `/etc/resolver/<suffix>` pointing at the agent's MagicDNS
   listener. This is why the DNS server defaults to an unprivileged port —
   `port` here is what makes that work without running the agent as root."
  [{:keys [tailnet port] :or {port 5354}}]
  {:path (str "/etc/resolver/" tailnet)
   :content (str "nameserver 127.0.0.1\nport " port "\n")})
