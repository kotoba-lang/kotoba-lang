(ns kotoba.compiler.target)

(def profiles
  {:wasm32-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :wasm :isa :wasm32 :os :unspecified :abi :wasm-mvp :runtime :kotoba-capability-host-v1}
   :wasm32-browser-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :wasm :isa :wasm32 :os :browser :abi :wasm-mvp :runtime :kotoba-browser-host-v1}
   :wasm32-wasi-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :wasm :isa :wasm32 :os :wasi :abi :wasm-mvp :runtime :kotoba-wasi-host-v1}
   :x86_64-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :x86_64 :os :unspecified :abi :sysv :runtime :kotoba-supervisor-v1}
   :x86_64-linux-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :x86_64 :os :linux :abi :sysv :runtime :kotoba-linux-supervisor-v1}
   :x86_64-macos-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :x86_64 :os :macos :abi :sysv :runtime :kotoba-macos-supervisor-v1}
   :x86_64-windows-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :x86_64 :os :windows :abi :kotoba-sysv-v1 :runtime :kotoba-windows-supervisor-v1}
   :x86_64-aiueos-uefi-v1 {:format :kotoba.target-profile/v1 :execution :firmware :isa :x86_64 :os :aiueos :abi :microsoft-x64 :runtime :none
                           :artifact :pe32+ :subsystem :efi-application :entry :efi_main
                           :entry-contract :microsoft-x64-zero-arity-efi-status-v1
                           :internal-abi :kotoba-sysv-context-r9-v2 :ambient-syscalls false}
   :x86_64-aiueos-kernel-v1 {:format :kotoba.target-profile/v1 :execution :kernel :isa :x86_64 :os :aiueos :abi :aiueos-kernel-v1 :runtime :none
                             :artifact :elf64 :link-artifact :elf64-relocatable
                             :entry :aiueos_kernel_entry :ambient-syscalls false
                             :host-imports false :dynamic-linker false}
   :x86_64-aiueos-user-v1 {:format :kotoba.target-profile/v1 :execution :process :isa :x86_64 :os :aiueos :abi :aiueos-user-v1 :runtime :kotoba-aiueos-user-v1
                           :artifact :elf64 :entry :aiueos_process_entry
                           :entry-contract :kotoba-sysv-context-r9-aiueos-runtime-v2
                           :ambient-syscalls false :host-imports false :dynamic-linker false}
   :aarch64-aiueos-kernel-v1 {:format :kotoba.target-profile/v1 :execution :kernel :isa :aarch64 :os :aiueos :abi :aiueos-kernel-v1 :runtime :none
                              :artifact :elf64 :link-artifact :elf64-relocatable
                              :entry :aiueos_kernel_entry :ambient-syscalls false
                              :host-imports false :dynamic-linker false}
   :aarch64-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :aarch64 :os :unspecified :abi :aapcs64 :runtime :kotoba-supervisor-v1}
   :aarch64-linux-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :aarch64 :os :linux :abi :aapcs64 :runtime :kotoba-linux-supervisor-v1}
   :aarch64-macos-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :aarch64 :os :macos :abi :aapcs64 :runtime :kotoba-macos-supervisor-v1}
   :aarch64-windows-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :aarch64 :os :windows :abi :kotoba-aapcs64-v1 :runtime :kotoba-windows-supervisor-v1}
   :aarch64-android-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :aarch64 :os :android :abi :aapcs64 :runtime :kotoba-android-isolated-host-v1}
   :aarch64-ios-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :native :isa :aarch64 :os :ios :abi :aapcs64 :runtime :kotoba-ios-static-host-v1}
   ;; ADR-2607151500: a genuinely new execution target (KIR -> cljs SOURCE
   ;; TEXT, not machine code), distinct from every profile above -- a host
   ;; requires the emitted namespace directly (nbb, a browser bundle,
   ;; shadow-cljs) and calls `main` itself, same "host writes inputs, host
   ;; calls main" shape as wasm32/native, but with zero WASM-instantiation
   ;; boundary. :cljs-kotoba-v1 is the generic/default profile;
   ;; :cljs-node-kotoba-v1/:cljs-browser-kotoba-v1 share the identical
   ;; backend/:isa and differ only in :os/:runtime, mirroring how
   ;; wasm32-browser/wasm32-wasi already relate to wasm32-kotoba-v1.
   :cljs-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :cljs :isa :cljs :os :unspecified :abi :cljs-source-v1 :runtime :kotoba-cljs-host-v1}
   :cljs-node-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :cljs :isa :cljs :os :node :abi :cljs-source-v1 :runtime :kotoba-cljs-node-host-v1}
   :cljs-browser-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :cljs :isa :cljs :os :browser :abi :cljs-source-v1 :runtime :kotoba-cljs-browser-host-v1}
   :js-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :javascript :isa :javascript :os :unspecified :abi :kotoba-restricted-esm-v1 :runtime :kototama-js-host-v1}
   :js-browser-kotoba-v1 {:format :kotoba.target-profile/v1 :execution :javascript :isa :javascript :os :browser :abi :kotoba-restricted-esm-v1 :runtime :kototama-worker-host-v1}})

(def compatibility-targets #{:wasm32-kotoba-v1 :x86_64-kotoba-v1 :aarch64-kotoba-v1 :cljs-kotoba-v1 :js-kotoba-v1})
(defn profile [target] (get profiles target))
(defn backend [target]
  (case (:isa (profile target))
    :wasm32 :wasm32-kotoba-v1
    :x86_64 :x86_64-kotoba-v1
    :aarch64 :aarch64-kotoba-v1
    :cljs :cljs-kotoba-v1
    :javascript :js-kotoba-v1
    nil))
