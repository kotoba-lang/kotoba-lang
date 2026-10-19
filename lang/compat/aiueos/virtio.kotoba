(ns aiueos.virtio
  "The virtio guest protocol core, ported from the retired
  `aiueos/src/virtio.rs` (removed in 961dee4302, recovered via the GitHub API
  from its pre-retirement parent commit `79ad05e910...`) per ADR-0011.

  This is the ~85% of the old Rust module that was already pure/portable:
  feature negotiation, split-queue layout, descriptor-chain validation, a
  DMA/IOMMU accounting model, and the virtio-blk request planner + service
  core. It never touched real hardware even in Rust -- the two places that did
  (`VolatileMmio`/`PciBarMapping`, raw pointer reads/writes over an
  already-mapped region) are NOT ported here; that seam is `regs` below (a
  plain `{:read32 (fn [offset]) :write32 (fn [offset value])}` map), and
  ADR-0011's `aiueos.vfio` namespace supplies a real implementation backed by
  Linux VFIO. Tests here use an in-memory atom-backed `regs` fake, mirroring
  the old Rust unit tests against `MemTransport`/`MemMmio`.

  Everywhere the Rust used `&mut self`, this module uses a plain immutable EDN
  map and pure `(f state ...) -> state'` functions (or `(f state ...) ->
  [state' value]` where the Rust method also returned something), matching
  `aiueos.topic`'s convention. Invalid input throws `ex-info`, matching
  `aiueos.launcher`/`aiueos.signing`'s convention for what the Rust modeled as
  `Result::Err`.

  virtio-console (single-descriptor receive/transmit buffers) is ALSO ported,
  same shape as virtio-blk's service core. NOT ported: virtio-gpu (the old
  Rust never had more than the `DeviceType::Gpu` enum case for it either --
  greenfield, not a port), and PCI-capability/BAR scanning (the VFIO seam
  gets BAR info from `VFIO_DEVICE_GET_REGION_INFO` directly, so PCI
  config-space capability walking is not on the critical path for Phase 0's
  virtio-mmio-shaped transport).")

;; ---------------------------------------------------------------------------
;; Constants (virtio.rs's `mmio`/`interrupt`/`status`/`block` modules)

(def mmio-reg
  "Virtio MMIO register offsets."
  {:magic-value 0x000 :version 0x004 :device-id 0x008 :vendor-id 0x00c
   :device-features 0x010 :device-features-sel 0x014
   :driver-features 0x020 :driver-features-sel 0x024
   :queue-sel 0x030 :queue-num-max 0x034 :queue-num 0x038 :queue-ready 0x044
   :queue-notify 0x050 :interrupt-status 0x060 :interrupt-ack 0x064
   :status 0x070
   :queue-desc-low 0x080 :queue-desc-high 0x084
   :queue-driver-low 0x090 :queue-driver-high 0x094
   :queue-device-low 0x0a0 :queue-device-high 0x0a4})

(def mmio-magic 0x74726976)
(def mmio-version-2 2)

(def interrupt-bit {:used-ring 1 :config-change 2})

(def device-status-bit
  {:acknowledge 1 :driver 2 :driver-ok 4 :features-ok 8
   :device-needs-reset 64 :failed 128})

(def desc-flag {:next 1 :write 2 :indirect 4})

(def block-req-type {:in 0 :out 1})
(def block-status {:ok 0 :ioerr 1 :unsupp 2})

;; ---------------------------------------------------------------------------
;; InterruptStatus

(defn interrupt-status-none? [bits] (zero? bits))
(defn interrupt-status-used-ring? [bits] (not (zero? (bit-and bits (:used-ring interrupt-bit)))))
(defn interrupt-status-config-change? [bits] (not (zero? (bit-and bits (:config-change interrupt-bit)))))
(defn interrupt-status-unknown-bits [bits]
  (bit-and bits (bit-not (bit-or (:used-ring interrupt-bit) (:config-change interrupt-bit)))))

;; ---------------------------------------------------------------------------
;; IRQ subscription (CheckedVirtioIrqController)

(defn irq-line
  "Validate a kernel IRQ line number (must be non-zero)."
  [line]
  (when (zero? line)
    (throw (ex-info "IRQ line must be non-zero" {:line line})))
  line)

(def empty-irq-controller {:aiueos.virtio/subscriptions {}})

(defn subscribe-virtio-irq
  "[controller' subscription] -- fails if `line` is already subscribed."
  [controller line device-type]
  (irq-line line)
  (when-let [existing (get-in controller [:aiueos.virtio/subscriptions line])]
    (throw (ex-info (str "virtio IRQ line " line " already subscribed for " (:device-type existing))
                     {:line line :existing existing})))
  (let [subscription {:line line :device-type device-type}]
    [(assoc-in controller [:aiueos.virtio/subscriptions line] subscription) subscription]))

(defn irq-subscription [controller line]
  (get-in controller [:aiueos.virtio/subscriptions line]))

(defn deliver-pending-virtio-interrupt
  "Reads-and-clears interrupt bits via `take-interrupts!` (a 0-arg fn returning
  raw status bits), and if non-zero, returns `{:subscription ... :status
  bits}`; else nil."
  [take-interrupts! subscription]
  (let [bits (take-interrupts!)]
    (when-not (interrupt-status-none? bits)
      {:subscription subscription :status bits})))

;; ---------------------------------------------------------------------------
;; DeviceType

(def device-type-id {:network 1 :block 2 :console 3 :gpu 16 :input 18})
(def id->device-type (into {} (map (fn [[k v]] [v k]) device-type-id)))

(def device-type-capabilities
  {:network ["net/fetch"]
   :block ["block/read" "block/write"]
   :console ["console/read" "console/write"]
   :gpu ["framebuffer/present"]
   :input ["input/event"]})

(defn device-type-capability [dt] (first (get device-type-capabilities dt)))

(defn device-type-from-id [id]
  (or (get id->device-type id)
      (throw (ex-info (str "unsupported virtio device id " id) {:id id}))))

;; ---------------------------------------------------------------------------
;; Features (u64 bitset)

(def features-version-1 (bit-shift-left 1 32))
(def features-ring-event-idx (bit-shift-left 1 29))
(def features-ring-indirect-desc (bit-shift-left 1 28))

(defn features-contains? [features other] (= (bit-and features other) other))
(defn features-union [a b] (bit-or a b))

(defn negotiate-features
  "All `required` bits must be in `offered`; `wanted` bits are accepted only
  when offered."
  [offered required wanted]
  (when-not (features-contains? offered required)
    (throw (ex-info (str "virtio feature negotiation failed: missing required bits "
                         (bit-and required (bit-not offered)))
                     {:offered offered :required required})))
  (bit-or required (bit-and wanted offered)))

;; ---------------------------------------------------------------------------
;; QueueSize

(defn power-of-two? [n] (and (pos? n) (zero? (bit-and n (dec n)))))

(defn queue-size
  "Validate a virtqueue size: a power of two in 1..=32768."
  [size]
  (when-not (and (pos? size) (power-of-two? size) (<= size 32768))
    (throw (ex-info (str "virtqueue size must be a power of two in 1..=32768, got " size)
                     {:size size})))
  size)

;; ---------------------------------------------------------------------------
;; DMA / IOMMU accounting model

(declare dma-map)

(def dma-perm {:device-read 1 :device-write 2})
(def dma-perm-read-write (bit-or (:device-read dma-perm) (:device-write dma-perm)))

(defn dma-perms-contains? [perms required] (= (bit-and perms required) required))

(defn dma-range
  "{:start :len :perms} for a mapped guest-physical range."
  [start len perms]
  (when (zero? len)
    (throw (ex-info "DMA range length must be non-zero" {:start start :len len})))
  (when (zero? perms)
    (throw (ex-info "DMA range must grant at least one permission" {:start start :len len})))
  {:start start :len len :perms perms})

(defn dma-range-end [r] (+ (:start r) (:len r)))

(defn dma-range-contains?
  [r addr len required]
  (and (pos? len)
       (dma-perms-contains? (:perms r) required)
       (<= (:start r) addr)
       (<= (+ addr len) (dma-range-end r))))

(defn dma-allocation
  [guest-phys len perms]
  (dma-range guest-phys len perms)
  {:guest-phys guest-phys :len len :perms perms})

(defn dma-allocation-range [a] (dma-range (:guest-phys a) (:len a) (:perms a)))

;; --- Iommu boundary: a pure state map {:aperture range :mappings {phys -> allocation}}

(defn make-checked-iommu [base len]
  {:aiueos.virtio/aperture (dma-range base len dma-perm-read-write)
   :aiueos.virtio/mappings {}})

(defn checked-iommu-dma-map [iommu]
  (let [ranges (map dma-allocation-range (vals (:aiueos.virtio/mappings iommu)))]
    (dma-map ranges)))

(defn- iommu-contains-allocation? [iommu allocation]
  (dma-range-contains? (:aiueos.virtio/aperture iommu) (:guest-phys allocation) (:len allocation) (:perms allocation)))

(defn iommu-map-dma
  "-> iommu' ; throws if `allocation` is outside the aperture or overlaps an
  active mapping."
  [iommu allocation]
  (when-not (iommu-contains-allocation? iommu allocation)
    (throw (ex-info "DMA allocation outside IOMMU aperture" {:allocation allocation :aperture (:aiueos.virtio/aperture iommu)})))
  (let [allocation-range (dma-allocation-range allocation)]
    (doseq [mapped (vals (:aiueos.virtio/mappings iommu))]
      (let [mapped-range (dma-allocation-range mapped)]
        (when (and (< (:start allocation-range) (dma-range-end mapped-range))
                   (< (:start mapped-range) (dma-range-end allocation-range)))
          (throw (ex-info "DMA allocation overlaps active mapping" {:allocation allocation :mapped mapped})))))
    (assoc-in iommu [:aiueos.virtio/mappings (:guest-phys allocation)] allocation)))

(defn iommu-unmap-dma
  "-> iommu' ; throws on unmap of an unmapped or mismatched allocation."
  [iommu allocation]
  (let [mapped (get-in iommu [:aiueos.virtio/mappings (:guest-phys allocation)])]
    (cond
      (nil? mapped)
      (throw (ex-info "DMA unmap for unknown mapping" {:allocation allocation}))

      (not= mapped allocation)
      (throw (ex-info "DMA unmap mismatch" {:allocation allocation :mapped mapped}))

      :else
      (update iommu :aiueos.virtio/mappings dissoc (:guest-phys allocation)))))

;; --- DmaAllocator: BumpDmaAllocator

(defn align-up [value align]
  (when (or (zero? align) (not (power-of-two? align)))
    (throw (ex-info (str "alignment must be a non-zero power of two, got " align) {:align align})))
  (bit-and (+ value (dec align)) (bit-not (dec align))))

(defn make-bump-dma-allocator [base len]
  (when (zero? len)
    (throw (ex-info "DMA allocator aperture length must be non-zero" {:len len})))
  {:aiueos.virtio/next base :aiueos.virtio/end (+ base len)})

(defn bump-allocator-remaining [allocator]
  (max 0 (- (:aiueos.virtio/end allocator) (:aiueos.virtio/next allocator))))

(defn bump-allocate-dma
  "[allocator' allocation]"
  [allocator len align perms]
  (when (zero? len)
    (throw (ex-info "DMA allocation length must be non-zero" {:len len})))
  (let [start (align-up (:aiueos.virtio/next allocator) align)
        end (+ start len)]
    (when (> end (:aiueos.virtio/end allocator))
      (throw (ex-info (str "DMA aperture exhausted: need " len " bytes aligned to " align)
                       {:len len :align align})))
    [(assoc allocator :aiueos.virtio/next end) (dma-allocation start len perms)]))

;; --- DmaMap: a sorted, non-overlapping set of DmaRanges

(defn dma-map
  "Validate that `ranges` don't overlap; returns the sorted vector (the map)."
  [ranges]
  (let [sorted (vec (sort-by :start ranges))]
    (doseq [[left right] (partition 2 1 sorted)]
      (when (> (dma-range-end left) (:start right))
        (throw (ex-info "DMA ranges overlap" {:left left :right right}))))
    sorted))

(def empty-dma-map [])

(defn dma-map-allows? [m addr len required]
  (boolean (some #(dma-range-contains? % addr len required) m)))

(defn dma-map-validate-descriptor
  "Throws unless `desc`'s DMA range is mapped for the direction its flags imply."
  [m index desc]
  (let [required (if (zero? (bit-and (:flags desc) (:write desc-flag)))
                    (:device-read dma-perm)
                    (:device-write dma-perm))]
    (when-not (dma-map-allows? m (:addr desc) (:len desc) required)
      (throw (ex-info (str "descriptor " index " DMA range is not mapped for the required direction")
                       {:index index :desc desc :required required})))))

;; ---------------------------------------------------------------------------
;; Descriptor / DescriptorChain

(defn desc-read [addr len] {:addr addr :len len :flags 0 :next 0})
(defn desc-write [addr len] {:addr addr :len len :flags (:write desc-flag) :next 0})
(defn desc-with-next [d next] (assoc d :flags (bit-or (:flags d) (:next desc-flag)) :next next))
(defn desc-indirect [d] (assoc d :flags (bit-or (:flags d) (:indirect desc-flag))))
(defn- desc-has-next? [d] (not (zero? (bit-and (:flags d) (:next desc-flag)))))

(defn validate-descriptor-chain-with-features
  "table: vector of descriptors (length must equal queue-size). Returns
  `{:head :descriptors :readable-bytes :writable-bytes}`, or throws."
  [qsize table head features]
  (let [q qsize]
    (when-not (= (count table) q)
      (throw (ex-info (str "descriptor table length " (count table) " does not match queue size " q)
                       {:table-len (count table) :queue-size q})))
    (when (>= head q)
      (throw (ex-info (str "descriptor head " head " outside queue size " q) {:head head :queue-size q})))
    (loop [current head seen #{} order [] readable 0 writable 0]
      (when (>= current q)
        (throw (ex-info (str "descriptor index " current " outside queue size " q) {:current current})))
      (when (contains? seen current)
        (throw (ex-info (str "descriptor chain loops at index " current) {:current current})))
      (let [desc (nth table current)]
        (when (zero? (:len desc))
          (throw (ex-info (str "descriptor " current " has zero length") {:current current})))
        (when (and (not (zero? (bit-and (:flags desc) (:indirect desc-flag))))
                   (not (features-contains? features features-ring-indirect-desc)))
          (throw (ex-info (str "descriptor " current " uses indirect descriptors without negotiated support")
                           {:current current})))
        (let [readable' (if (zero? (bit-and (:flags desc) (:write desc-flag))) (+ readable (:len desc)) readable)
              writable' (if (zero? (bit-and (:flags desc) (:write desc-flag))) writable (+ writable (:len desc)))
              order' (conj order current)]
          (if (desc-has-next? desc)
            (recur (:next desc) (conj seen current) order' readable' writable')
            {:head head :descriptors order' :readable-bytes readable' :writable-bytes writable'}))))))

(defn validate-descriptor-chain [qsize table head]
  (validate-descriptor-chain-with-features qsize table head 0))

(defn validate-descriptor-chain-for-dma
  "Validates the chain AND proves every segment is covered by `dma` (a
  `dma-map`) with the direction its flags imply."
  [qsize table head features dma]
  (let [chain (validate-descriptor-chain-with-features qsize table head features)]
    (doseq [index (:descriptors chain)]
      (dma-map-validate-descriptor dma index (nth table index)))
    chain))

;; ---------------------------------------------------------------------------
;; Split-queue layout

(defn split-queue-layout
  "{:descriptor-table :available-ring :used-ring :total-len} for a split
  virtqueue starting at `base`. Descriptor table is 16-byte aligned; used ring
  is aligned to `used-ring-align` (the guest page size, typically)."
  [base qsize used-ring-align]
  (let [q qsize
        descriptor-table (align-up base 16)
        descriptor-len (* q 16)
        available-ring (+ descriptor-table descriptor-len)
        available-len (+ 6 (* q 2))
        used-ring (align-up (+ available-ring available-len) used-ring-align)
        used-len (+ 6 (* q 8))
        total-len (- (+ used-ring used-len) base)]
    {:descriptor-table descriptor-table :available-ring available-ring
     :used-ring used-ring :total-len total-len}))

(defn queue-layout-descriptor-len [qsize] (* qsize 16))
(defn queue-layout-available-len [qsize] (+ 6 (* qsize 2)))
(defn queue-layout-used-len [qsize] (+ 6 (* qsize 8)))

(defn validate-queue-layout-dma
  "Throws unless the descriptor table / available ring / used ring memory
  itself is DMA-mapped in the directions the device needs."
  [layout qsize dma]
  (let [desc-len (queue-layout-descriptor-len qsize)
        avail-len (queue-layout-available-len qsize)
        used-len (queue-layout-used-len qsize)]
    (when-not (dma-map-allows? dma (:descriptor-table layout) desc-len (:device-read dma-perm))
      (throw (ex-info "descriptor table DMA range is not mapped for device read" {:layout layout})))
    (when-not (dma-map-allows? dma (:available-ring layout) avail-len (:device-read dma-perm))
      (throw (ex-info "available ring DMA range is not mapped for device read" {:layout layout})))
    (when-not (dma-map-allows? dma (:used-ring layout) used-len (:device-write dma-perm))
      (throw (ex-info "used ring DMA range is not mapped for device write" {:layout layout})))
    nil))

;; ---------------------------------------------------------------------------
;; AvailRing / UsedRing

(defn make-avail-ring [qsize] {:aiueos.virtio/queue-size qsize :aiueos.virtio/idx 0 :aiueos.virtio/ring (vec (repeat qsize 0))})

(defn avail-ring-push
  "[ring' slot]"
  [ring head]
  (let [qsize (:aiueos.virtio/queue-size ring)]
    (when (>= head qsize)
      (throw (ex-info (str "available descriptor head " head " outside queue size " qsize) {:head head})))
    (let [slot (mod (:aiueos.virtio/idx ring) qsize)]
      [(-> ring
           (assoc-in [:aiueos.virtio/ring slot] head)
           (update :aiueos.virtio/idx #(mod (inc %) 65536)))
       slot])))

(defn avail-ring-idx [ring] (:aiueos.virtio/idx ring))

(defn make-used-ring [qsize]
  {:aiueos.virtio/queue-size qsize :aiueos.virtio/idx 0
   :aiueos.virtio/ring (vec (repeat qsize {:id 0 :len 0}))})

(defn used-ring-push
  "[ring' slot]"
  [ring id len]
  (let [qsize (:aiueos.virtio/queue-size ring)]
    (when (>= id qsize)
      (throw (ex-info (str "used descriptor id " id " outside queue size " qsize) {:id id})))
    (let [slot (mod (:aiueos.virtio/idx ring) qsize)]
      [(-> ring
           (assoc-in [:aiueos.virtio/ring slot] {:id id :len len})
           (update :aiueos.virtio/idx #(mod (inc %) 65536)))
       slot])))

(defn used-ring-idx [ring] (:aiueos.virtio/idx ring))

(defn used-ring-get [ring slot]
  (let [qsize (:aiueos.virtio/queue-size ring)]
    (when (>= slot qsize)
      (throw (ex-info (str "used ring slot " slot " outside queue size " qsize) {:slot slot})))
    (nth (:aiueos.virtio/ring ring) slot)))

;; ---------------------------------------------------------------------------
;; virtio-mmio transport (status/feature-negotiation sequencing over `regs`)
;;
;; `regs` is `{:read32 (fn [offset] -> u32) :write32 (fn [offset value] -> nil)}`
;; -- the seam `aiueos.vfio` backs for real over a VFIO-mapped BAR, and tests
;; back with an atom over an in-memory register file.

(defn regs-read32 [regs offset] ((:read32 regs) offset))
(defn regs-write32 [regs offset value] ((:write32 regs) offset value) nil)

(defn- regs-write-u64 [regs low-offset value]
  (regs-write32 regs low-offset (bit-and value 0xffffffff))
  (regs-write32 regs (+ low-offset 4) (bit-and (unsigned-bit-shift-right value 32) 0xffffffff)))

(defn mmio-transport
  "Validate the magic/version handshake; returns `regs` unchanged (the
  'transport' is just `regs` plus the invariant this function checked)."
  [regs]
  (let [magic (regs-read32 regs (:magic-value mmio-reg))
        version (regs-read32 regs (:version mmio-reg))]
    (when-not (= magic mmio-magic)
      (throw (ex-info (str "virtio-mmio magic mismatch: got " magic) {:magic magic})))
    (when-not (= version mmio-version-2)
      (throw (ex-info (str "unsupported virtio-mmio version " version) {:version version}))))
  regs)

(defn mmio-queue-max [regs index]
  (regs-write32 regs (:queue-sel mmio-reg) index)
  (queue-size (regs-read32 regs (:queue-num-max mmio-reg))))

(defn mmio-configure-split-queue [regs index qsize layout]
  (let [max (mmio-queue-max regs index)]
    (when (> qsize max)
      (throw (ex-info (str "virtio queue " index " size " qsize " exceeds device max " max) {:index index}))))
  (regs-write32 regs (:queue-sel mmio-reg) index)
  (regs-write32 regs (:queue-num mmio-reg) qsize)
  (regs-write-u64 regs (:queue-desc-low mmio-reg) (:descriptor-table layout))
  (regs-write-u64 regs (:queue-driver-low mmio-reg) (:available-ring layout))
  (regs-write-u64 regs (:queue-device-low mmio-reg) (:used-ring layout))
  (regs-write32 regs (:queue-ready mmio-reg) 1)
  nil)

(defn mmio-configure-mapped-split-queue [regs index qsize layout dma]
  (validate-queue-layout-dma layout qsize dma)
  (mmio-configure-split-queue regs index qsize layout))

(defn mmio-notify-queue [regs index] (regs-write32 regs (:queue-notify mmio-reg) index))
(defn mmio-interrupt-status [regs] (regs-read32 regs (:interrupt-status mmio-reg)))
(defn mmio-ack-interrupts [regs bits] (regs-write32 regs (:interrupt-ack mmio-reg) bits))

(defn mmio-take-interrupts!
  "Reads interrupt status and, if non-zero, acks it. Returns the raw bits."
  [regs]
  (let [bits (mmio-interrupt-status regs)]
    (when-not (interrupt-status-none? bits)
      (mmio-ack-interrupts regs bits))
    bits))

(defn- mmio-device-features [regs]
  (regs-write32 regs (:device-features-sel mmio-reg) 0)
  (let [low (regs-read32 regs (:device-features mmio-reg))]
    (regs-write32 regs (:device-features-sel mmio-reg) 1)
    (let [high (regs-read32 regs (:device-features mmio-reg))]
      (bit-or low (bit-shift-left high 32)))))

(defn- mmio-write-driver-features [regs features]
  (regs-write32 regs (:driver-features-sel mmio-reg) 0)
  (regs-write32 regs (:driver-features mmio-reg) (bit-and features 0xffffffff))
  (regs-write32 regs (:driver-features-sel mmio-reg) 1)
  (regs-write32 regs (:driver-features mmio-reg) (bit-and (unsigned-bit-shift-right features 32) 0xffffffff)))

(defn initialize-mmio-transport
  "The virtio-mmio status/feature handshake: ACKNOWLEDGE -> DRIVER -> negotiate
  -> FEATURES_OK -> (verify FEATURES_OK stuck) -> DRIVER_OK. Returns
  `{:negotiated-features ...}`, or throws (including if the device silently
  rejects FEATURES_OK -- a device that clears that bit back out is signaling
  it didn't like the feature set, per the virtio spec)."
  [regs expected required wanted]
  (let [actual (device-type-from-id (regs-read32 regs (:device-id mmio-reg)))]
    (when-not (= actual expected)
      (throw (ex-info (str "virtio device type mismatch: expected " expected ", got " actual)
                       {:expected expected :actual actual}))))
  (regs-write32 regs (:status mmio-reg) 0)
  (regs-write32 regs (:status mmio-reg) (:acknowledge device-status-bit))
  (regs-write32 regs (:status mmio-reg) (bit-or (:acknowledge device-status-bit) (:driver device-status-bit)))
  (let [offered (mmio-device-features regs)
        negotiated (negotiate-features offered required wanted)]
    (mmio-write-driver-features regs negotiated)
    (regs-write32 regs (:status mmio-reg)
                  (bit-or (:acknowledge device-status-bit) (:driver device-status-bit) (:features-ok device-status-bit)))
    (let [status (regs-read32 regs (:status mmio-reg))]
      (when (zero? (bit-and status (:features-ok device-status-bit)))
        (throw (ex-info "virtio device rejected FEATURES_OK" {:status status}))))
    (regs-write32 regs (:status mmio-reg)
                  (bit-or (:acknowledge device-status-bit) (:driver device-status-bit)
                          (:features-ok device-status-bit) (:driver-ok device-status-bit)))
    {:negotiated-features negotiated}))

;; ---------------------------------------------------------------------------
;; virtio-blk

(defn block-request-header [request-type sector] {:request-type request-type :sector sector})
(defn block-request-header-read [sector] (block-request-header (:in block-req-type) sector))
(defn block-request-header-write [sector] (block-request-header (:out block-req-type) sector))

(def block-request-header-len 16)

(defn- plan-block-request
  "Plan a three-descriptor virtio-blk request chain (header/data/status).
  `read-into-data?` true = device writes into `data-addr` (a read from disk);
  false = device reads from `data-addr` (a write to disk). Returns
  `{:header :descriptors :head :data-len}`, or throws (including if the
  planned chain isn't covered by `dma` in the right directions)."
  [qsize head header header-addr data-addr data-len status-addr read-into-data? dma]
  (when (or (zero? data-len) (not (zero? (mod data-len 512))))
    (throw (ex-info (str "virtio-blk data length must be a non-zero multiple of 512, got " data-len)
                     {:data-len data-len})))
  (when (>= (+ head 2) qsize)
    (throw (ex-info (str "virtio-blk request needs three contiguous descriptors starting at " head
                          " in queue size " qsize)
                     {:head head :queue-size qsize})))
  (let [data (if read-into-data? (desc-write data-addr data-len) (desc-read data-addr data-len))
        base (vec (repeat qsize (desc-read 0 1)))
        descriptors (-> base
                        (assoc head (desc-with-next (desc-read header-addr block-request-header-len) (inc head)))
                        (assoc (+ head 1) (desc-with-next data (+ head 2)))
                        (assoc (+ head 2) (desc-write status-addr 1)))]
    (validate-descriptor-chain-for-dma qsize descriptors head 0 dma)
    {:header header :descriptors descriptors :head head :data-len data-len}))

(defn plan-block-read
  "Plan a virtio-blk read: device writes `data-len` bytes into `data-addr`."
  [qsize head header-addr data-addr data-len status-addr sector dma]
  (plan-block-request qsize head (block-request-header-read sector) header-addr
                       data-addr data-len status-addr true dma))

(defn plan-block-write
  "Plan a virtio-blk write: device reads `data-len` bytes from `data-addr`."
  [qsize head header-addr data-addr data-len status-addr sector dma]
  (plan-block-request qsize head (block-request-header-write sector) header-addr
                       data-addr data-len status-addr false dma))

(defn decode-block-status
  "Throws unless `status` is `S_OK`."
  [status]
  (cond
    (= status (:ok block-status)) nil
    (= status (:ioerr block-status)) (throw (ex-info "virtio-blk I/O error" {:status status}))
    (= status (:unsupp block-status)) (throw (ex-info "virtio-blk request is unsupported by device" {:status status}))
    :else (throw (ex-info (str "unknown virtio-blk status " status) {:status status}))))

(defn block-plan-chain
  "Re-validate a planned request's descriptor chain against `dma` (mirrors the
  Rust `BlockRequestPlan::chain`)."
  [plan qsize dma]
  (validate-descriptor-chain-for-dma qsize (:descriptors plan) (:head plan) 0 dma))

;; ---------------------------------------------------------------------------
;; VirtioBlkServiceCore -- request planning, available-ring submission, pending
;; id tracking, used-ring consumption, and status decoding. Device-specific
;; code (the tender, over `aiueos.vfio`) still owns guest memory writes and
;; MMIO notification.

(defn make-blk-service [qsize]
  {:aiueos.virtio/queue-size qsize
   :aiueos.virtio/avail (make-avail-ring qsize)
   :aiueos.virtio/pending {}
   :aiueos.virtio/last-used-idx 0})

(defn blk-service-available-idx [svc] (avail-ring-idx (:aiueos.virtio/avail svc)))
(defn blk-service-last-used-idx [svc] (:aiueos.virtio/last-used-idx svc))
(defn blk-service-pending-len [svc] (count (:aiueos.virtio/pending svc)))

(defn- blk-service-submit-plan
  "[svc' plan]"
  [svc plan kind]
  (when (contains? (:aiueos.virtio/pending svc) (:head plan))
    (throw (ex-info (str "virtio-blk descriptor head " (:head plan) " is already pending") {:plan plan})))
  (let [[avail' available-slot] (avail-ring-push (:aiueos.virtio/avail svc) (:head plan))]
    [(-> svc
         (assoc :aiueos.virtio/avail avail')
         (assoc-in [:aiueos.virtio/pending (:head plan)]
                   {:kind kind :head (:head plan) :sector (:sector (:header plan))
                    :data-len (:data-len plan) :available-slot available-slot}))
     plan]))

(defn blk-service-submit-read
  "[svc' plan]"
  [svc head header-addr data-addr data-len status-addr sector dma]
  (let [plan (plan-block-read (:aiueos.virtio/queue-size svc) head header-addr data-addr data-len status-addr sector dma)]
    (blk-service-submit-plan svc plan :read)))

(defn blk-service-submit-write
  "[svc' plan]"
  [svc head header-addr data-addr data-len status-addr sector dma]
  (let [plan (plan-block-write (:aiueos.virtio/queue-size svc) head header-addr data-addr data-len status-addr sector dma)]
    (blk-service-submit-plan svc plan :write)))

(defn blk-service-complete-used-element
  "Consume one used-ring completion at the service's expected index. `used-idx`
  must equal the service's own tracked index (strict in-order consumption,
  matching the Rust original). [svc' completed-or-nil]"
  [svc used-idx used-element status-byte]
  (let [qsize (:aiueos.virtio/queue-size svc)]
    (when-not (= used-idx (:aiueos.virtio/last-used-idx svc))
      (throw (ex-info (str "virtio-blk completion idx " used-idx " does not match expected idx "
                            (:aiueos.virtio/last-used-idx svc))
                       {:used-idx used-idx})))
    (when (>= (:id used-element) qsize)
      (throw (ex-info (str "virtio-blk used id " (:id used-element) " outside queue size " qsize)
                       {:used-element used-element})))
    (let [head (:id used-element)
          request (get-in svc [:aiueos.virtio/pending head])]
      (when-not request
        (throw (ex-info (str "virtio-blk completion for unknown descriptor head " head) {:head head})))
      (decode-block-status status-byte)
      [(-> svc
           (update :aiueos.virtio/pending dissoc head)
           (update :aiueos.virtio/last-used-idx #(mod (inc %) 65536)))
       {:request request :used used-element}])))

;; ---------------------------------------------------------------------------
;; VirtioConsoleServiceCore -- console queues use a single descriptor per
;; buffer (receive = device-writable, transmit = device-readable), unlike
;; virtio-blk's fixed 3-descriptor chain. Mirrors the blk service's shape
;; (planning, available-ring submission, pending tracking, used-ring
;; consumption) -- ADR-0011 follow-up (virtio-gpu remains unported: the old
;; Rust never had more than the `DeviceType::Gpu` enum case for it either).

(defn- plan-console-request
  "Plan a one-descriptor virtio-console buffer. `device-writes?` true =
  receive (device writes into `data-addr`); false = transmit (device reads
  from `data-addr`). Returns `{:kind :descriptors :head :data-addr
  :data-len}`, or throws."
  [qsize head data-addr data-len kind device-writes? dma]
  (when (zero? data-len)
    (throw (ex-info "virtio-console data length must be non-zero" {:data-len data-len})))
  (when (>= head qsize)
    (throw (ex-info (str "virtio-console descriptor head " head " outside queue size " qsize)
                     {:head head :queue-size qsize})))
  (let [desc (if device-writes? (desc-write data-addr data-len) (desc-read data-addr data-len))
        descriptors (assoc (vec (repeat qsize (desc-read 0 1))) head desc)]
    (validate-descriptor-chain-for-dma qsize descriptors head 0 dma)
    {:kind kind :descriptors descriptors :head head :data-addr data-addr :data-len data-len}))

(defn plan-console-receive
  "Plan a virtio-console receive buffer: device writes into `data-addr`."
  [qsize head data-addr data-len dma]
  (plan-console-request qsize head data-addr data-len :receive true dma))

(defn plan-console-transmit
  "Plan a virtio-console transmit buffer: device reads from `data-addr`."
  [qsize head data-addr data-len dma]
  (plan-console-request qsize head data-addr data-len :transmit false dma))

(defn console-plan-chain
  "Re-validate a planned console request's descriptor chain against `dma`
  (mirrors the Rust `ConsoleRequestPlan::chain`)."
  [plan qsize dma]
  (validate-descriptor-chain-for-dma qsize (:descriptors plan) (:head plan) 0 dma))

(defn make-console-service [qsize]
  {:aiueos.virtio/queue-size qsize
   :aiueos.virtio/avail (make-avail-ring qsize)
   :aiueos.virtio/pending {}
   :aiueos.virtio/last-used-idx 0})

(defn console-service-available-idx [svc] (avail-ring-idx (:aiueos.virtio/avail svc)))
(defn console-service-last-used-idx [svc] (:aiueos.virtio/last-used-idx svc))
(defn console-service-pending-len [svc] (count (:aiueos.virtio/pending svc)))

(defn- console-service-submit-plan
  "[svc' plan]"
  [svc plan]
  (when (contains? (:aiueos.virtio/pending svc) (:head plan))
    (throw (ex-info (str "virtio-console descriptor head " (:head plan) " is already pending") {:plan plan})))
  (let [[avail' available-slot] (avail-ring-push (:aiueos.virtio/avail svc) (:head plan))]
    [(-> svc
         (assoc :aiueos.virtio/avail avail')
         (assoc-in [:aiueos.virtio/pending (:head plan)]
                   {:kind (:kind plan) :head (:head plan) :data-addr (:data-addr plan)
                    :data-len (:data-len plan) :available-slot available-slot}))
     plan]))

(defn console-service-submit-receive
  "[svc' plan]"
  [svc head data-addr data-len dma]
  (console-service-submit-plan svc (plan-console-receive (:aiueos.virtio/queue-size svc) head data-addr data-len dma)))

(defn console-service-submit-transmit
  "[svc' plan]"
  [svc head data-addr data-len dma]
  (console-service-submit-plan svc (plan-console-transmit (:aiueos.virtio/queue-size svc) head data-addr data-len dma)))

(defn console-service-complete-used-element
  "Consume one used-ring completion at the service's expected index; throws if
  the completion reports more bytes than the buffer it was submitted with
  declared (`used-element`'s `:len` > the pending request's `:data-len`).
  [svc' completed-or-nil]"
  [svc used-idx used-element]
  (let [qsize (:aiueos.virtio/queue-size svc)]
    (when-not (= used-idx (:aiueos.virtio/last-used-idx svc))
      (throw (ex-info (str "virtio-console completion idx " used-idx " does not match expected idx "
                            (:aiueos.virtio/last-used-idx svc))
                       {:used-idx used-idx})))
    (when (>= (:id used-element) qsize)
      (throw (ex-info (str "virtio-console used id " (:id used-element) " outside queue size " qsize)
                       {:used-element used-element})))
    (let [head (:id used-element)
          request (get-in svc [:aiueos.virtio/pending head])]
      (when-not request
        (throw (ex-info (str "virtio-console completion for unknown descriptor head " head) {:head head})))
      (when (> (:len used-element) (:data-len request))
        (throw (ex-info (str "virtio-console completion length " (:len used-element)
                              " exceeds buffer length " (:data-len request))
                         {:used-element used-element :request request})))
      [(-> svc
           (update :aiueos.virtio/pending dissoc head)
           (update :aiueos.virtio/last-used-idx #(mod (inc %) 65536)))
       {:request request :used used-element}])))
