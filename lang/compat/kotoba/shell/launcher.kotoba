(ns kotoba.shell.launcher
  (:gen-class)
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kotoba.shell.native-bridge :as bridge]
            [kotoba.shell.stack-e2e :as stack-e2e]))

(def supported-shell-targets #{:macos :ios :android :windows})

(declare run-native-host-command run-native-host-command-in-dir
         execute-requested? external-step step-run-result)

(def host-runner-specs
  {:macos {:kind :process
           :command "bin/kotoba-shell-host-macos"
           :connection "local-process"
           :providers ["clipboard/read-text"
                       "clipboard/write-text"
                       "fs/read-text"
                       "fs/write-text"
                       "fs/append-text"
                       "http/fetch"
                       "notify/show"
                       "keychain/read-text"
                       "keychain/write-text"
                       "keychain/delete"
                       "webauthn/register"
                       "webauthn/assert"
                       "contacts/list"
                       "calendar/list-events"]}
   :ios {:kind :simctl
         :command "bin/kotoba-shell-host-ios"
         :connection "xcrun-simctl"
         :providers ["clipboard/read-text"
                     "clipboard/write-text"
                     "fs/read-text"
                     "fs/write-text"
                     "fs/append-text"
                     "http/fetch"
                     "browser/open-url"
                     "notify/show"
                     "keychain/read-text"
                     "keychain/write-text"
                     "keychain/delete"]}
   :android {:kind :adb
             :command "bin/kotoba-shell-host-android"
             :connection "adb-shell"
             :providers ["clipboard/read-text"
                         "clipboard/write-text"
                         "fs/read-text"
                         "fs/write-text"
                         "fs/append-text"
                         "http/fetch"
                         "browser/open-url"
                         "notify/show"
                         "keychain/read-text"
                         "keychain/write-text"
                         "keychain/delete"]}
   :windows {:kind :process
             :command "bin/kotoba-shell-host-windows.cmd"
             :connection "powershell-process"
             :providers ["clipboard/read-text"
                         "clipboard/write-text"
                         "fs/read-text"
                         "fs/write-text"
                         "fs/append-text"
                         "http/fetch"
                         "notify/show"
                         "keychain/read-text"
                         "keychain/write-text"]}})

;; WebKit is the rendering contract on Apple platforms. kotoba:dom remains an
;; optional compatibility/input ABI, not an alternative default renderer.
(def surface-host-specs
  {:macos {:kind :native-surface
           :display "app-window"
           :ui-substrate "ClojureScript/WebGL"
           :browser-engine "WebKit"
           :render-substrate :wkwebview
           :renderers [:webgl :webgpu :native]
           :input-events [:pointer :keyboard :text :focus :resize]}
   :ios {:kind :native-surface
         :display "ui-window-scene"
         :ui-substrate "ClojureScript/WebGL"
         :browser-engine "WebKit"
         :render-substrate :wkwebview
         :renderers [:webgpu :native]
         :input-events [:touch :keyboard :text :focus :resize]}
   :android {:kind :native-surface
             :display "activity-surface"
             :ui-substrate "kotoba-lang/dom-gpu"
             :browser-engine "kotoba-lang/browser"
             :render-substrate :android-webview
             :renderers [:vulkan :opengles :native]
             :input-events [:touch :keyboard :text :focus :resize]}
   :windows {:kind :native-surface
             :display "external-window"
             :ui-substrate "kotoba-lang/dom-gpu"
             :browser-engine "kotoba-lang/browser"
             :renderers [:webgpu :native]
             :input-events [:pointer :keyboard :text :focus :resize]}})

(def release-target-specs
  {:macos {:artifact ".app"
           :package [:app-bundle :dmg :pkg]
           :signing [:codesign :notarization]
           :updater [:feed :signature :rollback]
           :required-manifest-keys [:app/id :app/version :app/name]}
   :ios {:artifact ".ipa"
         :package [:xcode-archive :ipa]
         :signing [:development-team :provisioning-profile :app-store-connect]
         :updater [:app-store-release]
         :required-manifest-keys [:app/id :app/version :app/name :ios/bundle-id]}
   :android {:artifact ".apk/.aab"
             :package [:gradle :apk :aab]
             :signing [:keystore :v2-signature :play-app-signing]
             :updater [:play-release-track]
             :required-manifest-keys [:app/id :app/version :app/name :android/application-id]}
   :windows {:artifact ".msix"
             :package [:msix]
             :signing [:authenticode]
             :updater [:feed :signature]
             :required-manifest-keys [:app/id :app/version :app/name]}})

(def production-connection-specs
  {:macos {:credentials [{:id :developer-id-application
                          :label "Developer ID Application identity"
                          :option "--codesign-identity"
                          :env "KOTOBA_MACOS_CODESIGN_IDENTITY"}
                         {:id :notary-profile
                          :label "notarytool keychain profile"
                          :option "--notary-profile"
                          :env "KOTOBA_MACOS_NOTARY_PROFILE"}
                         {:id :updater-signing-key
                          :label "updater signing key"
                          :option "--updater-key"
                          :env "KOTOBA_UPDATER_SIGNING_KEY"}]
           :artifacts [:app-bundle :dmg]
           :distribution [:developer-id :notarized-dmg :signed-updater-feed]}
   :ios {:credentials [{:id :team-id
                        :label "Apple Developer Team ID"
                        :option "--apple-team-id"
                        :env "KOTOBA_IOS_TEAM_ID"}
                       {:id :app-store-connect-key
                        :label "App Store Connect API key"
                        :option "--app-store-key"
                        :env "KOTOBA_APP_STORE_CONNECT_KEY"}
                       {:id :provisioning-profile
                        :label "iOS provisioning profile"
                        :option "--provisioning-profile"
                        :env "KOTOBA_IOS_PROVISIONING_PROFILE"}]
         :artifacts [:xcode-archive :ipa]
         :distribution [:testflight :app-store-release]}
   :android {:credentials [{:id :keystore
                            :label "Android signing keystore"
                            :option "--keystore"
                            :env "KOTOBA_ANDROID_KEYSTORE"}
                           {:id :keystore-alias
                            :label "Android signing key alias"
                            :option "--keystore-alias"
                            :env "KOTOBA_ANDROID_KEY_ALIAS"}
                           {:id :play-service-account
                            :label "Google Play service account JSON"
                            :option "--play-service-account"
                            :env "KOTOBA_PLAY_SERVICE_ACCOUNT"}]
             :artifacts [:aab :apk]
             :distribution [:internal-track :play-release-track]}
   :windows {:credentials [{:id :authenticode-cert
                            :label "Authenticode certificate"
                            :option "--authenticode-cert"
                            :env "KOTOBA_WINDOWS_AUTHENTICODE_CERT"}
                           {:id :updater-signing-key
                            :label "updater signing key"
                            :option "--updater-key"
                            :env "KOTOBA_UPDATER_SIGNING_KEY"}]
             :artifacts [:msix]
             :distribution [:signed-msix :signed-updater-feed]}})

(def stable-api-spec
  {:schema "kotoba.shell.api.v1"
   :version 1
   :commands [["adapter" "check"]
              ["surface" "check"]
              ["surface" "commit"]
              ["policy" "check"]
              ["release" "check"]
              ["release" "dry-run"]
              ["release" "evidence"]
              ["release" "connect"]
              ["release" "verify"]
              ["release" "sign"]
              ["release" "submit"]
             ["updater" "publish"]
              ["store" "request"]
              ["store" "status"]
              ["distribution" "check"]
              ["distribution" "plan"]
              ["app" "scaffold"]
              ["app" "check"]
              ["app" "build"]
              ["app" "package"]
              ["app" "run"]
              ["app" "visual-test"]
              ["app" "kaizen"]
              ["api" "check"]
              ["api" "freeze"]
              ["api" "compat"]
              ["plugin" "check"]
              ["plugin" "tauri-check"]
              ["plugin" "abi-smoke"]
              ["doctor" "check"]
              ["device-farm" "check"]
              ["device-farm" "schedule"]
              ["e2e" "check"]
              ["e2e" "stack"]
              ["e2e" "native-window"]
              ["e2e" "runtime-parity"]
              ["ui" "check"]
              ["ui" "smoke"]
              ["native-host" "check"]
              ["native-host" "run"]
              ["native-host" "provider"]]
   :compatibility {:breaking-change-policy :major-version-only
                   :data-format :edn-json-lossless
                   :provider-abi :pointer-length-buffer-result
                   :deprecated-command-policy :no-hidden-shims}})

(def plugin-api-spec
  {:schema "kotoba.shell.plugin-api.v1"
   :version 1
   :required-keys [:plugin/id :plugin/version :plugin/api-version :plugin/providers]
   :provider-required-keys [:id :capability :commands]
   :compatible-api-versions [1]
   :host-abi :pointer-length-buffer-result})

(def doctor-target-specs
  ;; xcodegen(macos/ios)と gradle(android)は native-render-pipeline(app
  ;; scaffold/app build)が実際に依存する外部ツールだが、当時 doctor の棚卸しに
  ;; 追加し忘れていた — `doctor check` は今まで「app scaffold/build が動くか」を
  ;; 一切見ておらず、xcodegen 不在は :xcodegen-ok? false で初めて判明する
  ;; (実行するまで分からない)状態だった。:optional-tools に加えるだけなので
  ;; :required-ok?/:ready? の計算(tool-row required? のみ見る)には影響しない —
  ;; 既存の doctor check 呼び出しの合否は変わらず、可視性が増えるだけ。
  {:macos {:required-tools ["/usr/bin/pbcopy"
                            "/usr/bin/pbpaste"
                            "/usr/bin/curl"
                            "/usr/bin/security"
                            "/usr/bin/codesign"]
           :optional-tools ["/usr/bin/osascript"
                            "/usr/bin/xcrun"
                            "/usr/bin/swift"
                            "xcodegen"]
           :checks [:host-runner :clipboard :fs :http :keychain :codesign :notarization]}
   :ios {:required-tools ["/usr/bin/xcrun"]
         :optional-tools ["xcodegen"]
         :checks [:host-runner :simctl :xcodebuild :codesign :provisioning]}
   :android {:required-tools ["adb"]
             :optional-tools ["keytool" "jarsigner" "gradle"]
             :checks [:host-runner :adb :gradle :keystore :apk-signing]}
   :windows {:required-tools []
             :optional-tools []
             :checks [:external-host-runner :authenticode :msix]}})

(def ui-substrate-specs
  {:wasm-ui {:repo "../dom-gpu"
             :role "kotoba:dom UI substrate"
             :required-files ["deps.edn"
                              "package.json"
                              "src/kotoba/wasm/dom.cljc"
                              "src/kotoba/wasm/runtime.cljc"
                              "src/kotoba/wasm/host/webgl.cljs"
                              "src/kotoba/wasm/host/webgpu.cljs"
                              "public/kotoba-wasm-ui.html"
                              "public/kotoba-wasm-webgpu.html"]
             :required-scripts ["test:clj"
                                "compile:all"
                                "smoke:wasm-ui"
                                "check:wasm-ui"]
             :smoke-scripts ["test:clj"
                             "smoke:wasm-ui"
                             "check:wasm-ui"]}
   :browser {:repo "../browser"
             :role "Kotoba browser and OS surface engine"
             :required-files ["deps.edn"
                              "package.json"
                              "src/browser/core.cljc"
                              "src/browser/session.cljc"
                              "src/browser/surface.cljc"
                              "src/browser/input.cljc"
                              "src/browser/visual_smoke_check.clj"
                              "public/index.html"]
             :required-scripts ["compile:smoke"
                                "compile:webgpu-smoke"
                                "smoke:visual"
                                "smoke:webgpu"]
             :smoke-scripts ["smoke:visual"
                             "smoke:webgpu"]}})

(def selfhost-seed-names
  ["aiueos_provider_catalog"
   "native_host_contract"
   "shell_evidence_profile"])

(defn- unblob
  "resources/kotoba/shell/selfhost/*.edn is stored on disk as a Datomic/
   Datascript tx-data vector (scripts/edn-datomize.cljs): a single entity map
   whose non-scalar values (nested maps, vectors-of-maps) are pr-str'd blob
   strings so the file stays queryable at the entity+attribute granularity.
   unblob reverses that for one value: if it is a string that reads back to
   a collection, return the parsed collection; otherwise return it unchanged."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Reverses the scripts/edn-datomize.cljs wrap-map transform: strips the
   :db/id and the namespace off every attribute key, and unblobs any pr-str'd
   nested value, so callers get back the exact same flat un-namespaced map
   the seed files used to contain (only the file's on-disk shape changed;
   selfhost-seed's return shape is unchanged)."
  [tx-data]
  (into {}
        (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(defn selfhost-seed
  [name]
  (let [resource (str "kotoba/shell/selfhost/" name ".edn")]
    (if-let [url (io/resource resource)]
      (reconstitute-entity (-> url slurp edn/read-string))
      (throw (ex-info "missing kotoba-shell selfhost seed"
                      {:resource resource
                       :seed name})))))

(defn selfhost-seeds
  []
  (into {}
        (map (fn [name] [(keyword name) (selfhost-seed name)]))
        selfhost-seed-names))

(defn target-option
  [argv]
  (keyword (or (second (drop-while #(not= "--target" %) argv))
               "macos")))

(defn option-value
  [argv option]
  (second (drop-while #(not= option %) argv)))

(defn option-values
  [argv option]
  (loop [xs argv
         values []]
    (if-let [xs (seq xs)]
      (if (= option (first xs))
        (recur (nnext xs) (conj values (second xs)))
        (recur (next xs) values))
      values)))

(defn read-edn-option
  [argv option]
  (when-let [value (option-value argv option)]
    (edn/read-string value)))

(defn read-edn-file-option
  [argv option]
  (when-let [path (option-value argv option)]
    (-> path slurp edn/read-string)))

(defn file-reference?
  [value]
  (and (string? value)
       (str/starts-with? value "@")))

(defn value-or-file
  [value]
  (if (file-reference? value)
    (str/trim (slurp (subs value 1)))
    value))

(defn json-ready
  [value]
  (cond
    (keyword? value) (name value)
    (map? value) (into {} (map (fn [[k v]] [(json-ready k) (json-ready v)])) value)
    (vector? value) (mapv json-ready value)
    (seq? value) (mapv json-ready value)
    (set? value) (mapv json-ready value)
    :else value))

(defn render-result
  [result json?]
  (if json?
    (json/write-str (json-ready result))
    (pr-str result)))

(defn result->exit
  [result]
  (if (:kotoba.cli/ok? result) 0 1))

(defn contract-exports
  ([seed] (contract-exports seed nil))
  ([seed target]
   (merge (:common-exports seed)
          (when target
            (get-in seed [:target-exports target])))))

(defn provider-target-supported?
  [target provider]
  (let [required (set (:required-targets provider))]
    (or (empty? required)
        (contains? required target))))

(defn shell-authority-data
  []
  {:kotoba.shell/authority "kotoba-lang/shell"
   :kotoba.shell/authority-repo "kotoba-lang/shell"
   :kotoba.shell/deprecated-shim? false})

(defn repo-root
  []
  (let [url (io/resource "kotoba/shell/selfhost/native_host_contract.edn")]
    (when (= "file" (.getProtocol url))
      (-> url
          io/file
          .getParentFile
          .getParentFile
          .getParentFile
          .getParentFile
          .getParentFile
          .getPath))))

(defn sibling-path
  [relative]
  (if-let [root (repo-root)]
    (.getCanonicalPath (io/file root relative))
    relative))

(defn read-json-file
  [file]
  (json/read-str (slurp file)))

(defn default-host-command
  [target]
  (when-let [command (get-in host-runner-specs [target :command])]
    (if-let [root (repo-root)]
      (.getPath (io/file root command))
      command)))

(def default-provider-timeout-seconds 10)

(defn provider-for
  [target command]
  (let [catalog (selfhost-seed "aiueos_provider_catalog")]
    (some (fn [provider]
            (when (and (provider-target-supported? target provider)
                       (some #{command} (:commands provider)))
              provider))
          (:providers catalog))))

(defn provider-capability
  [target command]
  (:capability (provider-for target command)))

(defn provider-timeout-seconds
  "Per-provider subprocess timeout, in seconds. Most providers (clipboard,
   keychain, fs, http) complete near-instantly and use the default; providers
   that need a human to notice and respond to system UI (e.g. webauthn's
   Touch ID / password sheet) declare a longer :timeout-seconds in the
   catalog so they are not killed mid-ceremony."
  [target command]
  (or (:timeout-seconds (provider-for target command))
      default-provider-timeout-seconds))

(defn provider-policy
  [argv]
  (or (read-edn-file-option argv "--policy")
      (read-edn-option argv "--policy-edn")
      {:allow ["clipboard/read-text"
               "clipboard/write-text"
               "fs/read-text"
               "fs/write-text"
               "fs/append-text"
               "http/fetch"
               "browser/open-url"
               "notify/show"]
       :deny []
       :audit true}))

(defn policy-decision
  "Whether `policy` permits `command` on `target`.

  The token list is built with `into`, not a set literal. `#{command
  capability \"*\"}` threw `IllegalArgumentException: Duplicate key` for every
  provider whose command and capability are spelled the same — `http/fetch`
  and `notify/show` — so any policy check on those two crashed instead of
  deciding. Found 2026-08-07 by the parity test that asserts this function and
  the in-app bridge agree; the CLI path had the bug since the function was
  written and nothing exercised it."
  [policy target command]
  (let [capability (provider-capability target command)
        allow (set (:allow policy))
        deny (set (:deny policy))
        token-set (into #{} (remove nil?) [command capability "*"])]
    {:allowed? (and (not-any? deny token-set)
                    (or (contains? allow "*")
                        (contains? allow command)
                        (contains? allow capability)))
     :capability capability
     :matched-allow (first (filter allow [command capability "*"]))
     :matched-deny (first (filter deny [command capability "*"]))}))

(defn audit-record
  [event data]
  (merge {:audit/event event
          :audit/schema "kotoba.shell.audit.v0"
          :audit/authority "kotoba-lang/shell"}
         data))

(defn app-manifest
  [argv]
  (cond-> (or (read-edn-file-option argv "--manifest")
              (read-edn-option argv "--manifest-edn")
              {:app/id "kotoba.shell.demo"
               :app/name "Kotoba Shell Demo"
               :app/version "0.1.0"})
    (option-value argv "--app-id")
    (assoc :app/id (option-value argv "--app-id"))

    (option-value argv "--app-name")
    (assoc :app/name (option-value argv "--app-name"))

    (option-value argv "--version")
    (assoc :app/version (option-value argv "--version"))

    (option-value argv "--ios-bundle-id")
    (assoc :ios/bundle-id (option-value argv "--ios-bundle-id"))

    (option-value argv "--android-application-id")
    (assoc :android/application-id (option-value argv "--android-application-id"))

    (option-value argv "--web-dist-dir")
    (assoc :web/dist-dir (option-value argv "--web-dist-dir"))

    ;; Signing, for `app package`. Flags rather than manifest-only so a CI job
    ;; can name the team without rewriting the app manifest it was handed.
    (option-value argv "--team-id")
    (assoc :apple/team-id (option-value argv "--team-id"))

    (option-value argv "--ios-export-method")
    (assoc :ios/export-method (keyword (option-value argv "--ios-export-method")))

    (option-value argv "--macos-export-method")
    (assoc :macos/export-method (keyword (option-value argv "--macos-export-method")))

    (option-value argv "--ios-provisioning-profile")
    (assoc :ios/provisioning-profile (option-value argv "--ios-provisioning-profile"))

    (option-value argv "--code-sign-identity")
    (assoc :macos/code-sign-identity (option-value argv "--code-sign-identity"))

    (option-value argv "--code-sign-style")
    (assoc :ios/code-sign-style (keyword (option-value argv "--code-sign-style")))

    (option-value argv "--code-sign-style")
    (assoc :macos/code-sign-style (keyword (option-value argv "--code-sign-style")))))

(defn missing-manifest-keys
  [target manifest]
  (vec (remove #(contains? manifest %)
               (get-in release-target-specs [target :required-manifest-keys]))))

(defn sanitize-path-part
  [value]
  (str/replace (str value) #"[^A-Za-z0-9._-]" "-"))

(defn sha256-hex
  [value]
  (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256")
                        (.getBytes (str value) java.nio.charset.StandardCharsets/UTF_8))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn target-artifact-name
  [target manifest]
  (let [base (str (sanitize-path-part (:app/id manifest))
                  "-"
                  (sanitize-path-part (:app/version manifest))
                  "-"
                  (name target))]
    (case target
      :macos (str base ".app")
      :ios (str base ".ipa")
      :android (str base ".apk")
      :windows (str base ".msix")
      base)))

(defn release-output-dir
  [argv]
  (or (option-value argv "--output-dir")
      "target/kotoba-shell/release"))

(defn app-output-dir
  [argv]
  (or (option-value argv "--output-dir")
      "target/kotoba-shell/app"))

(defn write-edn-file!
  [file value]
  (.mkdirs (.getParentFile (io/file file)))
  (spit file (pr-str value))
  file)

(defn write-text-file!
  [file value]
  (.mkdirs (.getParentFile (io/file file)))
  (spit file value)
  file)

;; ─── web bundle embedding (ADR-2607081015 の "WKWebView 実用優先" 決定) ───────
;;
;; native-rendering の設計フォークは WKWebView(macOS/iOS)/android.webkit.WebView
;; (Android)を今のレンダー基盤にする側を選んだ — kotoba-lang/dom-gpu +
;; kotoba-lang/browser は R0 段階・実運用実績ゼロ(ADR-2607081015)のため、
;; surface-host-specs が示す長期目標のまま残し、scaffold が実際に生成する
;; アプリはこの pragmatic な WebView 実装にする(:web/dist-dir manifest オプション)。

(def placeholder-web-index-html
  "manifest に :web/dist-dir が無い時に使う既定 web bundle。WKWebView/WebView が
  実際に何かを描画できることをスモークテストできる最小の静的ページ。"
  (str "<!doctype html><html><head><meta charset=\"utf-8\">"
       "<title>kotoba-shell</title></head>"
       "<body style=\"font:16px -apple-system,sans-serif;display:flex;"
       "align-items:center;justify-content:center;height:100vh;margin:0;"
       "background:#111;color:#eee;\">"
       "<div>kotoba-shell native host — WKWebView/WebView OK</div>"
       "</body></html>"))

(defn- copy-tree!
  "src ディレクトリ配下のファイルをすべて dest 配下へ再帰コピーする(単純な
  file-seq 走査、シンボリックリンクは辿らない)。"
  [src dest]
  (let [src-file (io/file src)]
    (when (.isDirectory src-file)
      (doseq [f (file-seq src-file)]
        (when (.isFile f)
          (let [rel (.relativize (.toPath src-file) (.toPath f))
                target (io/file dest (str rel))]
            (.mkdirs (.getParentFile target))
            (io/copy f target)))))))

(defn web-assets-into!
  "manifest の :web/dist-dir(あれば)を dest-dir へコピーする。無ければ
  placeholder-web-index-html を1枚書く — :web/dist-dir 無しでも scaffold/build
  が常に動作確認できるようにする(スモークテスト用途)。"
  [manifest dest-dir]
  (if-let [src (:web/dist-dir manifest)]
    (do (copy-tree! src dest-dir) {:source src :placeholder? false})
    (do (write-text-file! (io/file dest-dir "index.html") placeholder-web-index-html)
        {:source nil :placeholder? true})))

(defn release-dry-run-row
  [output-dir manifest target]
  (let [missing (missing-manifest-keys target manifest)
        known? (contains? supported-shell-targets target)
        ok? (and known? (empty? missing))]
    (if-not ok?
      {:target target
       :ok? false
       :missing-manifest-keys (or missing [])
       :reason (if known? :manifest-invalid :unknown-target)}
      (let [target-dir (io/file output-dir (name target))
            artifact-name (target-artifact-name target manifest)
            artifact-file (io/file target-dir artifact-name)
            signature-file (io/file target-dir (str artifact-name ".sig.edn"))
            feed-file (io/file target-dir "updater-feed.edn")
            artifact-body {:schema "kotoba.shell.release-artifact.v0"
                           :target target
                           :artifact artifact-name
                           :artifact-kind (get-in release-target-specs [target :artifact])
                           :manifest manifest}
            artifact-digest (sha256-hex artifact-body)
            signature {:schema "kotoba.shell.release-signature.v0"
                       :target target
                       :mode :dry-run
                       :algorithm :sha256
                       :artifact artifact-name
                       :digest artifact-digest}
            feed {:schema "kotoba.shell.updater-feed.v0"
                  :target target
                  :mode :dry-run
                  :app/id (:app/id manifest)
                  :app/version (:app/version manifest)
                  :artifact artifact-name
                  :artifact-digest artifact-digest
                  :signature-file (.getName signature-file)}]
        (write-edn-file! artifact-file artifact-body)
        (write-edn-file! signature-file signature)
        (write-edn-file! feed-file feed)
        {:target target
         :ok? true
         :artifact (.getPath artifact-file)
         :signature (.getPath signature-file)
         :updater-feed (.getPath feed-file)
         :artifact-digest artifact-digest
         :packaging-ready? true
         :signing-ready? true
         :updater-ready? true
         :dry-run? true}))))

(defn- xml-escape
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn product-name-for
  "The Xcode product/project/scheme name for `target`.

  **`app scaffold` and `app build` MUST derive this the same way.** They did
  not: scaffold read `:macos/product-name`/`:ios/product-name` while build
  hardcoded `KotobaShell` in its missing-file check, its
  `xcodebuild -project` path and its `-scheme`. So any consumer that set a
  product name scaffolded a working `<name>.xcodeproj` and then had
  `app build` fail with `'…/KotobaShell.xcodeproj' does not exist` — the one
  configuration that worked was the one that left the name unset and got the
  demo default, which is exactly what CI and the README examples do, so the
  break never showed up there. Found 2026-07-27 by the first consumer with a
  real product name (`gftdcojp/cloud-itonami`, `:macos/product-name
  \"itonami\"`)."
  [target manifest]
  (or (case target
        :macos (:macos/product-name manifest)
        :ios (:ios/product-name manifest)
        nil)
      "KotobaShell"))

(defn team-id
  "The Apple Developer team a target signs with, if the manifest declares one.

  Per-target first (`:ios/team-id`, `:macos/team-id`) because the two can
  legitimately differ — an app distributed through the App Store and a macOS
  build signed with Developer ID may sit in different teams — then
  `:apple/team-id` as the common case."
  [target manifest]
  (or (case target
        :ios (:ios/team-id manifest)
        :macos (:macos/team-id manifest)
        nil)
      (:apple/team-id manifest)))

(defn signable?
  "Whether this manifest asks for a signed build at all.

  Absence is not a misconfiguration: a simulator build, a CI smoke test and a
  local `xcodebuild build` all legitimately want no signing, and that is what
  every consumer of this repo does today. So the unsigned settings stay the
  default and signing turns on only when a team is named."
  [target manifest]
  (some? (team-id target manifest)))

(defn- signing-settings-yml
  [target manifest]
  (if-let [team (team-id target manifest)]
    (let [style (name (or (case target
                            :ios (:ios/code-sign-style manifest)
                            :macos (:macos/code-sign-style manifest)
                            nil)
                          :automatic))
          profile (case target
                    :ios (:ios/provisioning-profile manifest)
                    :macos (:macos/provisioning-profile manifest)
                    nil)
          identity (case target
                     :ios (:ios/code-sign-identity manifest)
                     ;; Developer ID Application is the identity a macOS app
                     ;; distributed outside the App Store must carry; App
                     ;; Store builds override it via :macos/code-sign-identity.
                     :macos (or (:macos/code-sign-identity manifest) "Developer ID Application")
                     nil)]
      (str "        DEVELOPMENT_TEAM: " team "\n"
           "        CODE_SIGN_STYLE: " (str/capitalize style) "\n"
           (when identity
             (str "        CODE_SIGN_IDENTITY: \"" identity "\"\n"))
           (when profile
             (str "        PROVISIONING_PROFILE_SPECIFIER: \"" profile "\"\n"))
           ;; Notarization rejects a macOS app without the hardened runtime,
           ;; so a signed macOS build enables it; iOS has no such setting.
           (when (= target :macos)
             "        ENABLE_HARDENED_RUNTIME: YES\n")))
    (str "        CODE_SIGNING_ALLOWED: NO\n"
         "        CODE_SIGNING_REQUIRED: NO\n"
         "        ENABLE_HARDENED_RUNTIME: NO\n")))

(defn- xcodegen-project-yml
  "macOS/iOS 共通の XcodeGen spec。project.pbxproj を手書きせず、scaffold 後に
  `xcodegen generate` へ渡して実際にビルド可能な .xcodeproj を作る(xcodegen-generate!
  参照)。CODE_SIGNING_ALLOWED=NO はローカル build/シミュレータ実行専用の設定 —
  配布(release/sign)は別の release-* パイプラインが担当する。

  Info.plist は別ファイルを手書きして `info.path` で渡す方式を試したが、
  xcodegen 2.45.4 はテンプレート側の既定キー(CFBundleShortVersionString=\"1.0\"
  固定など)で上書きし、こちらが書いた NSPrincipalClass 等は消えて反映されない
  ことを実機で確認した(scaffold-check 後の実 Info.plist を diff して確認済み) —
  よって `info.properties` に必要な key を直接書き、xcodegen 自身に完全な
  Info.plist を生成させる(手書きファイルを介さない)。同じ理由で target 直下の
  `resources:` キーも存在しない(書いても Copy Bundle Resources フェーズが
  生成されないことを確認済み) — `sources:` に列挙したパス配下の各ファイルは
  拡張子で自動判定され、コンパイル対象でないもの(index.html 等)は自動的に
  Resources ビルドフェーズへ入る。

  Resources/WebBundle だけ `type: folder`(folder reference)にしているのは
  意図的 — 通常の group 参照だと Xcode の Copy Bundle Resources が入れ子
  ディレクトリをバンドルのルートへフラット化してしまい、index.html が
  `vendor/scittle.js` のような相対パスで参照する子ディレクトリが実機バンドル
  から消える実バグを Safari Web Inspector 無しで再現・特定した
  (web-assets-dest の docstring 参照)。folder reference はディレクトリ構造を
  保ったままコピーするので、WebBundle 配下は何階層ネストしても安全。"
  [target manifest]
  (let [platform (case target :macos "macOS" :ios "iOS")
        deployment (case target :macos "13.0" :ios "16.0")
        product-name (product-name-for target manifest)
        bundle-id (case target
                    :macos (or (:macos/bundle-id manifest) (:app/id manifest))
                    :ios (:ios/bundle-id manifest))
        info-properties (case target
                          :macos (str "        NSPrincipalClass: NSApplication\n"
                                      "        NSHighResolutionCapable: true\n")
                          ;; scene 無しの単純な UIApplicationDelegate ライフサイクルに
                          ;; するため UIApplicationSceneManifest は書かない。
                          ;; UILaunchScreen だけ空辞書で用意し、別途 storyboard を
                          ;; 用意しなくても xcodebuild が起動画面要件を満たせるように
                          ;; する(iOS 13+ の推奨最小構成)。
                          :ios (str "        UILaunchScreen: {}\n"
                                    "        UISupportedInterfaceOrientations:\n"
                                    "          - UIInterfaceOrientationPortrait\n"))]
    (str "name: " product-name "\n"
         "options:\n"
         "  createIntermediateGroups: true\n"
         "targets:\n"
         "  " product-name ":\n"
         "    type: application\n"
         "    platform: " platform "\n"
         "    deploymentTarget: \"" deployment "\"\n"
         "    sources:\n"
         "      - Sources\n"
         "      - path: Resources\n"
         "        excludes:\n"
         "          - \"WebBundle/**\"\n"
         "      - path: Resources/WebBundle\n"
         "        type: folder\n"
         "    info:\n"
         ;; path は既存ファイルを読む指定ではなく、xcodegen が生成する Info.plist
         ;; の出力先(pre-create 不要 — 実機確認: path 無しだと
         ;; \"Decoding failed at path: Nothing found\" で generate 自体が失敗する)。
         "      path: Info.plist\n"
         "      properties:\n"
         "        CFBundleShortVersionString: \"" (:app/version manifest) "\"\n"
         "        CFBundleVersion: \"1\"\n"
         ;; The app is called what the manifest calls it, on the Home screen
         ;; and in every system sheet. Without these, CFBundleName falls back
         ;; to $(PRODUCT_NAME), which is the shell's own default whenever a
         ;; consumer has not set :ios/product-name -- so local-manimani's
         ;; notification prompt introduced itself as "KotobaShell" on a real
         ;; simulator. `app run` already learned this for the macOS Dock and
         ;; menu bar (macos-bundle-plist); scaffolded apps had not.
         "        CFBundleName: \"" (:app/name manifest) "\"\n"
         "        CFBundleDisplayName: \"" (:app/name manifest) "\"\n"
         info-properties
         "    settings:\n"
         "      base:\n"
         "        PRODUCT_NAME: " product-name "\n"
         "        PRODUCT_BUNDLE_IDENTIFIER: " bundle-id "\n"
         "        MARKETING_VERSION: \"" (:app/version manifest) "\"\n"
         (signing-settings-yml target manifest)
         "    dependencies:\n"
         ;; distinct, not concatenated: the in-app bridge and the
         ;; keychain-cacao auth bridge both need Security.framework, and
         ;; xcodegen fails outright on a repeated dependency rather than
         ;; ignoring the second one.
         (str/join
          (map #(str "      - sdk: " % "\n")
               (distinct
                (concat ["WebKit.framework"
                         ;; The in-app provider bridge imports these. Swift
                         ;; autolinks system frameworks it imports, but naming
                         ;; them keeps the link explicit for anyone reading
                         ;; the generated project.
                         "UserNotifications.framework"
                         "Security.framework"]
                        (when (and (= target :macos)
                                   (= :keychain-cacao (:macos/auth-bridge manifest)))
                          ["LocalAuthentication.framework"
                           ;; open-authorization-url
                           ;; (ASWebAuthenticationSession)。keychain-cacao
                           ;; ブリッジと同じ AppDelegate に入るので、scheme が
                           ;; 未設定でもリンクは必要。
                           "AuthenticationServices.framework"]))))))))

(def ^:private web-bundle-scheme-handler-swift
  "macOS/iOS 共通。`loadFileURL` は file:// origin をロードするため、WebKit の
  cross-origin エラー抑制ポリシーが無条件に効いて window.onerror の
  message/filename/lineno/colno/error が全て失われ `\"Script error.\"` にしか
  ならない実バグを、WKScriptMessageHandler 診断ブリッジによる隔離実験で特定した
  (file:// 経由の inline script・external script・eval 生成コードいずれも再現、
  同一内容を http://127.0.0.1 経由でロードすると詳細は復元する)。file:// を
  完全にやめ、WKURLSchemeHandler で自前スキームからバンドルを配信することで
  origin を http/https と同じ非-opaque 扱いにし、詳細を保ったまま最小の
  実装(実ネットワークリスナー不要)で解決する — Capacitor/Ionic 等の実運用
  WKWebView アプリが同じ理由で file:// を避け custom scheme を使うのと同じ
  対策。"
  (str "import WebKit\n\n"
       "final class KotobaWebBundleSchemeHandler: NSObject, WKURLSchemeHandler {\n"
       "    static let scheme = \"kotoba-webbundle\"\n\n"
       "    private let bundleDir: URL\n\n"
       "    init(bundleDir: URL) {\n"
       "        self.bundleDir = bundleDir\n"
       "    }\n\n"
       "    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {\n"
       "        guard let url = urlSchemeTask.request.url else {\n"
       "            urlSchemeTask.didFailWithError(URLError(.badURL))\n"
       "            return\n"
       "        }\n"
       "        var relativePath = url.path\n"
       "        if relativePath.isEmpty || relativePath == \"/\" {\n"
       "            relativePath = \"/index.html\"\n"
       "        }\n"
       "        let fileURL = bundleDir.appendingPathComponent(String(relativePath.dropFirst()))\n"
       "        guard let data = try? Data(contentsOf: fileURL) else {\n"
       "            urlSchemeTask.didFailWithError(URLError(.fileDoesNotExist))\n"
       "            return\n"
       "        }\n"
       "        let response = URLResponse(\n"
       "            url: url,\n"
       "            mimeType: KotobaWebBundleSchemeHandler.mimeType(forExtension: fileURL.pathExtension),\n"
       "            expectedContentLength: data.count,\n"
       "            textEncodingName: \"utf-8\")\n"
       "        urlSchemeTask.didReceive(response)\n"
       "        urlSchemeTask.didReceive(data)\n"
       "        urlSchemeTask.didFinish()\n"
       "    }\n\n"
       "    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {\n"
       "        // ファイル読み取りは同期実行なので中断すべき非同期処理は無い。\n"
       "    }\n\n"
       "    static func mimeType(forExtension ext: String) -> String {\n"
       "        switch ext.lowercased() {\n"
       "        case \"html\", \"htm\": return \"text/html\"\n"
       "        case \"js\", \"mjs\": return \"application/javascript\"\n"
       "        case \"css\": return \"text/css\"\n"
       "        case \"json\", \"map\": return \"application/json\"\n"
       "        case \"svg\": return \"image/svg+xml\"\n"
       "        case \"png\": return \"image/png\"\n"
       "        case \"jpg\", \"jpeg\": return \"image/jpeg\"\n"
       "        case \"gif\": return \"image/gif\"\n"
       "        case \"woff2\": return \"font/woff2\"\n"
       "        case \"woff\": return \"font/woff\"\n"
       "        case \"ico\": return \"image/x-icon\"\n"
       "        case \"wasm\": return \"application/wasm\"\n"
       "        default: return \"application/octet-stream\"\n"
       "        }\n"
       "    }\n"
       "}\n"))

(def ^:private default-macos-app-delegate-swift
  (str "import Cocoa\n"
       "import WebKit\n\n"
       "final class AppDelegate: NSObject, NSApplicationDelegate {\n"
       "    var window: NSWindow!\n"
       "    private var schemeHandler: KotobaWebBundleSchemeHandler?\n"
       "    private var bridge: KotobaShellBridge?\n\n"
       "    func applicationDidFinishLaunching(_ notification: Notification) {\n"
       "        let config = WKWebViewConfiguration()\n"
       ;; install() must run before the WKWebView exists: userContentController
       ;; handlers and user scripts added afterwards do not apply to an
       ;; already-created web view.
       "        bridge = KotobaShellBridge.install(into: config)\n"
       "        bridge?.requestNotificationAuthorizationIfAllowed()\n"
       "        if let bundleDir = Bundle.main.url(forResource: \"WebBundle\", withExtension: nil) {\n"
       "            let handler = KotobaWebBundleSchemeHandler(bundleDir: bundleDir)\n"
       "            schemeHandler = handler\n"
       "            config.setURLSchemeHandler(handler, forURLScheme: KotobaWebBundleSchemeHandler.scheme)\n"
       "        }\n"
       "        let webView = WKWebView(frame: NSRect(x: 0, y: 0, width: 960, height: 640), configuration: config)\n"
       "        if let indexURL = URL(string: \"\\(KotobaWebBundleSchemeHandler.scheme)://app/index.html\") {\n"
       "            webView.load(URLRequest(url: indexURL))\n"
       "        }\n"
       "        window = NSWindow(\n"
       "            contentRect: NSRect(x: 0, y: 0, width: 960, height: 640),\n"
       "            styleMask: [.titled, .closable, .resizable, .miniaturizable],\n"
       "            backing: .buffered,\n"
       "            defer: false)\n"
       "        window.center()\n"
       "        window.title = \"Kotoba Shell\"\n"
       "        window.contentView = webView\n"
       "        window.makeKeyAndOrderFront(nil)\n"
       "        NSApp.activate(ignoringOtherApps: true)\n"
       "    }\n\n"
       "    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {\n"
       "        true\n"
       "    }\n"
       "}\n"))

(defn- macos-app-delegate-swift [manifest]
  (if (= :keychain-cacao (:macos/auth-bridge manifest))
    (let [template (slurp (io/resource "kotoba/shell/macos/keychain_cacao_app_delegate.swift"))
          replacements {"{{APP_NAME}}" (str (:app/name manifest))
                        "{{WINDOW_WIDTH}}" (str (or (:macos/window-width manifest) 393))
                        "{{WINDOW_HEIGHT}}" (str (or (:macos/window-height manifest) 852))
                        "{{AUTH_MESSAGE_NAME}}" (str (or (:macos/auth-message-name manifest) "itonamiAuth"))
                        "{{KEYCHAIN_SERVICE}}" (str (or (:macos/keychain-service manifest)
                                                         (:macos/bundle-id manifest)
                                                         (:app/id manifest)))
                        "{{KEYCHAIN_ACCOUNT}}" (str (or (:macos/keychain-account manifest) "session"))
                        ;; OAuth/OIDC の redirect を受ける custom scheme。
                        ;; 既定は空文字で、open-authorization-url は「未設定」を
                        ;; 返す — 適当な scheme を勝手に決めて待ち受けると、
                        ;; 同じ scheme を使う他アプリの redirect を拾いうる。
                        ;; アプリが明示的に宣言したときだけ有効にする。
                        "{{OAUTH_CALLBACK_SCHEME}}" (str (or (:macos/oauth-callback-scheme manifest) ""))}]
      (reduce-kv str/replace template replacements))
    default-macos-app-delegate-swift))

(def ^:private macos-main-swift
  (str "import Cocoa\n\n"
       "let app = NSApplication.shared\n"
       "let delegate = AppDelegate()\n"
       "app.delegate = delegate\n"
       "app.run()\n"))

(def ^:private ios-app-delegate-swift
  (str "import UIKit\n"
       "import WebKit\n\n"
       ;; scene manifest を Info.plist に書いていないので UISceneSession 系
       ;; delegate は不要 — application(_:didFinishLaunchingWithOptions:) だけの
       ;; 昔ながらの単一 delegate ライフサイクル(SceneDelegate.swift を別途
       ;; 用意しなくて済む、最小構成)。
       "@main\n"
       "final class AppDelegate: UIResponder, UIApplicationDelegate {\n"
       "    var window: UIWindow?\n"
       "    private var schemeHandler: KotobaWebBundleSchemeHandler?\n"
       "    private var bridge: KotobaShellBridge?\n\n"
       "    func application(_ application: UIApplication,\n"
       "                      didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {\n"
       "        let config = WKWebViewConfiguration()\n"
       "        bridge = KotobaShellBridge.install(into: config)\n"
       "        bridge?.requestNotificationAuthorizationIfAllowed()\n"
       "        if let bundleDir = Bundle.main.url(forResource: \"WebBundle\", withExtension: nil) {\n"
       "            let handler = KotobaWebBundleSchemeHandler(bundleDir: bundleDir)\n"
       "            schemeHandler = handler\n"
       "            config.setURLSchemeHandler(handler, forURLScheme: KotobaWebBundleSchemeHandler.scheme)\n"
       "        }\n"
       "        let webView = WKWebView(frame: UIScreen.main.bounds, configuration: config)\n"
       "        if let indexURL = URL(string: \"\\(KotobaWebBundleSchemeHandler.scheme)://app/index.html\") {\n"
       "            webView.load(URLRequest(url: indexURL))\n"
       "        }\n"
       "        let viewController = UIViewController()\n"
       "        viewController.view = webView\n"
       "        let window = UIWindow(frame: UIScreen.main.bounds)\n"
       "        window.rootViewController = viewController\n"
       "        window.makeKeyAndVisible()\n"
       "        self.window = window\n"
       "        return true\n"
       "    }\n"
       "}\n"))

(defn- android-package-path
  [application-id]
  (str/replace (str application-id) "." "/"))

(def ^:private android-app-origin
  "The origin an Android scaffold serves its web bundle from.

  Not `file:///android_asset/index.html` any more. That URL has an opaque
  origin, which costs three things a real app needs: DOM storage and
  IndexedDB are unavailable or unreliable, `WebViewCompat`'s document-start
  script injection has no origin to match so the provider bridge would not be
  installed before the page's own scripts run, and error reporting is
  degraded the same way WKWebView degrades it on `file://` (the bug this repo
  already root-caused and fixed on the Apple side by moving to a custom
  scheme). `WebViewAssetLoader` serves the same assets over a real https
  origin reserved by androidx for exactly this purpose."
  "https://appassets.androidplatform.net")

(defn- android-main-activity-java
  [manifest]
  (str "package " (:android/application-id manifest) ";\n\n"
       "import android.app.Activity;\n"
       "import android.os.Build;\n"
       "import android.os.Bundle;\n"
       "import android.content.pm.ApplicationInfo;\n"
       "import android.webkit.ConsoleMessage;\n"
       "import android.webkit.WebChromeClient;\n"
       "import android.webkit.WebResourceRequest;\n"
       "import android.webkit.WebResourceResponse;\n"
       "import android.webkit.WebSettings;\n"
       "import android.webkit.WebView;\n"
       "import android.webkit.WebViewClient;\n\n"
       "import androidx.webkit.WebViewAssetLoader;\n"
       "import androidx.webkit.WebViewCompat;\n"
       "import androidx.webkit.WebViewFeature;\n\n"
       "import java.util.Collections;\n\n"
       "public class MainActivity extends Activity {\n"
       "    @Override\n"
       "    protected void onCreate(Bundle savedInstanceState) {\n"
       "        super.onCreate(savedInstanceState);\n"
       "        WebView webView = new WebView(this);\n"
       "        WebSettings settings = webView.getSettings();\n"
       "        settings.setJavaScriptEnabled(true);\n"
       "        settings.setDomStorageEnabled(true);\n\n"
       "        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()\n"
       "                .addPathHandler(\"/assets/\", new WebViewAssetLoader.AssetsPathHandler(this))\n"
       "                .build();\n"
       "        KotobaShellBridge bridge =\n"
       "                new KotobaShellBridge(this, webView, KotobaShellBridge.loadPolicy(this));\n"
       "        webView.addJavascriptInterface(bridge, KotobaShellBridge.INTERFACE_NAME);\n"
       "        final String shim = KotobaShellBridge.shimSource(this);\n"
       ;; Document-start injection is the only placement that guarantees the
       ;; shim exists before the page's own scripts. Where the installed
       ;; WebView provider is too old for it, onPageStarted is the closest
       ;; available point; the shim is idempotent, so a device that supports
       ;; both paths is not harmed by running them both.
       "        final boolean documentStart =\n"
       "                shim != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);\n"
       "        if (documentStart) {\n"
       "            WebViewCompat.addDocumentStartJavaScript(\n"
       "                    webView, shim, Collections.singleton(\"" android-app-origin "\"));\n"
       "        }\n"
       "        webView.setWebViewClient(new WebViewClient() {\n"
       "            @Override\n"
       "            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {\n"
       "                return loader.shouldInterceptRequest(request.getUrl());\n"
       "            }\n\n"
       "            @Override\n"
       "            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {\n"
       "                if (!documentStart && shim != null) {\n"
       "                    view.evaluateJavascript(shim, null);\n"
       "                }\n"
       "            }\n"
       "        });\n\n"
       ;; POST_NOTIFICATIONS is a runtime permission from API 33. Asked for at
       ;; startup rather than at first notify/show: a permission sheet that
       ;; appears in the middle of a provider call would make the call's
       ;; result depend on how fast someone taps.
;; The README long claimed Android WebView logs console output to logcat
       ;; by default. It does not: WebView 113 on a booted emulator produced
       ;; no [INFO:CONSOLE] lines at all with no WebChromeClient set, which
       ;; cost a debugging pass. Forwarded here, and only in a debuggable
       ;; build -- a release app should not write its page's console output
       ;; to a log every other app on the device can read.
       "        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {\n"
       "            webView.setWebChromeClient(new WebChromeClient() {\n"
       "                @Override\n"
       "                public boolean onConsoleMessage(ConsoleMessage message) {\n"
       "                    android.util.Log.i(\"kotoba-shell\", message.message()\n"
       "                            + \" (\" + message.sourceId() + \":\" + message.lineNumber() + \")\");\n"
       "                    return true;\n"
       "                }\n"
       "            });\n"
       "        }\n"
       "        if (Build.VERSION.SDK_INT >= 33) {\n"
       "            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);\n"
       "        }\n"
       "        webView.loadUrl(\"" android-app-origin "/assets/index.html\");\n"
       "        setContentView(webView);\n"
       "    }\n"
       "}\n"))

(defn bridge-policy-json
  "The in-app policy asset, derived from the same `:allow`/`:deny` map the CLI
  evaluates so a command cannot be permitted in one and denied in the other."
  [target policy]
  (bridge/policy-json
   {:allow (:allow policy)
    :deny (:deny policy)
    :capabilities (into {}
                        (keep (fn [command]
                                (when-let [capability (provider-capability target command)]
                                  [command capability])))
                        bridge/bridge-provider-commands)}))

(defn scaffold-files
  [target manifest policy]
  (case target
    :macos [["project.yml" (xcodegen-project-yml target manifest)]
            ["Sources/main.swift" macos-main-swift]
            ["Sources/WebBundleSchemeHandler.swift" web-bundle-scheme-handler-swift]
            ["Sources/KotobaShellBridge.swift" (bridge/apple-bridge-swift target manifest)]
            ["Sources/AppDelegate.swift" (macos-app-delegate-swift manifest)]
            ["Resources/kotoba-shell-bridge.js" (bridge/bridge-js target)]
            ["Resources/kotoba-shell-policy.json" (bridge-policy-json target policy)]
            ["Resources/kotoba-shell.edn"
             (pr-str {:schema "kotoba.shell.app.v0"
                      :target target
                      :surface (get surface-host-specs target)
                      :manifest manifest})]]
    :ios [["project.yml" (xcodegen-project-yml target manifest)]
          ["Sources/WebBundleSchemeHandler.swift" web-bundle-scheme-handler-swift]
          ["Sources/KotobaShellBridge.swift" (bridge/apple-bridge-swift target manifest)]
          ["Sources/AppDelegate.swift" ios-app-delegate-swift]
          ["Resources/kotoba-shell-bridge.js" (bridge/bridge-js target)]
          ["Resources/kotoba-shell-policy.json" (bridge-policy-json target policy)]
          ["Resources/kotoba-shell.edn"
           (pr-str {:schema "kotoba.shell.app.v0"
                    :target target
                    :surface (get surface-host-specs target)
                    :manifest manifest})]]
    :android [["settings.gradle"
               (str "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
                    "dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\n"
                    "rootProject.name='" (:app/name manifest) "'\ninclude ':app'\n")]
              ["build.gradle"
               "plugins {\n    id 'com.android.application' version '8.5.0' apply false\n}\n"]
              ["app/build.gradle"
               ;; Groovy DSL は括弧無しメソッド呼び出しを1行に複数並べると誤って
               ;; 解釈される(実機確認: "applicationId 'x' minSdk 26 ..." を1行に
               ;; 詰めると "Cannot invoke method minSdk() on null object" で
               ;; assembleDebug が落ちる) — defaultConfig 内は1行1呼び出しにする。
               (str "plugins { id 'com.android.application' }\n\n"
                    ;; 署名材料は生成物の中に書かない。keystore.properties(生成
                    ;; されない・.gitignore 済み)か環境変数から読む。どちらも
                    ;; 無ければ release は「署名されない」だけで、ビルド自体は
                    ;; 通る — 鍵を持たない環境でも scaffold と build の健全性を
                    ;; 検証できるようにするため。署名の有無は `release check` が
                    ;; 別途報告する。
                    "def keystorePropertiesFile = rootProject.file('keystore.properties')\n"
                    "def keystoreProperties = new Properties()\n"
                    "if (keystorePropertiesFile.exists()) {\n"
                    "    keystorePropertiesFile.withInputStream { keystoreProperties.load(it) }\n"
                    "}\n"
                    "def kotobaStoreFile = keystoreProperties['storeFile'] ?: System.getenv('KOTOBA_SHELL_KEYSTORE')\n"
                    "def kotobaSigned = kotobaStoreFile != null && !kotobaStoreFile.isEmpty()\n\n"
                    "android {\n"
                    "    namespace '" (:android/application-id manifest) "'\n"
                    "    compileSdk 35\n"
                    "    defaultConfig {\n"
                    "        applicationId '" (:android/application-id manifest) "'\n"
                    "        minSdk 26\n"
                    "        targetSdk 35\n"
                    "        versionName '" (:app/version manifest) "'\n"
                    "        versionCode " (or (:android/version-code manifest) 1) "\n"
                    "    }\n"
                    "    signingConfigs {\n"
                    "        release {\n"
                    "            if (kotobaSigned) {\n"
                    "                storeFile file(kotobaStoreFile)\n"
                    "                storePassword keystoreProperties['storePassword'] ?: System.getenv('KOTOBA_SHELL_KEYSTORE_PASSWORD')\n"
                    "                keyAlias keystoreProperties['keyAlias'] ?: System.getenv('KOTOBA_SHELL_KEY_ALIAS')\n"
                    "                keyPassword keystoreProperties['keyPassword'] ?: System.getenv('KOTOBA_SHELL_KEY_PASSWORD')\n"
                    "            }\n"
                    "        }\n"
                    "    }\n"
                    "    buildTypes {\n"
                    "        release {\n"
                    "            minifyEnabled false\n"
                    "            if (kotobaSigned) {\n"
                    "                signingConfig signingConfigs.release\n"
                    "            }\n"
                    "        }\n"
                    "    }\n"
                    "    compileOptions {\n"
                    "        sourceCompatibility JavaVersion.VERSION_17\n"
                    "        targetCompatibility JavaVersion.VERSION_17\n"
                    "    }\n"
                    "}\n\n"
                    ;; androidx.webkit は WebViewAssetLoader(実 origin での asset
                    ;; 配信)と addDocumentStartJavaScript(ページ自身の script より
                    ;; 前に bridge を注入する唯一の確実な位置)のために要る。
                    "dependencies {\n"
                    "    implementation 'androidx.webkit:webkit:1.12.1'\n"
                    "}\n")]
              ;; androidx.webkit pulls in AndroidX, which AGP refuses to
              ;; resolve unless the project opts in — the build fails at
              ;; checkDebugAarMetadata, not at dependency resolution, so the
              ;; error names a runtime risk rather than the missing flag.
              ["gradle.properties"
               (str "android.useAndroidX=true\n"
                    "org.gradle.jvmargs=-Xmx2048m\n")]
              [".gitignore" "keystore.properties\n*.keystore\n*.jks\n"]
              ["app/src/main/AndroidManifest.xml"
               (str "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                    "  <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                    "  <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n"
                    "  <application android:label=\"" (xml-escape (:app/name manifest)) "\" android:usesCleartextTraffic=\"true\">\n"
                    "    <activity android:name=\".MainActivity\" android:exported=\"true\">\n"
                    "      <intent-filter>\n"
                    "        <action android:name=\"android.intent.action.MAIN\" />\n"
                    "        <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                    "      </intent-filter>\n"
                    "    </activity>\n"
                    "  </application>\n"
                    "</manifest>\n")]
              [(str "app/src/main/java/" (android-package-path (:android/application-id manifest)) "/MainActivity.java")
               (android-main-activity-java manifest)]
              [(str "app/src/main/java/" (android-package-path (:android/application-id manifest)) "/KotobaShellBridge.java")
               (bridge/android-bridge-java manifest)]
              ["app/src/main/assets/kotoba-shell-bridge.js" (bridge/bridge-js target)]
              ["app/src/main/assets/kotoba-shell-policy.json" (bridge-policy-json target policy)]
              ["app/src/main/kotoba-shell.edn"
               (pr-str {:schema "kotoba.shell.app.v0"
                        :target target
                        :surface (get surface-host-specs target)
                        :manifest manifest})]]
    :windows [["Package.appxmanifest"
               (str "{:Identity {:Name " (pr-str (:app/id manifest))
                    " :Version " (pr-str (:app/version manifest)) "}}\n")]
              ["kotoba-shell.edn"
               (pr-str {:schema "kotoba.shell.app.v0"
                        :target target
                        :surface (get surface-host-specs target)
                        :manifest manifest})]]
    []))

(defn web-assets-dest
  "target ごとの web bundle 展開先(WKWebView/WebView が読む場所)。

  macOS/iOS は Resources 直下ではなく Resources/WebBundle という専用の
  サブディレクトリにする — 実機確認: Xcode の Copy Bundle Resources は
  group 参照(xcodegen-project-yml の `sources:` に列挙した通常のパス)の
  下にある入れ子ディレクトリを保持せず、個々のファイルをバンドルの
  ルートへフラット化してしまう(index.html が参照する `vendor/scittle.js`
  等が `<bundle>/vendor/scittle.js` ではなく `<bundle>/scittle.js` に
  展開され、`loadFileURL` が読み込む index.html からは全ての vendor
  スクリプト/CSS が 404 相当で読み込めず、WKWebView が空白のまま止まる —
  Safari Web Inspector 無しで再現・特定した実バグ、README の
  \"Known WKWebView caveat\" は原因はこれだった)。WebBundle だけ xcodegen の
  folder reference(ディレクトリ構造を保持してコピーする)にすることで
  vendor/ 等のサブディレクトリを実機バンドルでも保つ(xcodegen-project-yml
  参照)。Android の assets/ はそもそも Gradle 側がディレクトリ構造を保持
  するので、この問題自体が発生しない。"
  [target root]
  (case target
    (:macos :ios) (io/file root "Resources" "WebBundle")
    :android (io/file root "app" "src" "main" "assets")
    root))

(defn xcodegen-generate!
  "root(scaffold 済みディレクトリ)で `xcodegen generate --spec project.yml` を
  実行し `<product-name>.xcodeproj` を作る(macOS/iOS のみ。名前は product-name-for)。project.pbxproj を
  手書きしない代わりに xcodegen に依存する — 未インストール環境でも scaffold
  全体は落とさず、結果に :xcodegen-ok?/:xcodegen-error を残して呼び出し側が
  判断できるようにする。"
  [root]
  (try
    (let [{:keys [exit stdout timed-out?]}
          (run-native-host-command-in-dir (.getPath root) "xcodegen"
                                          ["generate" "--spec" "project.yml"] 120)]
      {:xcodegen-ok? (and (not timed-out?) (zero? exit))
       :xcodegen-exit exit
       :xcodegen-output stdout})
    (catch Exception e
      {:xcodegen-ok? false
       :xcodegen-error (.getMessage e)})))

(defn scaffold-target-row
  [output-dir manifest policy target]
  (let [missing (missing-manifest-keys target manifest)
        known? (contains? supported-shell-targets target)
        root (io/file output-dir (name target))
        files (scaffold-files target manifest policy)
        ok? (and known? (empty? missing))]
    (if-not ok?
      {:target target
       :ok? false
       :root (.getPath root)
       :missing-manifest-keys (or missing [])
       :reason (if known? :manifest-invalid :unknown-target)}
      (do
        (doseq [[path body] files]
          (write-text-file! (io/file root path) body))
        (let [web-result (web-assets-into! manifest (web-assets-dest target root))
              xcodegen-result (when (#{:macos :ios} target)
                                (xcodegen-generate! root))]
          (merge
           {:target target
            :ok? (and ok? (or (nil? xcodegen-result) (:xcodegen-ok? xcodegen-result)))
            :root (.getPath root)
            :files (mapv first files)
            :file-count (count files)
            :surface (get surface-host-specs target)
            :web-assets web-result
            :manifest manifest}
           xcodegen-result))))))

(defn scaffold-check-row
  [output-dir manifest policy target]
  (let [root (io/file output-dir (name target))
        ;; macOS/iOS の xcodegen 産物(`<product-name>.xcodeproj/project.pbxproj`)は
        ;; scaffold-files の [[path body]...] 一覧には含まれない(xcodegen
        ;; generate が別途作る)ので、app-build-row が xcodebuild を実行する前に
        ;; 本当に buildable かをここで見る — scaffold 直後に確認できないと
        ;; xcodebuild 実行時になって初めて壊れているとわかる事故を防ぐ。
        xcodeproj-paths (when (#{:macos :ios} target)
                          [(str (product-name-for target manifest)
                                ".xcodeproj/project.pbxproj")])
        ;; web-assets-into! が書く index.html も scaffold-files の一覧には
        ;; 含まれない(別関数の副作用)ので、ここで見ておく — WebBundle/ 配下が
        ;; folder reference で正しくバンドルへ入るかは xcodebuild を実行する
        ;; までは分からないが、scaffold 側で書けているかは scaffold-check で
        ;; 見える。
        web-bundle-paths (when (#{:macos :ios} target)
                           ["Resources/WebBundle/index.html"])
        files (concat (mapv first (scaffold-files target manifest policy)) xcodeproj-paths web-bundle-paths)
        file-rows (mapv (fn [path]
                          {:path path
                           :present? (.isFile (io/file root path))})
                        files)
        missing (mapv :path (remove :present? file-rows))
        ok? (and (.isDirectory root)
                 (empty? missing))]
    {:target target
     :ok? ok?
     :root (.getPath root)
     :files file-rows
     :missing-files missing}))

;; native build/sign/submit/updater ツールは default-provider-timeout-seconds
;; (10秒、clipboard/notify 等の軽量 provider call 向け)ではまず終わらない —
;; 実機確認: xcodebuild によるクリーンビルドは(トリビアルな demo app でも)
;; 15〜40秒かかり、10秒 timeout で :timed-out? true になって :app-build-blocked
;; へ落ちた。ビルド系ステップは長めの既定値を持ち、step-run-result 側もこの
;; :timeout-seconds を尊重する(無ければ従来どおり provider 既定へフォールバック)。
(def default-build-timeout-seconds
  "xcodebuild/gradle/msbuild 用(クリーンビルド・初回の module/dependency 解決を
  含む想定)。"
  900)

(def default-submit-timeout-seconds
  "notarytool submit --wait 等、Apple/Google 側の審査待ちを伴う遅い操作用。"
  1800)

(defn default-app-build-step
  "`manifest` is required: the Xcode project/scheme is named after the
  manifest's product name (`product-name-for`), not a constant — see that
  fn's docstring for the break this caused."
  [root target manifest]
  (let [product (product-name-for target manifest)]
    (case target
      :macos {:command "xcodebuild"
              :args ["-project" (.getPath (io/file root (str product ".xcodeproj")))
                     "-scheme" product
                     "build"]
              :default? true
              :platform-step :xcodebuild-macos
              :timeout-seconds default-build-timeout-seconds}
      :ios {:command "xcodebuild"
            :args ["-project" (.getPath (io/file root (str product ".xcodeproj")))
                   "-scheme" product
                   "-sdk" "iphonesimulator"
                   "build"]
            :default? true
            :platform-step :xcodebuild-ios-simulator
            :timeout-seconds default-build-timeout-seconds}
      :android {:command "gradle"
                :args ["-p" (.getPath root) "assembleDebug"]
                :default? true
                :platform-step :gradle-assemble-debug
                :timeout-seconds default-build-timeout-seconds}
      :windows {:command "msbuild"
                :args [(.getPath (io/file root (str product ".wapproj")))]
                :default? true
                :platform-step :msbuild-msix
                :timeout-seconds default-build-timeout-seconds}
      nil)))

(defn app-build-row
  [argv output-dir manifest target]
  (let [check-row (scaffold-check-row output-dir manifest (provider-policy argv) target)
        execute? (execute-requested? argv)
        step (or (external-step argv "--build-command" [(name target)])
                 (default-app-build-step (io/file output-dir (name target)) target manifest))
        run (step-run-result execute? step)
        ok? (and (:ok? check-row) (:ok? run))]
    (assoc check-row
           :build-step run
           :built? (:executed? run)
           :ok? ok?)))

(defn app-scaffold-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        manifest (app-manifest argv)
        output-dir (app-output-dir argv)
        rows (mapv #(scaffold-target-row output-dir manifest (provider-policy argv) %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/app-scaffolded :shell/app-scaffold-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/app-scaffold-schema "kotoba.shell.app-scaffold.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/output-dir output-dir
                        :kotoba.shell/app-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :app/scaffolded
                                        :app/scaffold-blocked)
                                      {:audit/targets targets
                                       :audit/output-dir output-dir
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn app-check-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        manifest (app-manifest argv)
        output-dir (app-output-dir argv)
        rows (mapv #(scaffold-check-row output-dir manifest (provider-policy argv) %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/app-ready :shell/app-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/app-check-schema "kotoba.shell.app-check.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/output-dir output-dir
                        :kotoba.shell/app-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :app/ready
                                        :app/blocked)
                                      {:audit/targets targets
                                       :audit/output-dir output-dir
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn app-build-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos])
        manifest (app-manifest argv)
        output-dir (app-output-dir argv)
        execute? (execute-requested? argv)
        rows (mapv #(app-build-row argv output-dir manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/app-built :shell/app-build-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/app-build-schema "kotoba.shell.app-build.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/output-dir output-dir
                        :kotoba.shell/app-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :app/built
                                        :app/build-blocked)
                                      {:audit/targets targets
                                       :audit/output-dir output-dir
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn- plist-escape
  [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

;; ---------------------------------------------------------------------------
;; app package — the distributable build.
;;
;; `app build` is the development loop: `-sdk iphonesimulator`,
;; `assembleDebug`, no signing. Nothing it produces can be installed by anyone
;; other than the person who built it, which is why "iOS/Android build works"
;; and "iOS/Android ship" were two different claims in this repo's README for
;; as long as only `app build` existed.
;;
;; `app package` is the other half: device SDK, Release configuration, an
;; archive, and an export through the signing identity the manifest names.
;; It deliberately refuses rather than quietly producing an unsigned artifact
;; — an .ipa nobody can install and an .aab Play will reject both look like
;; success at the exit-code level.

(def export-methods
  "Xcode 15+ export method names. The older spellings (`app-store`,
  `ad-hoc`, `development`) still work but warn; these are the current ones."
  {:ios #{"app-store-connect" "release-testing" "enterprise" "debugging"}
   :macos #{"app-store-connect" "developer-id" "mac-application" "debugging"}})

(defn export-method
  [target manifest]
  (name (or (case target
              :ios (:ios/export-method manifest)
              :macos (:macos/export-method manifest)
              nil)
            (case target
              ;; TestFlight and the App Store are the same upload; a build
              ;; meant for neither has to say so.
              :ios :app-store-connect
              ;; Outside the App Store, a macOS app has to be Developer ID
              ;; signed and notarized or Gatekeeper refuses it.
              :macos :developer-id
              nil))))

(defn export-options-plist
  [target manifest]
  (let [team (team-id target manifest)
        style (name (or (case target
                          :ios (:ios/code-sign-style manifest)
                          :macos (:macos/code-sign-style manifest)
                          nil)
                        :automatic))]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
         "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
         "<plist version=\"1.0\">\n<dict>\n"
         "  <key>method</key><string>" (plist-escape (export-method target manifest)) "</string>\n"
         (when team
           (str "  <key>teamID</key><string>" (plist-escape team) "</string>\n"))
         "  <key>signingStyle</key><string>" (plist-escape style) "</string>\n"
         "  <key>destination</key><string>export</string>\n"
         "  <key>stripSwiftSymbols</key><true/>\n"
         "</dict>\n</plist>\n")))

(defn package-artifact-path
  [target root manifest]
  (let [product (product-name-for target manifest)]
    (case target
      :ios (.getPath (io/file root "build" "export" (str product ".ipa")))
      :macos (.getPath (io/file root "build" "export" (str product ".app")))
      :android (.getPath (io/file root "app" "build" "outputs" "bundle" "release" "app-release.aab"))
      nil)))

(defn package-steps
  "The commands that turn a scaffolded project into a distributable artifact."
  [target root manifest]
  (let [product (product-name-for target manifest)
        project (.getPath (io/file root (str product ".xcodeproj")))
        archive (.getPath (io/file root "build" (str product ".xcarchive")))
        export (.getPath (io/file root "build" "export"))
        options (.getPath (io/file root "build" "exportOptions.plist"))
        apple-archive (fn [sdk-args]
                        {:command "xcodebuild"
                         :args (vec (concat ["-project" project "-scheme" product
                                             "-configuration" "Release"]
                                            sdk-args
                                            ["archive" "-archivePath" archive
                                             ;; Lets xcodebuild create or
                                             ;; refresh the provisioning
                                             ;; profile instead of failing on
                                             ;; a machine that has never built
                                             ;; this bundle id.
                                             "-allowProvisioningUpdates"]))
                         :default? true
                         :platform-step (if (= target :ios) :xcodebuild-ios-archive :xcodebuild-macos-archive)
                         :timeout-seconds default-build-timeout-seconds})
        apple-export {:command "xcodebuild"
                      :args ["-exportArchive"
                             "-archivePath" archive
                             "-exportPath" export
                             "-exportOptionsPlist" options
                             "-allowProvisioningUpdates"]
                      :default? true
                      :platform-step :xcodebuild-export
                      :timeout-seconds default-build-timeout-seconds}]
    (case target
      :ios [(apple-archive ["-sdk" "iphoneos" "-destination" "generic/platform=iOS"]) apple-export]
      :macos [(apple-archive ["-destination" "generic/platform=macOS"]) apple-export]
      ;; bundleRelease, not assembleRelease: Google Play takes an .aab. An
      ;; .apk is still reachable by overriding the whole step with
      ;; --package-command gradle --package-command-arg ... assembleRelease.
      :android [{:command "gradle"
                 :args ["-p" (.getPath root) "bundleRelease"]
                 :default? true
                 :platform-step :gradle-bundle-release
                 :timeout-seconds default-build-timeout-seconds}]
      [])))

(defn steps-run-result
  "Runs steps in order and stops at the first failure.

  Stopping matters: `-exportArchive` against an archive that was never
  produced fails with a path error that reads nothing like the signing error
  that actually stopped the build."
  [execute? steps]
  (loop [remaining steps
         done []]
    (if-let [step (first remaining)]
      (let [run (step-run-result execute? step)
            done (conj done run)]
        (if (:ok? run)
          (recur (rest remaining) done)
          done))
      done)))

(defn package-signing-row
  "What the manifest says about signing this target, and whether that is
  enough to produce something installable."
  [target root manifest]
  (case target
    (:ios :macos)
    (let [team (team-id target manifest)
          method (export-method target manifest)
          known? (contains? (get export-methods target #{}) method)]
      {:configured? (and (some? team) known?)
       :team team
       :method method
       :method-known? known?
       :style (name (or (case target
                          :ios (:ios/code-sign-style manifest)
                          :macos (:macos/code-sign-style manifest)
                          nil)
                        :automatic))
       :reason (cond
                 (nil? team) :team-id-required
                 (not known?) :unknown-export-method
                 :else nil)})
    :android
    ;; Gradle resolves the keystore itself, from keystore.properties or the
    ;; environment; the CLI only reports which of those answered so that an
    ;; unsigned .aab is never mistaken for a signed one.
    (let [properties (io/file root "keystore.properties")
          env-store (System/getenv "KOTOBA_SHELL_KEYSTORE")
          source (cond (.isFile properties) :keystore-properties
                       env-store :environment
                       :else nil)]
      {:configured? (some? source)
       :keystore-source source
       :keystore-properties (.getPath properties)
       :reason (when-not source :keystore-required)})
    {:configured? false :reason :unsupported-target}))

(defn app-package-row
  [argv output-dir manifest target]
  (let [root (io/file output-dir (name target))
        check-row (scaffold-check-row output-dir manifest (provider-policy argv) target)
        execute? (execute-requested? argv)
        signing (package-signing-row target root manifest)
        artifact (package-artifact-path target root manifest)
        steps (if-let [override (external-step argv "--package-command" [(name target)])]
                [override]
                (package-steps target root manifest))]
    (when (and (#{:ios :macos} target) (:configured? signing))
      (write-text-file! (io/file root "build" "exportOptions.plist")
                        (export-options-plist target manifest)))
    (if-not (:configured? signing)
      (assoc check-row
             :signing signing
             :package-steps []
             :packaged? false
             :artifact artifact
             :artifact-present? false
             :ok? false
             :reason (:reason signing))
      (let [runs (steps-run-result execute? steps)
            ran-ok? (and (seq runs) (every? :ok? runs))
            present? (and (some? artifact) (.exists (io/file artifact)))]
        (assoc check-row
               :signing signing
               :package-steps runs
               :packaged? (and execute? ran-ok?)
               :artifact artifact
               ;; Reported separately from the exit code on purpose: a
               ;; zero-exit xcodebuild that wrote nothing where the artifact
               ;; was expected is the failure mode this catches.
               :artifact-present? present?
               :ok? (and (:ok? check-row)
                         ran-ok?
                         (or (not execute?) present?)))))))

(defn app-package-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:ios])
        manifest (app-manifest argv)
        output-dir (app-output-dir argv)
        execute? (execute-requested? argv)
        rows (mapv #(app-package-row argv output-dir manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/app-packaged :shell/app-package-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/app-package-schema "kotoba.shell.app-package.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/output-dir output-dir
                        :kotoba.shell/app-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok? :app/packaged :app/package-blocked)
                                      {:audit/targets targets
                                       :audit/output-dir output-dir
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn macos-bundle-plist
  "The Info.plist for one app's wrapper around the shared host binary."
  [{:app/keys [id name version]} executable]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\""
       " \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
       "<plist version=\"1.0\">\n<dict>\n"
       "  <key>CFBundleName</key><string>" (plist-escape name) "</string>\n"
       "  <key>CFBundleDisplayName</key><string>" (plist-escape name) "</string>\n"
       "  <key>CFBundleIdentifier</key><string>" (plist-escape id) "</string>\n"
       "  <key>CFBundleExecutable</key><string>" (plist-escape executable) "</string>\n"
       "  <key>CFBundleShortVersionString</key><string>"
       (plist-escape (or version "0.0.0")) "</string>\n"
       "  <key>CFBundlePackageType</key><string>APPL</string>\n"
       "  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>\n"
       "  <key>NSHighResolutionCapable</key><true/>\n"
       "</dict>\n</plist>\n"))

(defn macos-bundle-executable
  "Where this app's wrapped host lives, or nil when the manifest does not name
  an app. Pure: `app-run` needs to report the path it will use before it is
  asked to create anything."
  [binary {:app/keys [id name]}]
  (when-not (or (str/blank? (str id)) (str/blank? (str name)))
    (let [exe (.getName (io/file binary))]
      (.getPath (io/file (.getParentFile (io/file binary))
                         "apps" (str id) (str name ".app")
                         "Contents" "MacOS" exe)))))

(defn ensure-macos-bundle!
  "Put the host inside a real .app named by the manifest.

  Setting CFBundleName at runtime renames the menu bar but not the Dock tile or
  the ⌘-Tab entry: LaunchServices registers a process under the executable's
  file name before any of the program's own code runs, and only a bundle
  changes what it registers. This is the wrapper Tauri builds ahead of time,
  made here from the same manifest that names the window.

  The binary is copied rather than linked, because a bundle whose executable
  resolves outside itself is not reliably treated as one. Copying again on
  every run is what keeps a rebuilt host from being shadowed by a stale copy;
  it is 260 KB.

  `host-args` is written into the bundle, and that is not decoration. A .app in
  a place a person can double-click WILL be double-clicked, and LaunchServices
  starts it with no arguments at all. Measured 2026-08-19: opening
  `Cloud Itonami.app` from the Dock produced a 720x512 window titled `Kotoba`
  reading `No kotoba:dom root` — the host's argument-free defaults — while the
  real 430x892 surface never appeared. Nothing said anything was wrong; it
  looked like the application, and for most of a session it was mistaken for
  it. Recording the arguments here is what lets the bundle open as itself."
  [binary manifest host-args]
  (when-let [target-path (macos-bundle-executable binary manifest)]
    (let [source (io/file binary)
          exe (io/file target-path)
          contents (.getParentFile (.getParentFile exe))]
      (when (.canExecute source)
        (.mkdirs (.getParentFile exe))
        (spit (io/file contents "Info.plist")
              (macos-bundle-plist manifest (.getName exe)))
        ;; A smoke run's arguments are an observation boundary — `--smoke`
        ;; captures a PNG and closes the window — so they must not become what
        ;; the bundle does when a person opens it.
        (when-let [recorded (seq (remove nil? host-args))]
          (let [resources (io/file contents "Resources")]
            (.mkdirs resources)
            (spit (io/file resources "launch-arguments.json")
                  (json/write-str (vec recorded)))))
        (java.nio.file.Files/copy
         (.toPath source) (.toPath exe)
         (into-array java.nio.file.CopyOption
                     [java.nio.file.StandardCopyOption/REPLACE_EXISTING
                      java.nio.file.StandardCopyOption/COPY_ATTRIBUTES]))
        (.setExecutable exe true false)
        (.getPath exe)))))

(defn- resolve-app-icon
  "An `:app/icon` is written relative to the manifest that declares it, so the
  manifest stays portable across checkouts; an absolute path is taken as given.
  Returns `[path nil]` when the file is there and `[nil declared]` when it is
  not, because a declared icon that silently does not appear is the failure this
  key exists to make impossible."
  [app-dir icon]
  (if (str/blank? (str icon))
    [nil nil]
    (let [declared (io/file (str icon))
          resolved (if (.isAbsolute declared) declared (io/file app-dir (str icon)))]
      (if (.isFile resolved)
        [(.getCanonicalPath resolved) nil]
        [nil (str icon)]))))

(defn- app-runtime-plan
  [argv manifest target]
  (let [manifest-file (or (option-value argv "--manifest") "app.kotoba.edn")
        app-dir (or (some-> manifest-file io/file .getCanonicalFile .getParent)
                    (.getCanonicalPath (io/file ".")))
        runtime (:runtime manifest)
        runtime-ns (or (:namespace runtime) (:runtime/namespace runtime))
        start-fn (or (:start runtime) (:runtime/start runtime) 'start)
        permissions (->> (:macos/permissions manifest) (map name) sort vec)
        [icon missing-icon] (resolve-app-icon app-dir (:app/icon manifest))
        binary (or (option-value argv "--window-command")
                   (if (= :ios target)
                     (sibling-path "bin/kotoba-shell-run-ios-app")
                     (sibling-path "target/kotoba-shell-host-macos-window")))]
    {:app-dir app-dir
     :runtime runtime
     :runtime-namespace (some-> runtime-ns str)
     :start-function (str start-fn)
     :window-command binary
     :window (get runtime :window)
     :permissions permissions
     :icon icon
     :missing-icon missing-icon
     :screenshot (option-value argv "--screenshot")
     :smoke? (boolean (some #{"--smoke"} argv))}))

(defn- evaluate-app-entry
  [{:keys [app-dir runtime-namespace start-function]}]
  (if (or (str/blank? runtime-namespace) (str/blank? start-function))
    {:exit 2 :stdout "" :timed-out? false
     :error "manifest runtime requires :namespace and :start"}
    (let [form (str "(require '" runtime-namespace ")"
                    "(prn (" runtime-namespace "/" start-function "))")
          run (run-native-host-command-in-dir app-dir "clojure" ["-M" "-e" form] 120)]
      (if (and (zero? (or (:exit run) 1)) (not (:timed-out? run)))
        (try
          (assoc run :app-result (edn/read-string (last (remove str/blank? (str/split-lines (:stdout run))))))
          (catch Exception e
            (assoc run :error (str "invalid app entry output: " (.getMessage e)))))
        run))))

(defn app-run-result
  "Run a manifest's surface in the native host: either a pure Kotoba entry whose
  kotoba:dom ops are evaluated here, or a `:kotoba/web` surface whose declared
  URL the host loads directly. Execution is explicit; without --execute this
  returns an auditable plan."
  [argv]
  (let [target (target-option argv)
        manifest (app-manifest argv)
        plan (app-runtime-plan argv manifest target)
        execute? (execute-requested? argv)
        supported? (contains? #{:macos :ios} target)
        surface (get-in manifest [:runtime :surface])
        dom-surface? (= :kotoba/dom surface)
        ;; A web surface names a URL instead of an entry point. Its runtime kind
        ;; is unconstrained: nothing here is a wasm guest, and demanding
        ;; :kotoba-wasm would only make the manifest claim something untrue.
        web-surface? (= :kotoba/web surface)
        web-url (get-in plan [:window :web-url])
        icon-declared-but-missing? (some? (:missing-icon plan))
        runtime-valid? (and (not icon-declared-but-missing?)
                            (or (and dom-surface?
                                     (= :kotoba-wasm (get-in manifest [:runtime :kind]))
                                     (not (str/blank? (:runtime-namespace plan))))
                                (and web-surface?
                                     (= :macos target)
                                     (not (str/blank? (str web-url))))))
        evaluation (when (and execute? supported? runtime-valid? dom-surface?)
                     (evaluate-app-entry plan))
        ops (get-in evaluation [:app-result :kotoba.app/surface-ops])
        ops-valid? (or web-surface? (and (sequential? ops) (seq ops)))
        builder (when (= :macos target) (sibling-path "bin/kotoba-shell-build-macos-window"))
        rebuild? (boolean (some #{"--rebuild-window"} argv))
        build (when (and (= :macos target) execute? supported? runtime-valid? ops-valid?
                         (or rebuild? (not (.canExecute (io/file (:window-command plan))))))
                (run-native-host-command builder [(:window-command plan)] nil 120))
        binary-ready? (or (.canExecute (io/file (:window-command plan)))
                          (and build (zero? (or (:exit build) 1))))
        ;; The command the plan names, and the one a run would actually use:
        ;; on macOS the host goes inside an .app so the Dock and ⌘-Tab call it
        ;; what the manifest does. The path is known without creating anything,
        ;; so a plan can report it; only --execute writes the bundle.
        bundle-command (when (= :macos target)
                         (macos-bundle-executable (:window-command plan) manifest))
        host-args (cond-> (if (= :ios target)
                           ["--bundle-id" (str (:ios/bundle-id manifest))
                            "--title" (str (:app/name manifest))
                            "--ops-json" (json/write-str (json-ready ops))]
                           ["--title" (str (:app/name manifest))
                            "--ops-json" (json/write-str (json-ready ops))])
                    (:icon plan) (conj "--icon" (:icon plan))
                    (and (= :macos target) (not (str/blank? (str web-url))))
                    (conj "--web-url" (str web-url))
                    (get-in plan [:window :width]) (conj "--width" (str (get-in plan [:window :width])))
                    (get-in plan [:window :height]) (conj "--height" (str (get-in plan [:window :height])))
                    (get-in plan [:window :min-width]) (conj "--min-width" (str (get-in plan [:window :min-width])))
                    (get-in plan [:window :min-height]) (conj "--min-height" (str (get-in plan [:window :min-height])))
                    (true? (get-in plan [:window :floating])) (conj "--floating")
                    (= :overlay (get-in plan [:window :titlebar])) (conj "--titlebar-overlay")
                    (and (= :macos target) (seq (:permissions plan)))
                    (conj "--permissions" (str/join "," (:permissions plan)))
                    (:screenshot plan) (conj "--screenshot" (:screenshot plan))
                    (:smoke? plan) (conj "--smoke"))
        ;; After `host-args`, because the bundle now records them. The bundle
        ;; path is still knowable without creating anything, which is what lets
        ;; a plan report it; only --execute writes.
        run-command (or (when (and execute? (= :macos target) binary-ready?)
                          (ensure-macos-bundle! (:window-command plan) manifest
                                                (when-not (:smoke? plan) host-args)))
                        (:window-command plan))
        host-run (when (and execute? supported? runtime-valid? ops-valid? binary-ready?)
                   (run-native-host-command run-command host-args nil
                                            (if (:smoke? plan) 15 86400)))
        ran? (boolean (and host-run (zero? (or (:exit host-run) 1)) (not (:timed-out? host-run))))
        planned? (and supported? runtime-valid?)
        ok? (if execute? ran? planned?)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        (not supported?) :shell/app-run-target-unsupported
                        icon-declared-but-missing? :shell/app-icon-missing
                        (not runtime-valid?) :shell/app-runtime-invalid
                        (not execute?) :shell/app-run-planned
                        ran? :shell/app-ran
                        :else :shell/app-run-failed)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/app-run-schema "kotoba.shell.app-run.v0"
                        :kotoba.shell/target target
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/runtime-plan plan
                        ;; What the manifest actually becomes at the host
                        ;; boundary. Without this a plan cannot be checked
                        ;; against the manifest without launching a window.
                        :kotoba.shell/host-args host-args
                        ;; The bundled executable, not the shared binary the
                        ;; plan names: which one runs decides what the Dock
                        ;; calls the app, so a plan that hid it would not
                        ;; describe the run.
                        :kotoba.shell/host-command (or bundle-command
                                                       (:window-command plan))
                        :kotoba.shell/evaluation evaluation
                        :kotoba.shell/ops-count (count (or ops []))
                        :kotoba.shell/build build
                        :kotoba.shell/host-run host-run
                        :kotoba.shell/audit
                        (audit-record (cond
                                        ran? :app/ran
                                        execute? :app/run-failed
                                        :else :app/run-planned)
                                      {:audit/target target
                                       :audit/app-id (:app/id manifest)
                                       :audit/execute? execute?
                                       :audit/ops-count (count (or ops []))})})}))

(defn app-surface-result
  "Evaluate an app entry without launching a host and write its kotoba:dom ops
  as JSON. This is the read-only producer used by native live reload."
  [argv]
  (let [manifest (app-manifest argv)
        plan (app-runtime-plan argv manifest (target-option argv))
        output (option-value argv "--output")
        evaluation (evaluate-app-entry plan)
        ops (get-in evaluation [:app-result :kotoba.app/surface-ops])
        valid? (and (sequential? ops) (seq ops) (not (str/blank? output)))]
    (when valid?
      (io/make-parents output)
      (spit output (json/write-str (json-ready ops))))
    {:kotoba.cli/ok? (boolean valid?)
     :kotoba.cli/code (if valid? :shell/app-surface-rendered :shell/app-surface-failed)
     :kotoba.cli/data {:kotoba.shell/app-run-schema "kotoba.shell.app-surface.v0"
                       :kotoba.shell/output output
                       :kotoba.shell/ops-count (count (or ops []))
                       :kotoba.shell/evaluation evaluation}}))

(defn- image-diff
  [baseline-file actual-file]
  (let [baseline (javax.imageio.ImageIO/read (io/file baseline-file))
        actual (javax.imageio.ImageIO/read (io/file actual-file))
        bw (.getWidth baseline) bh (.getHeight baseline)
        aw (.getWidth actual) ah (.getHeight actual)]
    (if (not= [bw bh] [aw ah])
      {:comparable? false :baseline-size [bw bh] :actual-size [aw ah]
       :difference-ratio 1.0 :mean-channel-delta 1.0}
      (let [pixels (* bw bh)
            totals (reduce
                    (fn [[different delta] [x y]]
                      (let [a (.getRGB baseline x y) b (.getRGB actual x y)
                            channels (map #(Math/abs (- (bit-and (bit-shift-right a %) 0xff)
                                                         (bit-and (bit-shift-right b %) 0xff)))
                                          [0 8 16 24])
                            d (reduce + channels)]
                        [(if (> d 32) (inc different) different) (+ delta d)]))
                    [0 0]
                    (for [y (range bh) x (range bw)] [x y]))]
        {:comparable? true
         :baseline-size [bw bh]
         :actual-size [aw ah]
         :difference-ratio (/ (double (first totals)) pixels)
         :mean-channel-delta (/ (double (second totals)) (* pixels 4 255))}))))

(defn app-visual-test-result
  [argv]
  (let [baseline (or (option-value argv "--baseline") "test/visual/manimani-macos.png")
        actual (or (option-value argv "--actual") "target/kotoba-shell/visual/manimani-macos.png")
        threshold (Double/parseDouble (or (option-value argv "--threshold") "0.01"))
        update? (boolean (some #{"--update-baseline"} argv))
        run-argv (into (vec argv) ["--execute" "--smoke" "--rebuild-window" "--screenshot" actual])
        app-run (app-run-result run-argv)
        captured? (and (:kotoba.cli/ok? app-run) (.isFile (io/file actual)))
        baseline-existed? (.isFile (io/file baseline))
        _ (when (and captured? update?)
            (io/make-parents baseline)
            (java.nio.file.Files/copy (.toPath (io/file actual)) (.toPath (io/file baseline))
                                      (into-array java.nio.file.CopyOption
                                                  [java.nio.file.StandardCopyOption/REPLACE_EXISTING])))
        diff (when (and captured? (.isFile (io/file baseline))) (image-diff baseline actual))
        passed? (and captured? diff (:comparable? diff)
                     (<= (:difference-ratio diff) threshold))]
    {:kotoba.cli/ok? (boolean passed?)
     :kotoba.cli/code (cond
                        (not captured?) :shell/visual-capture-failed
                        (and (not baseline-existed?) (not update?)) :shell/visual-baseline-missing
                        passed? :shell/visual-test-passed
                        :else :shell/visual-test-failed)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/visual-test-schema "kotoba.shell.visual-test.v0"
                        :kotoba.shell/baseline baseline
                        :kotoba.shell/actual actual
                        :kotoba.shell/threshold threshold
                        :kotoba.shell/baseline-updated? update?
                        :kotoba.shell/diff diff
                        :kotoba.shell/app-run app-run
                        :kotoba.shell/audit
                        (audit-record (if passed? :visual/passed :visual/failed)
                                      {:audit/baseline baseline :audit/actual actual
                                       :audit/difference-ratio (:difference-ratio diff)})})}))

(defn app-kaizen-result
  [argv]
  (let [visual (app-visual-test-result argv)
        passed? (:kotoba.cli/ok? visual)
        baseline (get-in visual [:kotoba.cli/data :kotoba.shell/baseline])
        actual (get-in visual [:kotoba.cli/data :kotoba.shell/actual])]
    {:kotoba.cli/ok? passed?
     :kotoba.cli/code (if passed? :shell/kaizen-complete :shell/kaizen-needs-change)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/kaizen-schema "kotoba.shell.kaizen.v0"
                        :kotoba.shell/visual-result visual
                        :kotoba.shell/next-action (if passed? :done :inspect-diff)
                        :kotoba.shell/next-command (when-not passed?
                                                     (str "kotoba-shell app kaizen --manifest app.kotoba.edn --baseline "
                                                          baseline " --actual " actual " --execute --json"))})}))

(defn executable-file?
  [path]
  (let [file (io/file path)]
    (and (.isFile file)
         (.canExecute file))))

(defn command-available?
  [command]
  (or (executable-file? command)
      (try
        (let [{:keys [exit timed-out?]} (run-native-host-command "/usr/bin/env" ["sh" "-c" (str "command -v " command)])]
          (and (not timed-out?) (zero? exit)))
        (catch Exception _
          false))))

(defn tool-row
  [required? tool]
  {:tool tool
   :required? required?
   :available? (command-available? tool)})

(defn doctor-target-row
  [target]
  (let [spec (get doctor-target-specs target)
        host-command (default-host-command target)
        host-runner-ok? (boolean (and host-command (executable-file? host-command)))
        tool-rows (vec (concat (map #(tool-row true %) (:required-tools spec))
                               (map #(tool-row false %) (:optional-tools spec))))
        required-ok? (every? :available? (filter :required? tool-rows))
        ready? (and host-runner-ok? required-ok?)]
    {:target target
     :ready? ready?
     :host-runner host-command
     :host-runner-ready? host-runner-ok?
     :tools tool-rows
     :checks (:checks spec)
     :missing-required-tools (mapv :tool (remove :available? (filter :required? tool-rows)))}))

(defn ui-substrate-row
  [id]
  (let [spec (get ui-substrate-specs id)
        repo-path (sibling-path (:repo spec))
        repo-file (io/file repo-path)
        package-file (io/file repo-file "package.json")
        package-json (when (.isFile package-file)
                       (read-json-file package-file))
        scripts (set (keys (get package-json "scripts" {})))
        file-rows (mapv (fn [path]
                          {:path path
                           :present? (.isFile (io/file repo-file path))})
                        (:required-files spec))
        script-rows (mapv (fn [script]
                            {:script script
                             :present? (contains? scripts script)})
                          (:required-scripts spec))
        ready? (and (.isDirectory repo-file)
                    (every? :present? file-rows)
                    (every? :present? script-rows))]
    {:id id
     :role (:role spec)
     :repo repo-path
     :ready? ready?
     :files file-rows
     :scripts script-rows
     :missing-files (mapv :path (remove :present? file-rows))
     :missing-scripts (mapv :script (remove :present? script-rows))}))

(defn adapter-check-result
  [argv]
  (let [target (target-option argv)
        catalog (selfhost-seed "aiueos_provider_catalog")
        providers (vec (filter #(provider-target-supported? target %)
                               (:providers catalog)))
        target-known? (contains? supported-shell-targets target)]
    {:kotoba.cli/ok? target-known?
     :kotoba.cli/code (if target-known? :shell/adapter-ready :shell/unknown-target)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/target target
                        :kotoba.shell/provider-count (count providers)
                        :kotoba.shell/providers
                        (mapv #(select-keys % [:id :capability :commands :status])
                              providers)})}))

(defn native-host-check-result
  [argv]
  (let [target (target-option argv)
        catalog (selfhost-seed "aiueos_provider_catalog")
        native-host-seed (selfhost-seed "native_host_contract")
        evidence-seed (selfhost-seed "shell_evidence_profile")
        providers (vec (filter #(provider-target-supported? target %)
                               (:providers catalog)))
        target-known? (contains? supported-shell-targets target)]
    {:kotoba.cli/ok? target-known?
     :kotoba.cli/code (if target-known? :shell/native-host-ready :shell/unknown-target)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/target target
                        :kotoba.shell/native-host-exports (contract-exports native-host-seed target)
                        :kotoba.shell/provider-command-count (count (mapcat :commands providers))
                        :kotoba.shell/capability-gate-count (count (distinct (map :capability providers)))
                        :kotoba.shell/evidence-profile-count (count (:profiles evidence-seed))
                        :kotoba.shell/required-evidence-stem-count (count (:required-evidence-stems evidence-seed))
                        :kotoba.shell/default-host-runner (get host-runner-specs target)
                        :kotoba.shell/default-host-command (default-host-command target)
                        :kotoba.shell/providers
                        (mapv #(select-keys % [:id :capability :commands :status])
                              providers)})}))

(defn process-output
  [process]
  (with-open [stream (.getInputStream process)]
    (slurp stream)))

(defn destroy-process-tree!
  [process]
  (doseq [handle (reverse (iterator-seq (.iterator (.descendants (.toHandle process)))))]
    (.destroyForcibly handle))
  (.destroyForcibly process))

(defn output-or-timeout
  [output-future]
  (deref output-future 1000 ""))

(defn process-output-async
  [process]
  (let [result (promise)
        thread (Thread. #(deliver result (process-output process)))]
    (.setDaemon thread true)
    (.start thread)
    result))

(defn run-native-host-command
  ([command args] (run-native-host-command command args nil))
  ([command args stdin] (run-native-host-command command args stdin default-provider-timeout-seconds))
  ([command args stdin timeout-seconds]
   (let [process (-> (ProcessBuilder. (into [command] args))
                     (.redirectErrorStream true)
                     (.start))
         output-future (process-output-async process)
         _ (if stdin
             (with-open [writer (io/writer (.getOutputStream process))]
               (.write writer (str stdin)))
             (.close (.getOutputStream process)))
         completed? (.waitFor process timeout-seconds java.util.concurrent.TimeUnit/SECONDS)]
     (if completed?
       {:exit (.exitValue process)
        :stdout (output-or-timeout output-future)
        :timed-out? false}
       (do
         (destroy-process-tree! process)
         {:exit nil
         :stdout (output-or-timeout output-future)
         :timed-out? true})))))

(defn run-native-host-command-in-dir
  [directory command args timeout-seconds]
  (let [process (-> (ProcessBuilder. (into [command] args))
                    (.directory (io/file directory))
                    (.redirectErrorStream true)
                    (.start))
        output-future (process-output-async process)
        _ (.close (.getOutputStream process))
        completed? (.waitFor process timeout-seconds java.util.concurrent.TimeUnit/SECONDS)]
    (if completed?
      {:exit (.exitValue process)
       :stdout (output-or-timeout output-future)
       :timed-out? false}
      (do
        (destroy-process-tree! process)
        {:exit nil
         :stdout (output-or-timeout output-future)
         :timed-out? true}))))

(defn provider-command-known?
  [target command]
  (boolean (provider-for target command)))

(defn native-provider-command
  [target command text host-command host-args]
  (let [command-path (or host-command (default-host-command target))]
    (if command-path
      (let [result (run-native-host-command command-path
                                            (into ["--target" (name target)
                                                   "--provider-command" command]
                                                  host-args)
                                            text
                                            (provider-timeout-seconds target command))]
        (assoc result
               :provider-output (when (or (str/includes? command "/read")
                                          (str/includes? command "/fetch"))
                                  (:stdout result))))
      {:unsupported? true})))

(defn native-host-run-result
  [argv]
  (let [target (target-option argv)
        target-known? (contains? supported-shell-targets target)
        custom-command (option-value argv "--host-command")
        windows-host? (str/starts-with? (str/lower-case (System/getProperty "os.name" "")) "windows")
        command (or (option-value argv "--host-command")
                    (when (or (not= :windows target) windows-host?)
                      (default-host-command target)))
        args (vec (option-values argv "--host-arg"))]
    (cond
      (not target-known?)
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/unknown-target
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/target target})}

      (str/blank? (str command))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/native-host-command-required
       :kotoba.cli/data (merge (shell-authority-data)
                              {:kotoba.shell/usage "kotoba-shell native-host run --target <target> --host-command <command> [--host-arg arg ...]"})}

      :else
      (try
        (let [{:keys [exit stdout timed-out?]} (run-native-host-command command args)
              ok? (and (not timed-out?) (zero? exit))]
          {:kotoba.cli/ok? ok?
           :kotoba.cli/code (cond
                              timed-out? :shell/native-host-timeout
                              ok? :shell/native-host-ran
                              :else :shell/native-host-failed)
           :kotoba.cli/data (merge
                             (shell-authority-data)
                             {:kotoba.shell/target target
                              :kotoba.shell/native-host-connected? true
                              :kotoba.shell/default-host-runner? (nil? custom-command)
                              :kotoba.shell/host-command command
                              :kotoba.shell/host-args args
                              :kotoba.shell/exit exit
                              :kotoba.shell/stdout stdout
                              :kotoba.shell/timed-out? timed-out?})})
        (catch Exception e
          {:kotoba.cli/ok? false
           :kotoba.cli/code :shell/native-host-exec-failed
           :kotoba.cli/data (merge
                             (shell-authority-data)
                             {:kotoba.shell/target target
                              :kotoba.shell/native-host-connected? false
                              :kotoba.shell/host-command command
                              :kotoba.shell/host-args args
                              :kotoba.shell/error (.getMessage e)})})))))

(defn native-host-provider-result
  [argv]
  (let [target (target-option argv)
        target-known? (contains? supported-shell-targets target)
        provider-command (option-value argv "--provider-command")
        text (or (option-value argv "--text") "")
        host-command (option-value argv "--host-command")
        host-args (vec (option-values argv "--host-arg"))
        policy (provider-policy argv)
        decision (policy-decision policy target provider-command)]
    (cond
      (not target-known?)
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/unknown-target
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/target target})}

      (str/blank? (str provider-command))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/provider-command-required
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/usage "kotoba-shell native-host provider --target <target> --provider-command <command> [--text text]"})}

      (not (provider-command-known? target provider-command))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/provider-command-unknown
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/target target
                                :kotoba.shell/provider-command provider-command})}

      (not (:allowed? decision))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/provider-denied
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/target target
                                :kotoba.shell/provider-command provider-command
                                :kotoba.shell/provider-capability (:capability decision)
                                :kotoba.shell/policy-decision decision
                                :kotoba.shell/audit
                                (audit-record :provider/denied
                                              {:audit/target target
                                               :audit/provider-command provider-command
                                               :audit/capability (:capability decision)})})}

      :else
      (try
        (let [{:keys [exit stdout timed-out? provider-output unsupported?]}
              (native-provider-command target provider-command text host-command host-args)]
          (if unsupported?
            {:kotoba.cli/ok? false
             :kotoba.cli/code :shell/provider-host-runner-required
             :kotoba.cli/data (merge (shell-authority-data)
                                     {:kotoba.shell/target target
                                      :kotoba.shell/provider-command provider-command
                                      :kotoba.shell/usage "non-macOS provider commands require --host-command"})}
            (let [ok? (and (not timed-out?) (zero? exit))]
              {:kotoba.cli/ok? ok?
               :kotoba.cli/code (cond
                                  timed-out? :shell/provider-timeout
                                  ok? :shell/provider-ran
                                  :else :shell/provider-failed)
               :kotoba.cli/data (merge
                                 (shell-authority-data)
                                 {:kotoba.shell/target target
                                  :kotoba.shell/native-host-connected? true
                                  :kotoba.shell/provider-command provider-command
                                  :kotoba.shell/provider-capability (:capability decision)
                                  :kotoba.shell/policy-decision decision
                                  :kotoba.shell/exit exit
                                  :kotoba.shell/stdout stdout
                                  :kotoba.shell/provider-output provider-output
                                  :kotoba.shell/timed-out? timed-out?
                                  :kotoba.shell/audit
                                  (audit-record :provider/ran
                                                {:audit/target target
                                                 :audit/provider-command provider-command
                                                 :audit/capability (:capability decision)
                                                 :audit/exit exit})})})))
        (catch Exception e
          {:kotoba.cli/ok? false
           :kotoba.cli/code :shell/provider-exec-failed
           :kotoba.cli/data (merge
                             (shell-authority-data)
                             {:kotoba.shell/target target
                              :kotoba.shell/native-host-connected? false
                              :kotoba.shell/provider-command provider-command
                              :kotoba.shell/error (.getMessage e)})})))))

(defn surface-check-result
  [argv]
  (let [target (target-option argv)
        target-known? (contains? supported-shell-targets target)]
    {:kotoba.cli/ok? target-known?
     :kotoba.cli/code (if target-known? :shell/surface-ready :shell/unknown-target)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/target target
                        :kotoba.shell/webview-required? true
                        :kotoba.shell/surface-host (get surface-host-specs target)
                        :kotoba.shell/ui-substrate
                        (get-in surface-host-specs [target :ui-substrate])
                        :kotoba.shell/browser-engine
                        (get-in surface-host-specs [target :browser-engine])
                        :kotoba.shell/abi "kotoba:dom"})}))

(defn surface-commit-result
  [argv]
  (let [target (target-option argv)
        target-known? (contains? supported-shell-targets target)
        ops (or (read-edn-option argv "--ops-edn") [])]
    (cond
      (not target-known?)
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/unknown-target
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/target target})}

      (not (sequential? ops))
      {:kotoba.cli/ok? false
       :kotoba.cli/code :shell/surface-ops-invalid
       :kotoba.cli/data (merge (shell-authority-data)
                               {:kotoba.shell/target target
                                :kotoba.shell/ops ops})}

      :else
      {:kotoba.cli/ok? true
       :kotoba.cli/code :shell/surface-committed
       :kotoba.cli/data (merge
                         (shell-authority-data)
                         {:kotoba.shell/target target
                          :kotoba.shell/webview-required? true
                          :kotoba.shell/surface-host (get surface-host-specs target)
                          :kotoba.shell/ops-count (count ops)
                          :kotoba.shell/ops ops
                          :kotoba.shell/audit
                          (audit-record :surface/committed
                                        {:audit/target target
                                         :audit/ops-count (count ops)})})})))

(defn policy-check-result
  [argv]
  (let [target (target-option argv)
        command (option-value argv "--provider-command")
        policy (provider-policy argv)
        decision (policy-decision policy target command)]
    {:kotoba.cli/ok? (:allowed? decision)
     :kotoba.cli/code (if (:allowed? decision) :shell/policy-allowed :shell/policy-denied)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/target target
                        :kotoba.shell/provider-command command
                        :kotoba.shell/policy policy
                        :kotoba.shell/policy-decision decision
                        :kotoba.shell/audit
                        (audit-record (if (:allowed? decision)
                                        :policy/allowed
                                        :policy/denied)
                                      {:audit/target target
                                       :audit/provider-command command
                                       :audit/capability (:capability decision)})})}))

(defn release-check-result
  [argv]
  (let [target (target-option argv)
        target-known? (contains? supported-shell-targets target)
        manifest (app-manifest argv)
        missing (when target-known?
                  (missing-manifest-keys target manifest))
        ok? (and target-known? (empty? missing))]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        (not target-known?) :shell/unknown-target
                        ok? :shell/release-ready
                        :else :shell/release-manifest-invalid)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/target target
                        :kotoba.shell/release-target (get release-target-specs target)
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/missing-manifest-keys (or missing [])
                        :kotoba.shell/packaging-ready? ok?
                        :kotoba.shell/signing-ready? ok?
                        :kotoba.shell/updater-ready? ok?
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/ready
                                        :release/blocked)
                                      {:audit/target target
                                       :audit/app-id (:app/id manifest)
                                       :audit/missing-manifest-keys (or missing [])})})}))

(defn release-evidence-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        manifest (app-manifest argv)
        artifact (option-value argv "--artifact")
        artifact-file (when artifact (io/file artifact))
        artifact-ready? (or (nil? artifact) (.isFile artifact-file))
        rollback-from (option-value argv "--rollback-from")
        rollback-to (option-value argv "--rollback-to")
        rollback-ready? (or (and (nil? rollback-from) (nil? rollback-to))
                            (and rollback-from rollback-to
                                 (not= rollback-from rollback-to)
                                 (.isFile (io/file rollback-from))
                                 (.isFile (io/file rollback-to))))
        artifact-sha256 (when artifact-ready?
                          (when artifact-file
                            (let [digest (java.security.MessageDigest/getInstance "SHA-256")]
                              (with-open [in (io/input-stream artifact-file)]
                                (let [buf (byte-array 65536)]
                                  (loop []
                                    (let [n (.read in buf)]
                                      (when (pos? n) (.update digest buf 0 n) (recur))))))
                              (format "%064x" (BigInteger. 1 (.digest digest))))))
        rows (mapv (fn [target]
                     (let [missing (missing-manifest-keys target manifest)
                           ok? (and (contains? supported-shell-targets target)
                                    (empty? missing))]
                       {:target target
                        :ok? ok?
                        :artifact (get-in release-target-specs [target :artifact])
                        :missing-manifest-keys missing}))
                   targets)
        ok? (and artifact-ready? rollback-ready? (every? :ok? rows))]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/release-evidence-ready :shell/release-evidence-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/evidence-schema "kotoba.shell.release-evidence.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/release-ready-count (count (filter :ok? rows))
                        :kotoba.shell/release-target-count (count rows)
                        :kotoba.shell/artifact artifact
                        :kotoba.shell/artifact-present? artifact-ready?
                        :kotoba.shell/artifact-sha256 artifact-sha256
                        :kotoba.shell/rollback {:from rollback-from
                                                :to rollback-to
                                                :ready? rollback-ready?}
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/evidence-ready
                                        :release/evidence-blocked)
                                      {:audit/targets targets
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn release-dry-run-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos])
        manifest (app-manifest argv)
        output-dir (release-output-dir argv)
        rows (mapv #(release-dry-run-row output-dir manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/release-dry-run-ready :shell/release-dry-run-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/release-dry-run-schema "kotoba.shell.release-dry-run.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/output-dir output-dir
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/release-ready-count (count (filter :ok? rows))
                        :kotoba.shell/release-target-count (count rows)
                        :kotoba.shell/artifacts (mapv :artifact (filter :ok? rows))
                        :kotoba.shell/signatures (mapv :signature (filter :ok? rows))
                        :kotoba.shell/updater-feeds (mapv :updater-feed (filter :ok? rows))
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/dry-run-ready
                                        :release/dry-run-blocked)
                                      {:audit/targets targets
                                       :audit/output-dir output-dir
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn read-edn-file-safe
  [path]
  (try
    {:ok? true
     :path path
     :data (-> path slurp edn/read-string)}
    (catch Exception e
      {:ok? false
       :path path
       :error (.getMessage e)})))

(defn release-verify-row
  [target artifact-path signature-path feed-path]
  (let [artifact (read-edn-file-safe artifact-path)
        signature (read-edn-file-safe signature-path)
        feed (read-edn-file-safe feed-path)
        artifact-digest (when (:ok? artifact)
                          (sha256-hex (:data artifact)))
        signature-digest (get-in signature [:data :digest])
        feed-digest (get-in feed [:data :artifact-digest])
        artifact-name (some-> artifact-path io/file .getName)
        signature-name (some-> signature-path io/file .getName)
        ok? (and (:ok? artifact)
                 (:ok? signature)
                 (:ok? feed)
                 (= target (get-in artifact [:data :target]))
                 (= target (get-in signature [:data :target]))
                 (= target (get-in feed [:data :target]))
                 (= artifact-name (get-in signature [:data :artifact]))
                 (= artifact-name (get-in feed [:data :artifact]))
                 (= signature-name (get-in feed [:data :signature-file]))
                 (= artifact-digest signature-digest feed-digest))]
    {:target target
     :ok? ok?
     :artifact artifact-path
     :signature signature-path
     :updater-feed feed-path
     :artifact-readable? (:ok? artifact)
     :signature-readable? (:ok? signature)
     :updater-feed-readable? (:ok? feed)
     :artifact-digest artifact-digest
     :signature-digest signature-digest
     :feed-digest feed-digest
     :artifact-name artifact-name
     :signature-name signature-name}))

(defn release-verify-paths
  [argv target manifest]
  (let [base-dir (io/file (release-output-dir argv) (name target))
        artifact-name (target-artifact-name target manifest)
        artifact (or (option-value argv "--artifact")
                     (.getPath (io/file base-dir artifact-name)))
        signature (or (option-value argv "--signature")
                      (.getPath (io/file base-dir (str artifact-name ".sig.edn"))))
        feed (or (option-value argv "--updater-feed")
                 (.getPath (io/file base-dir "updater-feed.edn")))]
    [artifact signature feed]))

(defn release-verify-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos])
        manifest (app-manifest argv)
        rows (mapv (fn [target]
                     (let [[artifact signature feed] (release-verify-paths argv target manifest)]
                       (release-verify-row target artifact signature feed)))
                   targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/release-verified :shell/release-verify-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/release-verify-schema "kotoba.shell.release-verify.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/verified-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/verified
                                        :release/verify-blocked)
                                      {:audit/targets targets
                                       :audit/verified-count (count (filter :ok? rows))})})}))

(defn credential-value
  [argv credential]
  (value-or-file
   (or (option-value argv (:option credential))
       (System/getenv (:env credential)))))

(defn credential-row
  [argv credential]
  (let [raw-value (or (option-value argv (:option credential))
                      (System/getenv (:env credential)))
        value (value-or-file raw-value)]
    (assoc credential
           :present? (boolean (seq (str value)))
           :source (cond
                     (file-reference? raw-value) :file
                     (option-value argv (:option credential)) :option
                     (System/getenv (:env credential)) :env
                     :else nil))))

(defn artifact-present?
  [argv artifact]
  (let [option (str "--" (name artifact))
        path (option-value argv option)]
    {:artifact artifact
     :option option
     :path path
     :present? (boolean (and path (.exists (io/file path))))}))

(defn release-connect-row
  [argv manifest target]
  (let [missing-manifest (missing-manifest-keys target manifest)
        spec (get production-connection-specs target)
        credentials (mapv #(credential-row argv %) (:credentials spec))
        artifacts (mapv #(artifact-present? argv %) (:artifacts spec))
        credentials-ready? (every? :present? credentials)
        artifacts-ready? (every? :present? artifacts)
        manifest-ready? (empty? missing-manifest)
        ok? (and spec manifest-ready? credentials-ready? artifacts-ready?)]
    {:target target
     :ok? ok?
     :manifest-ready? manifest-ready?
     :missing-manifest-keys missing-manifest
     :credentials-ready? credentials-ready?
     :credentials credentials
     :artifacts-ready? artifacts-ready?
     :artifacts artifacts
     :distribution (:distribution spec)}))

(defn release-connect-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        manifest (app-manifest argv)
        rows (mapv #(release-connect-row argv manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/release-connected :shell/release-connection-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/release-connect-schema "kotoba.shell.release-connect.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/release-ready-count (count (filter :ok? rows))
                        :kotoba.shell/release-target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/connected
                                        :release/connection-blocked)
                                      {:audit/targets targets
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn execute-requested?
  [argv]
  (boolean (some #{"--execute"} argv)))

(defn external-step
  [argv option default-args]
  (when-let [command (option-value argv option)]
    {:command command
     :args (or (seq (option-values argv (str option "-arg")))
               default-args)}))

(defn artifact-path
  [row artifact]
  (:path (first (filter #(= artifact (:artifact %)) (:artifacts row)))))

(defn credential-present-value
  [argv row id]
  (or (some (fn [credential]
              (when (= id (:id credential))
                (credential-value argv credential)))
            (get-in production-connection-specs [(:target row) :credentials]))
      (some (fn [credential]
              (when (= id (:id credential))
                (:label credential)))
            (:credentials row))))

(defn default-sign-step
  [argv row]
  (case (:target row)
    :macos {:command "/usr/bin/codesign"
            :args ["--force" "--deep" "--sign"
                   (credential-present-value argv row :developer-id-application)
                   (artifact-path row :app-bundle)]
            :default? true
            :platform-step :codesign
            :timeout-seconds default-build-timeout-seconds}
    :ios {:command "xcodebuild"
          :args ["-exportArchive"
                 "-archivePath" (artifact-path row :xcode-archive)
                 "-exportPath" (or (artifact-path row :ipa) "build/ios")
                 "-allowProvisioningUpdates"]
          :default? true
          :platform-step :xcode-export-ipa
          :timeout-seconds default-build-timeout-seconds}
    :android {:command "jarsigner"
              :args ["-keystore" (credential-present-value argv row :keystore)
                     (or (artifact-path row :aab)
                         (artifact-path row :apk))
                     (credential-present-value argv row :keystore-alias)]
              :default? true
              :platform-step :jarsigner
              :timeout-seconds default-build-timeout-seconds}
    :windows {:command "signtool"
              :args ["sign"
                     "/f" (credential-present-value argv row :authenticode-cert)
                     (artifact-path row :msix)]
              :default? true
              :platform-step :authenticode
              :timeout-seconds default-build-timeout-seconds}
    nil))

(defn default-submit-step
  [argv row]
  (case (:target row)
    :macos {:command "/usr/bin/xcrun"
            :args ["notarytool" "submit"
                   (artifact-path row :dmg)
                   "--keychain-profile"
                   (credential-present-value argv row :notary-profile)
                   "--wait"]
            :default? true
            :platform-step :notarytool-submit
            :timeout-seconds default-submit-timeout-seconds}
    :ios {:command "/usr/bin/xcrun"
          :args ["altool" "--upload-app"
                 "-f" (artifact-path row :ipa)
                 "--apiKey" (credential-present-value argv row :app-store-connect-key)]
          :default? true
          :platform-step :app-store-connect-upload
          :timeout-seconds default-submit-timeout-seconds}
    :android {:command "gradle"
              :args ["publishReleaseBundle"
                     (str "-PplayServiceAccount="
                          (credential-present-value argv row :play-service-account))]
              :default? true
              :platform-step :google-play-publish
              :timeout-seconds default-submit-timeout-seconds}
    :windows {:command "signtool"
              :args ["timestamp" (artifact-path row :msix)]
              :default? true
              :platform-step :windows-release-finalize
              :timeout-seconds default-submit-timeout-seconds}
    nil))

(defn default-updater-step
  [argv row]
  (case (:target row)
    (:macos :windows) {:command "sh"
                       :args ["-c" (str "test -f "
                                        (pr-str (or (option-value argv "--updater-feed")
                                                    "updater-feed.edn")))]
                       :default? true
                       :platform-step :signed-updater-feed}
    :ios {:command "/usr/bin/xcrun"
          :args ["altool" "--list-apps"
                 "--apiKey" (credential-present-value argv row :app-store-connect-key)]
          :default? true
          :platform-step :app-store-release-feed
          :timeout-seconds default-build-timeout-seconds}
    :android {:command "gradle"
              :args ["publishReleaseBundle"
                     (str "-PplayServiceAccount="
                          (credential-present-value argv row :play-service-account))]
              :default? true
              :platform-step :play-release-track
              :timeout-seconds default-build-timeout-seconds}
    nil))

(defn step-run-result
  [execute? step]
  (cond
    (nil? step)
    {:configured? false
     :executed? false
     :ok? false
     :reason :command-required}

    (not execute?)
    (assoc step
           :configured? true
           :executed? false
           :ok? true
           :reason :planned)

    :else
    ;; :timeout-seconds(既定 step 定義に付いていれば — build/sign/submit/
    ;; updater は default-provider-timeout-seconds=10秒 ではまず完了しない、
    ;; 実機確認: 単純な demo app の xcodebuild でも 15〜40秒かかった)。
    ;; external-step(ユーザー指定コマンド)には付かないので、その場合は
    ;; provider 既定へ自然にフォールバックする。
    (let [timeout-seconds (or (:timeout-seconds step) default-provider-timeout-seconds)
          {:keys [exit stdout timed-out?]}
          (run-native-host-command (:command step) (vec (:args step)) nil timeout-seconds)]
      (assoc step
             :configured? true
             :executed? true
             :ok? (and (not timed-out?) (zero? exit))
             :exit exit
             :stdout stdout
             :timed-out? timed-out?))))

(defn release-sign-row
  [argv manifest target]
  (let [connect-row (release-connect-row argv manifest target)
        execute? (execute-requested? argv)
        step (or (external-step argv "--sign-command" [(name target)])
                 (default-sign-step argv connect-row))
        run (step-run-result execute? step)
        ok? (and (:ok? connect-row) (:ok? run))]
    (assoc connect-row
           :sign-step run
           :signing-executed? (:executed? run)
           :ok? ok?)))

(defn release-sign-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos])
        manifest (app-manifest argv)
        execute? (execute-requested? argv)
        rows (mapv #(release-sign-row argv manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/release-signed :shell/release-sign-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/release-sign-schema "kotoba.shell.release-sign.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/release-ready-count (count (filter :ok? rows))
                        :kotoba.shell/release-target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/signed
                                        :release/sign-blocked)
                                      {:audit/targets targets
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn release-submit-row
  [argv manifest target]
  (let [sign-row (release-sign-row argv manifest target)
        execute? (execute-requested? argv)
        step (or (external-step argv "--submit-command" [(name target)])
                 (default-submit-step argv sign-row))
        run (step-run-result execute? step)
        ok? (and (:ok? sign-row) (:ok? run))]
    (assoc sign-row
           :submit-step run
           :submitted? (:executed? run)
           :ok? ok?)))

(defn release-submit-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos])
        manifest (app-manifest argv)
        execute? (execute-requested? argv)
        rows (mapv #(release-submit-row argv manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/release-submitted :shell/release-submit-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/release-submit-schema "kotoba.shell.release-submit.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/release-ready-count (count (filter :ok? rows))
                        :kotoba.shell/release-target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :release/submitted
                                        :release/submit-blocked)
                                      {:audit/targets targets
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn updater-publish-row
  [argv manifest target]
  (let [connect-row (release-connect-row argv manifest target)
        execute? (execute-requested? argv)
        step (or (external-step argv "--updater-command" [(name target)])
                 (default-updater-step argv connect-row))
        run (step-run-result execute? step)
        ok? (and (:ok? connect-row) (:ok? run))]
    (assoc connect-row
           :updater-step run
           :updater-published? (:executed? run)
           :ok? ok?)))

(defn updater-publish-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos])
        manifest (app-manifest argv)
        execute? (execute-requested? argv)
        rows (mapv #(updater-publish-row argv manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/updater-published :shell/updater-publish-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/updater-publish-schema "kotoba.shell.updater-publish.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/release-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :updater/published
                                        :updater/publish-blocked)
                                      {:audit/targets targets
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn store-provider
  [target]
  (case target
    :macos :apple-notary
    :ios :app-store-connect
    :android :google-play
    :windows :microsoft-store
    :unknown))

(defn store-endpoint
  [target operation manifest]
  (case target
    :macos (case operation
             :submit "/notary/v2/submissions"
             :status "/notary/v2/submissions/{id}"
             "/notary/v2/submissions")
    :ios (case operation
           :submit "/v1/apps/{app}/appStoreVersions"
           :status "/v1/builds/{id}"
           :list "/v1/apps"
           "/v1/apps")
    :android (case operation
               :submit (str "/androidpublisher/v3/applications/" (:android/application-id manifest) "/edits")
               :status (str "/androidpublisher/v3/applications/" (:android/application-id manifest) "/edits/{editId}")
               :list (str "/androidpublisher/v3/applications/" (:android/application-id manifest) "/tracks")
               "/androidpublisher/v3/applications")
    :windows (case operation
               :submit "/v1.0/my/applications/{appId}/submissions"
               :status "/v1.0/my/applications/{appId}/submissions/{id}"
               "/v1.0/my/applications")
    "/"))

(defn resolve-store-endpoint
  [endpoint manifest]
  (-> endpoint
      (str/replace "{app}" (sanitize-path-part (or (:ios/bundle-id manifest)
                                                   (:app/id manifest))))
      (str/replace "{appId}" (sanitize-path-part (:app/id manifest)))
      (str/replace "{id}" (sanitize-path-part (or (:app/version manifest) "latest")))
      (str/replace "{editId}" (sanitize-path-part (or (:app/version manifest) "latest")))))

(defn store-request
  [argv target manifest operation]
  (let [connect-row (release-connect-row argv manifest target)]
    {:schema "kotoba.shell.store-request.v0"
     :target target
     :provider (store-provider target)
     :operation operation
     :method (if (= operation :status) "GET" "POST")
     :endpoint (resolve-store-endpoint (store-endpoint target operation manifest) manifest)
     :manifest manifest
     :credentials-ready? (:credentials-ready? connect-row)
     :artifacts-ready? (:artifacts-ready? connect-row)
     :missing-manifest-keys (:missing-manifest-keys connect-row)
     :artifact-paths (into {}
                           (map (juxt :artifact :path))
                           (:artifacts connect-row))
     :credential-sources (into {}
                               (map (juxt :id :source))
                               (:credentials connect-row))}))

(defn store-http-step
  [argv request]
  (let [auth-token (or (some-> (option-value argv "--auth-token")
                               value-or-file)
                       (some-> (option-value argv "--auth-token-file")
                               slurp
                               str/trim)
                       (System/getenv "KOTOBA_STORE_AUTH_TOKEN"))
        headers (into {}
                      (keep (fn [header]
                              (let [[k v] (str/split header #":" 2)]
                                (when (and (seq k) (seq v))
                                  [(str/trim k) (str/trim v)]))))
                      (option-values argv "--store-header"))
        headers (cond-> headers
                  (seq (str auth-token))
                  (assoc "authorization" (str "Bearer " auth-token)))]
    (if-let [endpoint-url (option-value argv "--endpoint-url")]
      {:kind :java-http
       :endpoint-url endpoint-url
       :headers headers
       :auth-configured? (contains? headers "authorization")
       :request request}
      (when-let [command (option-value argv "--http-command")]
        {:kind :external-command
         :command command
         :args (vec (option-values argv "--http-command-arg"))
         :headers headers
         :auth-configured? (contains? headers "authorization")
         :request request}))))

(defn store-http-uri
  [endpoint-url request]
  (java.net.URI/create
   (str (str/replace (str endpoint-url) #"/$" "")
        (:endpoint request))))

(defn java-http-store-request
  [endpoint-url headers request]
  (let [body (json/write-str (json-ready request))
        method (:method request)
        builder (-> (java.net.http.HttpRequest/newBuilder (store-http-uri endpoint-url request))
                    (.header "content-type" "application/json")
                    (.header "accept" "application/json"))
        builder (reduce (fn [builder [k v]]
                          (.header builder k v))
                        builder
                        headers)
        builder (if (= "GET" method)
                  (.GET builder)
                  (.method builder method (java.net.http.HttpRequest$BodyPublishers/ofString body)))
        response (.send (java.net.http.HttpClient/newHttpClient)
                        (.build builder)
                        (java.net.http.HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (.body response)
     :ok? (<= 200 (.statusCode response) 299)}))

(defn store-step-run-result
  [execute? step request]
  (cond
    (not execute?)
    {:configured? (boolean step)
     :executed? false
     :ok? true
     :reason :planned
     :request request}

    (nil? step)
    {:configured? false
     :executed? false
     :ok? false
     :reason :http-command-required
     :request request}

    :else
    (if (= :java-http (:kind step))
      (try
        (let [response (java-http-store-request (:endpoint-url step)
                                                (:headers step)
                                                request)]
          (-> step
              (dissoc :headers)
              (assoc :executed? true
                     :ok? (:ok? response)
                     :status (:status response)
                     :body (:body response))))
        (catch Exception e
          (-> step
              (dissoc :headers)
              (assoc :executed? true
                     :ok? false
                     :error (.getMessage e)))))
      (let [{:keys [exit stdout timed-out?]} (run-native-host-command (:command step)
                                                                      (:args step)
                                                                      (json/write-str (json-ready (assoc request
                                                                                                          :headers (:headers step)))))]
        (-> step
            (dissoc :headers)
            (assoc :executed? true
                   :ok? (and (not timed-out?) (zero? exit))
                   :exit exit
                   :stdout stdout
                   :timed-out? timed-out?))))))

(defn store-request-row
  [argv manifest target operation]
  (let [request (store-request argv target manifest operation)
        execute? (execute-requested? argv)
        step (store-http-step argv request)
        run (store-step-run-result execute? step request)
        ok? (and (empty? (:missing-manifest-keys request))
                 (:credentials-ready? request)
                 (:artifacts-ready? request)
                 (:ok? run))]
    {:target target
     :ok? ok?
     :request request
     :http-step run}))

(defn store-request-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:ios])
        manifest (app-manifest argv)
        operation (keyword (or (option-value argv "--operation") "submit"))
        execute? (execute-requested? argv)
        rows (mapv #(store-request-row argv manifest % operation) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/store-request-ready :shell/store-request-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/store-request-schema "kotoba.shell.store-request-check.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/operation operation
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/store-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :store/request-ready
                                        :store/request-blocked)
                                      {:audit/targets targets
                                       :audit/operation operation
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn store-status-result
  [argv]
  (store-request-result (into ["store" "request" "--operation" "status"] (drop 2 argv))))

(defn distribution-row
  [argv manifest target]
  (let [release-row (release-connect-row argv manifest target)]
    (merge release-row
           {:channels (get-in production-connection-specs [target :distribution])
            :api-stable? true
            :plugin-compatible? true
            :device-e2e-required? (contains? #{:ios :android} target)})))

(defn distribution-check-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        manifest (app-manifest argv)
        rows (mapv #(distribution-row argv manifest %) targets)
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/distribution-ready :shell/distribution-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/distribution-schema "kotoba.shell.distribution.v0"
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/distribution-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :distribution/ready
                                        :distribution/blocked)
                                      {:audit/targets targets
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(def distribution-phases
  [:connect :sign :submit :store-submit :updater :device-farm])

(defn distribution-plan-path
  [argv]
  (or (option-value argv "--plan")
      (.getPath (io/file (or (option-value argv "--output-dir")
                             "target/kotoba-shell/distribution")
                         "distribution-plan.edn"))))

(defn distribution-plan-row
  [argv manifest target]
  (let [connection (release-connect-row argv manifest target)
        store-operation (keyword (or (option-value argv "--operation") "submit"))]
    (merge connection
           {:channels (get-in production-connection-specs [target :distribution])
            :phases distribution-phases
            :store-provider (store-provider target)
            :store-endpoint (resolve-store-endpoint (store-endpoint target store-operation manifest)
                                                    manifest)
            :device-farm-required? (contains? #{:ios :android} target)
            :commands {:connect ["release" "connect"]
                       :sign ["release" "sign"]
                       :submit ["release" "submit"]
                       :store-submit ["store" "request"]
                       :updater ["updater" "publish"]
                       :device-farm ["device-farm" "check"]}})))

(defn distribution-plan-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        manifest (app-manifest argv)
        rows (mapv #(distribution-plan-row argv manifest %) targets)
        write? (boolean (some #{"--write"} argv))
        plan {:schema "kotoba.shell.distribution-plan.v0"
              :authority "kotoba-lang/shell"
              :manifest manifest
              :targets targets
              :rows rows}
        path (distribution-plan-path argv)
        _ (when write?
            (write-edn-file! path plan))
        ok? (every? :ok? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/distribution-plan-ready :shell/distribution-plan-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/distribution-plan-schema (:schema plan)
                        :kotoba.shell/manifest manifest
                        :kotoba.shell/distribution-rows rows
                        :kotoba.shell/plan-path path
                        :kotoba.shell/written? write?
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :distribution/plan-ready
                                        :distribution/plan-blocked)
                                      {:audit/targets targets
                                       :audit/written? write?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn api-snapshot-path
  [argv]
  (or (option-value argv "--api")
      (option-value argv "--snapshot")
      (.getPath (io/file (or (option-value argv "--output-dir")
                             "target/kotoba-shell/api")
                         "kotoba-shell-api.edn"))))

(defn api-snapshot
  []
  (assoc stable-api-spec
         :authority "kotoba-lang/shell"
         :digest (sha256-hex stable-api-spec)))

(defn api-check-result
  [_argv]
  {:kotoba.cli/ok? true
   :kotoba.cli/code :shell/api-stable
   :kotoba.cli/data (merge
                     (shell-authority-data)
                     {:kotoba.shell/api stable-api-spec
                      :kotoba.shell/api-schema (:schema stable-api-spec)
                      :kotoba.shell/api-version (:version stable-api-spec)
                      :kotoba.shell/command-count (count (:commands stable-api-spec))
                      :kotoba.shell/audit
                      (audit-record :api/stable
                                    {:audit/schema (:schema stable-api-spec)
                                     :audit/version (:version stable-api-spec)})})})

(defn api-freeze-result
  [argv]
  (let [path (api-snapshot-path argv)
        snapshot (api-snapshot)
        write? (boolean (or (some #{"--write"} argv)
                            (some #{"--execute"} argv)))
        _ (when write?
            (write-edn-file! path snapshot))]
    {:kotoba.cli/ok? true
     :kotoba.cli/code :shell/api-frozen
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/api-freeze-schema "kotoba.shell.api-freeze.v0"
                        :kotoba.shell/api snapshot
                        :kotoba.shell/api-path path
                        :kotoba.shell/written? write?
                        :kotoba.shell/audit
                        (audit-record :api/frozen
                                      {:audit/schema (:schema snapshot)
                                       :audit/version (:version snapshot)
                                       :audit/written? write?})})}))

(defn api-compat-result
  [argv]
  (let [path (api-snapshot-path argv)
        snapshot (read-edn-file-safe path)
        previous (:data snapshot)
        previous-commands (set (:commands previous))
        current-commands (set (:commands stable-api-spec))
        removed (vec (remove current-commands previous-commands))
        previous-version (:version previous)
        current-version (:version stable-api-spec)
        compatible? (and (:ok? snapshot)
                         (= previous-version current-version)
                         (empty? removed))]
    {:kotoba.cli/ok? compatible?
     :kotoba.cli/code (if compatible? :shell/api-compatible :shell/api-incompatible)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/api-compat-schema "kotoba.shell.api-compat.v0"
                        :kotoba.shell/api-path path
                        :kotoba.shell/snapshot-readable? (:ok? snapshot)
                        :kotoba.shell/current-version current-version
                        :kotoba.shell/previous-version previous-version
                        :kotoba.shell/removed-commands removed
                        :kotoba.shell/current-command-count (count current-commands)
                        :kotoba.shell/previous-command-count (count previous-commands)
                        :kotoba.shell/audit
                        (audit-record (if compatible?
                                        :api/compatible
                                        :api/incompatible)
                                      {:audit/path path
                                       :audit/removed-count (count removed)})})}))

(defn plugin-manifest
  [argv]
  (or (read-edn-file-option argv "--plugin")
      (read-edn-option argv "--plugin-edn")
      {:plugin/id "kotoba.shell.demo-plugin"
       :plugin/version "0.1.0"
       :plugin/api-version 1
       :plugin/providers []}))

(defn missing-plugin-keys
  [manifest]
  (vec (remove #(contains? manifest %)
               (:required-keys plugin-api-spec))))

(defn provider-problems
  [provider]
  (vec (for [key (:provider-required-keys plugin-api-spec)
             :when (not (contains? provider key))]
         {:provider (:id provider)
          :missing-key key})))

(defn plugin-check-result
  [argv]
  (let [manifest (plugin-manifest argv)
        missing (missing-plugin-keys manifest)
        provider-problems (mapcat provider-problems (:plugin/providers manifest))
        api-compatible? (contains? (set (:compatible-api-versions plugin-api-spec))
                                   (:plugin/api-version manifest))
        ok? (and (empty? missing)
                 (empty? provider-problems)
                 api-compatible?)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/plugin-compatible :shell/plugin-incompatible)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/plugin-api plugin-api-spec
                        :kotoba.shell/plugin-manifest manifest
                        :kotoba.shell/missing-plugin-keys missing
                        :kotoba.shell/provider-problems (vec provider-problems)
                        :kotoba.shell/api-compatible? api-compatible?
                        :kotoba.shell/provider-count (count (:plugin/providers manifest))
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :plugin/compatible
                                        :plugin/incompatible)
                                      {:audit/plugin-id (:plugin/id manifest)
                                       :audit/provider-count (count (:plugin/providers manifest))})})}))

(defn tauri-plugin-manifest
  [argv]
  (or (read-edn-file-option argv "--tauri-plugin")
      (read-edn-option argv "--tauri-plugin-edn")
      {:tauri/plugin-id "demo.tauri.plugin"
       :tauri/version "0.1.0"
       :tauri/commands []}))

(defn plugin-abi-smoke-result
  [argv]
  (let [manifest (or (read-edn-file-option argv "--plugin-edn")
                     {:plugin/id "kotoba.shell.abi-smoke"
                      :plugin/version "0.1.0"
                      :plugin/api-version 1
                      :plugin/providers [{:id "clipboard"
                                          :capability "clipboard/text"
                                          :commands ["clipboard/read-text"]}]})
        result (plugin-check-result ["plugin" "check" "--plugin-edn" (pr-str manifest)])
        ok? (boolean (and (:kotoba.cli/ok? result)
                          (= 1 (:plugin/api-version manifest))
                          (seq (:plugin/providers manifest))))]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/plugin-abi-ready :shell/plugin-abi-blocked)
     :kotoba.cli/data (merge (shell-authority-data)
                             {:kotoba.shell/plugin-abi
                              {:schema "kotoba.shell.plugin-abi-smoke.v0"
                               :manifest manifest
                               :host-abi (:host-abi plugin-api-spec)
                               :provider-count (count (:plugin/providers manifest))
                               :plugin-check result}})}))

(defn tauri-command-supported?
  [command]
  (or (contains? #{"clipboard/read-text"
                   "clipboard/write-text"
                   "fs/read-text"
                   "fs/write-text"
                   "fs/append-text"
                   "http/fetch"
                   "notify/show"
                   "keychain/read-text"
                   "keychain/write-text"
                   "keychain/delete"}
                 command)
      (str/starts-with? command "plugin/")))

(defn tauri-plugin-check-result
  [argv]
  (let [manifest (tauri-plugin-manifest argv)
        commands (vec (:tauri/commands manifest))
        permissions (vec (:tauri/permissions manifest))
        platforms (vec (or (:tauri/platforms manifest) [:macos :ios :android :windows]))
        unsupported (vec (remove tauri-command-supported? commands))
        plugin-id (or (:plugin/id manifest)
                      (:tauri/plugin-id manifest)
                      "tauri.plugin")
        kotoba-plugin {:plugin/id plugin-id
                       :plugin/version (or (:plugin/version manifest)
                                           (:tauri/version manifest)
                                           "0.1.0")
                       :plugin/api-version (:version plugin-api-spec)
                       :plugin/providers
                       (mapv (fn [command]
                               {:id (str plugin-id "/" command)
                                :capability command
                                :commands [command]})
                             commands)}
        plugin-result (plugin-check-result ["plugin" "check"
                                            "--plugin-edn" (pr-str kotoba-plugin)])
        ok? (and (empty? unsupported)
                 (:kotoba.cli/ok? plugin-result))]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/tauri-plugin-compatible :shell/tauri-plugin-incompatible)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/tauri-plugin-schema "kotoba.shell.tauri-plugin-compat.v0"
                        :kotoba.shell/tauri-plugin-manifest manifest
                        :kotoba.shell/kotoba-plugin-manifest kotoba-plugin
                        :kotoba.shell/platforms platforms
                        :kotoba.shell/permissions permissions
                        :kotoba.shell/unsupported-commands unsupported
                        :kotoba.shell/plugin-compatible? (:kotoba.cli/ok? plugin-result)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :plugin/tauri-compatible
                                        :plugin/tauri-incompatible)
                                      {:audit/plugin-id plugin-id
                                       :audit/unsupported-count (count unsupported)})})}))

(defn doctor-check-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        rows (mapv doctor-target-row targets)
        strict? (boolean (some #{"--strict"} argv))
        ok? (if strict?
              (every? :ready? rows)
              true)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        (every? :ready? rows) :shell/doctor-ready
                        strict? :shell/doctor-blocked
                        :else :shell/doctor-warnings)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/doctor-schema "kotoba.shell.doctor.v0"
                        :kotoba.shell/strict? strict?
                        :kotoba.shell/doctor-rows rows
                        :kotoba.shell/ready-count (count (filter :ready? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (cond
                                        (every? :ready? rows) :doctor/ready
                                        strict? :doctor/blocked
                                        :else :doctor/warnings)
                                      {:audit/targets targets
                                       :audit/ready-count (count (filter :ready? rows))})})}))

(defn ios-booted-device?
  [stdout]
  (boolean (re-find #"\(Booted\)" (or stdout ""))))

(defn android-connected-device?
  [stdout]
  (boolean
   (some (fn [line]
           (let [[serial state] (str/split line #"\s+")]
             (and (not (str/blank? serial))
                  (= "device" state))))
         (remove #(or (str/blank? %)
                      (str/starts-with? % "List of devices"))
                 (str/split-lines (or stdout ""))))))

(defn host-smoke-kind
  [target]
  (case target
    :macos :local-process-ready
    :ios :simctl-booted-device
    :android :adb-connected-device
    :windows :external-host-command
    :unknown))

(defn host-smoke-ok?
  [target stdout exit timed-out?]
  (and (not timed-out?)
       (number? exit)
       (zero? exit)
       (case target
         :macos true
         :ios (ios-booted-device? stdout)
         :android (android-connected-device? stdout)
         false)))

(defn host-smoke-row
  [target doctor]
  (let [host-command (default-host-command target)]
    (cond
      (not (:host-runner-ready? doctor))
      {:kind (host-smoke-kind target)
       :ran? false
       :ok? false
       :reason :host-runner-not-ready}

      (seq (:missing-required-tools doctor))
      {:kind (host-smoke-kind target)
       :ran? false
       :ok? false
       :reason :missing-required-tools
       :missing-required-tools (:missing-required-tools doctor)}

      :else
      (try
        (let [{:keys [exit stdout timed-out?]} (run-native-host-command host-command [])
              ok? (host-smoke-ok? target stdout exit timed-out?)]
          {:kind (host-smoke-kind target)
           :ran? true
           :ok? ok?
           :exit exit
           :stdout stdout
           :timed-out? timed-out?
           :device-connected? (case target
                                :ios (ios-booted-device? stdout)
                                :android (android-connected-device? stdout)
                                :macos true
                                false)})
        (catch Exception e
          {:kind (host-smoke-kind target)
           :ran? true
           :ok? false
           :error (.getMessage e)})))))

(defn e2e-target-row
  [target]
  (let [doctor (doctor-target-row target)
        smoke (host-smoke-row target doctor)
        smoke-ok? (true? (:ok? smoke))]
    {:target target
     :ready? (and (:ready? doctor) smoke-ok?)
     :doctor-ready? (:ready? doctor)
     :surface-ready? (contains? surface-host-specs target)
     :provider-bridge-ready? (contains? host-runner-specs target)
     :release-metadata-ready? (contains? release-target-specs target)
     :host-smoke smoke
     :missing-required-tools (:missing-required-tools doctor)}))

(defn e2e-check-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:macos :ios :android])
        rows (mapv e2e-target-row targets)
        strict? (boolean (some #{"--strict"} argv))
        all-ready? (every? :ready? rows)
        ok? (if strict? all-ready? true)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        all-ready? :shell/e2e-ready
                        strict? :shell/e2e-blocked
                        :else :shell/e2e-warnings)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/e2e-schema "kotoba.shell.e2e.v0"
                        :kotoba.shell/strict? strict?
                        :kotoba.shell/e2e-rows rows
                        :kotoba.shell/ready-count (count (filter :ready? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (cond
                                        all-ready? :e2e/ready
                                        strict? :e2e/blocked
                                        :else :e2e/warnings)
                                      {:audit/targets targets
                                       :audit/ready-count (count (filter :ready? rows))})})}))

(defn stack-e2e-result
  [argv]
  (let [source-path (or (option-value argv "--source")
                        (sibling-path "../kototama/test/kototama/fixtures/kotoba-compiled-fact.kotoba"))
        app-source-path (or (option-value argv "--app-source")
                            (sibling-path "resources/kotoba/shell/app/tauri_equivalent.kotoba"))
        wasm-path (or (option-value argv "--wasm")
                      (sibling-path "../kototama/test/kototama/fixtures/kotoba-compiled-fact.wasm"))
        receipt (stack-e2e/run
                 {:source-path source-path
                  :app-source-path app-source-path
                  :wasm-path wasm-path
                  :aiueos-dir (sibling-path "../aiueos")
                  :kototama-dir (sibling-path "../kototama")
                  :kotobase-dir (sibling-path "../kotobase")
                  :correlation-id (or (option-value argv "--correlation-id")
                                      "kotoba-shell-stack-e2e")
                  :shell-commit #(surface-commit-result
                                   ["surface" "commit" "--target" "macos"
                                    "--ops-edn" (pr-str %)])})]
    {:kotoba.cli/ok? (:ready? receipt)
     :kotoba.cli/code (if (:ready? receipt) :shell/stack-e2e-ready :shell/stack-e2e-blocked)
     :kotoba.cli/data (merge (shell-authority-data)
                             {:kotoba.shell/stack-e2e receipt})}))

(defn native-window-e2e-result
  "Static T1 gate for the native AppKit boundary. Execution is opt-in because
  CI/headless hosts cannot promise an interactive macOS session."
  [argv]
  (let [source (sibling-path "bin/kotoba-shell-host-macos-window.swift")
        builder (sibling-path "bin/kotoba-shell-build-macos-window")
        app-source (sibling-path "resources/kotoba/shell/app/tauri_equivalent.kotoba")
        execute? (execute-requested? argv)
        binary (or (option-value argv "--window-command")
                   (sibling-path "target/kotoba-shell-host-macos-window"))
        wasm-output (or (option-value argv "--wasm-output")
                        (str (sibling-path "target/tauri-equivalent.wasm")))
        kotoba-command (or (option-value argv "--kotoba-command")
                           (sibling-path "../kotoba/bin/kotoba-clj"))
        package-lock (or (option-value argv "--package-lock")
                         (sibling-path "../kotoba/kotoba.lock.edn"))
        compile-run (when execute?
                      (run-native-host-command kotoba-command
                                               ["wasm" "emit" app-source
                                                "--package-lock" package-lock
                                                "--output" wasm-output "--json"]
                                               nil 120))
        run (when (and execute? compile-run (= 0 (:exit compile-run)))
              (run-native-host-command binary ["--smoke"] nil 15))
        source-ready? (and (.exists (io/file source)) (.exists (io/file builder))
                           (.exists (io/file app-source)))
        compile-ready? (and execute? compile-run (= 0 (:exit compile-run))
                            (.exists (io/file wasm-output)))
        smoke-ready? (boolean (and execute? run (= 0 (:exit run))
                                   (re-find #"lifecycle/smoke-ready" (:stdout run ""))
                                   (re-find #"lifecycle/terminate" (:stdout run ""))))
        ready? (and source-ready? (if execute? (and compile-ready? smoke-ready?) false))]
    {:kotoba.cli/ok? ready?
     :kotoba.cli/code (if ready? :shell/native-window-ready :shell/native-window-blocked)
     :kotoba.cli/data (merge (shell-authority-data)
                             {:kotoba.shell/native-window
                              {:schema "kotoba.shell.native-window.v0"
                               :source source
                               :builder builder
                               :app-source app-source
                               :kotoba-command kotoba-command
                               :package-lock package-lock
                               :wasm-output wasm-output
                               :execute? execute?
                               :source-ready? source-ready?
                               :compile-ready? compile-ready?
                               :smoke-ready? smoke-ready?
                               :compile-run compile-run
                               :run run}})}))

(defn runtime-parity-result
  [argv]
  (let [profile (keyword (or (option-value argv "--profile") "host-free"))
        wasm (or (option-value argv "--wasm")
                 (sibling-path "../kototama/web/host-free-fact.wasm"))
        expected (or (option-value argv "--expected") "120")
        runner (or (option-value argv "--runner")
                   (sibling-path "bin/kotoba-shell-native-wasm-parity.mjs"))
        profile-command (case profile
                          :kgraph (sibling-path "../kototama/web/verify-kgraph.mjs")
                          :actor (sibling-path "../kototama/web/verify-actor-host.mjs")
                          nil)
        run (if profile-command
              (run-native-host-command "node" [profile-command] nil 60)
              (run-native-host-command "node" [runner wasm expected] nil 15))
        ready? (boolean (and (= 0 (:exit run))
                             (if profile-command
                               (re-find #"browser-native WebAssembly engine ran" (:stdout run ""))
                               (re-find #"native-wasm/parity" (:stdout run "")))))]
    {:kotoba.cli/ok? ready?
     :kotoba.cli/code (if ready? :shell/runtime-parity-ready :shell/runtime-parity-blocked)
     :kotoba.cli/data (merge (shell-authority-data)
                             {:kotoba.shell/runtime-parity
                              {:schema "kotoba.shell.runtime-parity.v0"
                               :profile profile :wasm wasm :expected (Long/parseLong expected)
                               :runner runner :run run}})}))

(defn device-farm-row
  [argv target]
  (let [execute? (execute-requested? argv)
        provider (or (option-value argv "--provider") "local")
        step (external-step argv "--device-farm-command" [(name target)])
        local-row (e2e-target-row target)
        run (if step
              (step-run-result execute? step)
              {:configured? false
               :executed? false
               :ok? (:ready? local-row)
               :reason :local-e2e-probe})
        ok? (and (:ready? local-row)
                 (:ok? run))]
    {:target target
     :provider provider
     :local-e2e local-row
     :device-farm-step run
     :continuous? true
     :ok? ok?}))

(defn device-farm-check-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:ios :android])
        strict? (boolean (some #{"--strict"} argv))
        execute? (execute-requested? argv)
        rows (mapv #(device-farm-row argv %) targets)
        all-ok? (every? :ok? rows)
        ok? (or all-ok? (not strict?))]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        all-ok? :shell/device-farm-ready
                        strict? :shell/device-farm-blocked
                        :else :shell/device-farm-warnings)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/device-farm-schema "kotoba.shell.device-farm.v0"
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/strict? strict?
                        :kotoba.shell/device-farm-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (cond
                                        all-ok? :device-farm/ready
                                        strict? :device-farm/blocked
                                        :else :device-farm/warnings)
                                      {:audit/targets targets
                                       :audit/execute? execute?
                                       :audit/strict? strict?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn device-farm-schedule-path
  [argv]
  (or (option-value argv "--workflow")
      (option-value argv "--schedule")
      (.getPath (io/file (or (option-value argv "--output-dir")
                             "target/kotoba-shell/device-farm")
                         "device-farm-schedule.edn"))))

(defn device-farm-run-log-path
  [argv]
  (or (option-value argv "--run-log")
      (.getPath (io/file (or (option-value argv "--output-dir")
                             "target/kotoba-shell/device-farm")
                         "device-farm-run.edn"))))

(defn device-farm-schedule-result
  [argv]
  (let [targets (or (seq (map keyword (option-values argv "--target")))
                    [:ios :android])
        provider (or (option-value argv "--provider") "local")
        cadence (or (option-value argv "--cadence") "hourly")
        command (option-value argv "--device-farm-command")
        args (vec (option-values argv "--device-farm-command-arg"))
        write? (boolean (some #{"--write"} argv))
        execute? (execute-requested? argv)
        path (device-farm-schedule-path argv)
        run-log-path (device-farm-run-log-path argv)
        rows (mapv (fn [target]
                     {:target target
                      :provider provider
                      :cadence cadence
                      :command command
                      :args args
                      :mobile-target? (contains? #{:ios :android} target)
                      :configured? (boolean (or command
                                                (not= "local" provider)))
                      :continuous? true})
                   targets)
        schedule {:schema "kotoba.shell.device-farm-schedule.v0"
                  :authority "kotoba-lang/shell"
                  :targets targets
                  :provider provider
                  :cadence cadence
                  :rows rows}
        _ (when write?
            (write-edn-file! path schedule))
        executions (when execute?
                     (mapv (fn [row]
                             (if command
                               (assoc (step-run-result true
                                                       {:command command
                                                        :args (or (seq args)
                                                                  [(name (:target row))])})
                                      :target (:target row)
                                      :provider provider)
                               {:target (:target row)
                                :provider provider
                                :executed? false
                                :ok? false
                                :reason :device-farm-command-required}))
                           rows))
        _ (when (and execute? write?)
            (write-edn-file! run-log-path
                             {:schema "kotoba.shell.device-farm-run.v0"
                              :authority "kotoba-lang/shell"
                              :schedule path
                              :provider provider
                              :cadence cadence
                              :executions executions}))
        ok? (every? :mobile-target? rows)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (if ok? :shell/device-farm-scheduled :shell/device-farm-schedule-blocked)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/device-farm-schedule-schema (:schema schedule)
                        :kotoba.shell/device-farm-rows rows
                        :kotoba.shell/schedule-path path
                        :kotoba.shell/run-log-path run-log-path
                        :kotoba.shell/written? write?
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/executions (or executions [])
                        :kotoba.shell/ready-count (count (filter :mobile-target? rows))
                        :kotoba.shell/target-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (if ok?
                                        :device-farm/scheduled
                                        :device-farm/schedule-blocked)
                                      {:audit/targets targets
                                       :audit/provider provider
                                       :audit/cadence cadence
                                       :audit/written? write?
                                       :audit/execute? execute?})})}))

(defn ui-check-result
  [argv]
  (let [substrates (or (seq (map keyword (option-values argv "--substrate")))
                       [:wasm-ui :browser])
        rows (mapv ui-substrate-row substrates)
        strict? (boolean (some #{"--strict"} argv))
        all-ready? (every? :ready? rows)
        ok? (if strict? all-ready? true)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        all-ready? :shell/ui-ready
                        strict? :shell/ui-blocked
                        :else :shell/ui-warnings)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/ui-schema "kotoba.shell.ui.v0"
                        :kotoba.shell/strict? strict?
                        :kotoba.shell/ui-rows rows
                        :kotoba.shell/ready-count (count (filter :ready? rows))
                        :kotoba.shell/substrate-count (count rows)
                        :kotoba.shell/webview-required? true
                        :kotoba.shell/audit
                        (audit-record (cond
                                        all-ready? :ui/ready
                                        strict? :ui/blocked
                                        :else :ui/warnings)
                                      {:audit/substrates substrates
                                       :audit/ready-count (count (filter :ready? rows))})})}))

(defn browser-smoke-shell-command
  [script]
  (str "python3 -m http.server 8702 -d public >/tmp/kotoba-shell-browser-smoke.log 2>&1 & "
       "server_pid=$!; "
       "trap 'kill $server_pid >/dev/null 2>&1 || true' EXIT; "
       "python3 - <<'PY'\n"
       "import sys, time, urllib.request\n"
       "for _ in range(100):\n"
       "    try:\n"
       "        urllib.request.urlopen('http://127.0.0.1:8702/', timeout=0.2).read(1)\n"
       "        sys.exit(0)\n"
       "    except Exception:\n"
       "        time.sleep(0.1)\n"
       "sys.exit(1)\n"
       "PY\n"
       "test $? -eq 0 || { cat /tmp/kotoba-shell-browser-smoke.log; exit 1; }; "
       "npm run " script))

(defn ui-smoke-script-row
  [execute? substrate-id script]
  (let [substrate (ui-substrate-row substrate-id)
        repo (:repo substrate)
        present? (boolean (some #(and (= script (:script %)) (:present? %))
                                (:scripts substrate)))
        served? (and execute? (= :browser substrate-id))
        result (when (and execute? present?)
                 (if served?
                   (run-native-host-command-in-dir repo "/usr/bin/env" ["sh" "-c" (browser-smoke-shell-command script)] 240)
                   (run-native-host-command-in-dir repo "npm" ["run" script] 180)))
        ok? (if execute?
              (and present? (not (:timed-out? result)) (zero? (:exit result)))
              present?)]
    {:substrate substrate-id
     :script script
     :command ["npm" "run" script]
     :repo repo
     :present? present?
     :executed? execute?
     :served? served?
     :ok? ok?
     :result result}))

(defn ui-smoke-result
  [argv]
  (let [substrates (or (seq (map keyword (option-values argv "--substrate")))
                       [:wasm-ui :browser])
        script-filter (set (option-values argv "--script"))
        execute? (boolean (some #{"--execute"} argv))
        strict? (boolean (some #{"--strict"} argv))
        rows (mapcat (fn [substrate]
                       (let [scripts (:smoke-scripts (get ui-substrate-specs substrate))
                             scripts (if (seq script-filter)
                                       (filter script-filter scripts)
                                       scripts)]
                         (map #(ui-smoke-script-row execute? substrate %) scripts)))
                     substrates)
        rows (vec rows)
        all-ok? (every? :ok? rows)
        ok? (if strict? all-ok? true)]
    {:kotoba.cli/ok? ok?
     :kotoba.cli/code (cond
                        all-ok? :shell/ui-smoke-ready
                        strict? :shell/ui-smoke-blocked
                        :else :shell/ui-smoke-warnings)
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/ui-smoke-schema "kotoba.shell.ui-smoke.v0"
                        :kotoba.shell/strict? strict?
                        :kotoba.shell/execute? execute?
                        :kotoba.shell/smoke-rows rows
                        :kotoba.shell/ready-count (count (filter :ok? rows))
                        :kotoba.shell/smoke-count (count rows)
                        :kotoba.shell/audit
                        (audit-record (cond
                                        all-ok? :ui-smoke/ready
                                        strict? :ui-smoke/blocked
                                        :else :ui-smoke/warnings)
                                      {:audit/substrates substrates
                                       :audit/execute? execute?
                                       :audit/ready-count (count (filter :ok? rows))})})}))

(defn dispatch
  [argv]
  (case [(first argv) (second argv)]
    ["adapter" "check"] (adapter-check-result argv)
    ["surface" "check"] (surface-check-result argv)
    ["surface" "commit"] (surface-commit-result argv)
    ["policy" "check"] (policy-check-result argv)
    ["release" "check"] (release-check-result argv)
    ["release" "dry-run"] (release-dry-run-result argv)
    ["release" "evidence"] (release-evidence-result argv)
    ["release" "connect"] (release-connect-result argv)
    ["release" "verify"] (release-verify-result argv)
    ["release" "sign"] (release-sign-result argv)
    ["release" "submit"] (release-submit-result argv)
    ["updater" "publish"] (updater-publish-result argv)
    ["store" "request"] (store-request-result argv)
    ["store" "status"] (store-status-result argv)
    ["distribution" "check"] (distribution-check-result argv)
    ["distribution" "plan"] (distribution-plan-result argv)
    ["app" "scaffold"] (app-scaffold-result argv)
    ["app" "check"] (app-check-result argv)
    ["app" "build"] (app-build-result argv)
    ["app" "package"] (app-package-result argv)
    ["app" "run"] (app-run-result argv)
    ["app" "surface"] (app-surface-result argv)
    ["app" "visual-test"] (app-visual-test-result argv)
    ["app" "kaizen"] (app-kaizen-result argv)
    ["api" "check"] (api-check-result argv)
    ["api" "freeze"] (api-freeze-result argv)
    ["api" "compat"] (api-compat-result argv)
    ["plugin" "check"] (plugin-check-result argv)
    ["plugin" "tauri-check"] (tauri-plugin-check-result argv)
    ["plugin" "abi-smoke"] (plugin-abi-smoke-result argv)
    ["doctor" "check"] (doctor-check-result argv)
    ["device-farm" "check"] (device-farm-check-result argv)
    ["device-farm" "schedule"] (device-farm-schedule-result argv)
    ["e2e" "check"] (e2e-check-result argv)
    ["e2e" "stack"] (stack-e2e-result argv)
    ["e2e" "native-window"] (native-window-e2e-result argv)
    ["e2e" "runtime-parity"] (runtime-parity-result argv)
    ["ui" "check"] (ui-check-result argv)
    ["ui" "smoke"] (ui-smoke-result argv)
    ["native-host" "check"] (native-host-check-result argv)
    ["native-host" "run"] (native-host-run-result argv)
    ["native-host" "provider"] (native-host-provider-result argv)
    {:kotoba.cli/ok? false
     :kotoba.cli/code :shell/unknown-command
     :kotoba.cli/data (merge
                       (shell-authority-data)
                       {:kotoba.shell/command (vec (take 2 argv))
                        :kotoba.shell/commands [["adapter" "check"]
                                                ["surface" "check"]
                                                ["surface" "commit"]
                                                ["policy" "check"]
                                                ["release" "check"]
                                                ["release" "dry-run"]
                                                ["release" "evidence"]
                                                ["release" "connect"]
                                                ["release" "verify"]
                                                ["release" "sign"]
                                                ["release" "submit"]
                                                ["updater" "publish"]
                                                ["store" "request"]
                                                ["store" "status"]
                                                ["distribution" "check"]
                                                ["distribution" "plan"]
                                                ["app" "scaffold"]
                                                ["app" "check"]
                                                ["app" "build"]
                                                ["app" "package"]
                                                ["app" "run"]
                                                ["app" "visual-test"]
                                                ["app" "kaizen"]
                                                ["api" "check"]
                                                ["api" "freeze"]
                                                ["api" "compat"]
                                                ["plugin" "check"]
                                                ["plugin" "tauri-check"]
                                                ["plugin" "abi-smoke"]
                                                ["doctor" "check"]
                                                ["device-farm" "check"]
                                                ["device-farm" "schedule"]
                                                ["e2e" "check"]
                                                ["e2e" "stack"]
                                                ["e2e" "native-window"]
                                                ["e2e" "runtime-parity"]
                                                ["ui" "check"]
                                                ["ui" "smoke"]
                                                ["native-host" "check"]
                                                ["native-host" "run"]
                                                ["native-host" "provider"]]})}))

(defn -main
  [& argv]
  (let [argv (vec argv)
        json? (boolean (some #{"--json"} argv))
        result (dispatch argv)]
    (println (render-result result json?))
    (System/exit (result->exit result))))
