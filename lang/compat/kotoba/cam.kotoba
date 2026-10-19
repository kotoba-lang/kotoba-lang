(ns kotoba.cam
  "KAMI CAM — Computer-Aided Manufacturing domain logic: toolpath
   generation, G-code output, tool library, and stock/material definitions.

   Ported from the Rust `kami-cam` crate (kotoba-lang/kami-engine — deleted
   from the working tree but never committed; recovered from git history
   per ADR-2607010000, the kotoba-runtime-sdk cljc migration). Geometry
   input is a minimal internal DVec3-equivalent (`kotoba.cam.vec3`) to avoid
   a circular dependency on a CAD namespace, mirroring the original crate's
   rationale.

   Sub-namespaces:
   - kotoba.cam.vec3      minimal 3D vector value type + pure math
   - kotoba.cam.util      portable fixed-point formatting / math helpers
   - kotoba.cam.tool      cutting tool + tool library (pure, persistent)
   - kotoba.cam.stock     workpiece stock shapes + material presets
   - kotoba.cam.toolpath  CAM operations -> toolpath segments (CamJob)
   - kotoba.cam.gcode     toolpath segments -> G-code text

   No network/IO in any of the above — pure data + functions only."
  (:require [kotoba.cam.vec3]
            [kotoba.cam.util]
            [kotoba.cam.tool]
            [kotoba.cam.stock]
            [kotoba.cam.toolpath]
            [kotoba.cam.gcode]))

(def version "0.1.0")
