(ns brep.step
  "ISO 10303-21 (STEP physical file) reader/writer, AP203-style
  MANIFOLD_SOLID_BREP entities. Scope: PLANE-faced solids with straight
  (LINE) edges only — exactly what brep.kernel/make-box produces. The
  entity mapping (CARTESIAN_POINT/DIRECTION/VECTOR/AXIS2_PLACEMENT_3D/
  LINE/PLANE/VERTEX_POINT/EDGE_CURVE/ORIENTED_EDGE/EDGE_LOOP/FACE_BOUND/
  ADVANCED_FACE/CLOSED_SHELL/MANIFOLD_SOLID_BREP) and parameter order were
  checked against steptools.com's reference AP203 sample `block.stp` (a
  real, third-party-authored file, not just this namespace's own
  round-trip) before writing this.

  brep.kernel/make-cylinder is NOT supported — it represents a cylinder
  as a polygon-approximated prism internally (see its docstring), and
  faithfully re-expressing that as STEP's analytic CYLINDRICAL_SURFACE +
  CIRCLE entities needs more design than writing already-analytic
  PLANE/LINE topology straight through. Left for follow-up.

  read-step is NOT a general-purpose STEP parser — it only recognizes
  the entity subset write-step emits. A third-party STEP file using
  INTERSECTION_CURVE, B_SPLINE_SURFACE, or any AP203 entity this doesn't
  emit will not parse (throws ex-info naming the unhandled entity)."
  (:require [clojure.string :as str]
            [brep.kernel :as k]))

;; ─────────────────────────────── write ───────────────────────────────

(defn- fmt-num
  "STEP REAL literals always show a decimal point (\"5.\" not \"5\")."
  [x]
  (let [x (double x)]
    (if (== x (Math/floor x))
      (str (long x) ".")
      (str x))))

(defn- fmt-triple [[x y z]] (str "(" (fmt-num x) "," (fmt-num y) "," (fmt-num z) ")"))

(defn write-step
  "Serialize `[solid edges vertices]` (make-box's return shape; single
  shell, :plane faces, :line edges only) to ISO 10303-21 STEP text.
  Throws ex-info if a face isn't :plane or an edge's curve isn't :line."
  [solid edges vertices]
  (let [next-id (atom 0)
        alloc! (fn [] (swap! next-id inc))
        out (atom [])
        emit! (fn [id entity params] (swap! out conj (str "#" id "=" entity "(" params ");")) id)
        cartesian-point! (fn [pt] (let [id (alloc!)] (emit! id "CARTESIAN_POINT" (str "''," (fmt-triple pt)))))
        direction! (fn [dir] (let [id (alloc!)] (emit! id "DIRECTION" (str "''," (fmt-triple dir)))))
        edge-map (into {} (map (fn [e] [(:id e) e])) edges)
        vertex-point-ids
        (into {}
              (map (fn [v]
                     [(:id v) (let [pid (cartesian-point! (:point v))
                                    vid (alloc!)]
                                (emit! vid "VERTEX_POINT" (str "'',#" pid))
                                vid)]))
              vertices)
        edge-curve-ids
        (into {}
              (map (fn [[eid e]]
                     (let [{:keys [curve start-vertex end-vertex]} e]
                       (when (not= :line (:kind curve))
                         (throw (ex-info "write-step: only :line edges are supported" {:edge e})))
                       [eid (let [origin-id (cartesian-point! (:origin curve))
                                  dir-id (direction! (k/v-normalize (:direction curve)))
                                  vec-id (alloc!)
                                  _ (emit! vec-id "VECTOR" (str "'',#" dir-id ",1."))
                                  line-id (alloc!)
                                  _ (emit! line-id "LINE" (str "'',#" origin-id ",#" vec-id))
                                  ec-id (alloc!)]
                              (emit! ec-id "EDGE_CURVE"
                                     (str "'',#" (get vertex-point-ids start-vertex) ",#"
                                          (get vertex-point-ids end-vertex) ",#" line-id ",.T."))
                              ec-id)])))
              edge-map)
        shell (first (:shells solid))
        face-ids
        (mapv
         (fn [face]
           (let [{:keys [surface wires]} face
                 _ (when (not= :plane (:kind surface))
                     (throw (ex-info "write-step: only :plane faces are supported" {:face face})))
                 wire (first wires)
                 origin-id (cartesian-point! (:origin surface))
                 axis-id (direction! (k/v-normalize (:normal surface)))
                 placement-id (emit! (alloc!) "AXIS2_PLACEMENT_3D" (str "'',#" origin-id ",#" axis-id ",$"))
                 plane-id (emit! (alloc!) "PLANE" (str "'',#" placement-id))
                 oriented-ids
                 (loop [eids wire cur nil acc []]
                   (if (empty? eids)
                     acc
                     (let [eid (first eids)
                           e (get edge-map eid)
                           forward? (or (nil? cur) (= cur (:start-vertex e)))
                           oid (emit! (alloc!) "ORIENTED_EDGE"
                                      (str "'',*,*,#" (get edge-curve-ids eid) "," (if forward? ".T." ".F.")))]
                       (recur (rest eids)
                              (if forward? (:end-vertex e) (:start-vertex e))
                              (conj acc oid)))))
                 loop-id (emit! (alloc!) "EDGE_LOOP"
                                (str "''," "(" (str/join "," (map #(str "#" %) oriented-ids)) ")"))
                 bound-id (emit! (alloc!) "FACE_BOUND" (str "'',#" loop-id ",.T."))]
             (emit! (alloc!) "ADVANCED_FACE" (str "'',(#" bound-id "),#" plane-id ",.T."))))
         (:faces shell))
        shell-id (emit! (alloc!) "CLOSED_SHELL"
                         (str "''," "(" (str/join "," (map #(str "#" %) face-ids)) ")"))
        _solid-id (emit! (alloc!) "MANIFOLD_SOLID_BREP" (str "'',#" shell-id))]
    (str "ISO-10303-21;\n"
         "HEADER;\n"
         "FILE_DESCRIPTION((''),'2;1');\n"
         "FILE_NAME('','',(''),(''),'kotoba-lang/brep','','');\n"
         "FILE_SCHEMA(('CONFIG_CONTROL_DESIGN'));\n"
         "ENDSEC;\n"
         "DATA;\n"
         (str/join "\n" @out) "\n"
         "ENDSEC;\n"
         "END-ISO-10303-21;\n")))

;; ─────────────────────────────── read ───────────────────────────────

(defn- split-statements
  "Split STEP DATA-section text into top-level `#N=NAME(...);` statement
  strings — paren-depth-aware so nested lists (point tuples, edge-ref
  lists) don't break the split."
  [text]
  (loop [chars (seq text) depth 0 current [] acc []]
    (if (empty? chars)
      (if (seq (remove #{\space \tab \newline \return \formfeed} current))
        (conj acc (str/join current))
        acc)
      (let [c (first chars)]
        (cond
          (= c \() (recur (rest chars) (inc depth) (conj current c) acc)
          (= c \)) (recur (rest chars) (dec depth) (conj current c) acc)
          (and (= c \;) (zero? depth))
          (recur (rest chars) depth [] (conj acc (str/join current)))
          :else (recur (rest chars) depth (conj current c) acc))))))

(defn- split-top-level
  "Split a comma-separated parameter-list string at top-level commas only
  (not commas nested inside parens or quoted strings)."
  [s]
  (loop [chars (seq s) depth 0 in-str? false current [] acc []]
    (if (empty? chars)
      (conj acc (str/join current))
      (let [c (first chars)]
        (cond
          (and (= c \') (not in-str?)) (recur (rest chars) depth true (conj current c) acc)
          (and (= c \') in-str?) (recur (rest chars) depth false (conj current c) acc)
          in-str? (recur (rest chars) depth in-str? (conj current c) acc)
          (= c \() (recur (rest chars) (inc depth) in-str? (conj current c) acc)
          (= c \)) (recur (rest chars) (dec depth) in-str? (conj current c) acc)
          (and (= c \,) (zero? depth))
          (recur (rest chars) depth in-str? [] (conj acc (str/join current)))
          :else (recur (rest chars) depth in-str? (conj current c) acc))))))

(defn- parse-param
  "Parse one raw STEP parameter token into a Clojure value: nil for `$`,
  a keyword `:*` for the unset-orientation placeholder `*`, boolean for
  `.T.`/`.F.`, `[:ref n]` for `#n`, a string for `'...'`, a vector for a
  parenthesized list (recursively parsed), a double for a bare number."
  [raw]
  (let [s (str/trim raw)]
    (cond
      (= s "$") nil
      (= s "*") :*
      (= s ".T.") true
      (= s ".F.") false
      (str/starts-with? s "#") [:ref (parse-long (subs s 1))]
      (and (str/starts-with? s "'") (str/ends-with? s "'"))
      (subs s 1 (dec (count s)))
      (and (str/starts-with? s "(") (str/ends-with? s ")"))
      (mapv parse-param (split-top-level (subs s 1 (dec (count s)))))
      (re-matches #"[+-]?(?:[0-9]+\.?[0-9]*|\.[0-9]+)([eE][+-]?[0-9]+)?" s) (parse-double s)
      :else (keyword s))))

(defn- parse-statement
  "`#N=ENTITY(name-param, real-params...);` -> [n [entity-name
  [real-param ...]]] (a map-entry pair, id -> [entity-name params]) --
  every AP203 entity's first parameter is a STEP \"name\" string (always
  `''` in what write-step emits), stripped here so every entity's
  `params` is just its meaningful attributes, uniformly. Returns nil if
  `stmt` isn't an entity-instance statement (header entities, etc.)."
  [stmt]
  (when-let [[_ n-str entity params-str]
             (re-matches #"(?s)\s*#(\d+)\s*=\s*([A-Z0-9_]+)\s*\((.*)\)\s*" (str/trim stmt))]
    [(parse-long n-str) [entity (vec (rest (mapv parse-param (split-top-level params-str))))]]))

(defn- data-section-text [step-text]
  (let [after-data (second (str/split step-text #"(?m)^\s*DATA;\s*$" 2))]
    (when-not after-data
      (throw (ex-info "read-step: no DATA; section found" {})))
    (first (str/split after-data #"(?m)^\s*ENDSEC;\s*$" 2))))

(defn- entity-table [step-text]
  (into {}
        (keep parse-statement)
        (split-statements (data-section-text step-text))))

(defn- ref->id [[tag n]] (when (= tag :ref) n))

(defn read-step
  "Parse STEP `text` (as written by write-step, or any AP203 file using
  only the same entity subset) back into `[solid edges vertices]`.
  Requires exactly one MANIFOLD_SOLID_BREP entity. Throws ex-info naming
  any referenced entity kind this reader doesn't recognize."
  [text]
  (let [entities (entity-table text)
        get-entity (fn [id] (or (get entities id) (throw (ex-info "read-step: unresolved #id" {:id id}))))
        point-cache (atom {})
        read-point! (fn [id]
                      (or (get @point-cache id)
                          (let [[entity-name params] (get-entity id)
                                _ (when (not= "CARTESIAN_POINT" entity-name)
                                    (throw (ex-info "read-step: expected CARTESIAN_POINT" {:id id :entity-name entity-name})))
                                pt (vec (first params))]
                            (swap! point-cache assoc id pt)
                            pt)))
        read-direction (fn [id]
                         (let [[entity-name params] (get-entity id)]
                           (when (not= "DIRECTION" entity-name)
                             (throw (ex-info "read-step: expected DIRECTION" {:id id :entity-name entity-name})))
                           (vec (first params))))
        [msb-id [_msb-name msb-params]]
        (or (first (filter (fn [[_ [n _]]] (= n "MANIFOLD_SOLID_BREP")) entities))
            (throw (ex-info "read-step: no MANIFOLD_SOLID_BREP entity found" {})))
        shell-id (ref->id (first msb-params))
        [_shell-name shell-params] (get-entity shell-id)
        face-ids (mapv ref->id (first shell-params))
        vertex-points (atom {}) ; brep vertex-id (== STEP VERTEX_POINT id) -> point
        edges-out (atom {})     ; brep edge-id (== STEP EDGE_CURVE id) -> brep-edge
        faces-out
        (mapv
         (fn [face-id]
           (let [[face-entity face-params] (get-entity face-id)
                 _ (when (not= "ADVANCED_FACE" face-entity)
                     (throw (ex-info "read-step: expected ADVANCED_FACE" {:id face-id})))
                 [bound-refs plane-ref _sense] face-params
                 bound-id (ref->id (first bound-refs))
                 [_fb-name fb-params] (get-entity bound-id)
                 loop-id (ref->id (first fb-params))
                 [_el-name el-params] (get-entity loop-id)
                 oriented-refs (mapv ref->id (first el-params))
                 wire
                 (mapv
                  (fn [oe-id]
                    (let [[_oe-name oe-params] (get-entity oe-id)
                          ec-id (ref->id (nth oe-params 2))]
                      (when-not (contains? @edges-out ec-id)
                        (let [[_ec-name ec-params] (get-entity ec-id)
                              [v1-ref v2-ref line-ref _same-sense] ec-params
                              v1-id (ref->id v1-ref) v2-id (ref->id v2-ref)
                              [_v1-name v1-params] (get-entity v1-id)
                              [_v2-name v2-params] (get-entity v2-id)
                              _ (swap! vertex-points assoc v1-id (read-point! (ref->id (first v1-params))))
                              _ (swap! vertex-points assoc v2-id (read-point! (ref->id (first v2-params))))
                              line-id (ref->id line-ref)
                              [line-entity line-params] (get-entity line-id)
                              _ (when (not= "LINE" line-entity)
                                  (throw (ex-info "read-step: only LINE curves are supported" {:id line-id :entity-name line-entity})))
                              [origin-ref vec-ref] line-params
                              origin (read-point! (ref->id origin-ref))
                              [_vec-name vec-params] (get-entity (ref->id vec-ref))
                              dir (read-direction (ref->id (first vec-params)))]
                          (swap! edges-out assoc ec-id
                                 (k/brep-edge ec-id (k/line-curve origin dir) v1-id v2-id
                                              [0.0 (k/v-distance (get @vertex-points v1-id) (get @vertex-points v2-id))]))))
                      ec-id))
                  oriented-refs)
                 plane-id (ref->id plane-ref)
                 [plane-entity plane-params] (get-entity plane-id)
                 _ (when (not= "PLANE" plane-entity)
                     (throw (ex-info "read-step: only PLANE surfaces are supported" {:id plane-id :entity-name plane-entity})))
                 placement-id (ref->id (first plane-params))
                 [_ap-name ap-params] (get-entity placement-id)
                 [origin-ref axis-ref] ap-params
                 origin (read-point! (ref->id origin-ref))
                 normal (read-direction (ref->id axis-ref))]
             (k/brep-face face-id (k/plane-surface origin normal) [wire] :forward)))
         face-ids)
        shell (k/brep-shell shell-id faces-out :forward)
        solid (k/brep-solid msb-id [shell])
        vertices (mapv (fn [[vid pt]] (k/brep-vertex vid pt)) @vertex-points)]
    [solid (vec (vals @edges-out)) vertices]))
