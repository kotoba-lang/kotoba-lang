(ns murakumo.generation.image
  "Still images, on the OpenAI-images-compatible gate.

  This is a **different API from the rest of this library**, and the difference
  is the reason it gets its own namespace rather than a fifth `submit!` arm:

  | | `murakumo.generation` (video/voice/music/sfx) | here |
  |---|---|---|
  | host | `generation.murakumo.cloud` | `api.murakumo.cloud` |
  | auth | capability token, 401 without it | **none — the gate is open** |
  | shape | submit → poll → fetch artifact | one synchronous POST |
  | result | a file at a URL, `contentHash` | base64 in the response body |

  ## Do not proxy this through a Cloudflare Worker

  A render is a measured 60–100 s of synchronous GPU work, and Cloudflare
  terminates a non-streaming Worker subrequest at 100 s with an HTML 524. A
  proxy therefore converts *slow successes* into failures nobody can fix from
  the client side. `network-awai-apex` holds this rule explicitly: its Worker
  exists to hold the generation token, and it deliberately does not carry image
  traffic — the browser calls the gate directly. Anything reusing this
  namespace should do the same.

  Since the gate needs no token, calling it from a browser leaks nothing: there
  is no credential to hide. Rate limiting, if it is wanted, belongs at the gate
  — a check on the page in front of it protects nothing, because the gate is
  reachable without the page.

  ## Model ids are never written down

  `model-map-url` is a live ComfyUI `/object_info/CheckpointLoaderSimple` scan
  of each Mac mini, so it reports what is on disk on a node this minute. That
  is the only honest answer to \"which models can I pick?\", and the repo-wide
  rule (ADR-2607173100) is that a concrete model id must not be a hardcoded
  default. `fallback-models` exists solely so a picker renders something when
  the map is unreachable, and every entry it yields is marked `:fallback? true`
  so the UI can say so rather than imply it knows."
  (:require [clojure.string :as str]))

(def model-map-url "https://api.murakumo.cloud/infer/model-map")
(def image-url "https://api.murakumo.cloud/v1/images/generations")

(def image-kinds
  "`model-kind` values in the fleet map that mean 'text-to-image checkpoint'."
  #{"image"})

(def fallback-models
  "Only when the live map cannot be read. Sourced from the fleet registry, not
  invented — and marked, so a picker can admit it is guessing."
  [{:model-id "animagine-xl-4.0" :label "Animagine XL 4.0" :fallback? true}
   {:model-id "wai-illustrious-sdxl-v150" :label "WAI Illustrious SDXL v1.5" :fallback? true}])

(defn- humanize
  "`animagine-xl-4.0` → `Animagine XL 4.0`-ish. A label, not an identity: the
  wire value stays in `:model-id`.

  `v150` stays lowercase rather than becoming `V150` — a version is not a word,
  and capitalising it made `wai-illustrious-sdxl-v150` read as two acronyms."
  [id]
  (->> (str/split (str id) #"[-_]")
       (map (fn [part]
              (cond
                (re-matches #"(?i)xl|sdxl|svd|ltx|vae|ti2v" part) (str/upper-case part)
                (re-matches #"(?i)v[0-9].*" part) (str/lower-case part)
                (re-matches #"[0-9].*" part) part
                :else (str/capitalize part))))
       (str/join " ")))

(defn parse-model-map
  "Fleet map (already keywordized) → the image models a picker can offer.

  Folded per model, because the same checkpoint sits on several minis and
  choosing a model is not choosing a machine. `:queue` is summed and `:nodes`
  kept so a UI can say where the work would land.

  `:exact?` false means at least one node carries a checkpoint whose id
  upstream could not confirm (`match: \"family-guess\"`). Surfaced rather than
  smoothed over — it is how a \"registered as 0.9.1, actually 0.9.6\" drift
  stays visible.

  It is `every?` and not the first entry's match: with the same model on
  several minis, reading one arbitrary node's match reports `exact` while
  another node is a guess, which is precisely the drift the field exists to
  show. (The copy of this fold inside `network-awai-apex` reads the first
  entry; caught here by a test built from a live map where the two nodes
  disagree.)"
  [m]
  (->> (or (:media m) [])
       (filter #(contains? image-kinds (str (:model-kind %))))
       (group-by :model-id)
       (map (fn [[id entries]]
              {:model-id id
               :label (humanize id)
               :nodes (vec (sort (map :node entries)))
               :checkpoint (:checkpoint (first entries))
               :queue (reduce + 0 (keep :queue entries))
               :exact? (every? #(= "exact" (:match %)) entries)}))
       (sort-by :model-id)
       vec))

(defn models
  "Live models from a parsed map, or the marked fallback when there are none."
  [parsed]
  (if (seq parsed) parsed fallback-models))

(def sizes
  "What the gate accepts, as `WxH`. SDXL-shaped: the gate parses the string and
  defaults to 832x1216. Offered as a closed list rather than free numbers
  because an arbitrary WxH is how you construct a 400."
  [{:size "832x1216"  :w 832  :h 1216 :label "縦長 832×1216"}
   {:size "1024x1024" :w 1024 :h 1024 :label "正方形 1024×1024"}
   {:size "1216x832"  :w 1216 :h 832  :label "横長 1216×832"}
   {:size "768x768"   :w 768  :h 768  :label "小さめ 768×768"}])

(defn size-for-aspect
  "The offered size whose aspect is closest to `aspect` (width/height).

  A コマ is rarely square, and making someone translate a panel's shape into a
  pixel size by eye is work a program can do. Returns the `WxH` string; a nil
  or non-positive aspect falls back to the gate's own default shape."
  [aspect]
  (let [a (when (number? aspect) (double aspect))]
    (if (and a (pos? a))
      (:size (first (sort-by #(Math/abs (- a (/ (double (:w %)) (double (:h %))))) sizes)))
      "832x1216")))

(defn request
  "Body for `POST /v1/images/generations`.

  `model` goes on the wire as the bare id: the gate appends `.safetensors`
  itself, and sending the extension surfaced as a misleading 'node became
  unreachable mid-render'. An empty prompt is not sent as a request at all —
  see `blank-prompt?`."
  [{:keys [prompt model size negative seed]}]
  (cond-> {:prompt (str/trim (str prompt))
           :n 1
           :size (or size "832x1216")}
    (seq model) (assoc :model model)
    (seq negative) (assoc :negative negative)
    (some? seed) (assoc :seed seed)))

(defn blank-prompt? [prompt] (str/blank? (str prompt)))

(defn b64
  "The gate answers with base64, not a URL: `{:data [{:b64_json …}]}`."
  [resp]
  (some-> resp :data first :b64_json not-empty))

(defn error-message
  "A human-readable reason from a failed response, or nil.

  The gate reports its own outages in the body rather than only in the status —
  `{\"error\": \"no GATEWAY_URL configured\"}` with a 503 is what a fleet whose
  image gate is unconfigured returns, and showing that string is far more use
  than \"生成に失敗しました\"."
  [resp]
  (let [e (:error resp)]
    (cond
      (string? e) (not-empty e)
      (map? e) (not-empty (str (or (:message e) (:code e))))
      :else nil)))
