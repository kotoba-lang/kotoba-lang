(ns kotoba.compiler.backend.x86-64)

(defn- le32 [n]
  (mapv #(bit-and (unsigned-bit-shift-right (long n) (* 8 %)) 0xff) (range 4)))
(defn- le64 [n]
  (mapv #(bit-and (unsigned-bit-shift-right (long n) (* 8 %)) 0xff) (range 8)))

(def ^:private param-pushes [[0x57] [0x56] [0x52] [0x51] [0x41 0x50]])
(def ^:private arg-pops [[0x5f] [0x5e] [0x5a] [0x59] [0x41 0x58]])
(def ^:private fuel-charge
  ;; context v2: fuel is qword [r9+8].
  [0x49 0x83 0x79 0x08 0x00 0x75 0x02 0x0f 0x0b 0x49 0xff 0x49 0x08])

(defn- normalize-expr [form env]
  (cond
    (integer? form) form
    (symbol? form) (get env form form)
    :else
    (let [[op & args] form]
      (if (= op 'let)
        (let [[bindings body] args
              env' (reduce (fn [e [name value]]
                             (assoc e name (normalize-expr value e)))
                           env (partition 2 bindings))]
          (normalize-expr body env'))
        (apply list op (map #(normalize-expr % env) args))))))

(defn- token-size [token]
  (if (and (map? token) (or (:call token) (:tail-self token))) 5 1))
(defn- code-size [tokens] (reduce + (map token-size tokens)))
(declare emit-expr)

(defn- load-param [param-index param-count pad? temp-depth]
  (let [disp (* 8 (+ (if pad? 1 0) (- param-count 1 param-index) temp-depth))]
    (into [0x48 0x8b 0x84 0x24] (le32 disp))))

(defn- emit-binary [left right opcode env ctx]
  (let [ctx (assoc ctx :tail? false)]
    (vec (concat (emit-expr left env ctx)
               [0x50]
               (emit-expr right env (update ctx :temp-depth inc))
               [0x48 0x89 0xc1 0x58]
               opcode))))

(defn- emit-tail-self-call [args env {:keys [param-count pad? temp-depth] :as ctx}]
  ;; All arguments are evaluated before any parameter slot is overwritten.
  ;; r11 then anchors the existing function frame while the temporary values
  ;; are popped in reverse order into their corresponding slots.  Charging
  ;; fuel here preserves ordinary call semantics; the final jump re-enters
  ;; the expression body without growing the native stack.
  (let [argc (count args)
        values (loop [remaining args depth temp-depth out []]
                 (if-let [arg (first remaining)]
                   (recur (next remaining) (inc depth)
                          (into out (concat (emit-expr arg env (assoc ctx :tail? false :temp-depth depth))
                                            [0x50])))
                   out))
        anchor [0x4c 0x8d 0x9c 0x24] ; lea r11,[rsp+argc*8]
        stores (mapcat (fn [param-index]
                         (let [disp (* 8 (+ (if pad? 1 0)
                                              (- param-count 1 param-index)))]
                           (concat [0x58 0x49 0x89 0x83] (le32 disp))))
                       (reverse (range argc)))]
    (vec (concat values anchor (le32 (* argc 8)) stores fuel-charge
                 [{:tail-self true}]))))

(defn- emit-call [op args env {:keys [temp-depth function-name tail?] :as ctx}]
  (let [argc (count args)]
    (when (> argc 5)
      (throw (ex-info "x86-64 fuel ABI supports at most five arguments"
                      {:phase :x86-64 :function op :arity argc})))
    (if (and tail? (= op function-name))
      (emit-tail-self-call args env ctx)
      (loop [remaining args depth temp-depth out []]
      (if-let [arg (first remaining)]
        (recur (next remaining) (inc depth)
               (into out (concat (emit-expr arg env (assoc ctx :tail? false :temp-depth depth)) [0x50])))
        (let [pops (mapcat #(nth arg-pops %) (reverse (range argc)))
              ;; SysV requires rsp%16==0 immediately before CALL. The fixed
              ;; function frame is aligned; expression temporaries may flip it.
              align? (odd? temp-depth)]
          (vec (concat out pops (when align? [0x50]) [{:call op}]
                       (when align? [0x48 0x83 0xc4 0x08])))))))))

(defn- emit-cap-call [cap-id value env {:keys [temp-depth] :as ctx}]
  (let [byte-offset (+ 16 (quot cap-id 8))
        mask (bit-shift-left 1 (mod cap-id 8))
        ;; Save context across the host ABI call. The fixed guest frame is
        ;; aligned; an even temp depth needs one additional 8-byte pad after
        ;; pushing r9.
        align? (even? temp-depth)]
    (vec (concat
          [0x41 0xf6 0x41 byte-offset mask 0x75 0x02 0x0f 0x0b]
          (emit-expr value env (assoc ctx :tail? false))
          [0x48 0x89 0xc2 0x41 0x51]             ; rdx=value; push r9
          (when align? [0x50])
          [0xbe] (le32 cap-id)                    ; esi=cap-id
          [0x4c 0x89 0xcf 0x41 0xff 0x51 0x30]   ; rdi=r9; call [r9+48]
          (when align? [0x48 0x83 0xc4 0x08])
          [0x41 0x59]))))                         ; pop r9

(defn- emit-heap-call [op args env {:keys [temp-depth] :as ctx}]
  (let [ctx (assoc ctx :tail? false)
        offset ({'pair 56 'pair-first 64 'pair-second 72} op)
        pair? (= op 'pair)
        values (if pair?
                 (concat (emit-expr (first args) env ctx) [0x50]
                         (emit-expr (second args) env (update ctx :temp-depth inc))
                         [0x48 0x89 0xc2 0x5e])   ; rdx=right; pop rsi=left
                 (concat (emit-expr (first args) env ctx)
                         [0x48 0x89 0xc6]))        ; rsi=handle
        align? (even? temp-depth)]
    (vec (concat values [0x41 0x51] (when align? [0x50])
                 [0x4c 0x89 0xcf 0x41 0xff 0x51 offset]
                 (when align? [0x48 0x83 0xc4 0x08]) [0x41 0x59]))))

(defn- emit-kernel-load-u8 [[base length index] maximum env {:keys [temp-depth] :as ctx}]
  ;; Evaluate exactly once, then enforce a non-null base, an unsigned index
  ;; below length, and the operation profile's maximum transfer
  ;; bytes. Every violation reaches UD2 before memory is touched.
  (let [ctx (assoc ctx :tail? false)]
    (vec (concat
        (emit-expr base env ctx) [0x50]
        (emit-expr length env (update ctx :temp-depth inc)) [0x50]
        (emit-expr index env (update ctx :temp-depth + 2))
        [0x59 0x5a                              ; rcx=length, rdx=base
         0x48 0x81 0xf9] (le32 maximum)          ; cmp rcx,maximum
        [0x0f 0x87 0x18 0x00 0x00 0x00         ; ja trap
         0x48 0x85 0xd2                         ; test rdx,rdx
         0x0f 0x84 0x0f 0x00 0x00 0x00         ; jz trap
         0x48 0x39 0xc8                         ; cmp rax,rcx
         0x0f 0x83 0x06 0x00 0x00 0x00         ; jae trap
         0x0f 0xb6 0x04 0x02                    ; movzx eax,byte [rdx+rax]
         0xeb 0x02 0x0f 0x0b]))))               ; skip UD2 / trap

(defn- emit-kernel-store-u8 [[base length index value] maximum env {:keys [temp-depth] :as ctx}]
  ;; Evaluate once and perform the same null/length/index checks as load-u8.
  ;; AL is stored only after every check succeeds; RAX remains the expression
  ;; result. Invalid writes trap before mutating memory.
  (let [ctx (assoc ctx :tail? false)]
    (vec (concat
        (emit-expr base env ctx) [0x50]
        (emit-expr length env (update ctx :temp-depth inc)) [0x50]
        (emit-expr index env (update ctx :temp-depth + 2)) [0x50]
        (emit-expr value env (update ctx :temp-depth + 3))
        [0x5f 0x59 0x5a                         ; rdi=index, rcx=length, rdx=base
         0x48 0x81 0xf9] (le32 maximum)          ; cmp rcx,maximum
        [0x0f 0x87 0x17 0x00 0x00 0x00         ; ja trap
         0x48 0x85 0xd2                         ; test rdx,rdx
         0x0f 0x84 0x0e 0x00 0x00 0x00         ; jz trap
         0x48 0x39 0xcf                         ; cmp rdi,rcx
         0x0f 0x83 0x05 0x00 0x00 0x00         ; jae trap
         0x88 0x04 0x3a                         ; mov byte [rdx+rdi],al
         0xeb 0x02 0x0f 0x0b]))))               ; skip UD2 / trap

(defn- emit-kernel-out [[port value] width env {:keys [temp-depth] :as ctx}]
  (let [ctx (assoc ctx :tail? false)]
    (vec (concat (emit-expr port env ctx) [0x50]
                 (emit-expr value env (update ctx :temp-depth inc))
                 [0x5a] (if (= width 8) [0xee] [0xef])))))

(defn emit-expr [form env {:keys [param-count pad? temp-depth] :as ctx}]
  (cond
    (integer? form) (into [0x48 0xb8] (le64 form))
    (symbol? form) (load-param (get env form) param-count pad? temp-depth)
    :else
    (let [[op & args] form]
      (cond
        (= op 'if)
        (let [[test then else] args
              test-code (emit-expr test env (assoc ctx :tail? false))
              then-code (emit-expr then env ctx)
              else-code (emit-expr else env ctx)]
          (vec (concat test-code [0x48 0x85 0xc0]
                       [0x0f 0x84] (le32 (+ (code-size then-code) 5))
                       then-code [0xe9] (le32 (code-size else-code)) else-code)))

        ;; `do`: emit each subexpression in order; each leaves its result in rax,
        ;; the next overwrites it, so only the last value survives while every
        ;; subexpression's side effects run exactly once, in order. All but the
        ;; last are in non-tail position.
        (= op 'do)
        (let [n (count args)]
          (vec (mapcat (fn [i arg]
                         (emit-expr arg env (if (= i (dec n)) ctx (assoc ctx :tail? false))))
                       (range n) args)))

        (= op 'cap-call)
        (emit-cap-call (first args) (second args) env ctx)

        (contains? '#{pair pair-first pair-second} op)
        (emit-heap-call op args env ctx)

        (= op 'kernel-load-u8)
        (emit-kernel-load-u8 args 512 env ctx)

        (= op 'kernel-load-u8-4k)
        (emit-kernel-load-u8 args 4096 env ctx)

        (= op 'kernel-load-u8-16k)
        (emit-kernel-load-u8 args 16384 env ctx)

        (= op 'kernel-store-u8)
        (emit-kernel-store-u8 args 512 env ctx)

        (= op 'kernel-store-u8-4k)
        (emit-kernel-store-u8 args 4096 env ctx)

        (= op 'kernel-boot-info) [0x49 0x8b 0x41 0x50]
        (= op 'kernel-read-cr2) [0x0f 0x20 0xd0]
        (= op 'kernel-read-cr3) [0x0f 0x20 0xd8]
        (= op 'kernel-write-cr3)
        (vec (concat (emit-expr (first args) env (assoc ctx :tail? false)) [0x0f 0x22 0xd8]))
        (= op 'kernel-invlpg)
        (vec (concat (emit-expr (first args) env (assoc ctx :tail? false)) [0x0f 0x01 0x38]))
        (= op 'kernel-cli) [0xfa 0x31 0xc0]
        (= op 'kernel-sti) [0xfb 0x31 0xc0]
        (= op 'kernel-hlt) [0xf4 0x31 0xc0]
        (= op 'kernel-pause) [0xf3 0x90 0x31 0xc0]
        (= op 'kernel-out-u8) (emit-kernel-out args 8 env ctx)
        (= op 'kernel-out-u32) (emit-kernel-out args 32 env ctx)

        (and (= op '-) (= 1 (count args)))
        (vec (concat (emit-expr (first args) env (assoc ctx :tail? false)) [0x48 0xf7 0xd8]))

        (contains? '#{+ - * quot bit-xor bit-and} op)
        (let [ctx (assoc ctx :tail? false)]
          (reduce (fn [left-code right]
                  (vec (concat left-code [0x50]
                               (emit-expr right env (update ctx :temp-depth inc))
                               [0x48 0x89 0xc1 0x58]
                               (case op + [0x48 0x01 0xc8] - [0x48 0x29 0xc8]
                                      * [0x48 0x0f 0xaf 0xc1]
                                      quot [0x48 0x99 0x48 0xf7 0xf9]
                                      bit-xor [0x48 0x31 0xc8]
                                      bit-and [0x48 0x21 0xc8]))))
                  (emit-expr (first args) env ctx) (rest args)))

        (contains? '#{= < > <= >=} op)
        (let [[left right] args setcc ({'= 0x94 '< 0x9c '> 0x9f '<= 0x9e '>= 0x9d} op)]
          (emit-binary left right
                       [0x48 0x39 0xc8 0x0f setcc 0xc0 0x48 0x0f 0xb6 0xc0] env ctx))

        :else (emit-call op args env ctx)))))

(defn- emit-function [{:keys [name params body]}]
  (when (> (count params) 5)
    (throw (ex-info "x86-64 fuel ABI supports at most five integer parameters"
                    {:phase :x86-64 :function name :arity (count params)})))
  (let [n (count params)
        pad? (even? n)
        normalized (normalize-expr body {})
        env (zipmap params (range))
        prologue (concat fuel-charge (mapcat #(nth param-pushes %) (range n))
                         (when pad? [0x50]))
        expression (emit-expr normalized env {:param-count n :pad? pad? :temp-depth 0
                                              :function-name name :tail? true})
        frame-bytes (* 8 (+ n (if pad? 1 0)))
        epilogue (concat [0x48 0x81 0xc4] (le32 frame-bytes) [0xc3])]
    {:tokens (vec (concat prologue expression epilogue))
     :expression-start (count prologue)}))

(defn- finalize [tokens function-offset expression-offset offsets]
  (loop [remaining tokens position 0 out []]
    (if-let [token (first remaining)]
      (cond
        (and (map? token) (:call token))
        (let [absolute (+ function-offset position)
              target (get offsets (:call token))]
          (when-not target
            (throw (ex-info "unknown x86-64 call target" {:target (:call token)})))
          (recur (next remaining) (+ position 5)
                 (into out (concat [0xe8] (le32 (- target (+ absolute 5)))))))

        (and (map? token) (:tail-self token))
        (let [absolute (+ function-offset position)]
          (recur (next remaining) (+ position 5)
                 (into out (concat [0xe9] (le32 (- expression-offset (+ absolute 5)))))))

        :else
        (recur (next remaining) (inc position) (conj out token)))
      out)))

(defn emit-program [kir]
  (let [exported-names (set (or (:exports kir) (map :name (:functions kir))))
        token-bodies (mapv (fn [f] [f (emit-function f)]) (:functions kir))
        offsets (loop [items token-bodies offset 0 out {}]
                  (if-let [[f emitted] (first items)]
                    (recur (next items) (+ offset (code-size (:tokens emitted)))
                           (assoc out (:name f) offset))
                    out))]
    (loop [items token-bodies code [] exports {}]
      (if-let [[function emitted] (first items)]
        (let [offset (get offsets (:name function))
              tokens (:tokens emitted)
              body (finalize tokens offset (+ offset (:expression-start emitted)) offsets)]
          (recur (next items) (into code body)
                 (cond-> exports
                   (contains? exported-names (:name function))
                   (assoc (:name function)
                          {:offset offset :length (count body) :arity (count (:params function))}))))
        {:code code :exports exports}))))
