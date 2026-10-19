(ns bpmn.xml
  "BPMN 2.0 XML ⇄ EDN model, with zero third-party deps and portable .cljc.

  Two layers:

  1. Model ⇄ *neutral elements* — `to-elements` / `from-elements`. A neutral
     element is the plain map `{:tag \"process\" :attrs {\"id\" \"…\"} :content […]}`
     with *string* local-name tags (XML-namespace prefixes stripped). A host may
     skip the built-in parser and feed `from-elements` whatever its platform XML
     library produced (e.g. clojure.data.xml on the JVM, DOMParser on the web),
     after normalising it to this shape.

  2. String ⇄ model — `parse-str` / `emit-str`. A *minimal* reader/emitter for the
     well-formed BPMN-2.0 subset machine tools emit: the XML declaration, comments,
     elements, single/double-quoted attributes, self-closing tags, text content and
     the five predefined entities. It is NOT a general XML parser — no DTD/CDATA/PI,
     and attribute values must escape `>` as `&gt;`. For anything exotic, use layer 1
     with a real parser."
  (:require [kotoba.lang.text :as str]))

;; --- element-name ⇄ keyword type maps ---

(def ^:private xml->type
  {"startEvent" :start-event "endEvent" :end-event
   "intermediateCatchEvent" :intermediate-catch-event
   "intermediateThrowEvent" :intermediate-throw-event "boundaryEvent" :boundary-event
   "task" :task "userTask" :user-task "serviceTask" :service-task
   "scriptTask" :script-task "manualTask" :manual-task
   "businessRuleTask" :business-rule-task "sendTask" :send-task
   "receiveTask" :receive-task "subProcess" :sub-process "callActivity" :call-activity
   "exclusiveGateway" :exclusive-gateway "parallelGateway" :parallel-gateway
   "inclusiveGateway" :inclusive-gateway "eventBasedGateway" :event-based-gateway
   "complexGateway" :complex-gateway})

(def ^:private type->xml (into {} (map (fn [[k v]] [v k]) xml->type)))

;; --- entity (de/en)coding ---

(defn- decode [s]
  (-> s
      (str/replace "&lt;" "<")   (str/replace "&gt;" ">")
      (str/replace "&quot;" "\"") (str/replace "&apos;" "'")
      (str/replace "&amp;" "&")))            ; ampersand last

(defn- encode [s]
  (-> (str s)
      (str/replace "&" "&amp;")              ; ampersand first
      (str/replace "<" "&lt;") (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

;; --- neutral-element helpers ---

(defn- strip-ns [s]
  (let [i (str/index-of s ":")] (if i (subs s (inc i)) s)))

(defn- children [el tag] (filter #(and (map? %) (= tag (:tag %))) (:content el)))
(defn- child    [el tag] (first (children el tag)))
(defn- text-of  [el] (some-> (str/join (filter string? (:content el))) str/trim not-empty))

;; --- minimal reader: XML string → neutral element tree ---

(def ^:private attr-re #"([\w:.\-]+)\s*=\s*(?:\"([^\"]*)\"|'([^']*)')")

(defn- parse-attrs [s]
  (reduce (fn [m [_ k v1 v2]] (assoc m (strip-ns k) (decode (or v1 v2))))
          {} (re-seq attr-re s)))

(defn parse-elements
  "Minimal reader: well-formed BPMN-2.0 XML string → neutral element tree (or nil)."
  [xml]
  (loop [i 0 stack [] root nil]
    (let [lt (str/index-of xml "<" i)]
      (if (nil? lt)
        root
        (let [text  (subs xml i lt)
              stack (if (and (seq stack) (seq (str/trim text)))
                      (update-in stack [(dec (count stack)) :content]
                                 conj (str/trim (decode text)))
                      stack)
              head  (subs xml lt (min (count xml) (+ lt 4)))]
          (cond
            (str/starts-with? head "<!--")
            (recur (+ (str/index-of xml "-->" lt) 3) stack root)

            (str/starts-with? head "<?")
            (recur (+ (str/index-of xml "?>" lt) 2) stack root)

            (str/starts-with? head "</")
            (let [gt (str/index-of xml ">" lt)
                  done (peek stack) stack' (pop stack)]
              (if (seq stack')
                (recur (inc gt)
                       (update-in stack' [(dec (count stack')) :content] conj done)
                       root)
                (recur (inc gt) stack' done)))

            :else
            (let [gt    (str/index-of xml ">" lt)
                  inner (subs xml (inc lt) gt)
                  self? (str/ends-with? inner "/")
                  inner (if self? (subs inner 0 (dec (count inner))) inner)
                  [_ nm at] (re-find #"^([\w:.\-]+)\s*([\s\S]*)$" inner)
                  el    {:tag (strip-ns nm) :attrs (parse-attrs at) :content []}]
              (if self?
                (if (seq stack)
                  (recur (inc gt)
                         (update-in stack [(dec (count stack)) :content] conj el) root)
                  (recur (inc gt) stack el))
                (recur (inc gt) (conj stack el) root)))))))))

;; --- neutral element → model ---

(defn- node-from-element [el]
  (cond-> {:bpmn/id (get-in el [:attrs "id"]) :bpmn/type (xml->type (:tag el))}
    (get-in el [:attrs "name"])    (assoc :bpmn/name (get-in el [:attrs "name"]))
    (get-in el [:attrs "default"]) (assoc :bpmn/default (get-in el [:attrs "default"]))))

(defn- flow-from-element [el]
  (cond-> {:bpmn/id (get-in el [:attrs "id"]) :bpmn/type :sequence-flow
           :bpmn/source (get-in el [:attrs "sourceRef"])
           :bpmn/target (get-in el [:attrs "targetRef"])}
    (get-in el [:attrs "name"])   (assoc :bpmn/name (get-in el [:attrs "name"]))
    (child el "conditionExpression")
    (assoc :bpmn/condition (text-of (child el "conditionExpression")))))

(defn- process-from-element [pel]
  (reduce
   (fn [m el]
     (let [tag (:tag el) id (get-in el [:attrs "id"])]
       (cond
         (= tag "sequenceFlow") (assoc-in m [:bpmn/flows id] (flow-from-element el))
         (xml->type tag)        (assoc-in m [:bpmn/nodes id] (node-from-element el))
         :else m)))                                  ; ignore DI, extensions, …
   (cond-> {:bpmn/id (get-in pel [:attrs "id"]) :bpmn/type :process
            :bpmn/executable (= "true" (get-in pel [:attrs "isExecutable"]))
            :bpmn/nodes {} :bpmn/flows {}}
     (get-in pel [:attrs "name"]) (assoc :bpmn/name (get-in pel [:attrs "name"])))
   (:content pel)))

(defn from-elements
  "Neutral element tree (a <definitions> or a bare <process>) → BPMN-as-EDN model."
  [root]
  (let [pel (if (= "process" (:tag root)) root (child root "process"))]
    (when pel (process-from-element pel))))

;; --- model → neutral element → string ---

(defn- m-vals [model k] (vals (get model k)))

(defn to-elements
  "BPMN-as-EDN model → a neutral <definitions> element tree."
  [model]
  (let [node-els (for [n (sort-by :bpmn/id (m-vals model :bpmn/nodes))]
                   {:tag (type->xml (:bpmn/type n))
                    :attrs (cond-> {"id" (:bpmn/id n)}
                             (:bpmn/name n)    (assoc "name" (:bpmn/name n))
                             (:bpmn/default n) (assoc "default" (:bpmn/default n)))
                    :content []})
        flow-els (for [f (sort-by :bpmn/id (m-vals model :bpmn/flows))]
                   {:tag "sequenceFlow"
                    :attrs (cond-> {"id" (:bpmn/id f)
                                    "sourceRef" (:bpmn/source f)
                                    "targetRef" (:bpmn/target f)}
                             (:bpmn/name f) (assoc "name" (:bpmn/name f)))
                    :content (if (:bpmn/condition f)
                               [{:tag "conditionExpression"
                                 :attrs {"xsi:type" "tFormalExpression"}
                                 :content [(:bpmn/condition f)]}]
                               [])})]
    {:tag "definitions"
     :attrs {"xmlns" "http://www.omg.org/spec/BPMN/20100524/MODEL"
             "xmlns:xsi" "http://www.w3.org/2001/XMLSchema-instance"
             "targetNamespace" "http://bpmn.io/schema/bpmn"}
     :content [{:tag "process"
                :attrs (cond-> {"id" (:bpmn/id model)
                                "isExecutable" (str (boolean (:bpmn/executable model)))}
                         (:bpmn/name model) (assoc "name" (:bpmn/name model)))
                :content (concat node-els flow-els)}]}))

(defn- emit-attrs [attrs]
  (let [rank {"id" 0 "name" 1}
        ks (sort-by #(get rank % 2) (keys attrs))]
    (apply str (for [k ks] (str " " k "=\"" (encode (get attrs k)) "\"")))))

(defn- emit-el [el depth]
  (let [pad (apply str (repeat depth "  "))]
    (if (map? el)
      (let [{:keys [tag attrs content]} el
            content (remove nil? content)]
        (if (empty? content)
          (str pad "<" tag (emit-attrs attrs) "/>\n")
          (str pad "<" tag (emit-attrs attrs) ">\n"
               (apply str (map #(emit-el % (inc depth)) content))
               pad "</" tag ">\n")))
      (str pad (encode el) "\n"))))

(defn emit-str
  "BPMN-as-EDN model → a BPMN 2.0 XML string."
  [model]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (emit-el (to-elements model) 0)))

(defn parse-str
  "BPMN 2.0 XML string → BPMN-as-EDN model (via the built-in minimal reader)."
  [xml]
  (from-elements (parse-elements xml)))
