(ns locales-test
  (:require [cljs.test :refer [deftest is run-tests]]
            [kotoba.site.locales :as locales]))

(deftest requested-languages
  (is (= ["en" "zh-Hans" "hi" "es" "ar" "fr" "bn" "pt" "id" "ur" "ru" "de" "ja" "ko" "pcm" "arz" "mr"]
         (mapv :tag locales/required)))
  (is (= #{"ar" "arz" "ur"}
         (set (map :tag (filter #(= "rtl" (:dir %)) locales/required))))))

(deftest protects-executable-and-source-data
  (let [tree [:div {:id "source" :aria-label "Run the example"}
              [:script "console.log('never translate')"]
              [:pre [:code "(+ 40 2)"]]
              [:p {:lang "en" :translate "no"} "Upstream source records"]
              [:a {:href "/evidence.json"} "Inspect evidence"]]
        translated (locales/map-text tree #(str "translated: " %))]
    (is (= "translated: Run the example" (get-in translated [1 :aria-label])))
    (is (= (subvec tree 2 5) (subvec translated 2 5)))
    (is (= "/evidence.json" (get-in translated [5 1 :href])))
    (is (= "translated: Inspect evidence" (get-in translated [5 2])))
    (is (not (locales/translatable? "19.42 ms")))))

(deftest language-choice-is-not-rebased-to-current-language
  (let [tree [:nav {:translate "no"}
              [:a {:href "/" :hreflang "en" :aria-current "page"} "English"]
              [:a {:href "/ur/" :hreflang "ur"} "اردو"]]
        result (-> tree (locales/rebase-links #(str "/ur" %)) (locales/mark-language "ur"))]
    (is (= "/" (get-in result [2 1 :href])))
    (is (nil? (get-in result [2 1 :aria-current])))
    (is (= "page" (get-in result [3 1 :aria-current])))))

(let [result (run-tests)]
  (when (pos? (+ (:fail result) (:error result))) (js/process.exit 1)))
