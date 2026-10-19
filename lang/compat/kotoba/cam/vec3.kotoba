(ns kotoba.cam.vec3
  "Minimal 3D vector value type + pure math, standing in for `glam::DVec3`
   used throughout the ported Rust `kami-cam` crate. Plain maps `{:x :y :z}`
   — no external linear-algebra dependency, keeping this portable `.cljc`
   across JVM / ClojureScript / SCI / GraalVM/WASM."
  (:require [kotoba.cam.util :as util]))

(defn v3
  "Construct a vector, coercing components to doubles."
  [x y z]
  {:x (double x) :y (double y) :z (double z)})

(def zero (v3 0.0 0.0 0.0))

(defn add [a b] (v3 (+ (:x a) (:x b)) (+ (:y a) (:y b)) (+ (:z a) (:z b))))
(defn sub [a b] (v3 (- (:x a) (:x b)) (- (:y a) (:y b)) (- (:z a) (:z b))))

(defn vmin [a b]
  (v3 (min (:x a) (:x b)) (min (:y a) (:y b)) (min (:z a) (:z b))))

(defn vmax [a b]
  (v3 (max (:x a) (:x b)) (max (:y a) (:y b)) (max (:z a) (:z b))))

(defn length [v]
  (util/sqrt (+ (* (:x v) (:x v)) (* (:y v) (:y v)) (* (:z v) (:z v)))))
