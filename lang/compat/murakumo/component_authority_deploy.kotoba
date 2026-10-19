(ns murakumo.component-authority-deploy
  "Deterministic deployment plan for Kototama authority receivers."
  (:require [kotoba.lang.text :as str]
            [murakumo.provision.plan :as provision]))

(def authority-keys
  #{:issuer :trusted-keys :port :path :tls-pkcs12-path :tls-password-env})
(def trusted-key-keys #{:issuer :public-key-hex})

(defn- identifier? [value]
  (and (string? value) (not (str/blank? value)) (<= (count value) 4096)))

(defn- reject [reason message data]
  (throw (ex-info message
                  (assoc data :murakumo.authority-deploy/reason reason))))

(defn validate-authority! [authority]
  (when-not
   (and (map? authority)
        (= authority-keys (set (keys authority)))
        (identifier? (:issuer authority))
        (map? (:trusted-keys authority))
        (seq (:trusted-keys authority))
        (every?
         (fn [[key-id entry]]
           (and (identifier? key-id)
                (map? entry)
                (= trusted-key-keys (set (keys entry)))
                (= (:issuer authority) (:issuer entry))
                (boolean
                 (re-matches #"[0-9a-f]{64}" (:public-key-hex entry)))))
         (:trusted-keys authority))
        (pos-int? (:port authority))
        (<= (:port authority) 65535)
        (identifier? (:path authority))
        (.startsWith ^String (:path authority) "/")
        (identifier? (:tls-pkcs12-path authority))
        (identifier? (:tls-password-env authority)))
    (reject :invalid-authority
            "Fleet authority deployment configuration is not exact" {}))
  authority)

(defn audience [node]
  (str "kototama://" (:name node)))

(defn endpoint [authority node]
  (let [host (or (:authority-host node) (:name node))]
    (str "https://" host ":" (:port authority) (:path authority))))

(defn receiver-config [authority node]
  (validate-authority! authority)
  (when-not (identifier? (:name node))
    (reject :invalid-node "Authority deployment requires a node name" {}))
  {:bind-host "0.0.0.0"
   :port (:port authority)
   :path (:path authority)
   :audience (audience node)
   :trusted-keys (:trusted-keys authority)
   :tls {:pkcs12-path (:tls-pkcs12-path authority)
         :password-env (:tls-password-env authority)}})

(defn- write-config-command [config]
  (str "sudo install -d -m 700 /etc/kototama && "
       "sudo tee /etc/kototama/component-authority.edn >/dev/null <<'EDN'\n"
       (pr-str config)
       "\nEDN\n"
       "sudo chmod 600 /etc/kototama/component-authority.edn"))

(defn- verify-secrets-command [authority]
  (format
   (str "sudo test -r /opt/kototama/deps.edn && "
        "sudo test -r %s && "
        "sudo test -r /etc/kototama/component-authority.secret && "
        "sudo grep -q '^%s=' /etc/kototama/component-authority.secret")
   (:tls-pkcs12-path authority)
   (:tls-password-env authority)))

(defn rsync-file-argv [local host remote]
  ["rsync" "-az" "-e" provision/ssh-rsync-options
   local (str host ":" remote)])

(defn deployment-plan
  "Create a secret-free rollout plan. TLS material and its password file must
  already exist root-only on the node; the plan verifies but never transports
  either secret. The default artifact root is the pinned sibling Kototama
  checkout used by this workspace."
  ([authority node]
   (deployment-plan authority node "../kototama"))
  ([authority node kototama-artifact-root]
   (let [config (receiver-config authority node)
         host (:host node)]
     (when-not (and (identifier? host)
                    (identifier? kototama-artifact-root))
       (reject :invalid-node
               "Authority deployment requires an SSH host and artifact root" {}))
     {:node (:name node)
      :host host
      :audience (audience node)
      :endpoint (endpoint authority node)
      :copies
      [(rsync-file-argv
        (str kototama-artifact-root "/deploy/bin/kototama-authority-daemon")
        host "/tmp/kototama-authority-daemon")
       (rsync-file-argv
        (str kototama-artifact-root
             "/deploy/systemd/kototama-authority-daemon.service")
        host "/tmp/kototama-authority-daemon.service")]
      :commands
      [(verify-secrets-command authority)
       (write-config-command config)
       (str "sudo install -m 755 /tmp/kototama-authority-daemon "
            "/usr/local/bin/kototama-authority-daemon")
       (str "sudo install -m 644 /tmp/kototama-authority-daemon.service "
            "/etc/systemd/system/kototama-authority-daemon.service")
       (str "sudo systemctl daemon-reload && "
            "sudo systemctl enable --now kototama-authority-daemon.service")]})))

(defn apply-deployment!
  "Execute a previously inspected plan with injected local and remote runners."
  [plan run-local! run-remote!]
  (doseq [argv (:copies plan)]
    (let [result (run-local! argv)]
      (when-not (zero? (:exit result))
        (reject :copy-failed "Authority deployment copy failed"
                {:node (:node plan) :result result}))))
  (doseq [command (:commands plan)]
    (let [result (run-remote! (:host plan) command)]
      (when-not (zero? (:exit result))
        (reject :remote-command-failed "Authority deployment command failed"
                {:node (:node plan) :result result}))))
  {:ok? true :node (:node plan) :endpoint (:endpoint plan)})
