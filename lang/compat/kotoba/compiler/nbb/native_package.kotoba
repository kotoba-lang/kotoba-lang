(ns kotoba.compiler.nbb.native-package
  "Artifact packaging for the JDK-free native driver.

  `kotoba.compiler.nbb.cli` sealed a `:kotoba.kexe/v1` artifact and wrote it
  out as EDN, full stop. The JVM CLI does one more thing for the aiueos target
  profiles: it hands that same sealed artifact to an ELF64 or PE32+ packager
  and writes THOSE bytes to `--output`. Nothing about that step needs a JVM --
  `kotoba.native.elf64` and `kotoba.compiler.packaging.pe32plus` are portable
  `.cljc` that load unchanged under nbb -- it simply had no caller on this
  route. That absence is the whole reason `os/aiueos` built every one of its
  67 kernel objects through `clojure`.

  Kept out of `cli.cljs` deliberately: that namespace is shared with the Wasm
  driver, which must not pay to load two native packagers it will never call.
  The ISA entrypoint supplies `package` the same way it supplies
  `emit-program`."
  (:require [kotoba.compiler.packaging.pe32plus :as pe32plus]
            [kotoba.kir.target :as target-profile]
            [kotoba.native.elf64 :as elf64]))

(def ^:private kits
  "Exactly the `cond->` arms of `kotoba.compiler.core/compile-source*`. A target
  absent here has no packaged form, which is not an error: it means `--output`
  receives the artifact EDN, same as on the JVM."
  {:x86_64-aiueos-kernel-v1  {:object elf64/package-kernel-object
                              :image  elf64/package-kernel}
   :aarch64-aiueos-kernel-v1 {:image  elf64/package-kernel-aarch64}
   :x86_64-aiueos-user-v1    {:image  elf64/package-user}
   :x86_64-aiueos-uefi-v1    {:image  pe32plus/package-efi}})

(def ^:private divergent-twins
  "`kotoba.native.elf64` is a TWIN: a `.clj` the JVM loads and a `.cljc` this
  route loads (kotoba-native ADR-0036), and the ADR keeps them apart on purpose
  -- the live-boot GDT/TSS shim lives only in the JVM file, which also lays the
  kernel RW context at a different offset.

  Measured 2026-08-31 on one source: the x86-64 kernel IMAGE is 65,904 bytes
  here and 110,872 through the JVM. The object, the CPL3 user image, the UEFI
  application and the AArch64 kernel image are byte-identical across both
  routes; this one is not, and it is not a bug to fix here -- the divergence is
  the ADR's decision.

  So it is refused rather than served. Producing a materially different kernel
  image under a flag whose whole value is that it refuses instead of falling
  back would be the exact failure this route was added to remove: a quiet wrong
  answer where the caller asked for an equivalent one."
  {[:x86_64-aiueos-kernel-v1 :image]
   (str "the portable elf64 twin does not carry the live-boot GDT/TSS shim, so "
        "this route's x86-64 kernel image is not the JVM's (65,904 vs 110,872 "
        "bytes measured 2026-08-31). kotoba-native ADR-0036 keeps the two files "
        "apart deliberately. Build the kernel image through the JVM entry point; "
        "the kernel OBJECT, the CPL3 user image, the UEFI application and the "
        "AArch64 kernel image are byte-identical on this route.")})

(defn- refusal-is-not-a-defect
  "Run a packager, giving its deliberate refusals a `:phase` so the CLI can
  tell them apart from a crash.

  `kotoba.native.elf64` refuses a source claiming an `aiueos-*` export with no
  admitted symbol, and the PE32+ packager refuses a malformed image; both raise
  `ex-info` carrying data but no `:phase`. `cli-support/exit-code` reads
  `:phase`, so every one of those arrived as `:internal`, exit 70, and the
  refusal's own sentence was replaced by the words `internal compiler error`.
  Measured 2026-09-02 on b1fdaad2, single-file and project route alike: a
  module exporting `aiueos-not-a-symbol` was told the compiler had broken.

  Only an error CARRYING `ex-data` is re-tagged: raising `ex-info` is how a
  packager says it decided to refuse. A `TypeError` out of a packager IS a
  compiler defect and has to keep saying so -- re-tagging everything would
  turn every internal packaging bug into `your source is wrong`, which is the
  same mistake pointed the other way."
  [target artifact-kind thunk]
  (try
    (thunk)
    (catch :default error
      (if-let [data (ex-data error)]
        (if (:phase data)
          (throw error)
          (throw (ex-info (ex-message error)
                          (assoc data :phase :artifact-target
                                      :target target
                                      :artifact artifact-kind))))
        (throw error)))))

(defn package
  "Select and run the packager exactly as `kotoba.compiler.cli`'s `:kexe/v1`
  branch does, and return a Buffer, or nil when the artifact EDN is what
  `--output` should hold.

  The defaulting rule is copied rather than simplified: with no `--artifact`,
  an object is preferred when one exists, and an image is used only for a
  `:process` profile. That is why `--target x86_64-aiueos-kernel-v1` alone
  yields a `.o` (what `bisect-object-migration.sh` hashes) while the UEFI
  image requires `--artifact image` (what `build-kotoba-native-kernel.sh`
  passes)."
  [target artifact artifact-kind]
  (let [kit (get kits target)
        packager (case artifact-kind
                   "image" (:image kit)
                   "object" (:object kit)
                   (or (:object kit)
                       (when (= :process (:execution (target-profile/profile target)))
                         (:image kit))))
        selected-kind (cond (= packager (:image kit)) :image
                            (= packager (:object kit)) :object)]
    (when-let [reason (get divergent-twins [target selected-kind])]
      (throw (ex-info (str "no JVM-free implementation: " reason)
                      {:phase :artifact-target :target target
                       :artifact selected-kind
                       :reason :packager-twin-divergence})))
    (when packager
      (let [packaged (refusal-is-not-a-defect
                      target selected-kind #(packager artifact))]
        (js/Buffer.from (clj->js (mapv #(bit-and (int %) 0xff) (:bytes packaged))))))))
