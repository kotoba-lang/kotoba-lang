(ns kotoba.compiler.backend.aarch64)

(defn- u32le [word]
  (mapv #(bit-and (unsigned-bit-shift-right (long word) (* 8 %)) 0xff) (range 4)))
(defn- insn [word] (u32le word))
(defn- mov-reg [dst src] (insn (bit-or 0xaa0003e0 (bit-shift-left src 16) dst)))
(defn- movz [rd imm shift]
  (insn (bit-or 0xd2800000 (bit-shift-left (quot shift 16) 21)
                (bit-shift-left (bit-and imm 0xffff) 5) rd)))
(defn- movk [rd imm shift]
  (insn (bit-or 0xf2800000 (bit-shift-left (quot shift 16) 21)
                (bit-shift-left (bit-and imm 0xffff) 5) rd)))
(defn- load-constant-reg [rd value]
  (vec (concat (movz rd value 0) (movk rd (unsigned-bit-shift-right value 16) 16)
               (movk rd (unsigned-bit-shift-right value 32) 32)
               (movk rd (unsigned-bit-shift-right value 48) 48))))
(defn- load-constant [value] (load-constant-reg 0 value))
(defn- b-ne [byte-offset]
  (insn (bit-or 0x54000001 (bit-shift-left (bit-and (quot byte-offset 4) 0x7ffff) 5))))

(def ^:private signed-division
  (concat (insn 0xb5000041) (insn 0xd4200000)
          (load-constant-reg 2 Long/MIN_VALUE) (insn 0xeb02001f) (b-ne 32)
          (load-constant-reg 2 -1) (insn 0xeb02003f) (b-ne 8)
          (insn 0xd4200000) (insn 0x9ac10c00)))

(def ^:private fuel-charge
  ;; context v2: fuel is qword [x7,#8].
  (concat (insn 0xf94004f0) (insn 0xb5000050) (insn 0xd4200000)
          (insn 0xd1000610) (insn 0xf90004f0)))

(defn- normalize-expr [form env]
  (cond
    (integer? form) form
    (symbol? form) (get env form form)
    :else (let [[op & args] form]
            (if (= op 'let)
              (let [[bindings body] args
                    env' (reduce (fn [e [name value]]
                                   (assoc e name (normalize-expr value e)))
                                 env (partition 2 bindings))]
                (normalize-expr body env'))
              (apply list op (map #(normalize-expr % env) args))))))

(defn- token-size [token] (if (and (map? token) (:call token)) 4 1))
(defn- code-size [tokens] (reduce + (map token-size tokens)))
(defn- sub-sp [amount] (insn (bit-or 0xd10003ff (bit-shift-left amount 10))))
(defn- add-sp [amount] (insn (bit-or 0x910003ff (bit-shift-left amount 10))))
(defn- str-sp [reg offset]
  (insn (bit-or 0xf90003e0 (bit-shift-left (quot offset 8) 10) reg)))
(defn- ldr-sp [reg offset]
  (insn (bit-or 0xf94003e0 (bit-shift-left (quot offset 8) 10) reg)))
(defn- save-x0 [] (concat (sub-sp 16) (str-sp 0 0)))
(defn- restore-to [reg] (concat (ldr-sp reg 0) (add-sp 16)))
(defn- restore-binary []
  ;; rhs is in x0; preserve it in x1, then restore lhs into x0.
  (concat (mov-reg 1 0) (ldr-sp 0 0) (add-sp 16)))
(defn- branch [byte-offset]
  (insn (bit-or 0x14000000 (bit-and (quot byte-offset 4) 0x03ffffff))))
(defn- cbz-x0 [byte-offset]
  (insn (bit-or 0xb4000000 (bit-shift-left (bit-and (quot byte-offset 4) 0x7ffff) 5))))

(declare emit-expr)
(defn- emit-binary [left right operation env]
  (vec (concat (emit-expr left env) (save-x0) (emit-expr right env)
               (restore-binary) operation)))

(defn- emit-call [op args env]
  (when (> (count args) 5)
    (throw (ex-info "AArch64 fuel ABI supports at most five arguments"
                    {:phase :aarch64 :function op :arity (count args)})))
  (let [saved (mapcat #(concat (emit-expr % env) (save-x0)) args)
        restored (mapcat #(restore-to %) (reverse (range (count args))))]
    (vec (concat saved restored [{:call op}]))))

(defn- ldr-context [reg offset]
  (insn (bit-or 0xf9400000 (bit-shift-left (quot offset 8) 10)
                (bit-shift-left 7 5) reg)))

(defn- tbnz [reg bit-index byte-offset]
  (insn (bit-or 0x37000000
                (bit-shift-left (bit-and bit-index 0x20) 26)
                (bit-shift-left (bit-and bit-index 0x1f) 19)
                (bit-shift-left (bit-and (quot byte-offset 4) 0x3fff) 5)
                reg)))

;; Bounded kernel memory access (aiueos kernel target). Mirrors the x86-64
;; backend's kernel-load-u8/store-u8: evaluate base/length/index(/value) once,
;; then enforce length<=maximum, base!=0, and index<length -- every violation
;; reaches `brk #0` before memory is touched. AArch64 uses MMIO load/store for
;; device access; there is no port-I/O intrinsic.
(defn- b-cond [cond-code byte-offset]
  (insn (bit-or 0x54000000 (bit-shift-left (bit-and (quot byte-offset 4) 0x7ffff) 5) cond-code)))
(defn- cbz-reg [rt byte-offset]
  (insn (bit-or 0xb4000000 (bit-shift-left (bit-and (quot byte-offset 4) 0x7ffff) 5) rt)))
(def ^:private cond-hi 8)     ; unsigned length > maximum
(def ^:private cond-hs 2)     ; unsigned index >= length

(defn- bounds-check [maximum]
  ;; Precondition: x1=base, x2=length, x3=index. The caller appends a two-insn
  ;; access (add x1,x1,x3 ; strb/ldrb w0,[x1]), then `b skip`, then the `brk`
  ;; trap. Byte layout from this block's first branch:
  ;;   +0 cmp x2,x4  +4 b.hi trap  +8 cbz x1,trap  +12 cmp x3,x2  +16 b.hs trap
  ;;   +20 add       +24 access    +28 b skip      +32 brk(trap)  +36 skip
  (concat (load-constant-reg 4 maximum)
          (insn 0xeb04005f)          ; cmp x2, x4  (length vs maximum)
          (b-cond cond-hi 28)        ; b.hi trap
          (cbz-reg 1 24)             ; cbz x1, trap
          (insn 0xeb02007f)          ; cmp x3, x2  (index vs length)
          (b-cond cond-hs 16)))      ; b.hs trap

;; NB: the access uses base-register addressing `strb/ldrb w0, [x1]` after
;; computing `x1 = base + index` (add x1,x1,x3). Register-offset addressing
;; (`[x1, x3]`) leaves the ESR instruction-syndrome invalid (ISV=0) for the MMIO
;; that a device store/load triggers, so KVM cannot emulate it (it injects a
;; data abort instead of exiting) -- base-register addressing keeps ISV=1.
(defn- emit-kernel-store-u8 [[base length index value] maximum env]
  (vec (concat
        (emit-expr base env) (save-x0)
        (emit-expr length env) (save-x0)
        (emit-expr index env) (save-x0)
        (emit-expr value env)                ; x0 = value (also the result)
        (ldr-sp 3 0) (add-sp 16)             ; x3 = index
        (ldr-sp 2 0) (add-sp 16)             ; x2 = length
        (ldr-sp 1 0) (add-sp 16)             ; x1 = base
        (bounds-check maximum)
        (insn 0x8b030021)                    ; add x1, x1, x3   (x1 = base+index)
        (insn 0x39000020)                    ; strb w0, [x1]
        (branch 8)                           ; b skip
        (insn 0xd4200000))))                 ; trap: brk ; skip:

(defn- emit-kernel-load-u8 [[base length index] maximum env]
  (vec (concat
        (emit-expr base env) (save-x0)
        (emit-expr length env) (save-x0)
        (emit-expr index env)                ; x0 = index
        (mov-reg 3 0)                        ; x3 = index
        (ldr-sp 2 0) (add-sp 16)             ; x2 = length
        (ldr-sp 1 0) (add-sp 16)             ; x1 = base
        (bounds-check maximum)
        (insn 0x8b030021)                    ; add x1, x1, x3   (x1 = base+index)
        (insn 0x39400020)                    ; ldrb w0, [x1]  -> x0 = byte
        (branch 8)                           ; b skip
        (insn 0xd4200000))))                 ; trap: brk ; skip:

;; 32-bit MMIO (virtio registers are u32). Same bounds discipline, but the
;; 4-byte access must fit: index+4 <= length. Byte layout from the first branch:
;;   +0 cmp x2,x4  +4 b.hi trap  +8 cbz x1,trap  +12 add x5,x3,#4  +16 cmp x5,x2
;;   +20 b.hi trap +24 add x1,x1,x3 +28 access  +32 b skip  +36 brk(trap)  +40 skip
(defn- bounds-check-u32 [maximum]
  (concat (load-constant-reg 4 maximum)
          (insn 0xeb04005f)          ; cmp x2, x4  (length vs maximum)
          (b-cond cond-hi 32)        ; b.hi trap
          (cbz-reg 1 28)             ; cbz x1, trap
          (insn 0x91001065)          ; add x5, x3, #4   (index + 4)
          (insn 0xeb0200bf)          ; cmp x5, x2       (index+4 vs length)
          (b-cond cond-hi 16)))      ; b.hi trap  (index+4 > length)

(defn- emit-kernel-store-u32 [[base length index value] maximum env]
  (vec (concat
        (emit-expr base env) (save-x0)
        (emit-expr length env) (save-x0)
        (emit-expr index env) (save-x0)
        (emit-expr value env)                ; x0 = value (also the result)
        (ldr-sp 3 0) (add-sp 16)             ; x3 = index
        (ldr-sp 2 0) (add-sp 16)             ; x2 = length
        (ldr-sp 1 0) (add-sp 16)             ; x1 = base
        (bounds-check-u32 maximum)
        (insn 0x8b030021)                    ; add x1, x1, x3
        (insn 0xb9000020)                    ; str w0, [x1]
        (branch 8)                           ; b skip
        (insn 0xd4200000))))                 ; trap: brk ; skip:

(defn- emit-kernel-load-u32 [[base length index] maximum env]
  (vec (concat
        (emit-expr base env) (save-x0)
        (emit-expr length env) (save-x0)
        (emit-expr index env)                ; x0 = index
        (mov-reg 3 0)                        ; x3 = index
        (ldr-sp 2 0) (add-sp 16)             ; x2 = length
        (ldr-sp 1 0) (add-sp 16)             ; x1 = base
        (bounds-check-u32 maximum)
        (insn 0x8b030021)                    ; add x1, x1, x3
        (insn 0xb9400020)                    ; ldr w0, [x1]  -> x0 = word
        (branch 8)                           ; b skip
        (insn 0xd4200000))))                 ; trap: brk ; skip:

(defn- emit-cap-call [cap-id value env]
  (let [word-offset (+ 16 (* 8 (quot cap-id 64)))
        bit-index (mod cap-id 64)]
    (vec (concat
          (ldr-context 16 word-offset) (tbnz 16 bit-index 8) (insn 0xd4200000)
          (emit-expr value env)
          (mov-reg 2 0)                           ; x2=value
          (sub-sp 16) (str-sp 7 0)                ; preserve context
          (load-constant-reg 1 cap-id) (mov-reg 0 7)
          (ldr-context 16 48) (insn 0xd63f0200)   ; blr x16
          (ldr-sp 7 0) (add-sp 16)))))

(defn- emit-heap-call [op args env]
  (let [offset ({'pair 56 'pair-first 64 'pair-second 72} op)
        values (if (= op 'pair)
                 (concat (emit-expr (first args) env) (save-x0)
                         (emit-expr (second args) env) (mov-reg 2 0)
                         (restore-to 1))
                 (concat (emit-expr (first args) env) (mov-reg 1 0)))]
    (vec (concat values
                 (sub-sp 16) (str-sp 7 0)
                 (mov-reg 0 7) (ldr-context 16 offset) (insn 0xd63f0200)
                 (ldr-sp 7 0) (add-sp 16)))))

(defn emit-expr [form env]
  (cond
    (integer? form) (load-constant form)
    (symbol? form) (mov-reg 0 (+ 19 (get env form)))
    :else
    (let [[op & args] form]
      (cond
        (= op 'if)
        (let [[test then else] args test-code (emit-expr test env)
              then-code (emit-expr then env) else-code (emit-expr else env)]
          (vec (concat test-code (cbz-x0 (+ 8 (code-size then-code)))
                       then-code (branch (+ 4 (code-size else-code))) else-code)))
        ;; `do`: emit each subexpression in order; each leaves its result in x0,
        ;; the next overwrites it, so only the last value survives -- but every
        ;; subexpression's side effects (kernel MMIO) execute exactly once, in
        ;; order. This is the ordered-sequencing primitive `let` can't give
        ;; (let inlines, so a side-effecting binding used 0 or >1 times drops or
        ;; duplicates its effect).
        (= op 'do)
        (vec (mapcat #(emit-expr % env) args))
        (= op 'cap-call)
        (emit-cap-call (first args) (second args) env)
        (contains? '#{pair pair-first pair-second} op)
        (emit-heap-call op args env)
        (and (= op '-) (= 1 (count args)))
        (vec (concat (emit-expr (first args) env) (insn 0xcb0003e0)))
        (contains? '#{+ - * quot} op)
        (reduce (fn [left-code right]
                  (vec (concat left-code (save-x0) (emit-expr right env) (restore-binary)
                               (case op + (insn 0x8b010000) - (insn 0xcb010000)
                                      * (insn 0x9b017c00) quot signed-division))))
                (emit-expr (first args) env) (rest args))
        (contains? '#{= < > <= >=} op)
        (let [[left right] args cset ({'= 0x9a9f17e0 '< 0x9a9fa7e0 '> 0x9a9fd7e0
                                      '<= 0x9a9fc7e0 '>= 0x9a9fb7e0} op)]
          (emit-binary left right (concat (insn 0xeb01001f) (insn cset)) env))
        (= op 'kernel-store-u8) (emit-kernel-store-u8 args 512 env)
        (= op 'kernel-store-u8-4k) (emit-kernel-store-u8 args 4096 env)
        (= op 'kernel-load-u8) (emit-kernel-load-u8 args 512 env)
        (= op 'kernel-load-u8-4k) (emit-kernel-load-u8 args 4096 env)
        (= op 'kernel-load-u8-16k) (emit-kernel-load-u8 args 16384 env)
        (= op 'kernel-store-u32) (emit-kernel-store-u32 args 512 env)
        (= op 'kernel-load-u32) (emit-kernel-load-u32 args 512 env)
        :else (emit-call op args env)))))

(defn- emit-function [{:keys [name params body]}]
  (when (> (count params) 5)
    (throw (ex-info "AArch64 fuel ABI supports at most five integer parameters"
                    {:phase :aarch64 :function name :arity (count params)})))
  (let [n (count params) register-frame (* 16 (quot (+ n 1) 2))
        save-frame (when (pos? register-frame)
                     (concat (sub-sp register-frame)
                             (mapcat (fn [i] (str-sp (+ 19 i) (* 8 i))) (range n))))
        restore-frame (when (pos? register-frame)
                        (concat (mapcat (fn [i] (ldr-sp (+ 19 i) (* 8 i))) (range n))
                                (add-sp register-frame)))
        params-to-saved (mapcat (fn [i] (mov-reg (+ 19 i) i)) (range n))
        expression (emit-expr (normalize-expr body {}) (zipmap params (range)))]
    (vec (concat fuel-charge
                 (insn 0xa9bf7bfd) (insn 0x910003fd) ; stp fp,lr,[sp,#-16]!; mov fp,sp
                 save-frame params-to-saved expression restore-frame
                 (insn 0xa8c17bfd) (insn 0xd65f03c0))))) ; ldp fp,lr,[sp],#16; ret

(defn- finalize [tokens function-offset offsets]
  (loop [remaining tokens position 0 out []]
    (if-let [token (first remaining)]
      (if (and (map? token) (:call token))
        (let [absolute (+ function-offset position) target (get offsets (:call token))
              displacement (- target absolute)]
          (when-not target
            (throw (ex-info "unknown AArch64 call target" {:target (:call token)})))
          (when-not (zero? (mod displacement 4))
            (throw (ex-info "unaligned AArch64 BL target" {:target (:call token)})))
          (recur (next remaining) (+ position 4)
                 (into out (insn (bit-or 0x94000000
                                         (bit-and (quot displacement 4) 0x03ffffff))))))
        (recur (next remaining) (inc position) (conj out token)))
      out)))

(defn emit-program [kir]
  (let [exported-names (set (or (:exports kir) (map :name (:functions kir))))
        token-bodies (mapv (fn [f] [f (emit-function f)]) (:functions kir))
        offsets (loop [items token-bodies offset 0 out {}]
                  (if-let [[f body] (first items)]
                    (recur (next items) (+ offset (code-size body)) (assoc out (:name f) offset)) out))]
    (loop [items token-bodies code [] exports {}]
      (if-let [[function tokens] (first items)]
        (let [offset (get offsets (:name function)) body (finalize tokens offset offsets)]
          (recur (next items) (into code body)
                 (cond-> exports
                   (contains? exported-names (:name function))
                   (assoc (:name function)
                          {:offset offset :length (count body) :arity (count (:params function))}))))
        {:code code :exports exports}))))
