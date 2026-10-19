(ns murakumo.component-runtime-deploy
  "Secret-free rollout plan for the resident native Kototama Component host.

  The receipt key is generated on the destination node and never crosses the
  operator boundary. The daemon binds loopback only; fleet access remains
  through the existing authenticated SSH transport."
  (:require [kotoba.lang.text :as str]
            [murakumo.provision.plan :as provision]))

(def label "com.murakumo.kototama-component")
(def port 18901)
(def remote-root ".murakumo/kototama-component")

(defn- identifier? [value]
  (and (string? value)
       (boolean (re-matches #"[A-Za-z0-9._-]{1,200}" value))))

(defn- digest? [value]
  (and (string? value) (boolean (re-matches #"[0-9a-f]{64}" value))))

(defn- cid? [value]
  (and (string? value) (boolean (re-matches #"b[a-z2-7]{20,200}" value))))

(defn validate-input!
  [{:keys [node user home binary bundle component-cid component-sha256
           expected-result budgets template capability-config] :as input}]
  (when-not
   (and (map? input)
        (map? node) (identifier? (:name node))
        (string? (:host node)) (not (str/blank? (:host node)))
        (identifier? user)
        (string? home) (.startsWith ^String home "/")
        (string? binary) (.startsWith ^String binary "/")
        (map? bundle)
        (= #{:component :wit :admission :provenance} (set (keys bundle)))
        (every? #(and (string? %) (.startsWith ^String % "/")) (vals bundle))
        (cid? component-cid)
        (digest? component-sha256)
        (integer? expected-result)
        (= #{:fuel :memory-pages :deadline-ms} (set (keys budgets)))
        (every? pos-int? (vals budgets))
        (or (nil? capability-config)
            (and (map? capability-config)
                 (= #{:local :sha256} (set (keys capability-config)))
                 (string? (:local capability-config))
                 (.startsWith ^String (:local capability-config) "/")
                 (digest? (:sha256 capability-config))))
        (string? template) (str/includes? template "{{COMPONENT_CID}}"))
    (throw (ex-info "invalid resident Component rollout input"
                    {:phase :component-runtime-deploy})))
  input)

(defn render-plist
  [template {:keys [node user home component-cid component-sha256
                    expected-result budgets capability-config]}]
  (let [root (str home "/" remote-root)]
    (-> template
        (str/replace "{{USER}}" user)
        (str/replace "{{BIN}}" (str root "/tender-component-host"))
        (str/replace "{{COMPONENT}}" (str root "/application.component.wasm"))
        (str/replace "{{COMPONENT_CID}}" component-cid)
        (str/replace "{{COMPONENT_SHA256}}" component-sha256)
        (str/replace "{{EXPECTED_RESULT}}" (str expected-result))
        (str/replace "{{FUEL}}" (str (:fuel budgets)))
        (str/replace "{{MEMORY_PAGES}}" (str (:memory-pages budgets)))
        (str/replace "{{NODE}}" (:name node))
        (str/replace "{{SEED}}" (str root "/receipt.seed"))
        (str/replace "{{RECEIPTS}}" (str root "/receipts.jsonl"))
        (str/replace "{{CAPABILITY_CONFIG}}"
                     (if capability-config
                       (str root "/capabilities.json")
                       ""))
        (str/replace "{{CAPABILITY_CONFIG_SHA256}}"
                     (or (:sha256 capability-config) ""))
        (str/replace "{{LOG}}" (str root "/daemon.log")))))

(defn- rsync-argv [local host remote]
  ["rsync" "-az" "-e" provision/ssh-rsync-options
   local (str host ":" remote)])

(defn deployment-plan
  [{:keys [node home binary bundle template capability-config] :as input}]
  (validate-input! input)
  (let [host (:host node)
        root (str home "/" remote-root)
        plist (render-plist template input)]
    {:format :murakumo.component-runtime-deployment/v1
     :node (:name node)
     :host host
     :endpoint (str "http://127.0.0.1:" port)
     :prepare-commands
     [(str "install -d -m 700 " root " && "
           "if test ! -s " root "/receipt.seed; then "
           "umask 077; openssl rand -hex 32 > " root "/receipt.seed; fi")]
     :copies
     (cond->
      [(rsync-argv binary host (str root "/tender-component-host"))
       (rsync-argv (:component bundle) host (str root "/application.component.wasm"))
       (rsync-argv (:wit bundle) host (str root "/application.component.wasm.wit"))
       (rsync-argv (:admission bundle) host
                   (str root "/application.component.wasm.admission.edn"))
       (rsync-argv (:provenance bundle) host
                   (str root "/application.component.wasm.provenance.edn"))]
       capability-config
       (conj (rsync-argv (:local capability-config) host
                         (str root "/capabilities.json"))))
     :activate-commands
     [(str "chmod 700 " root "/tender-component-host")
      (str "sudo tee /Library/LaunchDaemons/" label ".plist >/dev/null <<'PLIST'\n"
           plist "\nPLIST\n"
           "sudo chown root:wheel /Library/LaunchDaemons/" label ".plist && "
           "sudo chmod 644 /Library/LaunchDaemons/" label ".plist")
      (str "sudo launchctl bootout system/" label " 2>/dev/null || true; "
           "sleep 1; sudo launchctl bootstrap system /Library/LaunchDaemons/"
           label ".plist && sudo launchctl kickstart -k system/" label)]}))

(defn apply-deployment!
  [plan run-local! run-remote!]
  (doseq [command (:prepare-commands plan)]
    (let [result (run-remote! (:host plan) command)]
      (when-not (zero? (:exit result))
        (throw (ex-info "resident Component prepare failed"
                        {:phase :component-runtime-deploy :result result})))))
  (doseq [argv (:copies plan)]
    (let [result (run-local! argv)]
      (when-not (zero? (:exit result))
        (throw (ex-info "resident Component copy failed"
                        {:phase :component-runtime-deploy :result result})))))
  (doseq [command (:activate-commands plan)]
    (let [result (run-remote! (:host plan) command)]
      (when-not (zero? (:exit result))
        (throw (ex-info "resident Component activation failed"
                        {:phase :component-runtime-deploy :result result})))))
  {:ok? true :node (:node plan) :endpoint (:endpoint plan)})
