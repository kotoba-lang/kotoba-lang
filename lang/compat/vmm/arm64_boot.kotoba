(ns vmm.arm64-boot
  "Where an arm64 Linux guest's image, devicetree and initrd go.

  Every decision here is made by `kotoba/arm64_boot_core.kotoba`, executed
  from the shipped KIR. This namespace threads values between the staged
  admissions and names the result; it decides nothing itself, so there is no
  second implementation to drift from the first."
  (:require [vmm.oracle :as oracle]))

(defn- c [f args] (oracle/call :arm64-boot f args))
(defn- r [f args] (oracle/call-result :arm64-boot f args))

(def image-magic
  "\"ARM\\x64\" as a little-endian u32 -- offset 56 of the Image header."
  (delay (c :image-magic [])))

(defn plan
  "Plan a boot. Returns

    {:ok? true :kernel-gpa n :kernel-end n :dtb-gpa n :dtb-end n
     :initrd-gpa n :initrd-end n :x0 n :x1 0 :x2 0 :x3 0 :pc n}

  or `{:ok? false :reason \"arm64-boot/...\"}` naming the first rule that
  refused. `magic`, `image-size`, `text-offset` and `flags` come from the
  64-byte Image header; `ram-base`/`ram-size` from the memory slot the
  monitor is about to map."
  [{:keys [magic image-size text-offset flags ram-base ram-size
           dtb-size initrd-size]
    :or {text-offset 0 flags 0 dtb-size 0 initrd-size 0}}]
  (let [img (r :admit-image [magic image-size flags])]
    (if-not (:ok? img)
      img
      (let [krn (r :admit-kernel [ram-base ram-size text-offset image-size])]
        (if-not (:ok? krn)
          krn
          (let [dtb (r :admit-dtb [ram-base ram-size text-offset image-size dtb-size])]
            (if-not (:ok? dtb)
              dtb
              (let [i-gpa (c :initrd-gpa [ram-base text-offset image-size dtb-size])
                    limit (c :ram-end [ram-base ram-size])
                    ini (r :admit-initrd [i-gpa initrd-size limit])]
                (if-not (:ok? ini)
                  ini
                  {:ok? true
                   :kernel-gpa (c :kernel-gpa [ram-base text-offset])
                   :kernel-end (c :kernel-end [ram-base text-offset image-size])
                   :dtb-gpa (:value dtb)
                   :dtb-end (c :dtb-end [ram-base text-offset image-size dtb-size])
                   :initrd-gpa i-gpa
                   :initrd-end (:value ini)
                   ;; booting.rst: x0 = DTB physical address, x1..x3 zero.
                   :x0 (c :x0-value [ram-base text-offset image-size])
                   :x1 0 :x2 0 :x3 0
                   :pc (:value krn)})))))))))
