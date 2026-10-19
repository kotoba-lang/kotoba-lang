(ns kagitaba.import.onepux-file
  "`.1pux` ファイル(zip)を読んで `kagitaba.import.onepux` の純粋変換に渡す JVM 専用 IO 層。

  1PUX archive layout(1Password 公式ドキュメント support.1password.com/1pux-format/):
    export.attributes  — {version, description, createdAt}
    export.data        — 本体(accounts/vaults/items の JSON)
    files/...           — 添付ファイル・カスタムアイコン(パス構造は非公開。
                           best-effort でファイル名 → bytes を集めるだけに留める)"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kagitaba.import.onepux :as onepux])
  (:import [java.util.zip ZipFile]
           [java.io ByteArrayOutputStream]))

(def limits
  "Hard limits are applied to decompressed bytes, not attacker-controlled ZIP metadata."
  {:max-entries 10000
   :max-attributes-bytes (* 1024 1024)
   :max-data-bytes (* 64 1024 1024)
   :max-file-bytes (* 64 1024 1024)
   :max-total-bytes (* 256 1024 1024)
   :max-name-bytes 1024})

(defn- reject! [reason data]
  (throw (ex-info "unsafe 1PUX archive" (assoc data :reason reason))))

(defn- safe-name? [name]
  (and (string? name)
       (not (str/blank? name))
       (<= (alength (.getBytes name "UTF-8")) (:max-name-bytes limits))
       (not (str/includes? name "\u0000"))
       (not (str/includes? name "\\"))
       (not (str/starts-with? name "/"))
       (not-any? #{".."} (str/split name #"/"))))

(defn- entry-bytes ^bytes [^ZipFile zf entry max-bytes total]
  (when-not (safe-name? (.getName entry))
    (reject! :unsafe-entry-name {:entry (.getName entry)}))
  (with-open [in (.getInputStream zf entry)
              out (ByteArrayOutputStream.)]
    (let [buffer (byte-array 8192)]
      (loop [entry-total 0]
        (let [n (.read in buffer)]
          (if (neg? n)
            (.toByteArray out)
            (let [next-entry (+ entry-total n)
                  next-total (+ @total n)]
              (when (> next-entry max-bytes)
                (reject! :entry-too-large
                         {:entry (.getName entry) :max-bytes max-bytes}))
              (when (> next-total (:max-total-bytes limits))
                (reject! :archive-too-large
                         {:max-bytes (:max-total-bytes limits)}))
              (.write out buffer 0 n)
              (reset! total next-total)
              (recur next-entry))))))))

(defn- entry-str [^ZipFile zf entry max-bytes total]
  (String. (entry-bytes zf entry max-bytes total) "UTF-8"))

(defn load-1pux
  "path(String/java.io.File)を読み、
  `{:attributes {...} :vaults [...] :warnings [...] :files {name -> bytes}}` を返す。
  `:vaults`/`:warnings` の shape は `kagitaba.import.onepux/parse-export-data` と同じ。"
  [path]
  (with-open [zf (ZipFile. (io/file path))]
    (let [entries (vec (enumeration-seq (.entries zf)))
          _ (when (> (count entries) (:max-entries limits))
              (reject! :too-many-entries {:count (count entries)
                                          :max (:max-entries limits)}))
          names (mapv #(.getName %) entries)
          duplicate (some (fn [[name n]] (when (> n 1) name)) (frequencies names))
          _ (when duplicate (reject! :duplicate-entry {:entry duplicate}))
          _ (doseq [name names]
              (when-not (safe-name? name)
                (reject! :unsafe-entry-name {:entry name})))
          by-name (into {} (map (fn [e] [(.getName e) e])) entries)
          attrs-entry (get by-name "export.attributes")
          data-entry (or (get by-name "export.data")
                         (throw (ex-info "not a 1PUX export: export.data missing"
                                         {:path (str path) :entries (keys by-name)})))
          total (atom 0)
          attributes (when attrs-entry
                       (json/read-str (entry-str zf attrs-entry
                                                 (:max-attributes-bytes limits) total)
                                      :key-fn keyword))
          export-data (json/read-str (entry-str zf data-entry
                                                (:max-data-bytes limits) total)
                                     :key-fn keyword)
          {:keys [vaults warnings]} (onepux/parse-export-data export-data)
          files (into {}
                      (keep (fn [[name entry]]
                              (when (and (str/starts-with? name "files/")
                                         (not (.isDirectory entry)))
                                [(subs name (count "files/"))
                                 (entry-bytes zf entry (:max-file-bytes limits) total)])))
                      by-name)]
      {:attributes attributes
       :vaults vaults
       :warnings warnings
       :files files})))
