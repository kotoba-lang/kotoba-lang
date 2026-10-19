(ns murakumo.model-setup
  "Hugging Face model cache provisioning over the existing Tailscale SSH fleet."
  (:require [kotoba.lang.edn :as edn]
            [kotoba.lang.text :as str]
            [murakumo.fleet :as fleet]
            [murakumo.ssh :as ssh]))

(def default-cache-dir ".murakumo/models")
(def disk-headroom-kib (* 2 1024 1024))

(defn quote-arg [x]
  (str "'" (str/replace (str x) "'" "'\"'\"'") "'"))

(defn model-files [model]
  (vec (remove nil? [(:model/checkpoint model) (:model/gguf model)
                     (:model/mmproj model)])))

(defn download-command
  "Build a remote, resumable HF download command. Tokens are deliberately not
  embedded: gated/private repos must already be authenticated with `hf auth`."
  [model cache-dir]
  (let [repo (:hf/repo model)
        dest (str (str/replace (or cache-dir default-cache-dir) #"/+$" "")
                  "/" (:model/id model))
        files (model-files model)]
    (when-not (seq repo)
      (throw (ex-info "model has no :hf/repo" {:model (:model/id model)})))
    (str "mkdir -p " (quote-arg dest)
         ;; --break-system-packages: modern Debian/Ubuntu (PEP 668,
         ;; "externally-managed-environment") refuses even a --user pip
         ;; install without it — verified live 2026-07-19 against gad
         ;; (Ubuntu 24.04). Still --user (never touches system site-packages
         ;; or apt-managed files), just overrides the refusal for a
         ;; user-local install of one well-known PyPI package.
         " && (command -v hf >/dev/null 2>&1 || python3 -m pip install --user --break-system-packages -q -U huggingface_hub)"
         " && export PATH=\"$(python3 -m site --user-base)/bin:$HOME/.local/bin:$PATH\""
         " && hf download " (quote-arg repo)
         (apply str (map #(str " " (quote-arg %)) files))
         " --local-dir " (quote-arg dest))))

(defn status-command [model cache-dir]
  (let [dest (str (str/replace (or cache-dir default-cache-dir) #"/+$" "")
                  "/" (:model/id model))]
    (str "test -d " (quote-arg dest)
         " && du -sh " (quote-arg dest)
         " || echo missing")))

(defn- config [] (edn/read-string (slurp "infer.edn")))

(defn- model! [id]
  (or (get-in (config) [:models id])
      (throw (ex-info (str "unknown model " id)
                      {:known (sort (keys (:models (config))))}))))

(defn- node! [name]
  (or (first (filter #(= name (:name %)) (:nodes (fleet/load-fleet))))
      (throw (ex-info (str "unknown fleet node " name) {}))))

(defn- free-kib [node]
  (when (ssh/reachable? (:host node))
    (some-> (:out (ssh/sh (:host node)
                          "df -Pk \"$HOME\" | awk 'NR==2 {print $4}'"))
            str/trim parse-long)))

(defn select-live-node
  "Prefer a currently SSH-reachable regular node with free disk. The canary
  (`asher`) is fallback-only unless explicitly named, so setup work does not
  consume the fleet's control/relay reserve."
  ([nodes] (select-live-node nodes nil))
  ([nodes model]
   (let [required-kib (some-> (:model/snapshot-bytes model)
                              (+ 1023) (quot 1024) (+ disk-headroom-kib))]
    (->> nodes
       (pmap #(assoc % :disk/free-kib (free-kib %)))
       (filter :disk/free-kib)
       (filter #(or (nil? required-kib) (>= (:disk/free-kib %) required-kib)))
       (sort-by (juxt #(= "canary" (get-in % [:labels :role]))
                      #(long (- (:disk/free-kib %)))))
       first))))

(defn- target-node [node-name model]
  (if (and node-name (not= node-name "auto"))
    (let [node (node! node-name)
          free (free-kib node)
          need (some-> (:model/snapshot-bytes model) (+ 1023) (quot 1024)
                       (+ disk-headroom-kib))]
      (when-not free
        (throw (ex-info "requested fleet node is not SSH-reachable" {:node (:name node)})))
      (when (and need (< free need))
        (throw (ex-info "insufficient disk for model snapshot plus 2 GiB headroom"
                        {:node (:name node) :free-kib free :required-kib need})))
      node)
    (or (select-live-node (:nodes (fleet/load-fleet)) model)
        (throw (ex-info "no live fleet node has enough disk for model snapshot"
                        {:model (:model/id model)})))))

(defn cmd-plan [[model-id node-name cache-dir]]
  (let [model (model! model-id)]
    (println (pr-str {:model (:model/id model) :repo (:hf/repo model)
                      :files (model-files model) :node (or node-name "auto (most free disk)")
                      :cache-dir (or cache-dir default-cache-dir)
                      :command (download-command model cache-dir)}))))

(defn cmd-setup [[model-id node-name cache-dir]]
  (let [model (model! model-id)
        node (target-node node-name model)
        host (:host node)]
    (when-not (ssh/reachable? host)
      (throw (ex-info "fleet node is unreachable over Tailscale SSH" {:node (:name node)})))
    (println (format "[%s] downloading %s from huggingface.co/%s"
                     (:name node) (:model/id model) (:hf/repo model)))
    (let [{:keys [exit out err]} (ssh/sh host (download-command model cache-dir))]
      (print out)
      (when-not (zero? exit)
        (throw (ex-info "Hugging Face download failed"
                        {:node (:name node) :exit exit :error err})))
      (println (str "[" (:name node) "] "
                    (:out (ssh/sh host (status-command model cache-dir))))))))

(defn cmd-status [[model-id node-name cache-dir]]
  (let [model (model! model-id)
        nodes (if (or (nil? node-name) (= "all" node-name))
                (:nodes (fleet/load-fleet)) [(node! node-name)])]
    (doseq [node nodes]
      (if (ssh/reachable? (:host node))
        (println (format "[%-10s] %s" (:name node)
                         (:out (ssh/sh (:host node) (status-command model cache-dir)))))
        (println (format "[%-10s] unreachable" (:name node)))))))

(defn -main [& [cmd & args]]
  (case cmd
    "plan" (cmd-plan args)
    "setup" (cmd-setup args)
    "status" (cmd-status args)
    (println "usage: bb murakumo model plan|setup|status <model> [node|all] [cache-dir]")))
