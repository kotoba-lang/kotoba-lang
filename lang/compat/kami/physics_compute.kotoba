(ns kami.physics-compute
  "Phase 2.1 (ADR-CLJ-2607010930) — physics solver kernels authored as
  `kami.wgsl-emit` data with `@compute` workgroup entry points.

  The clj-wgsl migration invariant: the per-element hot loop runs on GPU as
  WGSL @compute; CLJ only authors (edit time) and dispatches (coarse-grained,
  wasmi-safe). This namespace authors the first such kernel — a vectorized
  cartpole semi-implicit Euler integrator — as pure data, mirroring
  `kami-genesis/src/wgsl/cartpole_step.wgsl` formula-for-formula.

  Storage-buffer indexing (`states[i]`), `if/return` early-out, and `var s: State`
  declarations exceed the small s-expr → WGSL lowering subset in `kami.wgsl-emit`,
  so the compute body uses the `:wgsl/body` raw-WGSL escape hatch. The
  surrounding `@compute @workgroup_size(...)` scaffolding, struct decls, and
  storage bindings remain fully data-driven. Phase 2.2 (audio DSP) may extend
  the s-expr subset or keep the escape hatch — the surface is additive."
  (:require [kami.wgsl-emit :as wgsl]))

;; --- cartpole step ----------------------------------------------------------

(def ^:private cartpole-state-struct
  {:State [[:x         :f32]
           [:x_dot     :f32]
           [:theta     :f32]
           [:theta_dot :f32]]})

(def ^:private cartpole-cfg-struct
  {:Cfg [[:cart_mass        :f32]
         [:pole_mass        :f32]
         [:pole_half_length :f32]
         [:gravity          :f32]
         [:force_mag        :f32]
         [:dt               :f32]
         [:num_envs         :u32]
         [:_pad             :u32]]})

(def ^:private cartpole-bindings
  [{:group 0 :binding 0 :var :storage :access :read_write
    :name "states" :type :array<State>}
   {:group 0 :binding 1 :var :storage :access :read
    :name "actions" :type :array<f32>}
   {:group 0 :binding 2 :var :uniform
    :name "cfg" :type :Cfg}])

(def ^:private cartpole-step-body
  "
  let i = gid.x;
  if (i >= cfg.num_envs) {
    return;
  }

  var s: State = states[i];
  let raw_force: f32 = actions[i];
  let force: f32 = clamp(raw_force, -cfg.force_mag, cfg.force_mag);

  let sin_t: f32 = sin(s.theta);
  let cos_t: f32 = cos(s.theta);
  let total_mass: f32 = cfg.cart_mass + cfg.pole_mass;
  let pml: f32 = cfg.pole_mass * cfg.pole_half_length;

  // Sutton & Barto 1983 cartpole equations:
  let temp: f32 = (force + pml * s.theta_dot * s.theta_dot * sin_t) / total_mass;
  let theta_acc: f32 =
    (cfg.gravity * sin_t - cos_t * temp)
    / (cfg.pole_half_length * (4.0 / 3.0 - cfg.pole_mass * cos_t * cos_t / total_mass));
  let x_acc: f32 = temp - pml * theta_acc * cos_t / total_mass;

  // Semi-implicit Euler:
  s.x_dot     = s.x_dot     + cfg.dt * x_acc;
  s.x         = s.x         + cfg.dt * s.x_dot;
  s.theta_dot = s.theta_dot + cfg.dt * theta_acc;
  s.theta     = s.theta     + cfg.dt * s.theta_dot;

  states[i] = s;")

(defn cartpole-step-shader
  "Return the cartpole semi-implicit Euler integrator as a `kami.wgsl-emit` data map.
  One workgroup invocation = one environment; 64 envs per workgroup, dispatch
  `ceil(num_envs / 64, 1, 1)` groups. Mirrors
  `kami-genesis/src/wgsl/cartpole_step.wgsl`."
  []
  {:wgsl/name     "cartpole_step"
   :wgsl/structs  (merge cartpole-state-struct cartpole-cfg-struct)
   :wgsl/bindings cartpole-bindings
   :wgsl/compute  {:workgroup-size [64 1 1]
                   :entry          "step_main"
                   :builtin        :global_invocation_id
                   :builtin-name   "gid"
                   :wgsl/body      cartpole-step-body}})

(defn cartpole-step-emit
  "Return the cartpole step WGSL source string via `kami.wgsl-emit/emit`. Pure."
  []
  (wgsl/emit (cartpole-step-shader)))
