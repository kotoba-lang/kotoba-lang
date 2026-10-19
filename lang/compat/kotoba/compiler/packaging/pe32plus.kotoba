(ns kotoba.compiler.packaging.pe32plus
  (:require [kotoba.artifact.core :as artifact]
            [kotoba.native.image-scratch :as image-scratch]
            [kotoba.object.pe32plus :as pe]))

(def ^:private firmware-target :x86_64-aiueos-uefi-v1)
(def ^:private file-alignment 0x200)
(def ^:private section-alignment 0x1000)
(def ^:private image-base 0x400000)
(def ^:private text-rva 0x1000)
;; boot: `data-rva 0x2000` and `reloc-rva 0x3000` used to be constants here,
;; and `image-size` was the constant 0x4000 at the call. That froze `.text` at
;; ONE PAGE. A Kotoba UEFI application whose code exceeded 4096 bytes was
;; packaged without complaint and had its `.data` mapped over the tail of its
;; own code -- silent corruption at load time, not a refusal at build time,
;; because `kotoba.object.pe32plus/encode-image` validated raw file offsets
;; and had no opinion about RVAs at all (fixed there the same day, ADR-0002).
;; `package-embedded-kernel` below never had the defect: it computed its
;; addresses from the real text size, which is what this now does too.
(def ^:private text-offset 0x200)
;; boot: 96, not 80. The two-arity EFI entry contract parks the ImageHandle at
;; context+0x50 and the SystemTable at +0x58, so the last addressed byte is 95.
;; The zero-arity contract gets the same 96 bytes rather than a second layout:
;; its extra 16 bytes are zero, which is strictly better than what it had --
;; `kernel-boot-info` reads [r9+0x50], which under an 80-byte virtual size was
;; a read one byte PAST the section.
(def ^:private context-size 96)
;; boot-scratch: the writable area this packager reserves for the guest, past
;; the context (kotoba-gmir ADR-0013, kotoba-native ADR-0068).
;;
;; It is DECLARED, not implied. `.data`'s virtual size is `context-size +
;; scratch-size`, the raw bytes are emitted rather than left to the loader's
;; zero-fill, and the numbers come from `kotoba.native.image-scratch` -- the
;; namespace the ENCODER reads to build `lea r10,[r9+0x60]`. One var, two
;; readers, the discipline `kotoba.native.interrupt-abi` established.
;;
;; UNCONDITIONAL. Every UEFI image gets the reservation whether or not its
;; program names the region. Sizing it by what the program uses would make the
;; operation lie in exactly the case that matters -- a module that gains a
;; `(kernel-scratch-region)` after the packager decided it needed none -- and
;; 16 KiB of zeros is a cheap price for the operation always being true.
(def ^:private scratch-offset image-scratch/offset)
(def ^:private scratch-size image-scratch/bytes-reserved)
(def ^:private data-size (+ context-size scratch-size))

;; The offset the encoder assumes has to BE the end of the context. If the
;; context ever grows past it the scratch would overlap the context's last
;; slots, silently: the emitted `lea` does not know how big the context is.
(when-not (= scratch-offset context-size)
  (throw (ex-info "the scratch region must begin where the context ends"
                  {:context-size context-size :scratch-offset scratch-offset})))

(def le pe/little-endian)
(def align pe/align-up)

;; 2^(8*width) for the widths this packager emits. Written as literals rather
;; than `(bit-shift-left 1 (* 8 width))`, which is NOT portable: JavaScript
;; shift counts are taken mod 32, so on ClojureScript `(bit-shift-left 1 32)`
;; is 1, not 2^32. Measured 2026-08-20 under nbb -- `limit` came out 1, so
;; `maximum` was 0 and every nonzero field was rejected as "out of range",
;; including a relocation displacement of 4085. Both bounds below are powers of
;; two, exact as a JVM long and as a JS double alike.
(def ^:private signed-limit {1 256 2 65536 4 4294967296 8 18446744073709551616})

(defn- signed-le [n width]
  (let [limit (or (signed-limit width)
                  (throw (ex-info "unsupported signed little-endian width"
                                  {:width width})))
        minimum (- (quot limit 2))
        maximum (dec (quot limit 2))]
    (when-not (<= minimum n maximum)
      (throw (ex-info "signed little-endian field is out of range"
                      {:value n :width width})))
    (le (if (neg? n) (+ limit n) n) width)))

(def ^:private zero-arity-shim-size 21)
;; boot: sub rsp,0x28 / lea r9 / two stores / two argument moves / call /
;; add rsp,0x28 / ret. Fixed, like the zero-arity one, so the context RVA can
;; be computed before the shim that references it is built.
(def ^:private two-arity-shim-size 35)

(defn- entry-shim
  "The Microsoft x64 -> Kotoba adapter at the image entry point.

  UEFI enters an EFI application with `(EFI_HANDLE ImageHandle,
  EFI_SYSTEM_TABLE *SystemTable)` in RCX and RDX. The zero-arity shim
  DISCARDED both, which is why a Kotoba program compiled for this target could
  not reach the console, boot services or the memory map, and why aiueos's
  BOOTX64.EFI is still C.

  The two-arity shim parks them in the hidden context at +0x50 and +0x58 --
  where `kernel-boot-info` and `kernel-system-table` read them -- AND passes
  them positionally in RDI and RSI, which is where the internal Kotoba ABI
  takes its first two parameters. Both, deliberately: the entry reads them as
  parameters, and anything it calls reads them out of the context without
  having to thread them through every signature.

  Neither shim is a claim that the internal lowering is Microsoft x64. It is
  not; this is the adapter that makes that irrelevant."
  [arity source-rva context-rva]
  (case arity
    0 (let [after-lea (+ text-rva 11)
            after-call (+ text-rva 16)]
        (vec (concat [0x48 0x83 0xec 0x28
                      0x4c 0x8d 0x0d]
                     (signed-le (- context-rva after-lea) 4)
                     [0xe8] (signed-le (- source-rva after-call) 4)
                     [0x48 0x83 0xc4 0x28 0xc3])))
    2 (let [after-lea (+ text-rva 11)
            after-call (+ text-rva 30)]
        (vec (concat [0x48 0x83 0xec 0x28]        ; sub rsp,0x28
                     [0x4c 0x8d 0x0d]             ; lea r9,[rip+ctx]
                     (signed-le (- context-rva after-lea) 4)
                     [0x49 0x89 0x49 0x50]        ; mov [r9+0x50],rcx
                     [0x49 0x89 0x51 0x58]        ; mov [r9+0x58],rdx
                     [0x48 0x89 0xcf]             ; mov rdi,rcx
                     [0x48 0x89 0xd6]             ; mov rsi,rdx
                     [0xe8] (signed-le (- source-rva after-call) 4)
                     [0x48 0x83 0xc4 0x28]        ; add rsp,0x28
                     [0xc3])))
    (throw (ex-info "UEFI boundary has no shim for this entry arity"
                    {:arity arity}))))

(def ^:private entry-contracts
  {0 {:contract :microsoft-x64-zero-arity-efi-status-v1 :size zero-arity-shim-size}
   2 {:contract :microsoft-x64-two-arity-efi-status-v2 :size two-arity-shim-size}})

;; boot: the two-arity entry is a named EXPORT rather than `main`, and the
;; name is the one the target profile already declares (`:entry :efi_main`).
;;
;; It is not a style choice. `kotoba.compiler.frontend` rejects any `main` that
;; takes arguments -- "main must take zero arguments" -- for every target,
;; because `main` is the guest entry every OTHER backend calls with no
;; arguments. Relaxing that globally to admit one target's firmware ABI would
;; be a language change made by a packager, which is the wrong direction; the
;; profile already had a name for this boundary, so this uses it.
;;
;; A module with no `efi-main` still packages through its `main`, on the
;; zero-arity contract, exactly as before.
;; fuel64: the budget this image starts life with, and the reason it is read
;; from the artifact rather than written as a constant.
;;
;; It WAS the constant 512, and had been since this packager existed. Every
;; UEFI image ran on 512 no matter what `--fuel` said -- the flag is parsed,
;; validated, sealed into `:limits :fuel` and `:fuel-abi :initial`, and then
;; the one place that decides what the machine actually gets ignored it.
;;
;; Measured 2026-09-03: `--fuel 512` and `--fuel 1048576` produce
;; BYTE-IDENTICAL images (sha256 fc742834811b3118...), a 2048x difference that
;; changes nothing. `--fuel 250000000` was refused, so the flag was reaching
;; the verifier; it simply never reached the image. Found by the LOADER stream
;; from the other end, where `sha256-region` costs 1,772 fuel per 64-byte
;; block, so ONE SHA-256 BLOCK could not fit in 512 and the loader's
;; `integrity` module had never returned. Four in-guest boots bisected it
;; before the cause was known -- scratch write/read-back, a 64-byte
;; `kernel-subregion` of a `bytes-literal`, `store32`/`load32` and `sha-init`
;; all pass, and only `sha-block` fails.
;;
;; This is the same class as the imm32 replenish ceiling one layer down
;; (kotoba-native ADR 0078): a budget that cannot be raised past 2^31, and a
;; budget that is silently ignored, are both "the number the machine runs on is
;; not the number anybody wrote".
;;
;; The two checks are `kotoba.native.elf64/artifact-fuel`'s, restated rather
;; than shared because this repository owns the PE32+ route and that one owns
;; ELF: the budget must be a positive integer, and `:limits :fuel` must AGREE
;; with `:fuel-abi :initial`. The agreement check is what makes this a seam
;; rather than a second opinion -- the verifier re-derives `:fuel-abi` from
;; `:limits`, so a packager that read one of the two could ship an image whose
;; running budget contradicts its own receipt.
(defn- artifact-fuel [artifact]
  (let [fuel (get-in artifact [:limits :fuel])
        abi-fuel (get-in artifact [:fuel-abi :initial])]
    (when-not (and (integer? fuel) (pos? fuel) (= fuel abi-fuel))
      (throw (ex-info "PE32+ EFI packaging requires one valid sealed fuel bound"
                      {:reason :efi-fuel-bound-invalid
                       :fuel fuel :fuel-abi-initial abi-fuel})))
    fuel))

(def ^:private efi-entry-name 'efi-main)

(defn package-efi
  "Package a sealed aiueos firmware artifact as an import-free PE32+ EFI image.

  Two entry contracts. A zero-arity Kotoba entry returning an EFI_STATUS-sized
  integer is v1 and still works; a two-arity entry -- `(defn main
  [image-handle system-table] ...)` -- is v2, and is the one a program that
  intends to talk to the firmware wants. The contract is chosen BY THE ENTRY'S
  ARITY rather than by a flag, so there is nothing to keep in step.

  Section placement is derived from the real code and context sizes. It used
  to be three frozen RVAs and a frozen SizeOfImage, which put a 4096-byte
  ceiling on `.text` that nothing checked: past it, `.data` was mapped over
  the tail of the code and the image was still a byte-valid PE."
  [artifact]
  (when-not (artifact/valid-seal? artifact)
    (throw (ex-info "PE32+ EFI packaging requires a sealed artifact" {})))
  (when-not (= firmware-target (:target artifact))
    (throw (ex-info "PE32+ EFI packaging requires the aiueos UEFI target"
                    {:target (:target artifact)})))
  (when-not (and (= :none (get-in artifact [:target-profile :runtime]))
                 (false? (get-in artifact [:target-profile :ambient-syscalls])))
    (throw (ex-info "PE32+ EFI packaging requires a freestanding profile"
                    {:target-profile (:target-profile artifact)})))
  (let [exports (:exports artifact)
        source-entry (if (contains? exports efi-entry-name)
                       efi-entry-name
                       (get-in artifact [:program :entry]))
        export (get exports source-entry)]
    (when-not export
      (throw (ex-info "Kotoba firmware entry is not exported" {:entry source-entry})))
    (when-not (contains? entry-contracts (:arity export))
      (throw (ex-info "UEFI boundary requires a zero-arity entry or a two-arity `efi-main`"
                      {:entry source-entry :arity (:arity export)
                       :admitted (sort (keys entry-contracts))})))
    (let [{:keys [contract] shim-size :size} (get entry-contracts (:arity export))
          source-rva (+ text-rva shim-size (:offset export))
          text-size (+ shim-size (count (:code artifact)))
          ;; boot: derived, not frozen. A section's mapped span is its virtual
          ;; size rounded up to the section alignment, which is the
          ;; granularity the loader assigns pages at -- the same rule
          ;; `encode-image` now enforces, computed here rather than assumed.
          data-rva (align (+ text-rva text-size) section-alignment)
          reloc-rva (align (+ data-rva data-size) section-alignment)
          image-size (align (+ reloc-rva 12) section-alignment)
          shim (entry-shim (:arity export) source-rva data-rva)
          text (into shim (:code artifact))
          context (into (vec (repeat 8 0))
                        (concat (le (artifact-fuel artifact) 8)
                                (repeat (- data-size 16) 0)))
          text-raw-size (align (count text) file-alignment)
          data-offset (+ text-offset text-raw-size)
          data-raw-size (align data-size file-alignment)
          reloc-offset (+ data-offset data-raw-size)
          ;; A legal relocation directory containing two IMAGE_REL_BASED_ABSOLUTE
          ;; padding entries. All image references are relative, so no fixups exist.
          reloc (vec (concat (le 0 4) (le 12 4) (le 0 2) (le 0 2)))
          reloc-raw-size (align (count reloc) file-alignment)
          bytes (pe/encode-image
                 {:machine :x86-64 :entry-rva text-rva :text-rva text-rva
                  :image-base image-base :section-alignment section-alignment
                  :file-alignment file-alignment :headers-size text-offset
                  :image-size image-size :subsystem :efi-application
                  :data-directories {5 {:rva reloc-rva :size (count reloc)}}
                  :sections [{:name ".text" :virtual-size (count text)
                              :rva text-rva :raw-size text-raw-size
                              :raw-offset text-offset :characteristics 0x60000020
                              :bytes text}
                             {:name ".data" :virtual-size data-size
                              :rva data-rva :raw-size data-raw-size
                              :raw-offset data-offset :characteristics 0xc0000040
                              :bytes context}
                             {:name ".reloc" :virtual-size (count reloc)
                              :rva reloc-rva :raw-size reloc-raw-size
                              :raw-offset reloc-offset :characteristics 0x42000040
                              :bytes reloc}]})]
      (when-not (= text-size (count text))
        (throw (ex-info "UEFI entry shim size disagrees with its declaration"
                        {:declared shim-size :emitted (- (count text)
                                                         (count (:code artifact)))})))
      {:format :pe32+/v1
       :target firmware-target
       :entry :efi_main
       :source-entry source-entry
       :entry-rva text-rva
       :entry-contract contract
       :entry-arity (:arity export)
       :context-size context-size
       :scratch {:offset scratch-offset :bytes scratch-size
                 :rva (+ data-rva scratch-offset)}
       :section-layout {:text {:rva text-rva :virtual-size (count text)}
                        :data {:rva data-rva :virtual-size data-size}
                        :reloc {:rva reloc-rva :virtual-size (count reloc)}
                        :image-size image-size}
       :sections [:text :data :reloc]
       :imports []
       :relocations {:format :pe-base-relocation/v1 :fixups 0 :position-independent true}
       :bytes bytes})))

;; 2^(8*i) for i in 0..7, for the same reason the bounds above are literals:
;; `(bit-shift-left 1 32)` is 1 on ClojureScript. Each is a power of two, exact
;; as a JVM long and as a JS double alike.
(def ^:private byte-scale
  [1 256 65536 16777216 4294967296 1099511627776 281474976710656 72057594037927936])

(defn- read-le
  "WIDTH little-endian bytes at OFFSET, as one number.

  This used to accumulate with `(bit-shift-left byte (* 8 index))`, which is
  the very hazard the comment above this file's bounds was written about, at a
  width the comment's author was not looking at: the six ELF fields read here
  with width 8 need shifts of 32, 40, 48 and 56, and cljs takes shift counts
  mod 32. Bytes 4 through 7 of every 64-bit field were folded into the LOW
  bits.

  It was inert for legal input -- the PT_LOAD contract caps paddr at
  0x40000000 and memsz at 0x100000, so those four bytes are zero in every
  kernel this packager is supposed to see. It was NOT inert for input the
  contract exists to refuse. Measured 2026-08-25 with
  `kotoba.compiler.packaging.elf-fixture`, whose first segment sits at
  0x0001000000100000: the JVM read 281474977759232 and rejected the kernel;
  nbb read 1114112, passed every bound check, and emitted a boot image.
  The admission check failed open on the second runtime."
  [bytes offset width]
  (reduce (fn [value index]
            (+ value (* (nth bytes (+ offset index)) (nth byte-scale index))))
          0 (range width)))

(defn- code-size [tokens]
  (reduce (fn [size token]
            (+ size (cond
                      (and (map? token) (:label token)) 0
                      (and (map? token) (:rel32 token)) 4
                      :else 1)))
          0 tokens))

(defn- finalize-loader [tokens external-labels]
  (let [labels (loop [remaining tokens position text-rva out external-labels]
                 (if-let [token (first remaining)]
                   (if-let [label (and (map? token) (:label token))]
                     (recur (next remaining) position (assoc out label position))
                     (recur (next remaining) (inc position) out))
                   out))]
    (loop [remaining tokens position text-rva out []]
      (if-let [token (first remaining)]
        (cond
          (and (map? token) (:label token))
          (recur (next remaining) position out)

          (and (map? token) (:rel32 token))
          (let [target (get labels (:rel32 token))]
            (when-not target (throw (ex-info "unknown UEFI loader label" {:label (:rel32 token)})))
            (recur (next remaining) (+ position 4)
                   (into out (signed-le (- target (+ position 4)) 4))))

          :else (recur (next remaining) (inc position) (conj out token)))
        (vec out)))))

(defn- rip [label] {:rel32 label})
(defn- label [name] {:label name})

(defn- allocate-segment [address-label pages source-label size]
  (concat
   [0xb9 2 0 0 0 0xba 2 0 0 0 0x41 0xb8] (le pages 4)
   [0x4c 0x8d 0x0d] [(rip address-label)]
   [0x41 0xff 0x56 0x28 0x48 0x85 0xc0 0x0f 0x85] [(rip :fail)]
   [0x48 0x8b 0x0d] [(rip address-label)]
   [0x48 0x8d 0x15] [(rip source-label)]
   [0x41 0xb8] (le size 4)
   [0x41 0xff 0x96 0x60 0x01 0x00 0x00]))

(defn package-embedded-kernel
  "Generate a position-independent PE32+ UEFI transition loader around a
  compiler-produced aiueos kernel ELF. No C object, CRT, import, or linker is
  involved. The current hard-flip contract admits exactly two bounded PT_LOAD
  segments and transfers control after ExitBootServices.

  ⚠ NOT PORTABLE, measured 2026-08-31. This function emits different bytes
  under ClojureScript than under the JVM. Given one identical kernel it
  produced 129,024 bytes on both, differing in exactly two, inside an
  eight-byte run the JVM writes as `AIUEBOOT` and cljs as a NUL followed by
  `HUEBOOT`; read as one little-endian word the two differ by 321. Both routes reported
  the same kernel sha256, so both had read the same input.

  `package-efi` in this same namespace IS byte-identical across the two, so
  this is one function, not the file. That is also why `bin/amu`'s JDK-free
  route serves `--target x86_64-aiueos-uefi-v1` and refuses
  `package-aiueos-boot`: the boot command was implemented on that route,
  measured, and reverted rather than shipped.

  No test guards this, and one cannot be written here. Clojure and nbb each
  load their own runtime; a comparison has to build the same input under BOTH
  and diff the bytes, which is what aiueos's
  `os/aiueos/scripts/verify-jvm-free-object-parity.cljs` does for kernel
  objects. Whoever narrows this down should do the same for the boot image.

  This file has ZERO reader conditionals. That was taken as evidence it was
  portable; it is evidence that nobody wrote one. Suspect the arithmetic --
  cljs bitwise operators truncate to int32, and the values here (entry points,
  paddrs, segment sizes) are 64-bit. See aiueos ADR-0130."
  ([kernel] (package-embedded-kernel kernel []))
  ([kernel payload]
  (let [kernel (vec kernel)
        payload (vec payload)]
    (when (> (count payload) 16384)
      (throw (ex-info "embedded RT payload exceeds 16 KiB" {:bytes (count payload)})))
    (when-not (and (= [0x7f 0x45 0x4c 0x46] (subvec kernel 0 4))
                   (= 2 (read-le kernel 16 2)) (= 0x3e (read-le kernel 18 2)))
      (throw (ex-info "embedded kernel must be x86-64 ET_EXEC" {})))
    (let [entry (read-le kernel 24 8)
          phoff (read-le kernel 32 8)
          phentsize (read-le kernel 54 2)
          phnum (read-le kernel 56 2)
          segments (mapv (fn [index]
                           (let [offset (+ phoff (* index phentsize))]
                             {:type (read-le kernel offset 4)
                              :flags (read-le kernel (+ offset 4) 4)
                              :offset (read-le kernel (+ offset 8) 8)
                              :paddr (read-le kernel (+ offset 24) 8)
                              :filesz (read-le kernel (+ offset 32) 8)
                              :memsz (read-le kernel (+ offset 40) 8)}))
                         (range phnum))]
      (let [first-segment (first segments) second-segment (second segments)
            entry-segment (some #(and (= 5 (:flags %))
                                      (<= (:paddr %) entry)
                                      (< entry (+ (:paddr %) (:memsz %)))) segments)
            non-overlap (or (<= (+ (:paddr first-segment) (:memsz first-segment))
                                (:paddr second-segment))
                            (<= (+ (:paddr second-segment) (:memsz second-segment))
                                (:paddr first-segment)))]
      (when-not (and (= 2 phnum) (= 56 phentsize)
                     (every? #(and (= 1 (:type %)) (pos? (:filesz %))
                                   (= (:filesz %) (:memsz %))
                                   (<= 0x100000 (:paddr %))
                                   (<= (:memsz %) 0x100000)
                                   (<= (:paddr %) (- 0x40000000 (:memsz %)))
                                   (zero? (mod (:paddr %) 4096))
                                   (<= (+ (:offset %) (:filesz %)) (count kernel))) segments)
                     (= [5 6] (mapv :flags segments)) entry-segment non-overlap)
        (throw (ex-info "embedded kernel PT_LOAD contract rejected" {:segments segments})))
      (let [data-addresses [0 8]
            payload? (seq payload)
            variables-size (if payload? 88 72)
            memory-map-offset (align variables-size 16)
            memory-map-capacity 16384
            embedded-offset (align (+ memory-map-offset memory-map-capacity) 16)
            payload-offset embedded-offset
            kernel-offset (align (+ payload-offset (count payload)) 16)
            ;; Build once with provisional external RVAs; instruction length is
            ;; independent of displacement values.
            segment-tokens (mapcat (fn [index segment]
                                     (allocate-segment (keyword (str "address" index))
                                      (quot (+ (:memsz segment) 4095) 4096)
                                      (keyword (str "segment" index)) (:filesz segment)))
                                   (range) segments)
            tokens (vec (concat
                    [0x41 0x54 0x41 0x55 0x41 0x56 0x41 0x57
                     0x48 0x83 0xec 0x28 0x49 0x89 0xcc 0x49 0x89 0xd5
                     0x4c 0x8b 0x72 0x60]
                    segment-tokens
                    (when payload?
                      (concat [0x48 0x8d 0x05] [(rip :payload)]
                              [0x48 0x89 0x05] [(rip :payload-pointer)]
                              [0xb8] (le (count payload) 4)
                              [0x48 0x89 0x05] [(rip :payload-length)]))
                    ;; The bounded memory map is part of the loader image's RW
                    ;; section, immediately after boot-info. This makes the
                    ;; handoff one compiler-owned region: the kernel derives
                    ;; boot+64 instead of trusting a pointer read from firmware
                    ;; data as a new memory authority. GetMemoryMap fails closed
                    ;; when 16 KiB is insufficient.
                    [0x48 0x8d 0x05] [(rip :memory-map)]
                    [0x48 0x89 0x05] [(rip :map-pointer)]
                    [(label :get-map)]
                    [0xb8 0x00 0x40 0x00 0x00]
                    [0x48 0x89 0x05] [(rip :map-size)]
                    [0x48 0x8d 0x0d] [(rip :map-size)]
                    [0x48 0x8b 0x15] [(rip :map-pointer)]
                    [0x4c 0x8d 0x05] [(rip :map-key)]
                    [0x4c 0x8d 0x0d] [(rip :descriptor-size)]
                    [0x48 0x8d 0x05] [(rip :descriptor-version)]
                    [0x48 0x89 0x44 0x24 0x20 0x41 0xff 0x56 0x38
                     0x48 0x85 0xc0 0x0f 0x85] [(rip :fail)]
                    [0x4c 0x89 0xe1 0x48 0x8b 0x15] [(rip :map-key)]
                    [0x41 0xff 0x96 0xe8 0x00 0x00 0x00 0x48 0x85 0xc0
                     0x0f 0x85] [(rip :get-map)]
                    [0x48 0x8d 0x3d] [(rip :boot-info)]
                    [0x48 0xb8] (le entry 8) [0xff 0xd0]
                    [(label :fail)]
                    [0x66 0xba 0xe9 0x00 0xb0 0x46 0xee
                     0x66 0xba 0xf4 0x00 0xb8 0x7f 0x00 0x00 0x00 0xef
                     0xfa 0xf4 0xeb 0xfc]))
            text-size (code-size tokens)
            data-address (align (+ text-rva text-size) section-alignment)
            data (vec (concat (mapcat #(le (:paddr %) 8) segments)
                              (le 0x544f4f4245554941 8)
                              (le (if payload? 2 1) 8)
                              (repeat (- variables-size 32) 0)
                              (repeat (- memory-map-offset variables-size) 0)
                              (repeat memory-map-capacity 0)
                              (repeat (- embedded-offset
                                         (+ memory-map-offset memory-map-capacity)) 0)
                              payload
                              (repeat (- kernel-offset
                                         (+ payload-offset (count payload))) 0)
                              kernel))
            data-raw-size (align (count data) file-alignment)
            reloc-address (align (+ data-address (count data)) section-alignment)
            labels (merge {:address0 (+ data-address (nth data-addresses 0))
                           :address1 (+ data-address (nth data-addresses 1))
                           :boot-info (+ data-address 16)
                           :map-pointer (+ data-address 32)
                           :map-size (+ data-address 40)
                           :descriptor-size (+ data-address 48)
                           :descriptor-version (+ data-address 56)
                           :map-key (+ data-address 64)
                           :memory-map (+ data-address memory-map-offset)
                           :payload-pointer (+ data-address 72)
                           :payload-length (+ data-address 80)
                           :payload (+ data-address payload-offset)}
                          (into {} (map-indexed
                                    (fn [index segment]
                                      [(keyword (str "segment" index))
                                       (+ data-address kernel-offset (:offset segment))])
                                    segments)))
            text (finalize-loader tokens labels)
            text-raw-size (align (count text) file-alignment)
            data-offset (+ text-offset text-raw-size)
            reloc-offset (+ data-offset data-raw-size)
            reloc (vec (concat (le 0 4) (le 12 4) (le 0 2) (le 0 2)))
            reloc-raw-size (align (count reloc) file-alignment)
            image-size (align (+ reloc-address (count reloc)) section-alignment)
            bytes (pe/encode-image
                   {:machine :x86-64 :entry-rva text-rva :text-rva text-rva
                    :image-base image-base :section-alignment section-alignment
                    :file-alignment file-alignment :headers-size text-offset
                    :image-size image-size :subsystem :efi-application
                    :data-directories {5 {:rva reloc-address :size (count reloc)}}
                    :sections [{:name ".text" :virtual-size (count text)
                                :rva text-rva :raw-size text-raw-size
                                :raw-offset text-offset :characteristics 0x60000020
                                :bytes text}
                               {:name ".data" :virtual-size (count data)
                                :rva data-address :raw-size data-raw-size
                                :raw-offset data-offset :characteristics 0xc0000040
                                :bytes data}
                               {:name ".reloc" :virtual-size (count reloc)
                                :rva reloc-address :raw-size reloc-raw-size
                                :raw-offset reloc-offset :characteristics 0x42000040
                                :bytes reloc}]})]
        {:format :pe32+-embedded-kernel/v2 :target firmware-target
         :entry :efi_main :entry-rva text-rva :sections [:text :data :reloc]
         :boot-info-layout (cond->
                            {:bytes (+ (- memory-map-offset 16)
                                       memory-map-capacity (count payload))
                             :memory-map-offset (- memory-map-offset 16)
                             :memory-map-capacity memory-map-capacity}
                             payload?
                             (assoc :payload-offset (- payload-offset 16)
                                    :payload-bytes (count payload)))
         :imports [] :embedded-kernel-sha256 (artifact/sha256 kernel)
         :embedded-payload-sha256 (when payload? (artifact/sha256 payload))
         :bytes bytes}))))))
