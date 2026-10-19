(ns kotodama.inference.mtp
  "Admission boundary from Kotodama runtime data to torch's executable MTP step."
  (:require [torch.speculative :as speculative]))

(defn make-step-fn
  "Create a continuous-serving MTP step only for a checkpoint admitted by its
  architecture loader. The injected draft/target forwards remain host-owned."
  [runtime-spec {:keys [draft-fn target-fn verify-options]}]
  (let [audit (:kotodama/checkpoint-audit runtime-spec)
        mtp (:kotodama/mtp runtime-spec)
        draft-count (:kotodama/draft-token-count mtp)]
    (when-not (and (= :text-generation/mtp (:kotodama/task runtime-spec))
                   (:kotodama/mtp-admitted? audit)
                   (:kotodama/verify-draft? mtp))
      (throw (ex-info "runtime is not admitted for verified MTP execution"
                      {:task (:kotodama/task runtime-spec)
                       :checkpoint-admitted? (:kotodama/mtp-admitted? audit)
                       :verify-draft? (:kotodama/verify-draft? mtp)})))
    (speculative/make-mtp-step-fn
     {:draft-fn draft-fn :target-fn target-fn
      :draft-token-count draft-count
      :verify-options verify-options})))
