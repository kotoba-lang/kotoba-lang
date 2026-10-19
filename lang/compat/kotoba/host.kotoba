(ns kotoba.host
  "Facade re-exporting `kami.host` (SSoT, ADR-2607102200 addendum 8).

   Browser WASM/ECS host for compiled kami games. Sibling ns in this package:
   `kami.input`, `kami.ui`, `kami.audio`."
  (:require [kami.host :as impl]))

(def new-state     impl/new-state)
(def import-object impl/import-object)
(def instantiate!  impl/instantiate!)
(def tick!         impl/tick!)
(def snapshot      impl/snapshot)
(def globals       impl/globals)

;; Rigid-body-2d ECS system (physics-2d wired through kotoba.physics.contract)
(def attach-rigid-body!     impl/attach-rigid-body!)
(def detach-rigid-body!     impl/detach-rigid-body!)
(def set-rigid-body-gravity! impl/set-rigid-body-gravity!)
(def rigid-body-ids         impl/rigid-body-ids)
(def step-rigid-bodies!     impl/step-rigid-bodies!)
