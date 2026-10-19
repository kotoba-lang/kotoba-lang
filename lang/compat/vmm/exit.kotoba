(ns vmm.exit
  "What a VM exit means: which device an MMIO access is, and which PSCI call
  an HVC is. Decided by `kotoba/exit_core.kotoba`; this is the call path."
  (:require [vmm.oracle :as oracle]))

(defn- c [f args] (oracle/call :exit f args))
(defn- r [f args] (oracle/call-result :exit f args))

(def ^:private device-name
  ;; Built from the core's own constants rather than restated here, so the
  ;; numbers cannot disagree.
  (delay {(c :device-uart []) :uart
          (c :device-virtio []) :virtio}))

(def ^:private action-name
  (delay {(c :action-version []) :version
          (c :action-cpu-on []) :cpu-on
          (c :action-system-off []) :system-off
          (c :action-system-reset []) :system-reset}))

(defn classify-mmio
  "Classify one MMIO exit. Returns

    {:ok? true :device :uart|:virtio :offset n :index n?}

  or `{:ok? false :reason \"mmio/...\"}`. `:index` is present for virtio only."
  [{:keys [gpa len]}]
  (let [d (r :classify [gpa len])]
    (if-not (:ok? d)
      d
      (let [device (get @device-name (:value d))
            off (r :device-offset [gpa len])
            base {:ok? true :device device :offset (:value off)}]
        (if (= :virtio device)
          (assoc base :index (:value (r :virtio-index [gpa])))
          base)))))

(defn psci-action
  "Map a PSCI function id to an action, or refuse it by name. The monitor
  turns `{:ok? false}` into PSCI_RET_NOT_SUPPORTED; it does not guess."
  [fid]
  (let [a (r :psci-action [fid])]
    (if (:ok? a)
      {:ok? true :action (get @action-name (:value a))}
      a)))
