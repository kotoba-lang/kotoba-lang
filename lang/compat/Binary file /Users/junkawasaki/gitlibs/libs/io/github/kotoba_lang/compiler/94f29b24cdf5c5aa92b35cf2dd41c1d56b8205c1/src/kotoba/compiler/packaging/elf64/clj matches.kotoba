(ns kotoba.compiler.packaging.elf64
  (:require [kotoba.compiler.artifact :as artifact]))

(def ^:private kernel-target :x86_64-aiueos-kernel-v1)
(def ^:private user-target :x86_64-aiueos-user-v1)
(def ^:private page-size 0x1000)
(def ^:private image-base 0x100000)
(def ^:private text-offset page-size)
(def ^:private data-offset (* 2 page-size))
(def ^:private kernel-data-offset (* 8 page-size))
(def ^:private context-size 80)
(def ^:private kernel-image-context-size 88)
(def ^:private user-context-size 88)
(def ^:private user-image-base 0x1e0000)

(def ^:private journal-entry 'aiueos-journal-plan)
(def ^:private kernel-object-entries
  {journal-entry {:arity 4 :symbol "kotoba_aiueos_journal_plan"}
   'aiueos-fnv1a {:arity 2 :symbol "kotoba_aiueos_fnv1a"}
   'aiueos-journal-record-valid {:arity 2 :symbol "kotoba_aiueos_journal_record_valid"}
   'aiueos-object-transaction-valid {:arity 2 :symbol "kotoba_aiueos_object_transaction_valid"}
   'aiueos-object-transaction-route {:arity 2 :symbol "kotoba_aiueos_object_transaction_route"}
   'aiueos-mutable-object-valid {:arity 5 :symbol "kotoba_aiueos_mutable_object_valid"}
   'aiueos-superblock-valid {:arity 2 :symbol "kotoba_aiueos_superblock_valid"}
   'aiueos-journal-record-build {:arity 3 :symbol "kotoba_aiueos_journal_record_build"}
   'aiueos-mutable-object-build {:arity 5 :symbol "kotoba_aiueos_mutable_object_build"}
   'aiueos-virtio-cap-valid {:arity 5 :symbol "kotoba_aiueos_virtio_cap_valid"}
   'aiueos-pci-extent-valid {:arity 2 :symbol "kotoba_aiueos_pci_extent_valid"}
   'aiueos-pci-region-valid {:arity 3 :symbol "kotoba_aiueos_pci_region_valid"}
   'aiueos-syscall-range-valid {:arity 4 :symbol "kotoba_aiueos_syscall_range_valid"}
   'aiueos-copy-in {:arity 5 :symbol "kotoba_aiueos_copy_in"}
   'aiueos-capability-plan {:arity 5 :symbol "kotoba_aiueos_capability_plan"}
   'aiueos-capability-mutation-plan {:arity 5 :symbol "kotoba_aiueos_capability_mutation_plan"}
   'aiueos-service-lifecycle {:arity 4 :symbol "kotoba_aiueos_service_lifecycle"}
   'aiueos-service-registry-build {:arity 5 :symbol "kotoba_aiueos_service_registry_build"}
   'aiueos-service-registry-state {:arity 3 :symbol "kotoba_aiueos_service_registry_state"}
   'aiueos-user-object-journal-build {:arity 5 :symbol "kotoba_aiueos_user_object_journal_build"}
   'aiueos-user-object-journal-valid {:arity 3 :symbol "kotoba_aiueos_user_object_journal_valid"}
   'aiueos-user-object-journal-value {:arity 2 :symbol "kotoba_aiueos_user_object_journal_value"}
   'aiueos-sha256 {:arity 5 :symbol "kotoba_aiueos_sha256"}
   'aiueos-digest-equal {:arity 3 :symbol "kotoba_aiueos_digest_equal"}
   'aiueos-app-catalog-valid {:arity 5 :symbol "kotoba_aiueos_app_catalog_valid"}
   'aiueos-app-lookup-plan {:arity 5 :symbol "kotoba_aiueos_app_lookup_plan"}
   'aiueos-user-elf-valid {:arity 2 :symbol "kotoba_aiueos_user_elf_valid"}
   'aiueos-user-context-build {:arity 4 :symbol "kotoba_aiueos_user_context_build"}
   'aiueos-page-mapping-plan {:arity 5 :symbol "kotoba_aiueos_page_mapping_plan"}
   'aiueos-process-create-plan {:arity 5 :symbol "kotoba_aiueos_process_create_plan"}
   'aiueos-process-teardown-plan {:arity 5 :symbol "kotoba_aiueos_process_teardown_plan"}
   'aiueos-task-slot-plan {:arity 5 :symbol "kotoba_aiueos_task_slot_plan"}
   'aiueos-scheduler-dispatch-plan {:arity 5 :symbol "kotoba_aiueos_scheduler_dispatch_plan"}
   'aiueos-task-exit-route {:arity 5 :symbol "kotoba_aiueos_task_exit_route"}
   'aiueos-service-task-transition {:arity 5 :symbol "kotoba_aiueos_service_task_transition"}
   'aiueos-rsa2048-sha256-verify {:arity 5 :symbol "kotoba_aiueos_rsa2048_sha256_verify"}})

(defn- le [n width]
  (mapv #(bit-and (unsigned-bit-shift-right (long n) (* 8 %)) 0xff)
        (range width)))

(defn- padded [bytes size]
  (when (> (count bytes) size)
    (throw (ex-info "ELF64 region exceeds its allocation"
                    {:size size :actual (count bytes)})))
  (into (vec bytes) (repeat (- size (count bytes)) 0)))

(def ^:private em-x86-64 0x3e)
(def ^:private em-aarch64 0xb7)

(defn- elf-header*
  [machine entry program-header-count section-offset section-count]
  (vec (concat
        [0x7f 0x45 0x4c 0x46 2 1 1 0] (repeat 8 0)
        (le 2 2)                         ; ET_EXEC
        (le machine 2)                   ; EM_X86_64 / EM_AARCH64
        (le 1 4)
        (le entry 8)
        (le 64 8)                        ; program headers follow ELF header
        (le section-offset 8)
        (le 0 4)
        (le 64 2) (le 56 2) (le program-header-count 2)
        (le 64 2) (le section-count 2) (le 3 2)))) ; .shstrtab index

(defn- elf-header [entry program-header-count section-offset section-count]
  (elf-header* em-x86-64 entry program-header-count section-offset section-count))

(defn- program-header [flags offset address file-size memory-size]
  (vec (concat (le 1 4) (le flags 4)     ; PT_LOAD
               (le offset 8) (le address 8) (le address 8)
               (le file-size 8) (le memory-size 8) (le page-size 8))))

(defn- section-header [name type flags address offset size alignment]
  (vec (concat (le name 4) (le type 4) (le flags 8) (le address 8)
               (le offset 8) (le size 8) (le 0 4) (le 0 4)
               (le alignment 8) (le 0 8))))

(defn- entry-shim [main-address context-address]
  ;; Preserve the loader's SysV rdi boot-info pointer in context+80, then
  ;; initialize r9 and call the zero-arity Kotoba entry.
  (let [shim-address (+ image-base text-offset)
        after-store (+ shim-address 7)
        after-lea (+ shim-address 14)
        after-call (+ shim-address 19)]
    (vec (concat [0x48 0x89 0x3d] (le (- (+ context-address 80) after-store) 4)
                 [0x4c 0x8d 0x0d] (le (- context-address after-lea) 4)
                 [0xe8] (le (- main-address after-call) 4)
                 [0xfa 0xf4 0xeb 0xfd]))))

(defn- user-entry-shim [main-address context-address]
  (let [entry-address (+ user-image-base text-offset)
        after-lea (+ entry-address 7)
        after-call (+ entry-address 12)
        after-store (+ entry-address 19)
        runtime-trampoline (+ entry-address 32)]
    (vec (concat [0x4c 0x8d 0x0d] (le (- context-address after-lea) 4)
                 [0xe8] (le (- main-address after-call) 4)
                 [0x48 0x89 0x05] (le (- context-address after-store) 4)
                 [0xf3 0x90 0xeb 0xfc]
                 (repeat 9 0x90)
                 ;; Kotoba cap-call callback: the compiler-derived bitmap has
                 ;; already admitted rsi=capability-id. rdx carries its scalar
                 ;; argument. Load the kernel-issued, domain-owned handle from
                 ;; context+80 and enter aiueos syscall 5. No ambient address or
                 ;; host import is exposed to the program.
                 [0xb8 0x05 0x00 0x00 0x00       ; mov eax,5
                  0x48 0x8b 0x7f 0x50            ; mov rdi,[rdi+80]
                  0x0f 0x05 0xc3]                ; syscall; ret
                 (repeat (- 64 44) 0x90)))))

(defn- capability-bitmap [effects]
  (reduce (fn [bitmap [_ id]]
            (update bitmap (quot id 8) bit-or (bit-shift-left 1 (mod id 8))))
          (vec (repeat 32 0))
          (filter #(= :cap/call (first %)) effects)))

(defn package-kernel
  "Package a sealed aiueos kernel artifact as a freestanding ELF64 ET_EXEC.
  The returned byte vector has no interpreter, dynamic section, or host imports."
  [artifact]
  (when-not (artifact/valid-seal? artifact)
    (throw (ex-info "ELF64 kernel packaging requires a sealed artifact" {})))
  (when-not (= kernel-target (:target artifact))
    (throw (ex-info "ELF64 kernel packaging requires the aiueos kernel target"
                    {:target (:target artifact)})))
  (when-not (and (= :none (get-in artifact [:target-profile :runtime]))
                 (false? (get-in artifact [:target-profile :ambient-syscalls])))
    (throw (ex-info "ELF64 kernel packaging requires a freestanding profile"
                    {:target-profile (:target-profile artifact)})))
  (let [source-entry (get-in artifact [:program :entry])
        export (get-in artifact [:exports source-entry])]
    (when-not export
      (throw (ex-info "Kotoba kernel entry is not exported" {:entry source-entry})))
    (let [entry-address (+ image-base text-offset)
          context-address (+ image-base kernel-data-offset)
          shim (entry-shim (+ entry-address 23 (:offset export)) context-address)
          text (into shim (:code artifact))
          context (into (vec (repeat 8 0))
                        (concat (le 256 8) (repeat (- kernel-image-context-size 16) 0)))
          names (mapv int (.getBytes "\u0000.text\u0000.data\u0000.shstrtab\u0000" "UTF-8"))
          names-offset (+ kernel-data-offset kernel-image-context-size)
          section-offset (+ names-offset (count names)
                            (mod (- 8 (mod (+ names-offset (count names)) 8)) 8))
          sections [(vec (repeat 64 0))
                    (section-header 1 1 0x6 entry-address text-offset (count text) 16)
                    (section-header 7 1 0x3 context-address kernel-data-offset kernel-image-context-size 8)
                    (section-header 13 3 0 0 names-offset (count names) 1)]
          header (elf-header entry-address 2 section-offset (count sections))
          phdrs (concat (program-header 0x5 text-offset entry-address (count text) (count text))
                        (program-header 0x6 kernel-data-offset context-address
                                        kernel-image-context-size kernel-image-context-size))
          before-text (padded (concat header phdrs) text-offset)
          before-data (padded (concat before-text text) kernel-data-offset)
          before-sections (padded (concat before-data context names) section-offset)
          bytes (vec (concat before-sections (mapcat identity sections)))]
      {:format :elf64/v1
       :target kernel-target
       :entry :aiueos_kernel_entry
       :source-entry source-entry
       :entry-address entry-address
       :sections [:text :data :shstrtab]
       :imports []
       :interpreter nil
       :bytes bytes})))

(def ^:private aarch64-kernel-target :aarch64-aiueos-kernel-v1)

(defn- entry-shim-aarch64 [main-address context-address]
  ;; AArch64 kernel entry: set the hidden context register x7 = context, call
  ;; the zero-arity Kotoba entry, then park. (The x86-64 shim also stashes the
  ;; SysV rdi boot-info pointer; AArch64's boot-info convention is separate and
  ;; a program that needs it would take it another way -- this bare shim only
  ;; establishes the fuel/capability context.)
  (let [shim-address (+ image-base text-offset)
        bl-pc (+ shim-address 8)
        ctx-lo (bit-and context-address 0xffff)
        ctx-hi (bit-and (unsigned-bit-shift-right context-address 16) 0xffff)]
    (vec (concat
          (le (bit-or 0xd2800007 (bit-shift-left ctx-lo 5)) 4)  ; movz x7, #ctx-lo
          (le (bit-or 0xf2a00007 (bit-shift-left ctx-hi 5)) 4)  ; movk x7, #ctx-hi, lsl #16
          (le (bit-or 0x94000000
                      (bit-and (quot (- main-address bl-pc) 4) 0x03ffffff)) 4) ; bl main
          (le 0x14000000 4)))))                                 ; b . (park)

(defn package-kernel-aarch64
  "Package a sealed aiueos AArch64 kernel artifact as a freestanding ELF64
  ET_EXEC (EM_AARCH64), mirroring `package-kernel`: an entry shim establishes the
  hidden x7 context (with fuel), then calls the zero-arity Kotoba entry."
  [artifact]
  (when-not (artifact/valid-seal? artifact)
    (throw (ex-info "ELF64 kernel packaging requires a sealed artifact" {})))
  (when-not (= aarch64-kernel-target (:target artifact))
    (throw (ex-info "AArch64 ELF64 kernel packaging requires the aarch64 aiueos kernel target"
                    {:target (:target artifact)})))
  (when-not (and (= :none (get-in artifact [:target-profile :runtime]))
                 (false? (get-in artifact [:target-profile :ambient-syscalls])))
    (throw (ex-info "ELF64 kernel packaging requires a freestanding profile"
                    {:target-profile (:target-profile artifact)})))
  (let [source-entry (get-in artifact [:program :entry])
        export (get-in artifact [:exports source-entry])]
    (when-not export
      (throw (ex-info "Kotoba kernel entry is not exported" {:entry source-entry})))
    (let [entry-address (+ image-base text-offset)
          context-address (+ image-base kernel-data-offset)
          ;; the aarch64 shim is 16 bytes (4 instructions).
          shim (entry-shim-aarch64 (+ entry-address 16 (:offset export)) context-address)
          text (into shim (:code artifact))
          context (into (vec (repeat 8 0))
                        (concat (le 256 8) (repeat (- kernel-image-context-size 16) 0)))
          names (mapv int (.getBytes " .text .data .shstrtab " "UTF-8"))
          names-offset (+ kernel-data-offset kernel-image-context-size)
          section-offset (+ names-offset (count names)
                            (mod (- 8 (mod (+ names-offset (count names)) 8)) 8))
          sections [(vec (repeat 64 0))
                    (section-header 1 1 0x6 entry-address text-offset (count text) 16)
                    (section-header 7 1 0x3 context-address kernel-data-offset kernel-image-context-size 8)
                    (section-header 13 3 0 0 names-offset (count names) 1)]
          header (elf-header* em-aarch64 entry-address 2 section-offset (count sections))
          phdrs (concat (program-header 0x5 text-offset entry-address (count text) (count text))
                        (program-header 0x6 kernel-data-offset context-address
                                        kernel-image-context-size kernel-image-context-size))
          before-text (padded (concat header phdrs) text-offset)
          before-data (padded (concat before-text text) kernel-data-offset)
          before-sections (padded (concat before-data context names) section-offset)
          bytes (vec (concat before-sections (mapcat identity sections)))]
      {:format :elf64/v1
       :target aarch64-kernel-target
       :entry :aiueos_kernel_entry
       :source-entry source-entry
       :entry-address entry-address
       :sections [:text :data :shstrtab]
       :imports []
       :interpreter nil
       :bytes bytes})))

(defn package-user
  "Package a sealed zero-arity Kotoba program as an aiueos CPL3 ELF64 image."
  [artifact]
  (when-not (artifact/valid-seal? artifact)
    (throw (ex-info "ELF64 user packaging requires a sealed artifact" {})))
  (when-not (= user-target (:target artifact))
    (throw (ex-info "ELF64 user packaging requires the aiueos user target"
                    {:target (:target artifact)})))
  (let [source-entry (get-in artifact [:program :entry])
        export (get-in artifact [:exports source-entry])]
    (when-not (and export (zero? (:arity export)))
      (throw (ex-info "aiueos process entry requires zero arguments" {:entry source-entry})))
    (let [entry-address (+ user-image-base text-offset)
          context-address (+ user-image-base data-offset)
          shim (user-entry-shim (+ entry-address 64 (:offset export)) context-address)
          text (into shim (:code artifact))
          bitmap (capability-bitmap (:effects artifact))
          callback (if (some #(= :cap/call (first %)) (:effects artifact))
                     (+ entry-address 32) 0)
          context (vec (concat (repeat 8 0) (le 256 8) bitmap
                               (le callback 8) (repeat 24 0)
                               (repeat 8 0)))
          names (mapv int (.getBytes "\u0000.text\u0000.data\u0000.shstrtab\u0000" "UTF-8"))
          names-offset (+ data-offset user-context-size)
          section-offset (+ names-offset (count names)
                            (mod (- 8 (mod (+ names-offset (count names)) 8)) 8))
          sections [(vec (repeat 64 0))
                    (section-header 1 1 0x6 entry-address text-offset (count text) 16)
                    (section-header 7 1 0x3 context-address data-offset user-context-size 8)
                    (section-header 13 3 0 0 names-offset (count names) 1)]
          header (elf-header entry-address 2 section-offset (count sections))
          phdrs (concat (program-header 0x5 text-offset entry-address (count text) (count text))
                        (program-header 0x6 data-offset context-address user-context-size user-context-size))
          before-text (padded (concat header phdrs) text-offset)
          before-data (padded (concat before-text text) data-offset)
          before-sections (padded (concat before-data context names) section-offset)]
      {:format :elf64/v1 :target user-target :entry :aiueos_process_entry
       :source-entry source-entry :entry-address entry-address
       :result-address context-address :sections [:text :data :shstrtab]
       :imports [] :interpreter nil
       :entry-contract :kotoba-sysv-context-r9-aiueos-runtime-v2
       :runtime-handle-offset 80
       :bytes (vec (concat before-sections (mapcat identity sections)))})))

(defn- rela [offset symbol type addend]
  (vec (concat (le offset 8)
               (le (bit-or (bit-shift-left symbol 32) type) 8)
               (le addend 8))))

(defn- symbol-entry [name info section value size]
  (vec (concat (le name 4) [info 0] (le section 2)
               (le value 8) (le size 8))))

(defn- reloc-section-header [name type flags offset size link info alignment entry-size]
  (vec (concat (le name 4) (le type 4) (le flags 8) (le 0 8)
               (le offset 8) (le size 8) (le link 4) (le info 4)
               (le alignment 8) (le entry-size 8))))

(defn package-kernel-object
  "Emit a linkable x86-64 ET_REL object whose public SysV probe calls the
  compiler-generated Kotoba entry with a private freestanding context.  The
  object deliberately contains no dynamic metadata or unresolved host symbol."
  [artifact]
  (when-not (artifact/valid-seal? artifact)
    (throw (ex-info "ELF64 kernel object packaging requires a sealed artifact" {})))
  (when-not (= kernel-target (:target artifact))
    (throw (ex-info "ELF64 kernel object packaging requires the aiueos kernel target"
                    {:target (:target artifact)})))
  (when-not (and (= :none (get-in artifact [:target-profile :runtime]))
                 (false? (get-in artifact [:target-profile :ambient-syscalls])))
    (throw (ex-info "ELF64 kernel object packaging requires a freestanding profile"
                    {:target-profile (:target-profile artifact)})))
  (let [source-entry (get-in artifact [:program :entry])
        object-entry (or (some #(when (contains? (:exports artifact) %) %)
                               (keys kernel-object-entries))
                         source-entry)
        export (get-in artifact [:exports object-entry])
        contract (get kernel-object-entries object-entry {:arity 0 :symbol "kotoba_aiueos_probe"})
        public-symbol (:symbol contract)]
    (when-not (and export (= (:arity export) (:arity contract)))
      (throw (ex-info "Kotoba kernel object entry has an invalid SysV arity"
                      {:entry object-entry :arity (:arity export)})))
    ;; lea r9,[rip+.data] (relocated); optionally replenish bounded-memory
    ;; fuel; sub rsp,8; call local Kotoba entry; add rsp,8; ret.
    (let [sha-fuel? (= 'aiueos-sha256 object-entry)
          rsa-fuel? (= 'aiueos-rsa2048-sha256-verify object-entry)
          context-fuel? (= 'aiueos-user-context-build object-entry)
          high-fuel? (contains? '#{aiueos-user-object-journal-build
                                    aiueos-user-object-journal-valid} object-entry)
          bounded-memory? (or sha-fuel? rsa-fuel? context-fuel? high-fuel? (contains? '#{aiueos-fnv1a aiueos-journal-record-valid
                                        aiueos-object-transaction-valid aiueos-object-transaction-route
                                        aiueos-mutable-object-valid
                                        aiueos-superblock-valid aiueos-journal-record-build
                                        aiueos-mutable-object-build aiueos-copy-in
                                        aiueos-digest-equal
                                        aiueos-app-catalog-valid
                                        aiueos-app-lookup-plan
                                        aiueos-user-elf-valid
                                        aiueos-user-context-build
                                        aiueos-process-create-plan
                                        aiueos-task-slot-plan
                                        aiueos-scheduler-dispatch-plan
                                        aiueos-task-exit-route
                                        aiueos-user-object-journal-value
                                        aiueos-service-registry-state} object-entry))
          replenish (when bounded-memory?
                      (cond
                        rsa-fuel? [0x49 0xc7 0x41 0x08 0x80 0xb2 0xe6 0x0e] ; 250,000,000
                        sha-fuel? [0x49 0xc7 0x41 0x08 0x80 0x96 0x98 0x00] ; 10,000,000
                        context-fuel? [0x49 0xc7 0x41 0x08 0x00 0x00 0x01 0x00] ; 65,536
                        high-fuel? [0x49 0xc7 0x41 0x08 0x00 0x10 0x00 0x00] ; 4096
                        :else [0x49 0xc7 0x41 0x08 0x00 0x04 0x00 0x00])) ; 1024
          wrapper (vec (concat [0x4c 0x8d 0x0d 0 0 0 0] replenish
                               [0x48 0x83 0xec 0x08 0xe8]))
          call-end (+ (count wrapper) 4)
          wrapper-size (+ call-end 5)
          main-offset (+ wrapper-size (:offset export))
          call-disp (- main-offset call-end)
          text (vec (concat wrapper (le call-disp 4)
                            [0x48 0x83 0xc4 0x08 0xc3]
                            (:code artifact)))
          context (vec (concat (repeat 8 0) (le 256 8)
                               (repeat (- context-size 16) 0)))
          shstr "\u0000.text\u0000.data\u0000.rela.text\u0000.symtab\u0000.strtab\u0000.shstrtab\u0000"
          shstr-bytes (mapv int (.getBytes shstr "UTF-8"))
          strtab (mapv int (.getBytes (str "\u0000" public-symbol "\u0000kotoba_source_entry\u0000") "UTF-8"))
          text-off 64
          data-off (+ text-off (count text))
          rela-off (+ data-off (count context))
          reloc (rela 3 2 2 -4) ; R_X86_64_PC32 against section symbol .data
          symtab-off (+ rela-off (count reloc))
          ;; null, local .text/.data section symbols, local source, global probe.
          ;; ELF requires every local symbol to precede the first global one.
          symbols (vec (concat (repeat 24 0)
                               (symbol-entry 0 0x03 1 0 0)
                               (symbol-entry 0 0x03 2 0 0)
                               (symbol-entry (+ 2 (count public-symbol)) 0x02 1 main-offset
                                             (count (:code artifact)))
                               ;; The public symbol owns the wrapper prefix; the
                               ;; selected Kotoba function remains a local symbol.
                               (symbol-entry 1 0x12 1 0 main-offset)))
          strtab-off (+ symtab-off (count symbols))
          shstr-off (+ strtab-off (count strtab))
          section-off (+ shstr-off (count shstr-bytes)
                         (mod (- 8 (mod (+ shstr-off (count shstr-bytes)) 8)) 8))
          header (vec (concat
                       [0x7f 0x45 0x4c 0x46 2 1 1 0] (repeat 8 0)
                       (le 1 2) (le 0x3e 2) (le 1 4) ; ET_REL, EM_X86_64
                       (le 0 8) (le 0 8) (le section-off 8) (le 0 4)
                       (le 64 2) (le 0 2) (le 0 2) (le 64 2) (le 7 2) (le 6 2)))
          sections [(vec (repeat 64 0))
                    (reloc-section-header 1 1 0x6 text-off (count text) 0 0 16 0)
                    (reloc-section-header 7 1 0x3 data-off (count context) 0 0 8 0)
                    (reloc-section-header 13 4 0 rela-off (count reloc) 4 1 8 24)
                    (reloc-section-header 24 2 0 symtab-off (count symbols) 5 4 8 24)
                    (reloc-section-header 32 3 0 strtab-off (count strtab) 0 0 1 0)
                    (reloc-section-header 40 3 0 shstr-off (count shstr-bytes) 0 0 1 0)]
          before-sections (padded (concat header text context reloc symbols strtab shstr-bytes)
                                  section-off)
          bytes (vec (concat before-sections (mapcat identity sections)))]
      {:format :elf64-relocatable/v1
       :target kernel-target
       :elf-type :relocatable
       :machine :x86_64
       :abi :sysv
       :export public-symbol
       :source-entry object-entry
       :sections [:text :data :rela.text :symtab :strtab :shstrtab]
       :relocations [{:section :text :offset 3 :type :r-x86-64-pc32
                      :symbol :data :addend -4}]
       :imports []
       :interpreter nil
       :bytes bytes})))
