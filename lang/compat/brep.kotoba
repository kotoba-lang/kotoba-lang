(ns brep
  "KAMI CAD kernel — BREP solid modeling, parametric feature tree, assembly
  management, tessellation. Restored from the legacy kami-engine/kami-cad
  Rust crate (deleted in kotoba-lang/kami-engine PR #82 'Remove Rust
  workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  Named `brep` (not `cad`) to avoid collision with the pre-existing,
  unrelated kotoba-lang/cad industrial CAD/CAM workbench (artifact
  registry + policy-gated runner + coverage scoring — a different
  initiative entirely; discovered when this restoration was attempted
  under the 'cad' name and would have overwritten real content).

  One namespace per original Rust module:
    brep.kernel     — BREP topology (vertex/edge/face/shell/solid) +
                       analytic/freeform curve & surface definitions
    brep.feature    — parametric feature tree (sketch/extrude/revolve/
                       fillet/chamfer/boolean/etc.)
    brep.assembly   — part instances, constraints, BOM extraction
    brep.tessellate — BREP solid -> triangle mesh + volume/surface-area

  Zero-dep portable CLJC — pure data + pure functions, no IO/GPU. f64
  precision throughout ([x y z] 3-vectors) for CAD-grade accuracy. Depends
  on kotoba-lang/engineer for shared contracts (constraint kind
  vocabulary; see brep.feature docstring).")
