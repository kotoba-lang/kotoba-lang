(ns public-locales-test
  (:require [cljs.test :refer [deftest is run-tests]]
            [kotoba.lang.text :as str]
            [kotoba.site.locales :as locales]
            ["node:fs" :as fs]))

(defn read-json [p] (js->clj (js/JSON.parse (fs/readFileSync p "utf8"))))
(def source (read-json "site/i18n/en.json"))
(def paths ["/" "/blog/infra-cost-measured-path/" "/blog/csf2-attack-graphs/" "/blog/" "/libraries/" "/legal/" "/sponsor/"])
(defn placeholders [s] (frequencies (re-seq #"\{[A-Za-z_]+\}" s)))
(defn code-blocks [s] (map second (re-seq #"(?s)<pre[^>]*>(.*?)</pre>" s)))

(deftest catalogs-and-equivalent-pages
  (is (> (count source) 900) "Missing source input must not pass")
  (doseq [{:keys [tag dir]} locales/required]
    (when (not= tag "en")
      (let [catalog (read-json (str "site/i18n/" tag ".json"))]
        (is (every? #(and (string? (get catalog %)) (not (str/blank? (get catalog %)))) (keys source)) tag)
        (doseq [[k v] source]
          (is (= (placeholders v) (placeholders (get catalog k ""))) (str tag " " k)))))
    (doseq [p paths]
      (let [prefix (if (= tag "en") "" (str "/" tag))
            html (fs/readFileSync (str "site/dist" prefix p "index.html") "utf8")
            english (fs/readFileSync (str "site/dist" p "index.html") "utf8")]
        (is (str/includes? html (str "<html lang=\"" tag "\"")) (str tag p))
        (when (= dir "rtl") (is (str/includes? html (str "<html lang=\"" tag "\" dir=\"rtl\"")) (str tag p)))
        (is (str/includes? html (str "rel=\"canonical\" href=\"https://kotoba-lang.org" prefix p "\"")) (str tag p))
        (is (= (code-blocks english) (code-blocks html)) (str "immutable executable examples " tag p))
        (doseq [{other :tag} locales/required]
          (is (str/includes? html (str "hreflang=\"" other "\" href=\"https://kotoba-lang.org"
                                       (when (not= other "en") (str "/" other)) p "\""))
              (str "equivalent alternate " tag p " → " other)))))))

(let [result (run-tests)]
  (when (pos? (+ (:fail result) (:error result))) (js/process.exit 1)))
