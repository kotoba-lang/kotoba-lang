(ns grant.surface
  "Deployment surfaces (ADR-0005/ADR-0007), ported from the retired
  `aiueos/src/surface.rs` Rust registry to CLJC per ADR-2607022200.

  The same component+manifest+capability model runs on edge, robotics, cloud,
  browser, client — but the capabilities a surface can back differ. A surface
  is, at the policy level, a map of capability -> provider it offers.
  `grant.policy/granted-to` intersects the kernel caps with a surface's
  offered set, so an import resolves to a kernel cap only if the active
  surface offers it — a missing provider is a loud `:unresolved-capability`
  denial, never a silent no-op.

  This namespace is the registry: data only. The host-function closures behind
  each provider are bound by a host adapter (formerly `src/host.rs`); every
  call there still gates on the capability named here first.")

(defn provider
  "One host-function provider: the aiueos:host import name a component calls
  plus the capability `gate` checks before the closure runs."
  [name cap]
  {:aiueos.surface/name name :aiueos.surface/cap cap})

(defn- surface-of [id providers]
  {:aiueos.surface/id id
   :aiueos.surface/providers (into {} (map (juxt :aiueos.surface/name identity)) providers)})

(defn robot
  "The in-process robot — the topic bus, clock/log/random, plus the
  device-broker primitives (ADR-0001)."
  []
  (surface-of "robot"
              [(provider "publish" :topic/publish)
               (provider "poll" :topic/subscribe)
               (provider "take" :topic/subscribe)
               (provider "count" :topic/subscribe)
               (provider "clock" :clock/monotonic)
               (provider "log" :log/write)
               (provider "random" :random/bytes)
               (provider "pci-config" :pci/config)
               (provider "dma-map" :dma/map)
               (provider "irq-subscribe" :irq/subscribe)
               (provider "mmio-map" :mmio/map)]))

(defn browser
  "DOM render/event shims over the host page, a Phase-0 input event FIFO,
  framebuffer present log, plus a `fetch` broker."
  []
  (surface-of "browser"
              [(provider "dom-render" :dom/render)
               (provider "dom-event" :dom/event)
               (provider "input-event" :input/event)
               (provider "fb-present" :framebuffer/present)
               (provider "fetch" :net/fetch)
               (provider "http-get-stream" :http/get-stream)
               (provider "log" :log/write)
               (provider "clock" :clock/monotonic)]))

(defn cloud
  "A KV store broker + a socket/HTTP `fetch` broker."
  []
  (surface-of "cloud"
              [(provider "kv-set" :storage/kv)
               (provider "kv-get" :storage/kv)
               (provider "fetch" :net/fetch)
               (provider "http-get-stream" :http/get-stream)
               (provider "object-get-stream" :object/get-stream)
               (provider "object-put-block" :object/put-block)
               (provider "object-compare-and-set-ref" :object/compare-and-set-ref)
               (provider "log" :log/write)
               (provider "clock" :clock/monotonic)
               (provider "random" :random/bytes)]))

(defn computer-virtual
  "The computer-use surface family (ADR-0007): a VIRTUAL screen + synthetic
  input. Deliberately offers no provider for the host's real
  keyboard/mouse/display — a computer-use component cannot reach the
  operator's real HID; calling one resolves to :unresolved-capability by
  construction. The backing is a host-isolated virtual display."
  []
  (surface-of "computer-virtual"
              [(provider "frame" :display/frame)
               (provider "pointer-move" :pointer/move)
               (provider "pointer-click" :pointer/click)
               (provider "key" :keyboard/key)
               (provider "type" :keyboard/type)
               (provider "fetch" :net/fetch)
               (provider "log" :log/write)
               (provider "clock" :clock/monotonic)]))

(defn computer-vm
  "Same capability surface as computer-virtual, backed by a microVM with
  virtio-gpu. A component moves between :virtual and :vm unchanged — only
  the backing (and fidelity) differs."
  []
  (assoc (computer-virtual) :aiueos.surface/id "computer-vm"))

(defn computer-host
  "The opt-in escape hatch: drives the host's REAL desktop. Offers the
  host-HID providers ON TOP of the virtual ABI. Reaching it requires a signed
  (:verified) component plus an explicit policy surface — never the default
  for :ai-generated (ADR-0007 §3)."
  []
  (let [base (computer-virtual)
        extra [(provider "pointer-host" :pointer/host)
               (provider "keyboard-host" :keyboard/host)
               (provider "display-host" :display/host)]]
    (-> base
        (assoc :aiueos.surface/id "computer-host")
        (update :aiueos.surface/providers
                into (map (juxt :aiueos.surface/name identity) extra)))))

(def ^:private registry
  {"robot" robot
   "browser" browser
   "cloud" cloud
   "computer-virtual" computer-virtual
   "computer-vm" computer-vm
   "computer-host" computer-host})

(defn by-id
  "Look up a known surface by id, or nil for an id aiueos doesn't know. Keep
  the registry in sync with the ADR-0005 / ADR-0007 tables."
  [id]
  (when-let [ctor (get registry id)]
    (ctor)))

(defn is-known? [id]
  (some? (by-id id)))

(defn offered
  "The capabilities `surface` can back."
  [surface]
  (set (map :aiueos.surface/cap (vals (:aiueos.surface/providers surface)))))

(defn offered-by-id
  "The capabilities a known surface id can back, or nil for an unknown id.
  A thin wrapper over the registry so policy and tooling share one source of
  truth."
  [id]
  (when-let [s (by-id id)]
    (offered s)))

(defn provider-for-cap
  "The provider backing `cap` on `surface`, if any."
  [surface cap]
  (first (filter #(= cap (:aiueos.surface/cap %))
                 (vals (:aiueos.surface/providers surface)))))

(defn provider-by-name
  "The provider for a specific aiueos:host import name, if `surface` installs
  it."
  [surface name]
  (get (:aiueos.surface/providers surface) name))

(defn union
  "Compose two surfaces (e.g. an edge gateway = robot ∪ cloud). Where both
  back a capability, `a`'s provider wins."
  [a b]
  {:aiueos.surface/id (str (:aiueos.surface/id a) "+" (:aiueos.surface/id b))
   :aiueos.surface/providers (merge (:aiueos.surface/providers b)
                                     (:aiueos.surface/providers a))})
