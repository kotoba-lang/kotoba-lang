(ns brep.assembly
  "Assembly management: part instances, constraints, BOM extraction.
  Restored from kami-cad's `assembly` module (deleted PR #82). A world
  transform (`DAffine3` in the original) is opaque data here — assembly
  `solve` only validates constraint instance references, it never reads
  transform contents (matches the original: the constraint solver itself
  is a documented TODO, iterative Newton-Raphson on 6-DOF transforms).")

(defn affine3-identity [] :identity)

(defn part-ref [solid-id name] {:solid-id solid-id :name name})

(defn part-instance [id part-ref transform name]
  {:id id :part-ref part-ref :transform transform :name name :suppressed false})

;; AssemblyConstraint variants
(defn mate-constraint [instance-a face-a instance-b face-b]
  {:kind :mate :instance-a instance-a :face-a face-a :instance-b instance-b :face-b face-b})
(defn align-constraint [instance-a face-a instance-b face-b]
  {:kind :align :instance-a instance-a :face-a face-a :instance-b instance-b :face-b face-b})
(defn insert-constraint [instance-a face-a instance-b face-b]
  {:kind :insert :instance-a instance-a :face-a face-a :instance-b instance-b :face-b face-b})
(defn angle-constraint [instance-a face-a instance-b face-b angle]
  {:kind :angle :instance-a instance-a :face-a face-a :instance-b instance-b :face-b face-b :angle angle})
(defn distance-constraint [instance-a face-a instance-b face-b distance]
  {:kind :distance :instance-a instance-a :face-a face-a :instance-b instance-b :face-b face-b :distance distance})

(defn bom-entry [part-name quantity] {:part-name part-name :quantity quantity})

(defn assembly
  "A fresh, empty assembly named `name`."
  [name]
  {:name name :instances [] :constraints [] :next-instance-id 1})

(defn add-instance
  "Add a part instance at `transform`. Returns `[id assembly']`."
  [asm part-ref transform name]
  (let [id (:next-instance-id asm)]
    [id (-> asm
            (update :instances conj (part-instance id part-ref transform name))
            (update :next-instance-id inc))]))

(defn add-constraint [asm constraint] (update asm :constraints conj constraint))

(defn- constraint-instances [c] [(:instance-a c) (:instance-b c)])

(defn solve
  "Validate all constraints reference existing instances. Returns
  `[:ok asm]` or `[:error msg]` (asm unchanged — matches the original:
  validation only, no actual transform solving yet)."
  [asm]
  (let [ids (into #{} (map :id (:instances asm)))]
    (loop [constraints (:constraints asm)]
      (if (empty? constraints)
        [:ok asm]
        (let [[a b] (constraint-instances (first constraints))]
          (cond
            (not (ids a)) [:error (str "instance " a " not found")]
            (not (ids b)) [:error (str "instance " b " not found")]
            :else (recur (rest constraints))))))))

(defn get-bom
  "Bill of materials: non-suppressed instances aggregated by part name,
  sorted alphabetically."
  [asm]
  (let [counts (reduce (fn [m inst]
                          (if (:suppressed inst)
                            m
                            (update m (:name (:part-ref inst)) (fnil inc 0))))
                        {} (:instances asm))]
    (vec (sort-by :part-name (map (fn [[name qty]] (bom-entry name qty)) counts)))))

(defn instances [asm] (:instances asm))
(defn constraints [asm] (:constraints asm))
(defn active-count [asm] (count (remove :suppressed (:instances asm))))
