(ns kotoba.compiler.ipld-adl-source
  "Fail-closed Kotoba-source compiler for the ipld-adl-wasm-v1 guest ABI.

  The profile is deliberately closed: byte transforms may return their input
  or the canonical empty bytes value, and validators may return literals, a
  bounded comparison of the actual input byte count, or a bounded comparison
  of one byte read out of the input at a literal index. Transforms are a closed
  expression grammar over the input -- subranges and joins, composed freely --
  of which only a join writes to memory. It is still a real
  source compiler--the admitted Kotoba HIR is the
  authority--and unsupported bodies are rejected instead of being silently
  replaced by another transform."
  (:require [kotoba.sema :as sema]
            [kotoba.kir.compatibility :as compatibility]))

(def target :wasm32-ipld-adl-v1)
(def target-profile
  {:format :kotoba.target-profile/v1 :execution :wasm :isa :wasm32
   :os :unspecified :abi :ipld-adl-wasm-v1
   :runtime :kotoba.ipld-adl-wasmtime/v1 :ambient-authority false})

(def ^:private required-exports
  '[validate-representation decode encode validate-logical])

(defn- uleb [n]
  (loop [n (long n) out []]
    (let [b (bit-and n 0x7f) n' (unsigned-bit-shift-right n 7)]
      (if (zero? n') (conj out b) (recur n' (conj out (bit-or b 0x80)))))))

(defn- sleb [n]
  (loop [n (long n) out []]
    (let [b (bit-and n 0x7f) n' (bit-shift-right n 7)
          done? (or (and (zero? n') (zero? (bit-and b 0x40)))
                    (and (= -1 n') (not (zero? (bit-and b 0x40)))))]
      (if done? (conj out b) (recur n' (conj out (bit-or b 0x80)))))))

(defn- utf8 [s]
  (mapv #(bit-and (int %) 0xff) (.getBytes ^String s "UTF-8")))

(defn- name-bytes [s]
  (let [bytes (utf8 s)] (into (uleb (count bytes)) bytes)))

(defn- section [id payload]
  (concat [id] (uleb (count payload)) payload))

(defn- export-entry [name kind index]
  (concat (name-bytes name) [kind] (uleb index)))

(defn- function-body
  ([instructions] (function-body 0 instructions))
  ([i32-locals instructions]
   (let [declarations (if (zero? i32-locals) [0] [1 i32-locals 0x7f])
         body (concat declarations instructions [0x0b])]
     (concat (uleb (count body)) body))))

(defn- packed [pointer length]
  (bit-or (bit-shift-left (long pointer) 32) (long length)))

(defn- emit-bool [condition]
  ;; CONDITION leaves one i32 on the stack; select the canonical DAG-CBOR
  ;; true/false node already present in the data segment.
  (concat condition [0x04 0x7e]
          [0x42] (sleb (packed 0 1)) [0x05]
          [0x42] (sleb (packed 1 1)) [0x0b]))

;; 0..2 are the ABI parameters. `free` is the bump cursor for materialised
;; results; the rest drive the copy loop.
(def ^:private local-free 3)
(def ^:private local-dst 4)
(def ^:private local-src 5)
(def ^:private local-remaining 6)
(def ^:private expression-local-base 7)
(def ^:const max-expression-nodes 32)

;; Each expression node owns a (pointer, length) slot pair, assigned in
;; post-order at compile time. The tree is static and small, so this is
;; ordinary register allocation rather than a run-time stack.
(defn- slot-pointer [index] (+ expression-local-base (* 2 index)))
(defn- slot-length [index] (+ expression-local-base (* 2 index) 1))

(defn- get-local [index] (concat [0x20] (uleb index)))
(defn- set-local [index] (concat [0x21] (uleb index)))

(defn- emit-copy-slot
  "Copy the bytes named by slot INDEX to `local-dst`, advancing `local-dst`.

   The loop is deliberate. `memory.copy` would move the same bytes for the
   price of a single instruction, which is exactly the fuel hole this profile
   must not have: the cost charged has to grow with the bytes moved."
  [index]
  (concat
   (get-local (slot-pointer index)) (set-local local-src)
   (get-local (slot-length index)) (set-local local-remaining)
   [0x02 0x40 0x03 0x40]
   (get-local local-remaining) [0x45 0x0d 0x01]
   (get-local local-dst) (get-local local-src) [0x2d 0x00 0x00 0x3a 0x00 0x00]
   (get-local local-dst) [0x41 0x01 0x6a] (set-local local-dst)
   (get-local local-src) [0x41 0x01 0x6a] (set-local local-src)
   (get-local local-remaining) [0x41 0x01 0x6b] (set-local local-remaining)
   [0x0c 0x00 0x0b 0x0b]))

(defn- emit-expression
  "Emit NODE in post-order, leaving its result in its own slot pair.

   Returns `[instructions index next-index]`. Sub-expressions are views and
   allocate nothing; only a join materialises, and it takes its destination
   from the bump cursor -- which is above every operand still in play, because
   children are emitted before the parent takes its space. That is the alias
   rule once expressions compose: not `past the operand`, which was enough
   while there was only one, but `above every live operand`."
  [node next memory-end]
  (case (first node)
    :whole
    [(concat (get-local 1) (set-local (slot-pointer next))
             (get-local 2) (set-local (slot-length next)))
     next (inc next)]

    :empty
    ;; Length zero, so the pointer is never dereferenced; it is still given a
    ;; real value rather than zero so every slot holds an in-bounds address.
    [(concat (get-local 1) (set-local (slot-pointer next))
             [0x41 0x00] (set-local (slot-length next)))
     next (inc next)]

    :sub
    (let [[_ child start end] node
          [child-code child-index after] (emit-expression child next memory-end)
          index after]
      [(concat child-code
               ;; The bound is the *operand's* length -- here the child's, held
               ;; in its slot -- and not the size of the buffer it lives in.
               (get-local (slot-length child-index)) [0x41] (sleb end)
               [0x49 0x04 0x40 0x00 0x0b]
               (get-local (slot-pointer child-index)) [0x41] (sleb start)
               [0x6a] (set-local (slot-pointer index))
               [0x41] (sleb (- end start)) (set-local (slot-length index)))
       index (inc index)])

    :join
    (let [[_ left right] node
          [left-code left-index after-left] (emit-expression left next memory-end)
          [right-code right-index after-right] (emit-expression right after-left memory-end)
          index after-right]
      [(concat
        left-code right-code
        (get-local (slot-length left-index)) (get-local (slot-length right-index))
        [0x6a] (set-local (slot-length index))
        ;; Room for this result above everything already materialised.
        (get-local local-free) (get-local (slot-length index)) [0x6a]
        [0x41] (sleb memory-end) [0x4b 0x04 0x40 0x00 0x0b]
        (get-local local-free) (set-local (slot-pointer index))
        (get-local local-free) (set-local local-dst)
        (emit-copy-slot left-index) (emit-copy-slot right-index)
        ;; The cursor advanced exactly by the bytes written.
        (get-local local-dst) (set-local local-free))
       index (inc index)])))

(defn- expression-nodes [node]
  (case (first node)
    (:whole :empty) 1
    :sub (inc (expression-nodes (nth node 1)))
    :join (+ 1 (expression-nodes (nth node 1)) (expression-nodes (nth node 2)))))

(defn- expression-offsets
  "Every literal `end` named anywhere in the expression."
  [node]
  (case (first node)
    (:whole :empty) []
    :sub (cons [(nth node 3)] (expression-offsets (nth node 1)))
    :join (concat (expression-offsets (nth node 1))
                  (expression-offsets (nth node 2)))))

(defn- expression-length
  "Symbolic byte length as `[constant operand-multiple]`."
  [node]
  (case (first node)
    :whole [0 1]
    :empty [0 0]
    :sub [(- (nth node 3) (nth node 2)) 0]
    :join (let [[lc lk] (expression-length (nth node 1))
                [rc rk] (expression-length (nth node 2))]
            [(+ lc rc) (+ lk rk)])))

(defn- expression-constant-bytes
  "Constant share of the space the bump cursor consumes: the sum over every
   join, because the cursor only ever moves up."
  [node]
  (case (first node)
    (:whole :empty) 0
    :sub (expression-constant-bytes (nth node 1))
    :join (+ (first (expression-length node))
             (expression-constant-bytes (nth node 1))
             (expression-constant-bytes (nth node 2)))))

(defn- emit-expression-body [node memory-end]
  (let [[code index _] (emit-expression node 0 memory-end)]
    (concat
     ;; Materialised results start immediately past the operand and only ever
     ;; move upward.
     (get-local 1) (get-local 2) [0x6a] (set-local local-free)
     code
     (get-local (slot-pointer index)) [0xad 0x42 0x20 0x86]
     (get-local (slot-length index)) [0xad 0x84])))

(defn- emit-result [result capacity]
  (cond
    (and (vector? result) (= :input-byte-count-eq (first result)))
    ;; The ABI supplies input length as local 2. Return canonical DAG-CBOR true
    ;; or false without reading guest memory or importing a host function.
    (emit-bool (concat [0x20 0x02 0x41] (sleb (second result)) [0x46]))

    (and (vector? result) (= :input-byte-at-eq (first result)))
    (let [[_ index expected] result]
      (concat
       ;; The bound that matters is the operand's length, not the memory's
       ;; size. Linear memory is two pages and the input is copied in at 1024,
       ;; so an index past the input still lands inside memory and would read
       ;; whatever follows it. `length <= index` therefore has to trap here,
       ;; before the load, rather than being left to Wasm's own bounds check.
       [0x20 0x02 0x41] (sleb index) [0x4d 0x04 0x40 0x00 0x0b]
       ;; i32.load8_u align=0 offset=index, folded onto the ABI pointer. The
       ;; unsigned load is what makes 0xFF read as 255 instead of -1.
       (emit-bool (concat [0x20 0x01 0x2d 0x00] (uleb index)
                          [0x41] (sleb expected) [0x46]))))

    (and (vector? result) (= :input-expression (first result)))
    (emit-expression-body (second result) (+ capacity 1024))

    :else
    (case result
      :true (concat [0x42] (sleb (packed 0 1)))
      :false (concat [0x42] (sleb (packed 1 1)))
      :empty-bytes (concat [0x42] (sleb (packed 2 1)))
      :identity [0x20 0x01 0xad 0x42 0x20 0x86
                 0x20 0x02 0xad 0x84])))

(defn- expression-locals
  "Declared i32 locals for the transform: the four cursors plus one slot pair
   per node of the largest expression in the plan. Plans with no expression
   declare none, so modules that predate this path keep their exact shape."
  [plan]
  (let [nodes (->> (vals plan)
                   (filter #(and (vector? %) (= :input-expression (first %))))
                   (map #(expression-nodes (second %))))]
    (if (seq nodes)
      (+ (- expression-local-base 3) (* 2 (apply max nodes)))
      0)))

(defn- emit-dispatch [entries capacity]
  (if-let [[operation result] (first entries)]
    (concat [0x20 0x00 0x41] (sleb operation) [0x46 0x04 0x7e]
            (emit-result result capacity) [0x05]
            (emit-dispatch (next entries) capacity) [0x0b])
    ;; Invalid operation: trap. The unreachable instruction is polymorphic;
    ;; the dead i64.const keeps the block's result shape explicit to validators.
    [0x00 0x42 0x00]))

(defn- closed-module
  [memory-pages plan profile]
  (let [capacity (- (* memory-pages 65536) 1024)
        types (concat [2]
                      [0x60 1 0x7f 1 0x7f]
                      [0x60 3 0x7f 0x7f 0x7f 1 0x7e])
        functions [2 0 1]
        memory [1 1 memory-pages memory-pages]
        exports (concat [3]
                        (export-entry "memory" 2 0)
                        (export-entry "adl_alloc" 0 0)
                        (export-entry "adl_transform" 0 1))
        alloc (concat
               [0x20 0x00 0x41] (sleb capacity) [0x4b 0x04 0x40 0x00 0x0b]
               [0x41] (sleb 1024))
        transform (emit-dispatch
                   [[0 (:validate-representation plan)]
                    [1 (:decode plan)] [2 (:encode plan)]
                    [3 (:validate-logical plan)]]
                   capacity)
        code (concat [2] (function-body alloc)
                     (function-body (expression-locals plan) transform))
        ;; canonical DAG-CBOR true, false, and empty byte string.
        data [1 0 0x41 0 0x0b 3 0xf5 0xf4 0x40]
        custom (concat (name-bytes "kotoba.ipld-adl")
                       (utf8 (str "ipld-adl-wasm-v1;profile=" (name profile))))
        bytes (concat [0 0x61 0x73 0x6d 1 0 0 0]
                      (section 0 custom)
                      (section 1 types) (section 3 functions)
                      (section 5 memory) (section 7 exports)
                      (section 10 code) (section 11 data))]
    (byte-array (map unchecked-byte bytes))))

(defn- reject! [message data]
  (throw (ex-info message (merge {:phase :ipld-adl-source} data))))

(defn- exact-function-shape? [function result]
  (and (= ['value] (:params function))
       (= [:bytes] (:param-types function))
       (= result (:result function))
       (empty? (:effects function))))

(defn- expression-node
  "Parse one transform body into the closed bytes expression grammar:

     E ::= value | (bytes-empty) | (bytes-slice E START END) | (bytes-concat E E)

  Returns a node, or nil when the form is outside the grammar. Offsets must be
  literals -- this profile's restriction, not the language's, and what keeps
  every emitted bound decidable at compile time.

  An inverted or negative subrange is refused by name rather than reported
  generically, because `0 <= start <= end` is the half of the bound that *is*
  decidable here; `end <= length` is not, and is emitted as a run-time trap."
  [form name]
  (cond
    (= 'value form) [:whole]
    (= '(bytes-empty) form) [:empty]

    (and (seq? form) (= 'bytes-slice (first form)) (= 4 (count form)))
    (let [[_ child start end] form]
      (when (and (integer? start) (integer? end))
        (when-not (and (<= 0 start end) (<= end Integer/MAX_VALUE))
          (reject! "IPLD ADL subrange offsets must satisfy 0 <= start <= end"
                   {:operation name :profile :pure-closed-v1
                    :start start :end end}))
        (when-let [node (expression-node child name)]
          [:sub node start end])))

    (and (seq? form) (= 'bytes-concat (first form)) (= 3 (count form)))
    (let [[_ left right] form
          a (expression-node left name)
          b (expression-node right name)]
      (when (and a b) [:join a b]))

    :else nil))

(defn- expression-plan [body name]
  (when-let [node (expression-node body name)]
    (when (> (expression-nodes node) max-expression-nodes)
      (reject! "IPLD ADL expression exceeds the node budget"
               {:operation name :profile :pure-closed-v1
                :nodes (expression-nodes node) :budget max-expression-nodes}))
    [:input-expression node]))

(defn- operation-plan [function name result allowed]
  (when-not (exact-function-shape? function result)
    (reject! "unsupported IPLD ADL operation signature"
             {:operation name :profile :pure-closed-v1}))
  (or (get allowed (:body function))
      (expression-plan (:body function) name)
      (reject! "unsupported IPLD ADL operation body"
               {:operation name :profile :pure-closed-v1 :body (:body function)})))

(defn- byte-at-plan
  "`(= (bytes-at value I) N)` -> `[:input-byte-at-eq I N]`, else nil.

  Returning nil means \"some other body shape\", which the caller reports
  generically. Two well-shaped bodies are rejected here by name instead,
  because a generic message would hide what is actually wrong with them:

  - a negative index is not an index;
  - `bytes-at` yields an unsigned byte, so comparing against anything outside
    0..255 is constantly false. Lowering it would turn a source mistake into a
    validator that quietly always answers no.

  The index must be a literal. That is this profile's restriction, not the
  language's -- `bytes-at` types an arbitrary i64 index -- and it is what keeps
  the emitted bounds check decidable at compile time."
  [body name]
  (let [[op read expected] (when (and (seq? body) (= 3 (count body))) body)]
    (when (and (= '= op) (seq? read) (= 3 (count read))
               (= 'bytes-at (first read)) (= 'value (second read))
               (integer? (nth read 2)) (integer? expected))
      (let [index (nth read 2)]
        (when-not (<= 0 index Integer/MAX_VALUE)
          (reject! "IPLD ADL byte index must be a non-negative int32"
                   {:operation name :profile :pure-closed-v1 :index index}))
        (when-not (<= 0 expected 255)
          (reject! "IPLD ADL byte comparison must be an unsigned byte"
                   {:operation name :profile :pure-closed-v1 :expected expected}))
        [:input-byte-at-eq index expected]))))

(defn- validator-plan [function name]
  (when-not (exact-function-shape? function :bool)
    (reject! "unsupported IPLD ADL operation signature"
             {:operation name :profile :pure-closed-v1}))
  (let [body (:body function)]
    (cond
      (= true body) :true
      (= false body) :false
      (and (seq? body) (= '= (first body)) (= 3 (count body))
           (= '(bytes-count value) (second body))
           (integer? (nth body 2)) (<= 0 (nth body 2) Integer/MAX_VALUE))
      [:input-byte-count-eq (nth body 2)]
      :else
      (or (byte-at-plan body name)
          (reject! "unsupported IPLD ADL operation body"
                   {:operation name :profile :pure-closed-v1 :body body})))))

(defn compile-source
  "Compile the closed pure bytes ADL Kotoba profile to ipld-adl-wasm-v1.

  Validators accept literal true/false, `(= (bytes-count value) N)`, or
  `(= (bytes-at value I) N)` for a literal index and an unsigned byte. Decode
  and encode accept the input value, `(bytes)`, which lowers to canonical
  DAG-CBOR empty bytes (0x40), and `bytes-slice` / `bytes-concat` composed
  freely over those for literal offsets. Subranges are views and allocate
  nothing; joins materialise at a bump cursor that starts past the input and
  only rises, so a destination never lands on a buffer still in use.
  MEMORY-PAGES is fixed in the emitted module (1..64; default 2)."
  ([source] (compile-source source {}))
  ([source {:keys [memory-pages] :or {memory-pages 2} :as options}]
   (when-not (= (set (keys options)) (if (contains? options :memory-pages)
                                      #{:memory-pages} #{}))
     (reject! "unsupported IPLD ADL compiler option" {:options (set (keys options))}))
   (when-not (and (integer? memory-pages) (<= 1 memory-pages 64))
     (reject! "invalid IPLD ADL memory pages" {:memory-pages memory-pages}))
   (let [hir (sema/analyze source)
         by-name (into {} (map (juxt :name identity)) (:functions hir))]
     (when-not (= required-exports (:exports hir))
       (reject! "IPLD ADL source must export the exact operation set in ABI order"
                {:expected required-exports :actual (:exports hir)}))
     (when-not (= (set required-exports) (set (keys by-name)))
       (reject! "IPLD ADL source must contain only the four ABI operations"
                {:expected (set required-exports) :actual (set (keys by-name))}))
     (let [plan {:validate-representation
                 (validator-plan (get by-name 'validate-representation)
                                 'validate-representation)
                 :decode (operation-plan (get by-name 'decode) 'decode :bytes
                                         {'value :identity '(bytes-empty) :empty-bytes})
                 :encode (operation-plan (get by-name 'encode) 'encode :bytes
                                         {'value :identity '(bytes-empty) :empty-bytes})
                 :validate-logical
                 (validator-plan (get by-name 'validate-logical)
                                 'validate-logical)}
           ;; The lowering half of the max-output bound. Input capacity is
           ;; fixed in the emitted module, so a subrange ending past it can
           ;; never be in range at run time -- that is decidable now, and a
           ;; module whose only possible outcome is a trap should not be built.
           ;; The runner still enforces its own `max_output` at execution time;
           ;; neither check subsumes the other, because this one cannot see the
           ;; host's limit and the host cannot see the source's offsets.
           capacity (- (* memory-pages 65536) 1024)
           _ (doseq [[operation entry] plan
                     :when (and (vector? entry) (= :input-expression (first entry)))]
               (doseq [[end] (expression-offsets (second entry))]
                 (when-not (<= end capacity)
                   (reject! "IPLD ADL subrange exceeds the module's input capacity"
                            {:operation operation :profile :pure-closed-v1
                             :end end :capacity capacity
                             :memory-pages memory-pages})))
               ;; Composition turns the compile-time part of the bound into a
               ;; function of the whole expression rather than of one join: the
               ;; bump cursor never falls, so the space needed is the sum over
               ;; every join. Only the constant share is knowable now; the
               ;; operand-dependent remainder is the emitted trap, and the
               ;; runner's own `max_output` is what neither of them can see.
               (let [constant (expression-constant-bytes (second entry))]
                 (when-not (<= constant capacity)
                   (reject! "IPLD ADL expression exceeds the module's input capacity"
                            {:operation operation :profile :pure-closed-v1
                             :constant-length constant :capacity capacity
                             :memory-pages memory-pages}))))
           profile (cond
                     (= {:validate-representation :true :decode :identity
                         :encode :identity :validate-logical :true} plan)
                     :pure-identity-v1
                     (some vector? (vals plan)) :input-bytes-v1
                     :else :pure-closed-v1)
           kir {:format :kotoba.ipld-adl-kir/v1
                :profile profile :operations required-exports :plan plan}
           compatible (assoc
                       (compatibility/descriptor
                        {:hir-format (:format hir) :kir-format (:format kir)
                         :target target :target-profile target-profile
                         :value-abi :ipld.canonical-dag-cbor/bytes-v1})
                       :tender-contract :ipld-adl-wasm-v1)]
       {:format :wasm/v1 :target target :target-profile target-profile
      :hir hir :kir kir :compatibility compatible
      :bytes (closed-module memory-pages plan profile)
      :adl {:abi "ipld-adl-wasm-v1" :profile profile :plan plan
            :operations #{:validate-representation :decode :encode :validate-logical}}
      :limits {:memory-pages memory-pages :ambient-authority false
               :imports 0 :max-input-bytes (- (* memory-pages 65536) 1024)}}))))
