(ns tamaki.observer.app
  (:require ["three" :as THREE]
            ["three/addons/controls/OrbitControls.js" :refer [OrbitControls]]
            [clojure.string :as str]
            [datascript.core :as d]
            [jp-go-dds.core :as dds]
            [re-frame.core :as rf]
            [reagent.dom :as rdom]
            [tamaki.observer.style :as style]
            [org-threejs.core :as three]))

(defonce runtime (atom nil))
(defonce audio-runtime (atom {:enabled? false :step 0}))
(defonce topology-signature (atom nil))
(defonce issue-node-positions (atom {}))
(def topology-schema
  {:repo/path {:db/unique :db.unique/identity}
   :agent/id {:db/unique :db.unique/identity}
   :loop/id {:db/unique :db.unique/identity}})
(defonce topology-db (d/create-conn topology-schema))
(defonce snapshot-repos (atom []))

(defn label [value fallback]
  (cond
    (keyword? value) (name value)
    (string? value) value
    :else fallback))

(rf/reg-event-db :snapshot
  (fn [db [_ snapshot]] (assoc db :snapshot snapshot)))
(rf/reg-event-db :select-repo
  (fn [db [_ repo]] (assoc db :selected repo)))
(rf/reg-event-db :group-mode
  (fn [db [_ mode]] (assoc db :group-mode mode)))
(rf/reg-event-db :organism-scope
  (fn [db [_ scope]] (assoc db :organism-scope scope :selected nil)))
(rf/reg-event-db :activity-agent
  (fn [db [_ agent-id]] (assoc db :activity-agent agent-id)))
(rf/reg-event-db :surface-view
  (fn [db [_ view]] (assoc db :surface-view view)))
(rf/reg-event-db :work-lens
  (fn [db [_ lens]] (assoc db :work-lens lens)))
(rf/reg-sub :snapshot (fn [db _] (:snapshot db)))
(rf/reg-sub :selected (fn [db _] (:selected db)))
(rf/reg-sub :group-mode (fn [db _] (or (:group-mode db) :org)))
(rf/reg-sub :organism-scope
  (fn [db _] (or (:organism-scope db) :federation)))
(rf/reg-sub :activity-agent
  (fn [db _] (or (:activity-agent db) "all")))
(rf/reg-sub :surface-view
  (fn [db _] (or (:surface-view db) :now)))
(rf/reg-sub :work-lens
  (fn [db _] (or (:work-lens db) :organism)))

(defn index-datoms! [snapshot]
  (d/reset-conn! topology-db (d/empty-db topology-schema))
  (let [stats (into {} (map (juxt :path identity)) (:repo-stats snapshot))]
    (reset! snapshot-repos
            (mapv (fn [repo]
                    (merge repo
                           (select-keys (get stats (:path repo))
                                        [:issues-open :patches-open :wip])))
                  (:repos snapshot)))
    (d/transact!
     topology-db
     (concat
      (map-indexed
       (fn [index repo]
         (let [stat (get stats (:path repo))]
           {:db/id (- (inc index))
            :repo/path (:path repo) :repo/name (:name repo)
            :repo/org (:remote repo) :repo/sync (or (:sync repo) "unmanaged")
            :repo/issues (or (:issues-open stat) 0)
            :repo/patches (or (:patches-open stat) 0)
            :repo/wip (or (:wip stat) 0)}))
       (:repos snapshot))
      (map-indexed
       (fn [index {:keys [from to]}]
         {:db/id (- (+ 1000001 index)) :dep/from from :dep/to to})
       (:dependencies snapshot))
      (keep-indexed (fn [index agent]
              (let [id (or (:agent.run/id agent) (:id agent))
                    project (or (:agent.run/source-project agent)
                                (:source-project agent)
                                (:agent.run/project agent) (:project agent))]
                (when (and id project)
                  {:db/id (- (+ 2000001 index))
                   :agent/id id :agent/project project
                   :agent/model (or (:agent.run/model agent) (:model agent) "default")
                   :agent/runner (or (:agent.run/runner agent) (:runner agent) "default")
                   :agent/status (label (or (:agent.run/status agent)
                                            (:status agent)) "unknown")
                   :agent/goal (or (:agent.run/goal agent) (:goal agent) "")
                   :agent/issue (or (:issue agent) "")})))
            (:agents snapshot))
      (keep-indexed (fn [index loop]
              (let [id (or (:tamaki.loop/id loop) (:id loop))
                    project (or (:tamaki.loop/project loop) (:project loop))]
                (when (and id project)
                  {:db/id (- (+ 3000001 index))
                   :loop/id id :loop/project project
                   :loop/runner (or (:tamaki.loop/runner loop) (:runner loop) "default")
                   :loop/model (or (:tamaki.loop/model loop) (:model loop) "default")
                   :loop/status (label (or (:tamaki.loop/status loop)
                                          (:status loop)) "unknown")})))
            (:loops snapshot))))))

(defn queried-repos []
  (->> (d/q '[:find ?path ?name ?org ?sync ?issues ?patches ?wip
              :where
              [?e :repo/path ?path] [?e :repo/name ?name]
              [?e :repo/org ?org] [?e :repo/sync ?sync]
              [?e :repo/issues ?issues] [?e :repo/patches ?patches]
              [?e :repo/wip ?wip]]
            @topology-db)
       (mapv (fn [[path name org sync issues patches wip]]
               {:path path :name name :remote org :sync sync
                :issues-open issues :patches-open patches :wip wip}))))

(defn visible-repos []
  (let [queried (queried-repos)]
    (if (= (count queried) (count @snapshot-repos))
      queried
      @snapshot-repos)))

(def organism-remote
  {:com-junkawasaki "com-junkawasaki"
   :etzhayyim "com-etzhayyim"
   :cloud-itonami "cloud-itonami"
   :jk-luxury "jk-luxury"
   :gftdcojp "gftdcojp"})

(def organism-project-markers
  {:com-junkawasaki ["orgs/com-junkawasaki/" "com-junkawasaki/"
                     "orgs/kotoba-lang/" "kotoba-lang/"]
   :etzhayyim ["orgs/etzhayyim/" "orgs/com-etzhayyim/" "etzhayyim/"]
   :cloud-itonami ["orgs/cloud-itonami/" "cloud-itonami/"]
   :jk-luxury ["orgs/jk-luxury/" "jk-luxury/"]
   :gftdcojp ["orgs/gftdcojp/" "gftdcojp/"]})

(defn project-in-scope? [scope project]
  (or (= scope :federation)
      (some #(str/includes? (str project) %)
            (get organism-project-markers scope []))))

(defn scoped-repos [scope]
  (let [repos (visible-repos)]
    (if (= scope :federation)
      repos
      (let [remote (get organism-remote scope)]
        (filterv #(or (= remote (:remote %))
                      (str/includes? (str (:path %)) remote))
                 repos)))))

(defn queried-agents []
  (mapv (fn [[id project model runner status issue goal]]
          {:agent.run/id id :agent.run/project project :agent.run/model model
           :agent.run/runner runner :agent.run/status status
           :agent.run/goal goal :issue issue})
        (d/q '[:find ?id ?project ?model ?runner ?status ?issue ?goal
               :where [?e :agent/id ?id] [?e :agent/project ?project]
               [?e :agent/model ?model] [?e :agent/runner ?runner]
               [?e :agent/status ?status]
               [?e :agent/issue ?issue] [?e :agent/goal ?goal]]
             @topology-db)))

(defn queried-loops []
  (mapv (fn [[id project status runner model]]
          {:tamaki.loop/id id :tamaki.loop/project project
           :tamaki.loop/status status :tamaki.loop/runner runner
           :tamaki.loop/model model})
        (d/q '[:find ?id ?project ?status ?runner ?model
               :where [?e :loop/id ?id] [?e :loop/project ?project]
               [?e :loop/status ?status] [?e :loop/runner ?runner]
               [?e :loop/model ?model]]
             @topology-db)))

(def colors
  {:active 0x35ff8a :different 0xff922e :synced 0x208cff
   :remote 0x76658f :platform 0x28143c :edge 0x48dcff})

(defn material [kind]
  (three/standard-material
   {:color (get colors kind) :metallic 0.32 :roughness 0.38
    :emissive (if (= kind :active) 1.8 0.12)
    :transparent true :opacity (if (= kind :remote) 0.58 1)}))

(def garden-colors
  {:soil 0x101a18 :soil-edge 0x274438 :water 0x43e8d3
   :root 0x9b6a32 :leaf 0x55df83 :leaf-dark 0x1d7c4c
   :lineage 0xf0c866 :withered 0x8b6337})

(defn garden-material [color emissive]
  (three/standard-material
   {:color color :emissive emissive :metallic 0.08 :roughness 0.78}))

(declare text-sprite result-colors)

(defn branch-curve [from to bend]
  (THREE/CatmullRomCurve3.
   #js [from
        (THREE/Vector3.
         (+ (.-x from) (* (- (.-x to) (.-x from)) 0.42) bend)
         (+ (.-y from) (* (- (.-y to) (.-y from)) 0.46))
         (+ (.-z from) (* (- (.-z to) (.-z from)) 0.42) (* bend 0.35)))
        to]))

(defn hex-unit [value offset]
  (let [text (str value)
        pair (subs text (min offset (max 0 (- (count text) 2)))
                   (min (+ offset 2) (count text)))]
    (/ (or (js/parseInt pair 16) 0) 255)))

(defn git-commit-position [index total sha]
  (let [progress (/ index (max 1 (dec total)))
        spread (+ 0.12 (* 1.7 (js/Math.sin (* progress js/Math.PI))))]
    (THREE/Vector3.
     (* spread (- (* 2 (hex-unit sha 0)) 1))
     (+ 0.55 (* progress 7.4))
     (* spread (- (* 2 (hex-unit sha 4)) 1)))))

(defn add-exact-git-dag! [tree git-tree animations]
  (let [commits (vec (reverse (:commits git-tree)))
        total (count commits)
        positions (into {}
                        (map-indexed
                         (fn [index commit]
                           [(:sha commit)
                            (git-commit-position index total (:sha commit))])
                         commits))
        node-geometry (THREE/SphereGeometry. 0.065 7 5)
        nodes (THREE/InstancedMesh.
               node-geometry
               (THREE/MeshBasicMaterial.
                #js {:color 0xffdf79 :transparent true :opacity 0.9})
               total)
        matrix (THREE/Matrix4.)
        edge-values
        (into-array
         (mapcat (fn [{:keys [sha parents]}]
                   (let [child (get positions sha)]
                     (mapcat (fn [parent]
                               (when-let [p (get positions parent)]
                                 [(.-x child) (.-y child) (.-z child)
                                  (.-x p) (.-y p) (.-z p)]))
                             parents)))
                 commits))
        edge-geometry (THREE/BufferGeometry.)
        edges (THREE/LineSegments.
               edge-geometry
               (THREE/LineBasicMaterial.
                #js {:color 0xd58a38 :transparent true :opacity 0.34}))]
    (doseq [[index {:keys [sha]}] (map-indexed vector commits)]
      (.setPosition matrix (get positions sha))
      (.setMatrixAt nodes index matrix))
    (set! (.. nodes -instanceMatrix -needsUpdate) true)
    (.setAttribute edge-geometry "position"
                   (THREE/Float32BufferAttribute. edge-values 3))
    (.add tree edges)
    (.add tree nodes)
    (when-let [head-position (get positions (:head git-tree))]
      (let [head (THREE/Mesh.
                  (THREE/SphereGeometry. 0.18 14 9)
                  (THREE/MeshBasicMaterial.
                   #js {:color 0x8dffb0 :transparent true :opacity 1}))]
        (.copy (.-position head) head-position)
        (.add tree head)
        (swap! animations conj {:object head :phase 0 :base-scale 1.25
                                :kind :fruit})))
    positions))

(defn add-exact-file-canopy! [tree git-tree]
  (let [entries (:files git-tree)
        position
        (fn [{:keys [path type]}]
          (let [depth (count (clojure.string/split (or path "") #"/"))
                angle (* 6.28318 (hex-unit path 0))
                radius (+ 2.4 (* 0.3 depth)
                          (if (= type "blob") (* 0.65 (hex-unit path 4)) 0))]
            (THREE/Vector3.
             (* radius (js/Math.cos angle))
             (+ 7.5 (* 0.32 depth) (* 0.25 (hex-unit path 6)))
             (* radius (js/Math.sin angle)))))
        positions (into {nil (THREE/Vector3. 0 7.25 0)}
                        (map (juxt :path position) entries))
        vertices (into-array
                  (mapcat (fn [entry]
                            (let [point (get positions (:path entry))]
                              [(.-x point) (.-y point) (.-z point)]))
                          entries))
        link-values
        (into-array
         (mapcat
          (fn [entry]
            (let [child (get positions (:path entry))
                  parent (get positions (:parent entry)
                              (get positions nil))]
              [(.-x child) (.-y child) (.-z child)
               (.-x parent) (.-y parent) (.-z parent)]))
          entries))
        point-geometry (THREE/BufferGeometry.)
        link-geometry (THREE/BufferGeometry.)
        points (THREE/Points.
                point-geometry
                (THREE/PointsMaterial.
                 #js {:color 0x62f99a :size 0.11 :sizeAttenuation true
                      :transparent true :opacity 0.8}))
        links (THREE/LineSegments.
               link-geometry
               (THREE/LineBasicMaterial.
                #js {:color 0x2c9d61 :transparent true :opacity 0.18}))]
    (.setAttribute point-geometry "position"
                   (THREE/Float32BufferAttribute. vertices 3))
    (.setAttribute link-geometry "position"
                   (THREE/Float32BufferAttribute. link-values 3))
    (.add tree links)
    (.add tree points)))

(defn add-life-tree! [root snapshot]
  (let [tree (three/group)
        git-tree (first (:git-trees snapshot))
        branch-refs (vec (filter #(= "branch" (:kind %)) (:refs git-tree)))
        pruning-names (set (map :name (:pruning-candidates git-tree)))
        branch-material (THREE/MeshStandardMaterial.
                         #js {:color 0x704622 :emissive 0x2c1808
                              :emissiveIntensity 0.35 :roughness 0.82})
        twig-material (THREE/MeshStandardMaterial.
                       #js {:color 0xa16a32 :emissive 0x46230d
                            :emissiveIntensity 0.5 :roughness 0.7})
        leaf-material (THREE/MeshPhysicalMaterial.
                       #js {:color 0x39d878 :emissive 0x0c6f37
                            :emissiveIntensity 0.85 :roughness 0.38
                            :transmission 0.12 :transparent true :opacity 0.9})
        commit-material (THREE/MeshBasicMaterial.
                         #js {:color 0xffdc75 :transparent true :opacity 0.95})
        recent-activity (count (take 24 (:activity snapshot)))
        working (count (filter #(contains? #{"running" "working" "started"}
                                           (label (or (:agent.run/status %)
                                                      (:status %)) ""))
                               (:agents snapshot)))
        vitality (+ 0.7 (* 0.05 recent-activity) (* 0.18 working))
        lineage-count (max 3 (min 18 (or (some->> (:refs git-tree)
                                                  (filter #(= "branch" (:kind %)))
                                                  count)
                                         (count (:results snapshot)))))
        ring (THREE/Mesh.
              (THREE/TorusGeometry. 5.15 0.075 10 96)
              (THREE/MeshBasicMaterial.
               #js {:color (:lineage garden-colors)
                    :transparent true :opacity 0.56}))
        title (text-sprite
               (if git-tree
                 (str "GIT LIVING TREE · " (count (:commits git-tree))
                      " COMMITS · " (count (:refs git-tree))
                      " REFS · " (count (:files git-tree)) " OBJECTS")
                 (str "TAMAKI · GIT LIVING TREE · "
                      lineage-count " ACTIVE LINEAGES"))
               "#8dffb0")
        heart-light (THREE/PointLight. 0x55ff9a 4.2 18 1.6)
        crown-light (THREE/PointLight. 0xffd76a 2.4 13 1.8)
        animations (atom [])
        tips (atom [])
        main-points (mapv (fn [i]
                            (THREE/Vector3.
                             (* 0.18 (js/Math.sin (* i 0.82)))
                             (* i 1.05)
                             (* 0.12 (js/Math.cos (* i 0.67)))))
                          (range 8))]
    ;; main is the persistent trunk; luminous commit nodes climb it.
    (doseq [[index [from to]] (map-indexed vector (partition 2 1 main-points))
            :let [curve (branch-curve from to (* 0.08 (js/Math.sin index)))
                  radius (- 0.72 (* index 0.065))
                  segment (THREE/Mesh.
                           (THREE/TubeGeometry. curve 12 radius 10 false)
                           branch-material)
                  commit (THREE/Mesh.
                          (THREE/SphereGeometry. (+ 0.12 (* 0.015 index)) 12 8)
                          commit-material)]]
      (.copy (.-position commit) to)
      (.add tree segment)
      (.add tree commit))
    (when git-tree
      (add-exact-git-dag! tree git-tree animations)
      (add-exact-file-canopy! tree git-tree))
    ;; Each recent issue/source/patch lineage becomes a fork from main.
    (doseq [branch-index (range lineage-count)
            :let [side (if (even? branch-index) 1 -1)
                  trunk-index (+ 2 (mod branch-index 5))
                  origin (nth main-points trunk-index)
                  azimuth (+ (* branch-index 2.399963) (* side 0.42))
                  length (+ 2.8 (* 0.24 (mod branch-index 4)))
                  fork (THREE/Vector3.
                        (+ (.-x origin) (* length (js/Math.cos azimuth)))
                        (+ (.-y origin) 1.25 (* 0.18 (mod branch-index 3)))
                        (+ (.-z origin) (* length (js/Math.sin azimuth))))
                  tip (THREE/Vector3.
                       (+ (.-x fork) (* 1.35 (js/Math.cos (+ azimuth 0.35))))
                       (+ (.-y fork) 1.2)
                       (+ (.-z fork) (* 1.35 (js/Math.sin (+ azimuth 0.35)))))
                  fork-curve (branch-curve origin fork (* side 0.35))
                  tip-curve (branch-curve fork tip (* side -0.18))
                  fork-mesh (THREE/Mesh.
                             (THREE/TubeGeometry. fork-curve 16 0.16 7 false)
                             twig-material)
                  tip-mesh (THREE/Mesh.
                            (THREE/TubeGeometry. tip-curve 12 0.085 7 false)
                            twig-material)
                  branch-ref (when (seq branch-refs)
                               (nth branch-refs
                                    (mod branch-index (count branch-refs))))
                  prune? (contains? pruning-names (:name branch-ref))
                  cut-ring (when prune?
                             (THREE/Mesh.
                              (THREE/TorusGeometry. 0.27 0.055 8 28)
                              (THREE/MeshBasicMaterial.
                               #js {:color 0xff9b56 :transparent true
                                    :opacity 0.92})))
                  fruit-kind (keyword (name (or (when (seq (:results snapshot))
                                                  (-> snapshot :results
                                                      (nth (mod branch-index
                                                                (count (:results snapshot)))
                                                           nil)
                                                      :nodes last :type))
                                               :source)))
                  fruit-color (get result-colors fruit-kind 0xffdc75)
                  fruit (THREE/Mesh.
                         (THREE/IcosahedronGeometry. 0.22 1)
                         (THREE/MeshBasicMaterial.
                          #js {:color fruit-color :transparent true
                               :opacity 0.92}))]]
      (.copy (.-position fruit) tip)
      (.add tree fork-mesh)
      (.add tree tip-mesh)
      (.add tree fruit)
      (when cut-ring
        (.copy (.-position cut-ring) fork)
        (.lookAt cut-ring origin)
        (.add tree cut-ring)
        (swap! animations conj {:object cut-ring
                                :phase (* branch-index 0.41)
                                :kind :prune}))
      (swap! tips conj tip)
      (swap! animations conj {:object fruit :phase (* branch-index 0.73)
                              :base-scale (+ 0.82 (* vitality 0.12))
                              :kind :fruit}))
    ;; A translucent living canopy: hundreds of leaves are cheap instanced
    ;; geometry, while their clusters breathe independently.
    (doseq [[cluster-index tip] (map-indexed vector @tips)
            :let [cluster (three/group)]]
      (doseq [leaf-index (range 13)
              :let [angle (* leaf-index 2.399963)
                    radius (+ 0.35 (* 0.1 (mod leaf-index 5)))
                    leaf (THREE/Mesh.
                          (THREE/IcosahedronGeometry.
                           (+ 0.22 (* 0.025 (mod leaf-index 3))) 1)
                          leaf-material)]]
        (three/set-position!
         leaf
         (+ (.-x tip) (* radius (js/Math.cos angle)))
         (+ (.-y tip) (* 0.18 (- (mod leaf-index 5) 2)))
         (+ (.-z tip) (* radius (js/Math.sin angle))))
        (three/set-scale! leaf 1.5 0.72 1)
        (.add cluster leaf))
      (.add tree cluster)
      (swap! animations conj {:object cluster :phase (* cluster-index 0.51)
                              :base-y (.-y (.-position cluster))
                              :kind :canopy}))
    ;; Agent activity travels upward as sap. More live work means faster light.
    (doseq [index (range (max 5 (min 14 (+ 4 working))))
            :let [curve (branch-curve (first main-points)
                                     (nth main-points (inc (mod index 7)))
                                     (* 0.12 (js/Math.sin index)))
                  sap (THREE/Mesh.
                       (THREE/SphereGeometry. 0.095 10 7)
                       (THREE/MeshBasicMaterial.
                        #js {:color 0x8dffb0 :transparent true :opacity 0.95}))]]
      (.add tree sap)
      (swap! animations conj {:object sap :curve curve
                              :phase (/ index 14)
                              :speed (+ 0.055 (* vitality 0.018))
                              :kind :sap}))
    (three/set-rotation-x! ring (/ js/Math.PI 2))
    (three/set-position! ring 0 0.12 0)
    (three/set-position! title 0 10.2 0)
    (three/set-scale! title 8.2 1.25 1)
    (three/set-position! heart-light 0 4.4 0)
    (three/set-position! crown-light 0 7.3 0)
    (.add tree ring)
    (.add tree title)
    (.add tree heart-light)
    (.add tree crown-light)
    (three/set-scale! tree 1.24 1.24 1.24)
    (.add root tree)
    (swap! runtime assoc :tree-animations @animations
           :tree-vitality vitality)
    tree))

(defn add-successor! [root]
  (let [island (three/mesh (THREE/CylinderGeometry. 2.1 2.5 0.7 24)
                           (garden-material (:soil garden-colors) 0.05))
        stem (three/mesh (THREE/CylinderGeometry. 0.1 0.16 1.5 8)
                         (garden-material 0x77502c 0.05))
        left (three/mesh (THREE/SphereGeometry. 0.42 12 8)
                         (garden-material 0x77f09a 0.8))
        right (three/mesh (THREE/SphereGeometry. 0.38 12 8)
                          (garden-material 0x64d989 0.7))
        dome (THREE/Mesh.
              (THREE/SphereGeometry. 1.35 24 14 0 (* 2 js/Math.PI)
                                    0 (/ js/Math.PI 2))
              (THREE/MeshPhysicalMaterial.
               #js {:color 0xb8fff0 :transparent true :opacity 0.18
                    :roughness 0.1 :metalness 0.0}))
        label (text-sprite "SUCCESSOR · PROTECTED SAPLING" "#f0c866")]
    (three/set-position! island 10 0.1 11)
    (three/set-position! stem 10 1.15 11)
    (three/set-position! left 9.72 1.85 11)
    (three/set-position! right 10.3 1.7 11)
    (three/set-position! dome 10 0.45 11)
    (three/set-position! label 10 3.2 11)
    (three/set-scale! label 4.8 0.85 1)
    (doseq [object [island stem left right dome label]] (.add root object))))

(defn island-origin [index]
  (let [angle (* index 2.399963229728653)
        radius (+ 10 (* 3.4 (js/Math.sqrt index)))]
    {:x (* radius (js/Math.cos angle))
     :z (* radius (js/Math.sin angle))}))

(defn status [repo active-paths]
  (cond
    (contains? active-paths (:path repo)) :active
    (= "different" (:sync repo)) :different
    (= "synced" (:sync repo)) :synced
    :else :remote))

(defn group-key [mode repo]
  (case mode
    :project (let [parts (str/split (:path repo) #"/")]
               (if (= "projects" (first parts))
                 (str "project/" (second parts))
                 (str (first parts) "/" (second parts))))
    :sync (str "sync/" (:sync repo))
    (:remote repo)))

(defn dependency-ranks [dependencies]
  (loop [ranks {} remaining dependencies pass 0]
    (if (or (empty? remaining) (> pass 100))
      ranks
      (let [next-ranks
            (reduce (fn [result {:keys [from to]}]
                      (assoc result from
                             (max (get result from 0)
                                  (inc (get result to 0)))))
                    ranks remaining)]
        (if (= ranks next-ranks)
          ranks
          (recur next-ranks remaining (inc pass)))))))

(defn clear! [group]
  (doseq [child (array-seq (.-children group))]
    (.remove group child)
    (three/dispose-object! child)))

(defn add-dependency-lines! [root positions dependencies]
  (doseq [{:keys [from to]} dependencies
          :let [a (get positions from) b (get positions to)]
          :when (and a b)]
    (let [start (THREE/Vector3. (:x a) 0.12 (:z a))
          end (THREE/Vector3. (:x b) 0.12 (:z b))
          middle (THREE/Vector3. (/ (+ (:x a) (:x b)) 2)
                                 0.18
                                 (/ (+ (:z a) (:z b)) 2))
          curve (THREE/QuadraticBezierCurve3. start middle end)
          geometry (THREE/TubeGeometry. curve 10 0.025 5 false)
          root-path (THREE/Mesh.
                     geometry
                     (THREE/MeshBasicMaterial.
                      #js {:color (:root garden-colors)
                           :transparent true :opacity 0.34}))]
      (.add root root-path))))

(declare text-sprite)

(defn add-project-topologies! [root positions projects]
  (reset! issue-node-positions {})
  (doseq [project projects
          :let [issues (into {} (map (juxt :key identity)) (:issues project))
                issue-positions (atom {})]]
    (doseq [[level keys] (map-indexed vector (:reverse-topology project))
            [column key] (map-indexed vector keys)
            :let [issue (get issues key)
                  base (get positions (:repo issue))]
            :when (and issue base)]
      (let [x (+ (:x base) (* (- column (/ (dec (count keys)) 2)) 0.9))
            y (+ (:height base) 1.2 (* level 0.85))
            z (+ (:z base) 0.7)
            status-color (case (:status issue)
                           "closed" 0x42f58d
                           "in-progress" 0x42a5ff
                           0xffd45a)
            objective? (= "objective" (:kind issue))
            node (three/mesh
                  (if objective?
                    (THREE/IcosahedronGeometry. 0.48 1)
                    (THREE/OctahedronGeometry. 0.28))
                  (three/standard-material
                   {:color (if objective? 0xf4a8ff status-color)
                    :emissive (if objective? 2.2 1.5) :roughness 0.25}))
            caption (text-sprite
                     (if objective?
                       (str "OBJECTIVE · " (or (:title issue) key))
                       (str "ISSUE · " (or (:rad issue) key)))
                     (if objective? "#f4a8ff" "#ffd45a"))]
        (three/set-position! node x y z)
        (three/set-position! caption x (+ y 0.75) z)
        (three/set-scale! caption (if objective? 5.6 3.4)
                          (if objective? 1.05 0.72) 1)
        (set! (.. node -userData -repo)
              (clj->js {:path (:repo issue) :issue (:rad issue)
                        :project (:id project) :issue-key key}))
        (swap! issue-positions assoc key {:x x :y y :z z})
        (swap! issue-node-positions assoc key {:x x :y y :z z})
        (when (:rad issue)
          (swap! issue-node-positions assoc (:rad issue) {:x x :y y :z z}))
        (.add root caption)
        (.add root node)))
    (doseq [[key issue] issues
            blocker (:blockers issue)
            :let [a (get @issue-positions key)
                  b (get @issue-positions blocker)]
            :when (and a b)]
      (let [geometry (doto (THREE/BufferGeometry.)
                       (.setFromPoints
                        #js [(THREE/Vector3. (:x a) (:y a) (:z a))
                             (THREE/Vector3. (:x b) (:y b) (:z b))]))
            line (THREE/Line.
                  geometry
                  (THREE/LineBasicMaterial.
                   #js {:color 0xffd45a :transparent true :opacity 0.85}))]
        (.add root line)))
    (doseq [{:keys [actor from to]} (:walks project)
            :let [a (get @issue-positions from)
                  b (get @issue-positions to)]
            :when (and a b)]
      (let [curve (THREE/QuadraticBezierCurve3.
                   (THREE/Vector3. (:x a) (:y a) (:z a))
                   (THREE/Vector3. (/ (+ (:x a) (:x b)) 2)
                                   (+ 1.4 (max (:y a) (:y b)))
                                   (/ (+ (:z a) (:z b)) 2))
                   (THREE/Vector3. (:x b) (:y b) (:z b)))
            geometry (THREE/TubeGeometry. curve 24 0.035 6 false)
            path (THREE/Mesh.
                  geometry
                  (THREE/MeshBasicMaterial.
                   #js {:color 0x35ff8a :transparent true :opacity 0.72}))]
        (set! (.. path -userData -walk)
              (clj->js {:actor actor :from from :to to}))
        (.add root path)))))

(def result-colors
  {:issue 0xffd45a :source 0x48dcff :radicle 0xc956ff
   :github 0xf5f5f5 :review 0x35ff8a :merge 0x208cff})

(defn add-result-graphs! [root positions graphs]
  ;; The garden keeps every result visible as fruit, but only the two newest
  ;; lineages grow nameplates.  Rendering every historical caption turned the
  ;; canopy into a wall of text at colony scale.
  (doseq [[graph-index graph] (map-indexed vector (take-last 8 graphs))
          :let [base (get positions (:project graph))
                nodes (:nodes graph)]
          :when base]
    (let [node-positions
          (into {}
                (map-indexed
                 (fn [index node]
                   [(:id node)
                    {:x (+ (:x base) (* (- index (/ (dec (count nodes)) 2))
                                        1.15))
                     :y (+ (:height base) 3.0 (* 0.28 (mod graph-index 3)))
                     :z (+ (:z base) 2.6 (* 0.7 graph-index))}])
                 nodes))]
      (doseq [node nodes
              :let [{:keys [x y z]} (get node-positions (:id node))
                    kind (keyword (name (:type node)))
                    color (get result-colors kind 0xffffff)
                    object (three/mesh
                            (case kind
                              :source (THREE/TetrahedronGeometry. 0.34)
                              :radicle (THREE/SphereGeometry. 0.3 12 9)
                              :github (THREE/SphereGeometry. 0.36 18 10
                                                               0 (* 2 js/Math.PI)
                                                               0 (/ js/Math.PI 2))
                              :review (THREE/TorusGeometry. 0.3 0.1 8 20)
                              :merge (THREE/IcosahedronGeometry. 0.38 1)
                              (THREE/OctahedronGeometry. 0.3))
                            (three/standard-material
                             {:color color :emissive 1.7 :roughness 0.24}))
                    caption (when (>= graph-index 6)
                              (text-sprite
                               (str (case kind
                                      :source "SOURCE SEED"
                                      :radicle "RADICLE POD"
                                      :github "GITHUB CONSERVATORY"
                                      :review "REVIEW BLOOM"
                                      :merge "MERGE CANOPY"
                                      (str/upper-case (name kind)))
                                    " · "
                                    (let [value (str (:value node))]
                                      (subs value 0 (min 12 (count value)))))
                               (str "#" (.toString color 16))))]]
        (three/set-position! object x y z)
        (set! (.. object -userData -result) (clj->js node))
        (.add root object)
        (when caption
          (three/set-position! caption x (+ y 0.62) z)
          (three/set-scale! caption 2.25 0.48 1)
          (.add root caption)))
      (doseq [edge (:edges graph)
              :let [a (get node-positions (:from edge))
                    b (get node-positions (:to edge))]
              :when (and a b)]
        (let [geometry (doto (THREE/BufferGeometry.)
                         (.setFromPoints
                          #js [(THREE/Vector3. (:x a) (:y a) (:z a))
                               (THREE/Vector3. (:x b) (:y b) (:z b))]))
              line (THREE/Line.
                    geometry
                    (THREE/LineBasicMaterial.
                     #js {:color 0x89f7ff :transparent true
                          :opacity 0.9}))]
          (.add root line))))))

(defn text-sprite [text color]
  (let [canvas (.createElement js/document "canvas")
        _ (set! (.-width canvas) 512)
        _ (set! (.-height canvas) 128)
        context (.getContext canvas "2d")
        _ (set! (.-fillStyle context) "rgba(9,6,17,.82)")
        _ (.fillRect context 0 0 512 128)
        _ (set! (.-strokeStyle context) color)
        _ (set! (.-lineWidth context) 5)
        _ (.strokeRect context 3 3 506 122)
        _ (set! (.-font context) "600 34px -apple-system, sans-serif")
        _ (set! (.-fillStyle context) "#f8f5ff")
        _ (set! (.-textAlign context) "center")
        _ (set! (.-textBaseline context) "middle")
        _ (.fillText context text 256 64)
        texture (THREE/CanvasTexture. canvas)
        sprite (THREE/Sprite.
                (THREE/SpriteMaterial.
                 #js {:map texture :transparent true :depthTest false}))]
    (three/set-scale! sprite 5.4 1.35 1)
    sprite))

(defn add-system-dynamics! [root dynamics]
  (let [stocks (:stocks dynamics)
        stock-x (into {} (map-indexed
                          (fn [index stock]
                            [(:id stock) (+ -12 (* index 8))])
                          stocks))
        ;; Keep the dynamics lane between repository group rows and toward the
        ;; camera. At negative Z it is depth-occluded by the large repo grids.
        z 10
        particles (atom [])]
    (doseq [stock stocks
            :let [x (get stock-x (:id stock))
                  value (or (:value stock) 0)
                  color (js/parseInt (subs (:color stock) 1) 16)
                  fill-height (+ 0.35 (min 5 (* 1.05 (js/Math.sqrt value))))
                  frame (THREE/Mesh.
                         (THREE/BoxGeometry. 4.8 5.7 4.8)
                         (THREE/MeshBasicMaterial.
                          #js {:color color :wireframe true
                               :transparent true :opacity 0.35}))
                  fill (three/mesh
                        (THREE/BoxGeometry. 4.2 fill-height 4.2)
                        (three/standard-material
                         {:color color :emissive 0.72 :roughness 0.28
                          :transparent true :opacity 0.82}))
                  title (text-sprite
                         (str (:label stock) " · " value " " (:unit stock))
                         (:color stock))]]
      (three/set-position! frame x 2.65 z)
      (three/set-position! fill x (/ fill-height 2) z)
      (three/set-position! title x 6.15 z)
      (set! (.. fill -userData -dynamics)
            (clj->js {:type "stock" :stock stock}))
      (.add root frame)
      (.add root fill)
      (.add root title))
    (doseq [flow (:flows dynamics)
            :let [from-x (or (get stock-x (:from flow)) -18)
                  to-x (get stock-x (:to flow))
                  rate (or (:rate flow) 0)]
            :when to-x]
      (let [a (THREE/Vector3. (+ from-x 2.5) 2.6 z)
            b (THREE/Vector3. (- to-x 2.5) 2.6 z)
            distance (.distanceTo a b)
            pipe (THREE/Mesh.
                  (THREE/CylinderGeometry. 0.09 0.09 distance 10)
                  (THREE/MeshBasicMaterial.
                   #js {:color (if (pos? rate) 0x79ffa8 0x51465f)
                        :transparent true :opacity 0.7}))
            arrow (THREE/Mesh.
                   (THREE/ConeGeometry. 0.28 0.7 12)
                   (THREE/MeshBasicMaterial.
                    #js {:color (if (pos? rate) 0x79ffa8 0x51465f)}))
            midpoint (doto (THREE/Vector3.) (.addVectors a b) (.multiplyScalar 0.5))
            flow-label (text-sprite
                        (str (:label flow) " · " rate "/h")
                        (if (pos? rate) "#79ffa8" "#81778e"))]
        (three/set-position! pipe (.-x midpoint) (.-y midpoint) (.-z midpoint))
        (set! (.. pipe -rotation -z) (/ js/Math.PI 2))
        (three/set-position! arrow (.-x b) (.-y b) (.-z b))
        (set! (.. arrow -rotation -z) (- (/ js/Math.PI 2)))
        (three/set-position! flow-label (.-x midpoint) 3.8 z)
        (.add root pipe)
        (.add root arrow)
        (.add root flow-label)
        (doseq [index (range (min 7 rate))
                :let [dot (THREE/Mesh.
                           (THREE/SphereGeometry. 0.16 10 8)
                           (THREE/MeshBasicMaterial.
                            #js {:color 0xffffff}))]]
          (.add root dot)
          (swap! particles conj
                 {:object dot :from a :to b
                  :phase (/ index (max 1 (min 7 rate)))
                  :speed (+ 0.18 (* 0.025 rate))}))))
    (let [title (text-sprite "SYSTEM DYNAMICS · STOCK → FLOW" "#d9b2ff")]
      (three/set-position! title 0 8.2 z)
      (three/set-scale! title 9.2 1.65 1)
      (.add root title))
    (swap! runtime assoc :flow-particles @particles)))

(def runner-style
  {"codex" {:body 0x27d8ff :accent 0xc6f7ff :head :visor}
   "claude" {:body 0xff9b52 :accent 0xffe1b7 :head :antenna}
   "claude-zai" {:body 0xa875ff :accent 0xe2d2ff :head :crown}
   "grok" {:body 0xff4f72 :accent 0xffc2cf :head :visor}})

(defn agent-runner [agent]
  (let [runner (:agent.run/runner agent)
        model (:agent.run/model agent)]
    (if (and runner (not= runner "default"))
      runner
      (or (some-> model (str/split #":") first not-empty) "agent"))))

(defn character-part [geometry color emissive]
  (three/mesh
   geometry
   (three/standard-material
    {:color color :emissive emissive :metallic 0.28 :roughness 0.34})))

(defn make-agent-character [agent]
  (let [runner (agent-runner agent)
        {:keys [body accent head]} (get runner-style runner
                                        {:body 0x35ff8a
                                         :accent 0xd5ffe3 :head :visor})
        actor (three/group)
        torso (character-part (THREE/CapsuleGeometry. 0.32 0.55 5 10)
                              body 0.65)
        face (character-part (THREE/SphereGeometry. 0.34 16 12)
                             accent 0.55)
        visor (character-part (THREE/BoxGeometry. 0.48 0.16 0.14)
                              0x101525 1.2)
        left-arm (character-part (THREE/CapsuleGeometry. 0.09 0.46 4 8)
                                 body 0.45)
        right-arm (character-part (THREE/CapsuleGeometry. 0.09 0.46 4 8)
                                  body 0.45)
        left-leg (character-part (THREE/CapsuleGeometry. 0.11 0.42 4 8)
                                 0x293145 0.25)
        right-leg (character-part (THREE/CapsuleGeometry. 0.11 0.42 4 8)
                                  0x293145 0.25)
        core (character-part (THREE/SphereGeometry. 0.11 10 8)
                             accent 1.8)
        beacon (THREE/Mesh.
                (THREE/TorusGeometry. 0.52 0.035 8 28)
                (THREE/MeshBasicMaterial.
                 #js {:color body :transparent true :opacity 0.72}))
        clickable (clj->js agent)]
    (three/set-position! torso 0 0.88 0)
    (three/set-position! face 0 1.55 0)
    (three/set-position! visor 0 1.58 0.29)
    (three/set-position! left-arm -0.42 0.9 0)
    (three/set-position! right-arm 0.42 0.9 0)
    (three/set-position! left-leg -0.18 0.28 0)
    (three/set-position! right-leg 0.18 0.28 0)
    (three/set-position! core 0 0.92 0.31)
    (three/set-rotation-x! beacon (/ js/Math.PI 2))
    (three/set-position! beacon 0 0.06 0)
    (doseq [part [torso face visor left-arm right-arm left-leg right-leg core
                  beacon]]
      (aset (.-userData ^js part) "repo" clickable)
      (.add actor part))
    (when (contains? #{:antenna :crown} head)
      (let [stem (character-part
                  (THREE/CylinderGeometry. 0.025 0.025 0.35 8)
                  body 0.8)
            tip (character-part
                 (if (= head :crown)
                   (THREE/OctahedronGeometry. 0.13)
                   (THREE/SphereGeometry. 0.1 10 8))
                 accent 1.8)]
        (three/set-position! stem 0 1.95 0)
        (three/set-position! tip 0 2.16 0)
        (aset (.-userData ^js stem) "repo" clickable)
        (aset (.-userData ^js tip) "repo" clickable)
        (.add actor stem)
        (.add actor tip)))
    (let [nameplate (text-sprite
                     (str runner " · " (:agent.run/model agent))
                     (str "#" (.toString body 16)))]
      (three/set-scale! nameplate 3.3 0.78 1)
      (three/set-position! nameplate 0 2.75 0)
      (.add actor nameplate))
    {:actor actor :head face :left-arm left-arm :right-arm right-arm
     :left-leg left-leg :right-leg right-leg :beacon beacon}))

(def organism-grove-style
  {:com-junkawasaki {:color 0x56c9a8 :label "PERSONAL · RESEARCH / MAKING"}
   :etzhayyim {:color 0xe0bd67 :label "ETZHAYYIM · WELLBECOMING"}
   :cloud-itonami {:color 0x45d8c4 :label "CLOUD-ITONAMI · NETWORK-AWAI"}
   :jk-luxury {:color 0xb89b62 :label "JK-LUXURY · TRUST / RARITY"}
   :gftdcojp {:color 0x4c9fe8 :label "GFTD · DEFENSIVE SECURITY"}})

(defn organism-key [organism]
  (keyword (name (or (:org organism) (:organism/org organism) :unknown))))

(defn add-organism-groves! [root organisms selected-scope]
  (doseq [[index organism] (map-indexed vector organisms)
          :let [key (organism-key organism)
                {:keys [color label]} (get organism-grove-style key
                                           {:color 0x75c69b
                                            :label (str/upper-case (name key))})
                angle (+ (- (/ js/Math.PI 2)) (* index (/ (* 2 js/Math.PI)
                                                          (max 1 (count organisms)))))
                focused? (or (= selected-scope :federation)
                             (= selected-scope key))
                radius (if (= selected-scope :federation) 17 0)
                x (* radius (js/Math.cos angle))
                z (* radius (js/Math.sin angle))
                grove (THREE/Mesh.
                       (THREE/CylinderGeometry. 5.1 5.7 0.55 36)
                       (THREE/MeshStandardMaterial.
                        #js {:color color :emissive color
                             :emissiveIntensity (if focused? 0.18 0.02)
                             :roughness 0.9 :transparent true
                             :opacity (if focused? 0.28 0.06)}))
                boundary (THREE/Mesh.
                          (THREE/TorusGeometry. 4.75 0.075 8 64)
                          (THREE/MeshBasicMaterial.
                           #js {:color color :transparent true
                                :opacity (if focused? 0.72 0.12)}))
                title (text-sprite label
                                   (str "#" (.toString color 16)))]]
    (three/set-position! grove x -0.42 z)
    (three/set-rotation-x! boundary (/ js/Math.PI 2))
    (three/set-position! boundary x -0.08 z)
    (three/set-position! title x 1.15 z)
    (three/set-scale! title 5.4 0.8 1)
    (set! (.. grove -userData -organism) (clj->js organism))
    (.add root grove)
    (.add root boundary)
    (.add root title)))

(defn rebuild-scene! [snapshot group-mode organism-scope]
  (when-let [{:keys [repo-root]} @runtime]
    (clear! repo-root)
    (let [repos (scoped-repos organism-scope)
          ranks (dependency-ranks (:dependencies snapshot))
          grouped (group-by #(group-key group-mode %) repos)
          groups (sort (keys grouped))
          group-columns (min 3 (max 1 (js/Math.ceil
                                        (js/Math.sqrt (count groups)))))
          active-by-path (into {} (map (juxt :path identity) (:active-repos snapshot)))
          active-paths (set (keys active-by-path))
          positions (atom {})]
      (add-organism-groves! repo-root (:organisms snapshot) organism-scope)
      (add-life-tree! repo-root snapshot)
      (add-successor! repo-root)
      (doseq [[group-index group] (map-indexed vector groups)
              :let [group-repos (->> (get grouped group)
                                     (sort-by (juxt #(get ranks (:path %) 0)
                                                    :name))
                                     vec)
                    index-by-path (into {} (map-indexed
                                            (fn [i repo] [(:path repo) i])
                                            group-repos))
                    columns (max 1 (js/Math.ceil (js/Math.sqrt (count group-repos))))
                    {:keys [x z]} (island-origin group-index)
                    origin-x x
                    origin-z z
                    extent (+ 2.2 (* columns 0.28))
                    platform (three/mesh
                              (THREE/CylinderGeometry.
                               (/ extent 2) (* 0.82 (/ extent 2))
                               0.62 24)
                              (garden-material (:soil garden-colors) 0.05))
                    island-ring (THREE/Mesh.
                                 (THREE/TorusGeometry.
                                  (* 0.43 extent) 0.045 7 42)
                                 (THREE/MeshBasicMaterial.
                                  #js {:color (:soil-edge garden-colors)
                                       :transparent true :opacity 0.65}))
                    island-label (text-sprite (str group) "#8dffb0")]]
        (three/set-position! platform origin-x -0.32 origin-z)
        (three/set-rotation-x! island-ring (/ js/Math.PI 2))
        (three/set-position! island-ring origin-x 0.01 origin-z)
        (three/set-position! island-label origin-x 2.5 origin-z)
        (three/set-scale! island-label 4.0 0.72 1)
        (.add repo-root platform)
        (.add repo-root island-ring)
        (.add repo-root island-label)
        (doseq [[[kind congestion] kind-repos]
                (group-by (fn [repo]
                            [(status repo active-paths)
                             (min 6 (+ (:issues-open repo)
                                       (:patches-open repo)))])
                          group-repos)
                :let [height (if (= kind :active)
                               1.25
                               (+ 0.28 (* congestion 0.12)))
                      geometry (if (= kind :different)
                                 (THREE/ConeGeometry. 0.105 height 5)
                                 (THREE/ConeGeometry. 0.09 height 7))
                      plant-kind (case kind
                                   :active :active
                                   :different :different
                                   :synced :synced
                                   :remote)
                      mesh (THREE/InstancedMesh. geometry (material plant-kind)
                                                 (count kind-repos))
                      matrix (THREE/Matrix4.)
                      repo-array (array)]]
          (doseq [[instance-id repo] (map-indexed vector kind-repos)
                  :let [repo-index (get index-by-path (:path repo))
                        x (+ origin-x (* (- (mod repo-index columns)
                                             (/ (dec columns) 2)) 0.25))
                        z (+ origin-z (* (- (quot repo-index columns)
                                             (/ (dec columns) 2)) 0.25))
                        y (/ height 2)]]
            (.setPosition matrix x y z)
            (.setMatrixAt mesh instance-id matrix)
            (.push repo-array (clj->js (merge repo (get active-by-path (:path repo)))))
            (swap! positions assoc (:path repo) {:x x :z z :height height}))
          (set! (.. mesh -userData -repos) repo-array)
          (set! (.. mesh -instanceMatrix -needsUpdate) true)
          (.add repo-root mesh)))
      ;; A bright point-cloud layer keeps every repository legible even when
      ;; thousands of very small instanced bars are viewed from far away.
      (let [geometry (THREE/BufferGeometry.)
            vertices (->> repos
                          (mapcat (fn [repo]
                                    (when-let [{:keys [x z height]}
                                               (get @positions (:path repo))]
                                      [x (+ height 0.08) z])))
                          (into-array))
            attribute (THREE/Float32BufferAttribute. vertices 3)
            points (THREE/Points.
                    geometry
                    (THREE/PointsMaterial.
                    #js {:color 0x6ff59b :size 0.16
                          :sizeAttenuation true :transparent true
                          :opacity 0.95}))]
        (.setAttribute geometry "position" attribute)
        (.add repo-root points))
      (add-dependency-lines! repo-root @positions (:dependencies snapshot))
      (add-project-topologies! repo-root @positions (:projects snapshot))
      (add-result-graphs! repo-root @positions (:results snapshot))
      (let [actor-animations (atom [])]
       (doseq [[agent-index agent] (map-indexed vector (queried-agents))
              :let [position (or (get @issue-node-positions (:issue agent))
                                 (get @positions (:agent.run/project agent)))]
              :when position]
        (let [{:keys [actor] :as character} (make-agent-character agent)
              phase (* agent-index 2.399)
              radius (+ 0.62 (* 0.12 (mod agent-index 3)))
              base (THREE/Vector3.
                    (:x position)
                    (+ (or (:y position) (:height position) 0) 1.2)
                    (:z position))
              style (get runner-style (agent-runner agent)
                         {:body 0x35ff8a})
              orbit (THREE/Mesh.
                     (THREE/TorusGeometry. radius 0.035 8 48)
                     (THREE/MeshBasicMaterial.
                      #js {:color (:body style) :transparent true
                           :opacity 0.5}))
              beam-geometry
              (doto (THREE/BufferGeometry.)
                (.setFromPoints
                 #js [(THREE/Vector3. (:x position)
                                     (or (:y position)
                                         (:height position) 0)
                                     (:z position))
                      (THREE/Vector3. (.-x base) (.-y base) (.-z base))]))
              beam (THREE/Line.
                    beam-geometry
                    (THREE/LineDashedMaterial.
                     #js {:color (:body style) :transparent true
                          :opacity 0.65 :dashSize 0.28 :gapSize 0.18}))]
          (.computeLineDistances beam)
          (three/set-rotation-x! orbit (/ js/Math.PI 2))
          (three/set-position! orbit (.-x base) (.-y base) (.-z base))
          (three/set-scale! actor 0.72 0.72 0.72)
          (three/set-position! actor
                               (+ (.-x base) (* radius (js/Math.cos phase)))
                               (.-y base)
                               (+ (.-z base) (* radius (js/Math.sin phase))))
          (.add repo-root beam)
          (.add repo-root orbit)
          (.add repo-root actor)
          (swap! actor-animations conj
                 (merge character
                        {:base base :phase phase :radius radius
                         :speed (+ 0.28 (* 0.04 (mod agent-index 4)))
                         :working? (= "running" (:agent.run/status agent))}))))
       (swap! runtime assoc :actor-animations @actor-animations))
      (doseq [loop (queried-loops)
              :let [position (get @positions (:tamaki.loop/project loop))]
              :when position]
        (let [ring (three/mesh
                    (THREE/TorusGeometry. 0.48 0.055 8 28)
                    (three/standard-material
                     {:color 0xc956ff :emissive 1.1 :roughness 0.3}))]
          (three/set-rotation-x! ring (/ js/Math.PI 2))
          (three/set-position! ring (:x position)
                               (+ (:height position) 0.18) (:z position))
          (.add repo-root ring))))))

(defn render-overlay! [snapshot selected group-mode]
  (let [{:keys [total west github rad]} (:counts snapshot)
        active (first (:active-repos snapshot))
        metrics (.getElementById js/document "metrics")
        details (.getElementById js/document "details")
        model-usage (.getElementById js/document "model-usage")
        dynamics-panel (.getElementById js/document "system-dynamics")
        grouping (.getElementById js/document "grouping")
        repo (or selected active)
        path (or (:path repo) (:agent.run/project repo))
        stat (some #(when (= path (:path %)) %) (:repo-stats snapshot))]
    (set! (.-value grouping) (label group-mode "org"))
    (set! (.-textContent metrics)
          (str (count (visible-repos)) "/" total " repo tiles · "
               (count (:actors snapshot)) " actors · "
               (count (:agents snapshot)) " agents · "
               (count (:loops snapshot)) " loops · WEST " west
               " · GitHub " github " · Radicle " rad))
    (when-let [bonsai-state (.getElementById js/document "bonsai-state")]
      (let [trees (:git-trees snapshot)
            candidates (mapcat :pruning-candidates trees)]
        (set! (.-innerHTML bonsai-state)
              (str "<b>Bonsai care</b>"
                   "<span>樹 " (count trees)
                   " · 剪定候補 " (count candidates)
                   " · 自動削除 off</span>"
                   (when-let [candidate (first candidates)]
                     (str "<small>✂ " (:name candidate)
                          " · review/approval required</small>"))))))
    (set! (.-innerHTML details)
          (if repo
            (str "<strong>" path "</strong><br>"
                 "issue " (or (:issue repo) "—") "<br>"
                 "sync " (label (:sync repo) "active") "<br>"
                 "open issues " (or (:issues-open stat) 0)
                 " · PR/patches " (or (:patches-open stat) 0)
                 " · loops " (or (:loops stat) 0) "<br><br>"
                 (when (:agent.run/id repo)
                   (str "agent " (:agent.run/id repo) "<br>"
                        "runner " (or (:agent.run/runner repo) "default") "<br>"
                        "model " (or (:agent.run/model repo) "default") "<br>"
                        (:agent.run/goal repo)))
                 (when-let [run (first (:runs repo))]
                   (str "agent " (get run :agent.run/id) "<br>"
                        "model " (or (get run :agent.run/model) "default") "<br>"
                        (get run :agent.run/goal))))
            "Select a repository tile"))
    (let [usage-by-provider (into {} (map (juxt :provider identity))
                                  (:model-usage snapshot))]
      (set! (.-innerHTML model-usage)
            (apply str
                   (for [provider ["codex" "claude" "claude-zai" "grok"]
                         :let [usage (get usage-by-provider provider)]]
                     (str "<div class=\"usage-card\"><b>" provider "</b>"
                          "<span>in " (or (:input usage) 0)
                          " · out " (or (:output usage) 0) "</span>"
                          "<span><em>remaining "
                          (or (:remaining usage) "unknown")
                          "</em></span></div>")))))
    (when-let [actor-state (.getElementById js/document "actor-state")]
      (set! (.-innerHTML actor-state)
            (apply str
                   (for [{:keys [id desired running queued blocked spawn]}
                         (:actors snapshot)]
                     (str "<div class=\"actor-card\"><b>" id "</b>"
                          "desired " desired " · running " running
                          " · queued " queued " · blocked " blocked
                          (when (pos? spawn)
                            (str " · <span class=\"pressure\">need +"
                                 spawn "</span>"))
                          "</div>")))))
    (let [{:keys [stocks flows bottleneck failure-pressure backlog-delta
                  throughput business-status business-control-score
                  business-kpis]} (:system-dynamics snapshot)]
      (set! (.-innerHTML dynamics-panel)
            (str "<div class=\"dynamics-heading\"><b>System dynamics</b>"
                 "<span>1h flow · bottleneck " bottleneck
                 " · failure " (.toFixed (* 100 (or failure-pressure 0)) 0)
                 "%</span></div>"
                 "<div class=\"business-control\"><b>Revenue control</b>"
                 (if (= business-status "observed")
                   (str "<span>score "
                        (.toFixed (* 100 (or business-control-score 0)) 0)
                        "% · MRR ¥" (or (:mrr-jpy business-kpis) 0)
                        " · risk-adjusted ΔMRR ¥"
                        (or (:risk-adjusted-delta-mrr-jpy business-kpis) 0)
                        " · experiments "
                        (.toFixed (or (:experiments-per-week business-kpis) 0) 1)
                        "/week</span>")
                   "<span class=\"pressure\">KPI observation required</span>")
                 "</div>"
                 "<div class=\"stock-row\">"
                 (apply str
                        (for [{:keys [id label value unit color]} stocks]
                          (str "<div class=\"stock-card"
                               (when (= id bottleneck) " bottleneck")
                               "\" style=\"--stock:" color "\">"
                               "<small>" label "</small><strong>" value
                               "</strong><em>" unit "</em></div>")))
                 "</div><div class=\"flow-row\">"
                 (apply str
                        (for [{:keys [label rate]} flows]
                          (str "<span>→ " label " <b>" rate "/h</b></span>")))
                 "<span class=\"" (if (pos? backlog-delta) "pressure" "relief")
                 "\">backlog Δ " (if (pos? backlog-delta) "+" "") backlog-delta
                 "/h</span><span>throughput <b>" throughput "/h</b></span>"
                 "</div>")))))

(defn scene-signature [snapshot]
  (let [dynamics (:system-dynamics snapshot)]
    [(count (:repos snapshot))
     (mapv (juxt :path :issue) (:active-repos snapshot))
     (frequencies (map :sync (:repos snapshot)))
     (:dependencies snapshot)
     (:organisms snapshot)
     (:projects snapshot) (:agents snapshot) (:loops snapshot)
     (:results snapshot)
     (:repo-stats snapshot)
     ;; observed-at advances every second but does not change geometry.
     ;; Excluding it avoids disposing/recreating thousands of WebGL objects
     ;; continuously until the context becomes blank.
     (select-keys dynamics
                  [:stocks :flows :bottleneck :failure-pressure
                   :backlog-delta :throughput])]))

(declare play-sfx! spawn-pulse! set-garden-view! init-scene! dispose-scene!)

(defn three-d-view? [view]
  (contains? #{:garden :three-d} view))

(defn apply-state! []
  (let [snapshot @(rf/subscribe [:snapshot])
        selected @(rf/subscribe [:selected])
        group-mode @(rf/subscribe [:group-mode])
        organism-scope @(rf/subscribe [:organism-scope])
        view @(rf/subscribe [:surface-view])]
    (when snapshot
      (when (and (three-d-view? view) @runtime)
        (let [signature (scene-signature snapshot)]
          (when (not= signature @topology-signature)
            (reset! topology-signature signature)
            (rebuild-scene! snapshot group-mode organism-scope))))
      (render-overlay! snapshot selected group-mode))))

(defn fetch-snapshot! []
  (-> (js/fetch (str "snapshot.json?t=" (.now js/Date))
                #js {:cache "no-store"})
      (.then #(.json %))
      (.then #(do (rf/dispatch-sync [:snapshot (js->clj % :keywordize-keys true)])
                  (apply-state!)))
      (.catch #(js/console.error "Tamaki snapshot" %))))

(defn set-surface-view! [view]
  (rf/dispatch-sync [:surface-view view])
  (if (three-d-view? view)
    (do
      (when-not @runtime (init-scene!))
      (reset! topology-signature nil)
      (apply-state!))
    (dispose-scene!)))

(defn group-mode-changed! [event]
  (play-sfx! :navigate)
  (spawn-pulse! (.-clientX event) (.-clientY event))
  (rf/dispatch-sync [:group-mode (keyword (.. event -target -value))])
  (reset! topology-signature nil)
  (apply-state!))

(defn select-organism! [scope]
  (play-sfx! :navigate)
  (rf/dispatch-sync [:organism-scope scope])
  (reset! topology-signature nil)
  (apply-state!)
  (when (= scope :federation)
    (set-garden-view! :world)))

(defn play-tone! [frequency duration volume wave]
  (when-let [context (:context @audio-runtime)]
    (when (= "running" (.-state context))
      (let [now (.-currentTime context)
            oscillator (.createOscillator context)
            gain (.createGain context)]
        (set! (.-type oscillator) wave)
        (.setValueAtTime (.-frequency oscillator) frequency now)
        (.setValueAtTime (.-gain gain) 0.0001 now)
        (.exponentialRampToValueAtTime (.-gain gain) volume (+ now 0.025))
        (.exponentialRampToValueAtTime (.-gain gain) 0.0001 (+ now duration))
        (.connect oscillator gain)
        (.connect gain (.-destination context))
        (.start oscillator now)
        (.stop oscillator (+ now duration 0.03))))))

(defn play-sfx! [kind]
  (when (:enabled? @audio-runtime)
    (case kind
      :select (do (play-tone! 523.25 0.12 0.035 "sine")
                  (js/setTimeout #(play-tone! 783.99 0.16 0.028 "sine") 55))
      :voice (do (play-tone! 392.0 0.18 0.04 "triangle")
                 (js/setTimeout #(play-tone! 587.33 0.22 0.03 "triangle") 90))
      :navigate (play-tone! 329.63 0.1 0.025 "sine")
      nil)))

(def ambient-notes [110.0 146.83 164.81 220.0 196.0 146.83 130.81 164.81])

(defn ambient-step! []
  (let [step (:step @audio-runtime)
        note (nth ambient-notes (mod step (count ambient-notes)))]
    (play-tone! note 1.8 0.012 "sine")
    (when (zero? (mod step 4))
      (play-tone! (/ note 2) 3.4 0.009 "triangle"))
    (swap! audio-runtime update :step inc)))

(defn update-sound-label! []
  (when-let [button (.getElementById js/document "sound-toggle")]
    (set! (.-textContent button)
          (if (:enabled? @audio-runtime) "♫ ambient on" "♫ ambient off"))))

(defn toggle-sound! []
  (if (:enabled? @audio-runtime)
    (do
      (when-let [timer (:timer @audio-runtime)] (js/clearInterval timer))
      (when-let [context (:context @audio-runtime)] (.suspend context))
      (swap! audio-runtime assoc :enabled? false :timer nil))
    (let [context (or (:context @audio-runtime)
                      (js/AudioContext.))]
      (.resume context)
      (swap! audio-runtime assoc
             :enabled? true :context context
             :timer (js/setInterval ambient-step! 900))
      (ambient-step!)
      (play-sfx! :select)))
  (update-sound-label!))

(defn spawn-pulse! [x y]
  (when-let [layer (.getElementById js/document "effects")]
    (let [pulse (.createElement js/document "span")]
      (set! (.-className pulse) "pulse")
      (set! (.. pulse -style -left) (str x "px"))
      (set! (.. pulse -style -top) (str y "px"))
      (.appendChild layer pulse)
      (js/setTimeout #(.remove pulse) 750))))

(defn submit-voice! [transcript]
  (when-not (str/blank? transcript)
    (play-sfx! :voice)
    (set! (.-textContent (.getElementById js/document "voice-status"))
          (str "queued · " transcript))
    (when-let [handler (some-> js/window .-webkit .-messageHandlers .-voice)]
      (.postMessage handler transcript))))

(defn start-voice! []
  (let [ctor (or (.-SpeechRecognition js/window)
                 (.-webkitSpeechRecognition js/window))]
    (if ctor
      (let [recognition (ctor.)]
        (set! (.-lang recognition) "ja-JP")
        (set! (.-interimResults recognition) false)
        (set! (.-continuous recognition) false)
        (set! (.-textContent (.getElementById js/document "voice-status"))
              "listening…")
        (set! (.-onresult recognition)
              (fn [event]
                (submit-voice!
                 (.. event -results (item 0) (item 0) -transcript))))
        (set! (.-onerror recognition)
              (fn [event]
                (set! (.-textContent
                       (.getElementById js/document "voice-status"))
                      (str "voice unavailable · " (.-error event)))
                (when-let [transcript
                           (js/prompt
                            "音声認識を利用できません。Tamaki への指示を入力してください")]
                  (submit-voice! transcript))))
        (.start recognition))
      (when-let [transcript
                 (js/prompt "Tamaki supervisor への指示を入力してください")]
        (submit-voice! transcript)))))

(defn short-agent-label [{:keys [agent-id agent-runner]}]
  (let [id (or agent-id "system")
        short-id (if (> (count id) 18)
                   (str (subs id 0 8) "…" (subs id (- (count id) 5)))
                   id)]
    (if (and agent-runner (not= agent-runner "system")
             (not= agent-runner id))
      (str agent-runner " · " short-id)
      short-id)))

(defn activity-panel []
  (let [snapshot @(rf/subscribe [:snapshot])
        selected-agent @(rf/subscribe [:activity-agent])
        events (:activity snapshot)
        agents (->> events
                    (reduce (fn [result event]
                              (assoc result (:agent-id event) event)) {})
                    vals
                    (sort-by (juxt :agent-runner :agent-id)))
        visible (if (= "all" selected-agent)
                  events
                  (filter #(= selected-agent (:agent-id %)) events))]
    [:<>
     [:div.activity-filters
      [:button {:class (when (= "all" selected-agent) "selected")
                :on-click #(rf/dispatch [:activity-agent "all"])}
       "all"]
      (for [{:keys [agent-id] :as agent} agents]
        ^{:key agent-id}
        [:button {:class (when (= agent-id selected-agent) "selected")
                  :title (str (:agent-runner agent) " · "
                              (:agent-model agent) " · " agent-id)
                  :on-click #(rf/dispatch [:activity-agent agent-id])}
         (short-agent-label agent)])]
     [:div#activity
      (if (seq visible)
        (for [{:keys [id at kind run issue patch text stream] :as event}
              (take 8 visible)]
          ^{:key (or id (str run "-" at "-" kind))}
          [:div.event
           [:time (.toLocaleTimeString (js/Date. at))]
           [:div.event-heading
            [:b (label kind "event")]
            [:span.stream (or stream "system")]]
           [:small.agent
            (str "[" (short-agent-label event) "] · "
                 (or text issue patch run "workspace"))]])
        [:div.activity-empty "この agent の activity を待機中…"])]]))

(defn result-panel []
  (let [snapshot @(rf/subscribe [:snapshot])
        results (take-last 2 (:results snapshot))]
    [:div.result-panel
     (if (seq results)
       (for [result (reverse results)]
         ^{:key (:id result)}
         [:div.result-chain
          [:small (str (:project result) " · " (:issue result))]
          [:div
           (interpose
            [:span.result-arrow "→"]
            (for [node (:nodes result)]
              ^{:key (:id node)}
              [:span {:class (str "result-node " (name (:type node)))
                      :title (str (:value node))}
               (str/upper-case (name (:type node))) ]))]])
       [:small "source / PR resultを待機中…"])]))

(defn event-time [event]
  (let [value (:at event)
        time (.getTime (js/Date. value))]
    (if (js/isNaN time) 0 time)))

(defn run-id [agent]
  (or (:agent.run/id agent) (:id agent) "unknown"))

(defn agent-project [agent]
  (or (:agent.run/source-project agent)
      (:source-project agent)
      (:agent.run/project agent)
      (:project agent)
      "workspace"))

(defn latest-agent-event [snapshot agent]
  (let [id (run-id agent)]
    (first (sort-by event-time >
                    (filter #(= id (or (:run %) (:agent-id %)))
                            (:activity snapshot))))))

(defn heartbeat-state [snapshot agent]
  (let [event (latest-agent-event snapshot agent)
        age (- (.now js/Date) (event-time event))
        status (label (or (:agent.run/status agent) (:status agent)) "unknown")]
    (cond
      (not (#{"running" "leased" "queued"} status)) :idle
      (and event (< age 180000)) :live
      (and event (< age 900000)) :quiet
      :else :stale)))

(defn work-stage [snapshot agent]
  (let [event (latest-agent-event snapshot agent)
        signal (str/lower-case
                (str (label (:kind event) "") " " (:stream event) " "
                     (:text event)))
        issue (or (:issue agent) (:issue event))]
    (cond
      (re-find #"fail|block|error|conflict" signal) :blocked
      (re-find #"review|pr-|patch|merge" signal) :review
      (or issue (re-find #"tool|code|test|source" signal)) :implement
      :else :discover)))

(def stage-copy
  {:discover ["Discover" "issueを特定"]
   :implement ["Implement" "source / test"]
   :review ["Review" "PR / patch"]
   :blocked ["Blocked" "判断・修復待ち"]})

(defn elapsed-label [event]
  (if event
    (let [seconds (max 0 (js/Math.floor
                          (/ (- (.now js/Date) (event-time event)) 1000)))]
      (cond
        (< seconds 60) (str seconds "s ago")
        (< seconds 3600) (str (js/Math.floor (/ seconds 60)) "m ago")
        :else (str (js/Math.floor (/ seconds 3600)) "h ago")))
    "no heartbeat"))

(defn work-card [snapshot agent]
  (let [event (latest-agent-event snapshot agent)
        heartbeat (heartbeat-state snapshot agent)
        project (agent-project agent)
        issue (or (:issue agent) (:issue event))
        runner (or (:agent.run/runner agent) (:runner agent) "agent")
        model (or (:agent.run/model agent) (:model agent) "default")]
    [:article {:class (str "work-card " (name heartbeat))
               :on-click #(do (rf/dispatch-sync [:select-repo agent])
                              (rf/dispatch [:activity-agent (run-id agent)]))}
     [:div.work-card-head
      [:span.heartbeat]
      [:b runner]
      [:small (elapsed-label event)]]
     [:strong.work-project project]
     [:span.work-issue (if issue (str "issue " issue) "issueを探索中")]
     [:div.work-output
      [:span (or (:text event) (:agent.run/goal agent) "成果物を待機中")]
      [:em (str model " · " (name heartbeat))]]]))

(defn work-row [snapshot agent]
  (let [event (latest-agent-event snapshot agent)
        heartbeat (heartbeat-state snapshot agent)
        stage (work-stage snapshot agent)
        [stage-name] (get stage-copy stage)
        project (agent-project agent)
        issue (or (:issue agent) (:issue event))
        runner (or (:agent.run/runner agent) (:runner agent) "agent")
        model (or (:agent.run/model agent) (:model agent) "default")]
    [:button {:class (str "work-row " (name heartbeat))
              :type "button"
              :on-click #(do (rf/dispatch-sync [:select-repo agent])
                             (rf/dispatch [:activity-agent (run-id agent)]))}
     [:span.row-status {:title (name heartbeat)}]
     [:span.row-title
      [:strong (if issue (str "Issue " issue) "次のissueを探索")]
      [:small (or (:text event) (:agent.run/goal agent) "成果物を待機中")]]
     [:span.row-project project]
     [:span.row-stage {:class (name stage)} stage-name]
     [:span.row-agent
      [:b runner]
      [:small model]]
     [:time (elapsed-label event)] ]))

(def active-loop-statuses #{"active" "running" "queued" "leased"})

(defn active-loop? [loop]
  (contains? active-loop-statuses
             (label (or (:tamaki.loop/status loop) (:status loop)) "unknown")))

(defn actor-state [actor]
  (cond
    (pos? (or (:blocked actor) 0)) :blocked
    (pos? (or (:running actor) 0)) :working
    (pos? (or (:queued actor) 0)) :queued
    (pos? (or (:spawn actor) 0)) :needs-agent
    :else :resident))

(def actor-state-copy
  {:blocked "blocked"
   :working "working"
   :queued "queued"
   :needs-agent "agent待ち"
   :resident "resident"})

(defn actor-card [actor]
  (let [state (actor-state actor)]
    [:article {:class (str "organism-actor " (name state))}
     [:header
      [:span.actor-glyph "◉"]
      [:div
       [:strong (str/replace (str (:id actor)) #"^:" "")]
       [:small (str/replace (str (:type actor)) #"^:" "")]]
      [:em (get actor-state-copy state)]]
     [:p (or (:objective actor) "deterministic responsibility")]
     [:footer
      [:span.actor-project (:project actor)]
      [:span (str "desired " (or (:desired actor) 0))]
      [:span (str "run " (or (:running actor) 0))]
      (when (pos? (or (:spawn actor) 0))
        [:b (str "+" (:spawn actor) " agent")])]]))

(defn loop-card [snapshot loop]
  (let [event (latest-agent-event snapshot loop)
        runner (or (:tamaki.loop/runner loop) (:runner loop) "agent")
        model (or (:tamaki.loop/model loop) (:model loop) "default")
        cycles (or (:tamaki.loop/cycles loop) (:cycles loop) 0)
        max-cycles (or (:tamaki.loop/max-cycles loop) (:max-cycles loop))
        ao (or (:tamaki.loop/ao loop) (:ao loop))]
    [:article.loop-agent
     [:span.loop-pulse]
     [:div
      [:strong (or ao (run-id loop))]
      [:small (or (:objective loop) "objectiveを観察中")]]
     [:span.loop-project (agent-project loop)]
     [:span.loop-model [:b runner] [:small model]]
     [:span.loop-cycles
      (if max-cycles (str cycles " / " max-cycles) (str cycles " cycles"))]
     [:time (if event (elapsed-label event) "campaign active")]]))

(defn organism-view [snapshot actors loops]
  (let [working (filter #(#{:working :queued} (actor-state %)) actors)
        waiting (filter #(= :needs-agent (actor-state %)) actors)
        residents (filter #(= :resident (actor-state %)) actors)
        visible-actors (concat working waiting residents)]
    [:div.organism-map
     [:section.organism-layer
      [:header
       [:div [:small "ACTORS / ORGANS"] [:h3 "Tamakiを構成する役割"]]
       [:span (str (count actors) " actors · "
                   (count waiting) " agent待ち")]]
      [:div.actor-grid
       (for [actor visible-actors]
         ^{:key (:id actor)} [actor-card actor])]]
     [:section.organism-layer.loop-layer
      [:header
       [:div [:small "AGENT LOOPS"] [:h3 "objectiveを歩いている実行系"]]
       [:span (str (count loops) " active loops")]]
      [:div.loop-list
       (if (seq loops)
         (for [loop loops]
           ^{:key (run-id loop)} [loop-card snapshot loop])
         [:p.organism-empty "active loopはありません"])]]]))

(defn objective-title [snapshot]
  (or (some-> snapshot :projects first :objective)
      (some-> snapshot :organisms first :organism/objective)
      "成果物を通じて objective を前進させる"))

(defn scoped-objective-title [snapshot scope]
  (if (= scope :federation)
    (objective-title snapshot)
    (or (some (fn [organism]
                (when (= (get organism-remote scope)
                         (or (:organism/org organism) (:org organism)))
                  (or (:organism/objective organism) (:objective organism))))
              (:organisms snapshot))
        (str (name scope) " のobjectiveを前進させる"))))

(defn operations-board []
  (let [snapshot @(rf/subscribe [:snapshot])
        view @(rf/subscribe [:surface-view])
        scope @(rf/subscribe [:organism-scope])
        work-lens @(rf/subscribe [:work-lens])
        agents (filter #(project-in-scope? scope (agent-project %))
                       (:agents snapshot))
        actors (filter #(project-in-scope? scope (:project %))
                       (:actors snapshot))
        active-loops (filter #(and (active-loop? %)
                                   (project-in-scope? scope (agent-project %)))
                             (:loops snapshot))
        dynamics (:system-dynamics snapshot)
        repo-stats (filter #(project-in-scope? scope (:path %))
                           (:repo-stats snapshot))
        results (filter #(project-in-scope? scope (:project %))
                        (:results snapshot))
        active (filter #(not= :idle (heartbeat-state snapshot %)) agents)
        live-count (count (filter #(= :live (heartbeat-state snapshot %)) active))
        stale-count (count (filter #(= :stale (heartbeat-state snapshot %)) active))
        spawn-count (reduce + 0 (map #(or (:spawn %) 0) actors))
        stages (group-by #(work-stage snapshot %) active)
        stocks (into {} (map (juxt :id :value)) (:stocks dynamics))
        issue-backlog (if (= scope :federation)
                        (or (get stocks "issue-backlog") 0)
                        (reduce + 0 (map #(or (:issues-open %) 0) repo-stats)))
        review-backlog (if (= scope :federation)
                         (or (get stocks "review-queue") 0)
                         (reduce + 0 (map #(or (:patches-open %) 0) repo-stats)))]
    [:main {:class (str style/operations " " style/dads-operations
                        (when-not (#{:now :flow} view) " hidden"))}
     [:section.objective-strip
      [:div
       [:small "CURRENT OBJECTIVE"]
       [:strong (scoped-objective-title snapshot scope)]]
      [:div.objective-health
       [:span.scope-label (if (= scope :federation)
                            "ALL"
                            (str/upper-case (name scope)))]
       [:span.live-dot] (str live-count " live")
       (when (pos? stale-count)
         [:span.stale-count (str stale-count " stale")])]]
     [:section.kpi-strip
      (for [[title value unit tone]
            [["Execution" (str (count active) " / " (count active-loops))
              "agents / loops" "green"]
             ["Actors" (count actors) (str spawn-count " need agent") "violet"]]]
        ^{:key title}
        [:div {:class (str "board-kpi " tone)}
         [:small title]
         [:div [:strong value] [:em unit]]])]
     (if (= view :now)
       [:section.now-board
        [:div.section-heading
         [:div [:small "NOW"] [:h2 "Tamakiは今、何をしているか"]]
         [:span "heartbeatから実行状態を判定"]]
        [:div.linear-workspace
         [:aside.work-sidebar
          [:small "WORKSPACE"]
          [:button {:class (when (= work-lens :organism) "selected")
                    :on-click #(rf/dispatch [:work-lens :organism])}
           [:span "◎"] "Organism" [:b (count actors)]]
          [:button {:class (when (= work-lens :agents) "selected")
                    :on-click #(rf/dispatch [:work-lens :agents])}
           [:span "◉"] "Agent runs" [:b (count active)]]
          [:button {:class (when (= work-lens :actors) "selected")
                    :on-click #(rf/dispatch [:work-lens :actors])}
           [:span "◫"] "Actors" [:b (count actors)]]
          [:button {:class (when (= work-lens :loops) "selected")
                    :on-click #(rf/dispatch [:work-lens :loops])}
           [:span "◇"] "Agent loops" [:b (count active-loops)]]
          [:button.attention
           {:class (str "attention "
                        (when (= work-lens :attention) "selected"))
            :on-click #(rf/dispatch [:work-lens :attention])}
           [:span "△"] "Needs attention" [:b (+ spawn-count stale-count)]]
          [:div.sidebar-divider]
          [:small "VIEWS"]
          [:button {:on-click #(set-surface-view! :flow)}
           [:span "⌘"] "Flow"]
          [:button {:on-click #(set-surface-view! :three-d)}
           [:span "◇"] "3D view"]]
         [:section {:class (str "work-list " (name work-lens))}
          (case work-lens
            :organism
            [organism-view snapshot actors active-loops]

            :actors
            [:div.actor-grid.actor-grid-full
             (for [actor actors]
               ^{:key (:id actor)} [actor-card actor])]

            :loops
            [:div.loop-list.loop-list-full
             (if (seq active-loops)
               (for [loop active-loops]
                 ^{:key (run-id loop)} [loop-card snapshot loop])
               [:p.organism-empty "active loopはありません"])]

            :attention
            [:div.actor-grid.actor-grid-full
             (let [attention (filter #(#{:blocked :needs-agent}
                                        (actor-state %))
                                     actors)]
               (if (seq attention)
                 (for [actor attention]
                   ^{:key (:id actor)} [actor-card actor])
                 [:p.organism-empty "attention待ちはありません"]))]

            [:<>
             [:header.work-list-header
              [:span]
              [:span "Work item"]
              [:span "Project"]
              [:span "State"]
              [:span "Agent / model"]
              [:span "Updated"]]
             [:div.work-list-body
              (if (seq active)
                (for [agent (sort-by #(case (heartbeat-state snapshot %)
                                       :live 0 :quiet 1 :stale 2 3)
                                     active)]
                  ^{:key (run-id agent)} [work-row snapshot agent])
                [:div.board-empty
                 [:strong "現在liveなagent runはありません"]
                 [:span "Organism viewでresident actorsとactive loopsを確認できます"]])]])]]]
       [:section.flow-board
        [:div.section-heading
         [:div [:small "FLOW"] [:h2 "Issue → Source → Review → Integrated"]]
         [:span "成果物の状態で判断"]]
        [:div.flow-columns
         (for [stage [:discover :implement :review :blocked]
               :let [[title description] (get stage-copy stage)
                     cards (get stages stage)]]
           ^{:key stage}
           [:section.flow-column
            [:header [:b title] [:span (count cards)] [:small description]]
            [:div
             (if (seq cards)
               (for [agent cards]
                 ^{:key (run-id agent)} [work-card snapshot agent])
               [:p "empty"])]])]])]))

(defn set-garden-view! [view]
  (when-let [{:keys [camera controls]} @runtime]
    (let [[x y z target-y] (case view
                             :world [48 58 72 1.5]
                             :organism [11 10 16 4.5]
                             [28 34 42 1.5])]
      (three/set-position! camera x y z)
      (three/set-position! (.-target controls) 0 target-y 0)
      (.update ^js controls))))

(defn money [value]
  (if (number? value)
    (str "¥" (.toLocaleString value "ja-JP"))
    "N/A"))

(defn finance-card [title rows]
  [:section.finance-card
   [:h3 title]
   (for [[label value emphasis?] rows]
     ^{:key label}
     [:div {:class (str "finance-line" (when emphasis? " total"))}
      [:span label]
      [:strong (money value)]])])

(defn finance-panel []
  (let [snapshot @(rf/subscribe [:snapshot])
        view @(rf/subscribe [:surface-view])
        {:keys [status period organizations pl bs cf segments]} (:finance snapshot)
        observed? (= "observed" (label status "unavailable"))
        balanced? (and (number? (:balance-delta bs))
                       (zero? (:balance-delta bs)))]
    [:main {:class (str "finance-dashboard " style/dads-finance
                        (when (not= view :finance) " hidden"))}
     [:div.finance-title
      [:div
       [:small "FINANCIAL CONTROL"]
       [:h2 "Finance Dashboard"]]
      [:div.finance-period
       (if observed?
         (str (or period "latest") " · " organizations " organizations")
         "Accounting observation required")]]
     [:div.finance-kpis
      [:div.finance-kpi
       [:small "Revenue"] [:strong (money (:revenue pl))]]
      [:div.finance-kpi
       [:small "Operating profit"] [:strong (money (:operating-profit pl))]]
      [:div.finance-kpi
       [:small "Cash"] [:strong (money (or (:cash bs) (:ending-cash cf)))]]
      [:div.finance-kpi
       [:small "Balance check"]
        [:strong {:class (when (and observed? (not balanced?)) "warning")}
         (cond
           (not observed?) "N/A"
           balanced? "BALANCED"
           :else (money (:balance-delta bs)))]]]
     [:div.finance-segments
      (for [[segment title] [[:personal "Personal"]
                             [:corporate "Corporate"]
                             [:crypto "Crypto"]]
            :let [data (get segments segment)]]
        ^{:key segment}
        [:div.finance-segment
         [:span title]
         [:strong (money (:assets data))]
         [:small (str (or (:observations data) 0) " observations")]])]
     [:div.finance-statements
      [finance-card "P/L"
       [["Revenue" (:revenue pl)]
        ["Cost of sales" (:cost-of-sales pl)]
        ["Gross profit" (:gross-profit pl) true]
        ["Operating expenses" (:operating-expenses pl)]
        ["Operating profit" (:operating-profit pl) true]]]
      [finance-card "B/S"
       [["Cash" (:cash bs)]
        ["Receivables" (:receivables bs)]
        ["Total assets" (:assets bs) true]
        ["Liabilities" (:liabilities bs)]
        ["Equity" (:equity bs) true]]]
      [finance-card "Cash Flow"
       [["Operating" (:operating cf)]
        ["Investing" (:investing cf)]
        ["Financing" (:financing cf)]
        ["Ending cash" (:ending-cash cf) true]]]]
     (when-not observed?
       [:div.finance-empty
        "No verified ledger observation yet. Missing accounting values remain N/A."])]))

(defn shell-view []
  (let [view @(rf/subscribe [:surface-view])
        current-scope @(rf/subscribe [:organism-scope])
        three-d? (contains? #{:garden :three-d} view)
        view-button
        (fn [target text]
          [dds/button
           text
           {:type (if (= target view) :solid-fill :outline)
            :size "sm"
            :attrs {:data-selected (str (= target view))
                    :aria-pressed (= target view)
                    :on-click #(set-surface-view! target)}}])
        scope-button
        (fn [scope text]
          [dds/button
           text
           {:type (if (= scope current-scope) :solid-fill :outline)
            :size "xs"
            :attrs {:data-selected (str (= scope current-scope))
                    :aria-pressed (= scope current-scope)
                    :on-click #(select-organism! scope)}}])]
  [:div {:class (str style/app " " style/finance " " style/dads-shell
                     (when three-d? " surface-3d"))}
   [:canvas#scene {:class (str style/scene (when-not three-d? " surface-hidden"))}]
   [:div#effects {:class style/effects}]
   [:header {:class (if three-d?
                      (str style/glass " " style/header)
                      style/navigation)}
    (when-not three-d?
      [:p.workspace-eyebrow "ARTIFICIAL ORGANISM"])
    [:h1 "Tamaki Observatory"]
    [:div#metrics.metrics "Connecting…"]
    [:label "表示単位"
     [:select#grouping {:on-change group-mode-changed!}
      [:option {:value "org"} "組織"]
      [:option {:value "project"} "プロジェクト"]
      [:option {:value "sync"} "同期状態"]]]
    [:div.garden-views
     [:span "表示"]
     (view-button :now "現在の活動")
     (view-button :flow "成果フロー")
     (view-button :three-d "3D 生態系")
     (view-button :finance "会計")
     (when three-d?
       [:<>
        [dds/button "全体"
         {:type :text :size "xs"
          :attrs {:on-click #(set-garden-view! :world)}}]
        [dds/button "群体"
         {:type :text :size "xs"
          :attrs {:on-click #(set-garden-view! :colony)}}]
        [dds/button "個体"
         {:type :text :size "xs"
          :attrs {:on-click #(set-garden-view! :organism)}}]])]
    [:div#bonsai-state.bonsai-state
     [:b "Bonsai care"]
     [:span "Git treeを観察中…"]]
    [:nav.organism-scopes
     (for [[scope text] [[:federation "Federation"]
                         [:com-junkawasaki "Personal"]
                         [:etzhayyim "Etzhayyim"]
                         [:cloud-itonami "Cloud"]
                         [:jk-luxury "JK Luxury"]
                         [:gftdcojp "GFTD"]]]
       ^{:key scope}
       (scope-button scope text))]
    [:div.voice-row
     [dds/button "Tamaki に話す"
      {:type :solid-fill :size "sm" :id "voice-button"
       :attrs {:on-click start-voice!}}]
     [:span#voice-status.voice-status
      "voice intent → supervisor queue"]
     [dds/button "♫ ambient off"
      {:type :outline :size "sm" :id "sound-toggle"
       :attrs {:on-click toggle-sound!}}]]
    [:div#actor-state.actor-state]
    [:div#model-usage.model-usage]]
   [operations-board]
   [finance-panel]
   [:aside#inspector {:class (str style/glass " " style/inspector
                                  (when-not three-d? " surface-hidden"))}
    [:h2 "Workspace"]
    [:div#details.details "Select a repository tile"]
    [:h2 "Source / PR results"]
    [result-panel]
    [:h2.activity-title "Live activity"]
    [activity-panel]]
   [:section#system-dynamics
    {:class (str style/glass " " style/dynamics
                 (when-not three-d? " surface-hidden"))}
    [:b "System dynamics"] [:span "Connecting…"]]
   [:div#legend {:class (str style/glass " " style/legend
                            (when-not three-d? " surface-hidden"))}
    "drag rotate · wheel zoom · click repo/agent" [:br]
    [:span.live "◆ walking agent actor"] "　"
    [:span.sync "● synced"] "　"
    [:span.diff "● west Δ"] "　"
    [:span.loop "○ loop"] [:br]
    "animated actor beam = active repo · tile height = congestion"]]))

(defn mount-shell! []
  (rdom/render [shell-view] (.getElementById js/document "app")))

(defn refresh-css! []
  (when-let [link (.querySelector js/document "link[href^='style.css']")]
    (set! (.-href link) (str "style.css?t=" (.now js/Date)))))

(defn dispose-scene! []
  (when-let [stop (:stop @runtime)] (stop))
  (when-let [root (:repo-root @runtime)] (clear! root))
  (when-let [renderer (:renderer @runtime)] (.dispose ^js renderer))
  (reset! runtime nil))

(defn install-picking! [canvas camera repo-root]
  (let [raycaster (THREE/Raycaster.)
        pointer (THREE/Vector2.)]
    (.addEventListener
     canvas "click"
     (fn [event]
       (play-sfx! :select)
       (spawn-pulse! (.-clientX event) (.-clientY event))
       (let [rect (.getBoundingClientRect canvas)]
         (set! (.-x pointer) (- (* (/ (- (.-clientX event) (.-left rect))
                                      (.-width rect)) 2) 1))
         (set! (.-y pointer) (- 1 (* (/ (- (.-clientY event) (.-top rect))
                                        (.-height rect)) 2)))
         (.setFromCamera raycaster pointer camera)
         (when-let [hit (first (array-seq
                                (.intersectObjects raycaster
                                                   (.-children repo-root) true)))]
           (if-let [repos (.. ^js hit -object -userData -repos)]
             (when-let [repo (aget repos (.-instanceId hit))]
               (rf/dispatch-sync [:select-repo
                                  (js->clj repo :keywordize-keys true)])
               (apply-state!))
             (when-let [repo (.. ^js hit -object -userData -repo)]
               (rf/dispatch-sync [:select-repo
                                  (js->clj repo :keywordize-keys true)])
               (apply-state!)))))))))

(defn init-scene! []
  (let [canvas (.getElementById js/document "scene")
        scene (three/scene)
        camera (three/perspective-camera (/ (.-innerWidth js/window)
                                             (.-innerHeight js/window)))
        renderer (three/webgl-renderer canvas {:antialias true :alpha false})
        controls (OrbitControls. camera canvas)
        repo-root (three/group)]
    (three/set-clear-color! renderer 0x030a08 1)
    (set! (.-fog scene) (THREE/FogExp2. 0x030a08 0.012))
    (three/set-pixel-ratio! renderer (min 2 (.-devicePixelRatio js/window)))
    (three/set-size! renderer (.-innerWidth js/window) (.-innerHeight js/window))
    (three/set-position! camera 28 34 42)
    (.lookAt ^js camera (THREE/Vector3. 0 0 0))
    (set! (.-enableDamping controls) true)
    (set! (.-dampingFactor controls) 0.07)
    (set! (.-minDistance controls) 5)
    (set! (.-maxDistance controls) 120)
    (.add scene (three/ambient-light 0xa9ffd0 1.25))
    (let [light (three/directional-light 0xffffff 3.2)]
      (three/set-position! light 10 20 12)
      (.add scene light))
    (let [ground (THREE/Mesh.
                  (THREE/CircleGeometry. 62 96)
                  (THREE/MeshStandardMaterial.
                   #js {:color 0x07120e :roughness 0.96
                        :metalness 0.0 :transparent true :opacity 0.9}))
          lineage-ring (THREE/Mesh.
                        (THREE/TorusGeometry. 8.2 0.035 8 96)
                        (THREE/MeshBasicMaterial.
                         #js {:color 0xd9b85c :transparent true
                              :opacity 0.28}))]
      (three/set-rotation-x! ground (- (/ js/Math.PI 2)))
      (three/set-position! ground 0 -0.66 0)
      (three/set-rotation-x! lineage-ring (/ js/Math.PI 2))
      (.add scene ground)
      (.add scene lineage-ring))
    (.add scene repo-root)
    (reset! runtime {:scene scene :camera camera :renderer renderer
                     :controls controls :repo-root repo-root})
    (install-picking! canvas camera repo-root)
    (.addEventListener js/window "resize"
      (fn []
        (set! (.-aspect camera) (/ (.-innerWidth js/window)
                                   (.-innerHeight js/window)))
        (.updateProjectionMatrix ^js camera)
        (three/set-size! renderer (.-innerWidth js/window)
                         (.-innerHeight js/window))))
    (let [stop
          (three/start-loop!
           renderer scene camera
           (fn [{:keys [time]}]
             (.update ^js controls)
             (doseq [{:keys [object from to phase speed]}
                     (:flow-particles @runtime)]
               (let [progress (mod (+ phase (* time speed)) 1)]
                 (.lerpVectors ^js (.-position ^js object)
                               ^js from ^js to progress)
                 (let [pulse (+ 0.75
                                (* 0.5 (js/Math.sin
                                        (* progress js/Math.PI))))]
                   (three/set-scale! object pulse pulse pulse))))
             (doseq [{:keys [object curve phase speed kind base-scale]}
                     (:tree-animations @runtime)]
               (case kind
                 :sap
                 (let [progress (mod (+ phase (* time speed)) 1)
                       point (.getPointAt ^js curve progress)
                       pulse (+ 0.7 (* 0.65
                                         (js/Math.sin (* progress js/Math.PI))))]
                   (.copy (.-position ^js object) point)
                   (three/set-scale! object pulse pulse pulse))

                 :fruit
                 (let [pulse (* base-scale
                                (+ 1 (* 0.16
                                        (js/Math.sin
                                         (+ phase (* time 2.7))))))]
                   (three/set-scale! object pulse pulse pulse)
                   (set! (.. object -rotation -y) (+ phase (* time 0.28))))

                 :canopy
                 (do
                   (set! (.. object -rotation -z)
                         (* 0.025 (js/Math.sin (+ phase (* time 0.72)))))
                   (set! (.. object -rotation -x)
                         (* 0.018 (js/Math.cos (+ phase (* time 0.61))))))
                 :prune
                 (do
                   (set! (.. object -rotation -z) (+ phase (* time 0.55)))
                   (let [pulse (+ 0.9 (* 0.18
                                         (js/Math.sin
                                          (+ phase (* time 2.2)))))]
                     (three/set-scale! object pulse pulse pulse)))
                 nil))
             (doseq [{:keys [actor head left-arm right-arm left-leg right-leg
                             beacon base phase radius speed working?]}
                     (:actor-animations @runtime)]
               (let [walk (+ phase (* time speed))
                     stride (js/Math.sin (* walk 5))
                     x (+ (.-x base) (* radius (js/Math.cos walk)))
                     z (+ (.-z base) (* radius (js/Math.sin walk)))
                     y (+ (.-y base)
                          (* 0.08 (js/Math.abs
                                   (js/Math.sin (* walk 5)))))]
                 (three/set-position! actor x y z)
                 (set! (.. actor -rotation -y) (- walk (/ js/Math.PI 2)))
                 (set! (.. left-arm -rotation -x) (* 0.65 stride))
                 (set! (.. right-arm -rotation -x) (* -0.65 stride))
                 (set! (.. left-leg -rotation -x) (* -0.55 stride))
                 (set! (.. right-leg -rotation -x) (* 0.55 stride))
                 (set! (.. head -rotation -y)
                       (* 0.18 (js/Math.sin (* time 1.7))))
                 (set! (.. beacon -rotation -z)
                       (+ (* time (if working? 1.8 0.35)) phase))
                 (let [pulse (if working?
                               (+ 1 (* 0.12 (js/Math.sin (* time 4))))
                               0.82)]
                   (three/set-scale! beacon pulse pulse pulse))))))]
      (swap! runtime assoc :stop stop))))

(defn ^:export init []
  (rf/dispatch-sync [:snapshot nil])
  (rf/dispatch-sync [:group-mode :org])
  (rf/dispatch-sync [:organism-scope :federation])
  (rf/dispatch-sync [:activity-agent "all"])
  (mount-shell!)
  (set! (.-tamakiReceive js/window)
        (fn [snapshot]
          (let [value (js->clj snapshot :keywordize-keys true)]
            (index-datoms! value)
            (rf/dispatch-sync [:snapshot value]))
          (apply-state!))))

(defn ^:dev/after-load hot-reload! []
  (refresh-css!)
  (dispose-scene!)
  (mount-shell!)
  (reset! topology-signature nil)
  (when (three-d-view? @(rf/subscribe [:surface-view]))
    (init-scene!)
    (apply-state!)))
