(ns kotoba.shell.native-bridge
  "The in-app provider bridge: native provider implementations that ship
  *inside* a scaffolded app, plus the policy asset that governs them.

  This is a different thing from `native-host`, and the difference is the
  whole point. `native-host run`/`native-host provider` execute providers
  through `bin/kotoba-shell-host-{macos,ios,android}` — for iOS and Android
  those are `xcrun simctl spawn booted` and `adb shell`, which are a
  developer's machine driving an attached device. Nothing in that path exists
  in an `.ipa` or `.apk` handed to someone else, so before this namespace the
  provider catalog's `:required-targets [:macos :ios :android :windows]` was
  true only of the CLI, never of a shipped build.

  The templates live in `resources/kotoba/shell/app/` rather than as string
  literals here, following `keychain_cacao_app_delegate.swift`: Swift and Java
  that is read as Swift and Java can be compiled, diffed and linted on its
  own, and a `{{PLACEHOLDER}}` is visible in a way string concatenation is
  not.

  Policy is carried into the app as JSON (`:json-role :interop`, the same
  convention the provider catalog already declares) because the native side
  has JSONSerialization and org.json built in and no EDN reader. EDN remains
  canonical: the JSON is generated from the same `:allow`/`:deny` map the CLI
  evaluates, and both native implementations reproduce
  `kotoba.shell.launcher/policy-decision` exactly."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def bridge-provider-commands
  "Commands the in-app bridge implements natively on every target it supports.

  Deliberately the portable subset. `contacts/list`, `calendar/list-events`
  and `webauthn/*` are macOS-only in the provider catalog and stay that way:
  each needs a real Contacts/EventKit/AuthenticationServices integration per
  platform, and listing them here would repeat the exact mistake the catalog
  already had to correct — claiming iOS/Android support with no
  implementation behind it."
  ["clipboard/read-text"
   "clipboard/write-text"
   "fs/read-text"
   "fs/write-text"
   "fs/append-text"
   "keychain/read-text"
   "keychain/write-text"
   "keychain/delete"
   "http/fetch"
   "notify/show"])

(def bridge-targets
  "Targets whose scaffold carries the in-app bridge. Windows' host boundary is
  a PowerShell contract with no WebView2 implementation, so it is not here."
  #{:macos :ios :android})

(defn- resource-text
  [path]
  (slurp (io/resource path)))

(defn- substitute
  [template replacements]
  (reduce-kv str/replace template replacements))

(defn keychain-service
  "Service name for keychain items. One app, one namespace: sharing a service
  string across apps would let either read the other's items."
  [target manifest]
  (or (case target
        :ios (:ios/bundle-id manifest)
        :macos (or (:macos/bundle-id manifest) (:app/id manifest))
        :android (:android/application-id manifest)
        nil)
      (:app/id manifest)
      "kotoba.shell.app"))

(def data-dir-name
  "Subdirectory of the app's own data directory that `fs/*` is scoped to.
  Named rather than the container root so an app's other files are not
  reachable through a provider even by an exact path."
  "kotoba-shell")

(defn policy-json
  "The policy asset the native bridge reads at startup.

  `capabilities` maps each command to its provider capability so the native
  decision can match on either, exactly as `policy-decision` does. Commands
  outside `bridge-provider-commands` are dropped: a policy that allows
  `webauthn/register` on iOS grants nothing there, and carrying it into the
  app would suggest otherwise."
  [{:keys [allow deny capabilities]}]
  (let [known (set bridge-provider-commands)
        capability-of (select-keys (or capabilities {}) bridge-provider-commands)
        capability-values (set (vals capability-of))
        relevant (fn [tokens]
                   (vec (distinct (filter (fn [token]
                                            (or (= "*" token)
                                                (contains? known token)
                                                (contains? capability-values token)))
                                          (or tokens [])))))]
    (str (json/write-str {"schema" "kotoba.shell.policy.v0"
                          "allow" (relevant allow)
                          "deny" (relevant deny)
                          "capabilities" capability-of})
         "\n")))

(defn bridge-js
  [target]
  (substitute (resource-text "kotoba/shell/app/kotoba_shell_bridge.js")
              {"{{TARGET}}" (name target)}))

(defn apple-bridge-swift
  [target manifest]
  (substitute (resource-text "kotoba/shell/app/kotoba_shell_bridge.swift")
              {"{{KEYCHAIN_SERVICE}}" (keychain-service target manifest)
               "{{DATA_DIR}}" data-dir-name}))

(defn android-bridge-java
  [manifest]
  (substitute (resource-text "kotoba/shell/app/KotobaShellBridge.java")
              {"{{PACKAGE}}" (str (:android/application-id manifest))
               "{{KEYCHAIN_PREFS}}" (str (keychain-service :android manifest) ".keychain")
               "{{DATA_DIR}}" data-dir-name}))
