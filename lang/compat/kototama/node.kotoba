(ns kototama.node
  "Node entry — runs the SAME .cljc actor (compiled to JavaScript by
  ClojureScript) in a JS runtime, proving the cljs output executes (not just
  compiles). The browser build (kototama.browser) is byte-for-byte the same
  actor code; only the I/O shims differ (fs here, fetch+DOM there)."
  (:require [cljs.reader :as reader]
            ["fs" :as fs]
            [langgraph.graph :as g]
            [langchain.model :as lcm]
            [langchain.message :as msg]
            [kototama.unspsc.taxonomy :as tax]
            [kototama.unspsc.capability :as cap]
            [kototama.unspsc.react :as react]))

(defn- demo [taxon]
  (println (str "\n▶ actor " (:code taxon) " — " (:title taxon)))
  (println (str "  DID " (:did taxon)))
  (let [model  (lcm/mock-model
                [(msg/ai "" {:tool-calls [{:id "t1" :name "validate_line"
                                           :input {:line {:quantity 1 :unit "head"}}}]})
                 (msg/ai "Verdict: NOT-OK — missing animal_health_certificate.")])
        actor  (react/react-actor taxon model)
        result (g/invoke actor {:messages [(msg/user "validate: quantity 1 head")]} {})
        messages (:messages result)]
    (println (str "  ReAct loop: " (count messages) " messages, "
                  (count (filter #(= :assistant (:role %)) messages)) " assistant turns, "
                  "tool executed=" (boolean (some #(= :tool (:role %)) messages))))
    (println (str "  final: " (msg/text (msg/last-message messages))))
    (let [v (cap/run taxon {:quantity 1 :unit "head"})]
      (println (str "  capability verdict ok?=" (:ok v) " missing=" (vec (:missing v)))))))

(defn -main [& _]
  (println "kototama actor — running compiled ClojureScript in a JS runtime (Node)")
  (let [text (.readFileSync fs "public/taxonomy-sample.edn" "utf8")]
    (tax/set-table! (reader/read-string text))
    (println (str "taxonomy loaded: " (tax/count-codes) " codes"))
    (demo (tax/taxon "10101500"))   ; bespoke-derived (livestock)
    (demo (tax/taxon "11101503"))   ; Haiku-enriched (Barite)
    (println "\n✓ DONE — actors ran their ReAct loop as compiled cljs")))
