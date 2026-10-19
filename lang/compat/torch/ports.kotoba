(ns torch.ports
  "Host-injected ports for torch-clj. The kernel (`torch.core`) carries no engine
  bindings; everything host-specific arrives through these protocols.

  - `ILayer`   — the layer vocabulary: shape + parameter semantics. The default
                 delegates to the built-ins in `torch.shape`; a host `reify`s a
                 custom `ILayer` to add layer types (or wrap/override built-ins)
                 without touching the kernel.
  - `IBackend` — *optional* real tensor execution (e.g. a libpython-clj / real
                 PyTorch binding). When absent, torch-clj is a pure shape &
                 parameter engine: it describes the network without running it."
  (:require [torch.shape :as shape]))

(defprotocol ILayer
  "The layer vocabulary a host understands."
  (custom-layer? [this ltype]
    "True if this host handles `ltype` itself (vs. falling back to built-ins).")
  (shape-of [this ltype args in-shape]
    "Return `[:ok out-shape]` or `[:error msg]` for one layer.")
  (params-of [this ltype args]
    "Learnable parameter count for one layer (non-negative int)."))

(defprotocol IBackend
  "Optional real tensor execution. Hosts that bind an engine implement this; the
  pure shape engine leaves it unimplemented."
  (forward [this model input]
    "Run a real forward pass. `input` is host tensor data; returns host data."))

(defprotocol IRuntimeBackend
  "Optional extension for forward calls carrying per-layer runtime tensors."
  (forward-with-options [this model input options]
    "Run forward with aligned `:layer-options`, such as attention context/masks."))

(def default-ports
  "Built-in layer vocabulary only (delegates to `torch.shape`); no backend."
  (reify ILayer
    (custom-layer? [_ ltype] (shape/known? ltype))
    (shape-of  [_ ltype args in] (shape/layer-shape ltype args in))
    (params-of [_ ltype args]    (shape/layer-params ltype args))))

(defn with-layers
  "Compose `extra` (an `ILayer`) over the built-ins: `extra` is consulted first
  for the types it claims via `custom-layer?`, otherwise the built-ins answer.
  This is the host-extension entry point — no kernel changes required."
  [extra]
  (reify ILayer
    (custom-layer? [_ ltype]
      (or (custom-layer? extra ltype) (shape/known? ltype)))
    (shape-of [_ ltype args in]
      (if (custom-layer? extra ltype)
        (shape-of extra ltype args in)
        (shape/layer-shape ltype args in)))
    (params-of [_ ltype args]
      (if (custom-layer? extra ltype)
        (params-of extra ltype args)
        (shape/layer-params ltype args)))))
