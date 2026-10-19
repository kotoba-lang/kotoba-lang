(ns anime.production
  "Work-agnostic anime production pipeline vocabulary — the craft library
  split out of gftdcojp's private ai-gftd-animeka actor (ADR-2607023000).

  The creative atom is the **cut** (a shot, time-axis), not the manga page.
  The entity hierarchy:

    work → episode → scene → cut → {storyboard layout conceptKeyframe
             keyframe inbetween colorTrace background composite soundCue}
    retake → targets a cut (or a cut child)
    character / colorModel / asset / project — reusable assets

  Nothing here knows about a specific studio, work, DID authority, or store:
  identity helpers are parameterized (pass your own DID / NSID prefix), and
  persistence stays with the consumer. gftd-specific wiring (did:web
  authority, XRPC server, store) lives in the private actor repo."
  (:require [clojure.string :as str]))

;; ───────────────────────── pipeline stages ─────────────────────────

(def stages
  "The 12 anime production stages a cut moves through. The pipeline is NOT
  serial — board columns run in parallel — so this is a set of trackable
  stages, not an ordered workflow."
  ["script" "storyboard" "layout" "keyAnim"
   "inbetween" "colorDesign" "finish" "background"
   "composite" "edit" "sound" "delivery"])

(def default-stage-status
  "Initial per-cut stage-status — every stage 'pending'."
  (into {} (map (fn [s] [s "pending"])) stages))

(defn derive-cut-priority
  "Derive a cut's overall priority from its per-stage status map: any stage in
  'retake' → \"retake\"; all 'approved' → \"approved\"; else \"normal\"."
  [stage-status]
  (let [vals (vals stage-status)]
    (cond
      (some #(= "retake" %) vals) "retake"
      (and (seq vals) (every? #(= "approved" %) vals)) "approved"
      :else "normal")))

;; ───────────────────────── layer vocabulary ─────────────────────────

(def layer-specs
  "The cut child layers a generation step can produce: record namespace,
  rkey prefix, the pipeline stage the layer advances, and the snake_case
  envelope keys used on the wire."
  {:storyboard {:ns "storyboard" :prefix "sb" :stage "storyboard" :id-key :storyboard_id :uri-key :storyboard_uri}
   :layout {:ns "layout" :prefix "ly" :stage "layout" :id-key :layout_id :uri-key :layout_uri}
   :concept-keyframe {:ns "conceptKeyframe" :prefix "ckf" :stage "keyAnim" :id-key :concept_keyframe_id :uri-key :concept_keyframe_uri}
   :keyframe {:ns "keyframe" :prefix "kf" :stage "keyAnim" :id-key :keyframe_id :uri-key :keyframe_uri}
   :inbetween {:ns "inbetween" :prefix "ib" :stage "inbetween" :id-key :inbetween_id :uri-key :inbetween_uri}
   :background {:ns "background" :prefix "bg" :stage "background" :id-key :background_id :uri-key :background_uri}
   :composite {:ns "composite" :prefix "cmp" :stage "composite" :id-key :composite_id :uri-key :composite_uri}
   :sound-cue {:ns "soundCue" :prefix "snd" :stage "sound" :id-key :sound_cue_id :uri-key :sound_cue_uri}})

;; ───────────────────────── schema ─────────────────────────

(def schema
  "DataScript-style schema (constraint attrs only — undeclared attrs are
  cardinality-one scalars). Only the unique :*/id attrs need declaring; the
  back-pointer ids (:cut/scene-id etc.) are plain scalar values so a child can
  be transacted before its parent exists."
  {:work/id    {:db/unique :db.unique/identity}
   :episode/id {:db/unique :db.unique/identity}
   :scene/id   {:db/unique :db.unique/identity}
   :cut/id     {:db/unique :db.unique/identity}
   :storyboard/id      {:db/unique :db.unique/identity}
   :layout/id          {:db/unique :db.unique/identity}
   :conceptKeyframe/id {:db/unique :db.unique/identity}
   :keyframe/id        {:db/unique :db.unique/identity}
   :inbetween/id       {:db/unique :db.unique/identity}
   :colorTrace/id      {:db/unique :db.unique/identity}
   :background/id      {:db/unique :db.unique/identity}
   :composite/id       {:db/unique :db.unique/identity}
   :soundCue/id        {:db/unique :db.unique/identity}
   :retake/id   {:db/unique :db.unique/identity}
   :character/id   {:db/unique :db.unique/identity}
   :colorModel/id  {:db/unique :db.unique/identity}
   :asset/id       {:db/unique :db.unique/identity}
   :project/id     {:db/unique :db.unique/identity}
   :chatMessage/id {:db/unique :db.unique/identity}})

;; ───────────────────────── identity helpers ─────────────────────────

(defn collection
  "The AT collection NSID for a record kind under an app's NSID prefix:
  (collection \"ai.gftd.apps.animeka\" \"cut\") → \"ai.gftd.apps.animeka.cut\"."
  [nsid-prefix kind]
  (str nsid-prefix "." kind))

(defn vertex-uri
  "Derived AT-URI for a record:
  at://<did>/<nsid-prefix>.<kind>/<rkey>. The stored form is the bare rkey;
  this is a derived view for the wire."
  [did nsid-prefix kind rkey]
  (str "at://" did "/" (collection nsid-prefix kind) "/" rkey))

(defn rkey-from-id
  "Accepts a bare rkey or a full at-uri, returns the bare rkey. Lets input
  reference an entity by either form."
  [v]
  (let [s (str v)]
    (if (str/starts-with? s "at://")
      (last (str/split (str/replace s #"/+$" "") #"/"))
      s)))

(defn slug
  "Lowercase, hyphenate non-alphanumeric runs, trim edge hyphens — the
  canonical id/slug derivation (\"Spring Storm 2\" → \"spring-storm-2\")."
  [s]
  (-> (str s) str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"(^-|-$)" "")))
