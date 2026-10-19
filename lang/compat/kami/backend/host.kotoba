(ns kami.backend.host
  "L1 backend (JVM) — OPTIONAL headless / server-render path. Not on the chosen
  'browser + Rust backend' critical path (ARCHITECTURE.md §4/§9), but the
  `IGpuBackend` protocol leaves room for it: a wasmtime-embedded kami-render
  component, or wgpu-native via FFI for offscreen render (thumbnails, CI golden
  images). Stub only.")

(defn make
  "Create a headless host backend. STUB — would embed kami-render (wasmtime
  component, or native wgpu offscreen surface) and impl `kami.gpu/IGpuBackend`."
  [opts]
  (throw (ex-info "kami.backend.host/make not implemented (optional path)" {:opts (keys opts)})))
