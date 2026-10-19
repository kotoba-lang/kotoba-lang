(ns kudaki.mesh
  "Model representation and lumped-mass assembly.

  A model is plain data (so a host can serialize / checkpoint / diff it):

      {:nodes     {id [x y z] …}                 ; reference coordinates
       :elements  [{:type :truss :nodes [a b]    ; connectivity (node ids)
                    :mat <mat-id> :section {:area A}} …]
       :materials {<mat-id> {:type … :E … :density …}}}

  The integrator needs three things from the mesh and nothing else: where the
  nodes are, how many degrees of freedom there are, and the diagonal (LUMPED)
  mass at each node. Lumping — putting the element mass straight onto its nodes
  instead of building a consistent mass matrix — is the entire reason explicit
  integration needs no linear solve: M is diagonal, so a = M⁻¹ f is elementwise.

  Mass assembly is ρ·V per element distributed equally to its nodes:
    • truss  V = A · L₀         (section area × reference length)
    • hex    V = 8 · det J(0)   (one-point rule: cell Jacobian at the centroid)
  Each node accumulates contributions from every element it touches; the result
  is one scalar mass per node, shared by its three translational DOFs."
  (:require [kudaki.linalg :as la]))

(defn node-ids
  "Stable, sorted vector of node ids — fixes the DOF ordering for the run."
  [model]
  (vec (sort (keys (:nodes model)))))

(defn coord
  "Reference coordinates [x y z] of node `id`."
  [model id]
  (get-in model [:nodes id]))

(defn dof-count
  "Total translational DOF count = 3 × number of nodes."
  [model]
  (* 3 (count (:nodes model))))

;; --- element geometry / volume -------------------------------------------

;; Local isoparametric coordinates of the 8 hex corners, standard ordering:
;;  bottom face z=−1: 1234 ccw, top face z=+1: 5678 ccw above them.
(def hex-local
  [[-1.0 -1.0 -1.0] [1.0 -1.0 -1.0] [1.0 1.0 -1.0] [-1.0 1.0 -1.0]
   [-1.0 -1.0  1.0] [1.0 -1.0  1.0] [1.0 1.0  1.0] [-1.0 1.0  1.0]])

(defn hex-jacobian
  "Isoparametric Jacobian J = ∂x/∂ξ of an 8-node hex at the CENTROID (ξ=η=ζ=0),
  as a flat row-major 3×3. At the centroid ∂Nₐ/∂ξⱼ = ⅛ ξₐⱼ, so J is just ⅛ Σ
  (corner coord) ⊗ (local sign). `xs` is the 8-vector of corner coordinates."
  [xs]
  (let [acc (double-array 9)]
    (dotimes [a 8]
      (let [xa (nth xs a)
            la (nth hex-local a)]
        (dotimes [i 3]
          (dotimes [j 3]
            (let [k (+ (* 3 i) j)]
              (aset acc k (+ (aget acc k)
                             (* 0.125 (nth la j) (nth xa i)))))))))
    (vec (map #(aget acc %) (range 9)))))

(defn element-volume
  "Reference volume of an element. Truss: A·L₀. Hex: 8·det J at the centroid
  (the one-point integration weight)."
  [model el]
  (let [xs (mapv #(coord model %) (:nodes el))]
    (case (:type el)
      :truss (let [[a b] xs
                   L (la/norm (la/v- b a))]
               (* (get-in el [:section :area] 1.0) L))
      :hex   (* 8.0 (la/det3 (hex-jacobian xs))))))

(defn lumped-mass
  "Assemble the lumped nodal mass: map node-id → scalar mass. Each element
  contributes ρ·V split equally over its nodes; a node touched by several
  elements sums their shares. ρ is read from the element's material. This is the
  diagonal of M; the integrator inverts it by simple reciprocal."
  [model]
  (reduce
   (fn [m el]
     (let [rho (get-in model [:materials (:mat el) :density] 1.0)
           v   (element-volume model el)
           nn  (count (:nodes el))
           per (/ (* rho v) nn)]
       (reduce (fn [m id] (update m id (fnil + 0.0) per)) m (:nodes el))))
   {}
   (:elements model)))

(defn total-mass
  "Sum of the lumped nodal masses — a sanity check against ρ·Σ V."
  [model]
  (reduce + (vals (lumped-mass model))))
