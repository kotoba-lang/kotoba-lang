(ns kotodama.inference.core
  "High-level .cljc API for kotodama model inference. This namespace is the
  common surface that browser CLJS, local CLJ, SCI, and kototama hosts can share."
  (:require [kotodama.inference.ports :as ports]
            [kotodama.inference.runtime :as runtime]
            [kotodama.inference.validate :as validate]))

(defn ensure-valid! [op]
  (let [problems (validate/problems op)]
    (when (seq problems)
      (throw (ex-info "invalid kotodama inference op"
                      {:kotodama/op op
                       :kotodama/problems problems}))))
  op)

(defn probe
  ([] (probe ports/no-runtime))
  ([model-runtime] (ports/probe! model-runtime)))

(defn load-model
  "Load a runtime spec with a host-injected runtime. Returns a session map."
  [model-runtime runtime-spec]
  (ensure-valid! (runtime/load-op runtime-spec))
  (ports/load! model-runtime runtime-spec))

(defn generate
  ([model-runtime session prompt-or-token-ids]
   (generate model-runtime session prompt-or-token-ids {}))
  ([model-runtime session prompt-or-token-ids opts]
   (let [op (ensure-valid! (runtime/generate-op session prompt-or-token-ids opts))]
     (ports/generate! model-runtime
                      (:kotodama/session op)
                      (:kotodama/input op)
                      (:kotodama/generation op)))))

(defn forward
  ([model-runtime session token-ids]
   (forward model-runtime session token-ids {}))
  ([model-runtime session token-ids opts]
   (let [op (ensure-valid! (runtime/forward-op session token-ids opts))]
     (ports/forward! model-runtime
                     (:kotodama/session op)
                     (:kotodama/input-ids op)
                     (:kotodama/options op)))))

(defn dispose
  [model-runtime session]
  (ports/dispose! model-runtime session))

(defn llm-infer
  "Portable host-side implementation shape for kototama's `(llm-infer model prompt)`.
  `resolve-session` receives the model id and should return an already-loaded
  session or load one through the supplied runtime."
  ([model-runtime resolve-session model prompt]
   (llm-infer model-runtime resolve-session model prompt {}))
  ([model-runtime resolve-session model prompt opts]
   (let [op (ensure-valid! (runtime/kototama-infer-op model prompt opts))
         session (resolve-session (:kotodama/model op))]
     (ports/generate! model-runtime
                      session
                      (:kotodama/prompt op)
                      (:kotodama/generation op)))))
