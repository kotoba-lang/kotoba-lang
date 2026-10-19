(ns kami.wgsl
  "WGSL as data — 'hiccup for shaders'. An EDN AST compiles to a WGSL source string, so a game's
   lighting / material / post-fx is authored and forked like the rest of the scene, and ONE shader
   source feeds both the web (kami.webgpu) and native (kami-webgpu-rs) executors — parity by source,
   not a hand-mirrored copy. `.cljc`: the same compiler runs on shadow-cljs (browser) and bb/JVM.

   Expressions (vectors are calls/operators; keywords/symbols are identifiers; numbers are f32):
     [:* a b]            → (a * b)        ;; +,-,*,/,<,>,<=,>=,==,&&,|| variadic; [:- a] is unary
     [:dot a b]          → dot(a, b)      ;; any other head is a function call (kebab→snake)
     [:vec4 r g b 1.0]   → vec4<f32>(r, g, b, 1.0)   ;; vec2/3/4, mat3/4 constructors
     [:i -1]             → -1             ;; raw integer literal (loop bounds, indices)
     [:. expr :xyz]      → (expr).xyz     ;; field / swizzle on a sub-expression
     :i.n  :g.sun-dir.xyz → i.n   g.sun_dir.xyz       ;; field paths + swizzles; kebab→snake
   Statements:
     [:let n e] [:var n e] [:var n type e] [:decl n type] [:set lhs e] [:+= lhs e] [:++ n]
     [:return e]  [:if cond [then…] [else…]]  [:for init cond step stmt…]
   Top level:
     (func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]} stmt…)
     (struct* :VO [[:clip [:vec4 :f32] {:builtin :position}] [:n [:vec3 :f32] {:location 0}] …])
     (binding* {:group 0 :binding 0 :space :uniform} :g :G)   (shader item…)

   Arithmetic/calls come from the shared kotoba.expr/expr.core (org-w3-webgpu-style split, ADR-2607051500);
   WGSL only customises the surface — idents are
   kebab→snake, integral literals get a `.0` (f32), vecN/matN heads become typed constructors, and the
   `:i`/`:.` forms (raw int, swizzle-on-expr) are WGSL-specific `:special`s."
  (:require [clojure.string :as str]
            [kotoba.expr :as kx]))

(defn- ident [s] (str/replace (name s) "-" "_"))

(defn- num [n]
  ;; WGSL f32 literals need a decimal point; CLJS can't tell 0 from 0.0, so float everything that
  ;; looks integral (use [:i n] for the rare integer literal — loop bounds, indices).
  (let [s (str n)]
    (if (or (str/includes? s ".") (str/includes? s "e")) s (str s ".0"))))

(def ^:private ctors {:vec2 "vec2<f32>" :vec3 "vec3<f32>" :vec4 "vec4<f32>"
                      :mat3 "mat3x3<f32>" :mat4 "mat4x4<f32>"
                      ;; short type spellings (WGSL aliases) — used by the kami-render shaders
                      :vec2f "vec2f" :vec3f "vec3f" :vec4f "vec4f"
                      :mat3x3f "mat3x3f" :mat4x4f "mat4x4f"})

(def ^:private vec-heads {:vec2 "vec2" :vec3 "vec3" :vec4 "vec4" :mat3 "mat3x3" :mat4 "mat4x4"})

(defn- type-str [t]
  (cond (string? t) t                                    ;; exotic types (texture_depth_2d, array<…>) pass through
        (vector? t) (let [[head elem] t]                 ;; [:vec3 :u32] → vec3<u32>, [:vec4 :f32] → vec4<f32>
                      (if-let [base (vec-heads head)]
                        (str base "<" (type-str (or elem :f32)) ">")
                        (or (ctors head) (ident head))))
        :else       (or (ctors t) (ident t))))                 ;; :mat4 → mat4x4<f32>, :vec3f → vec3f, :G → G

(defn- wgsl-call [op args]                ;; vecN/matN → typed ctor; else plain (kebab→snake) call
  (str (or (ctors op) (ident op)) "(" (str/join ", " args) ")"))

(defn- wgsl-special [op xs go]
  (case op
    :i  (str (first xs))                                    ;; raw integer literal (no f32 coercion)
    :.  (str "(" (go (first xs)) ")." (ident (second xs)))  ;; field/swizzle on a sub-expression
    nil))

(defn expr
  "Compile an EDN expression to a WGSL expression string."
  [e] (kx/compile {:ident ident :num num :call wgsl-call :special wgsl-special} e))

(declare stmt)
(defn- block [stmts] (str/join "\n" (map #(str "  " (str/replace (stmt %) "\n" "\n  ")) stmts)))
(defn- for-step [s]
  (let [[op x] s] (case op :++ (str (ident x) "++") :-- (str (ident x) "--")
                           (str/replace (stmt s) #";$" ""))))

(defn stmt
  "Compile an EDN statement to a WGSL statement string."
  [s]
  (let [[op & xs] s]
    (case op
      :let    (str "let " (ident (first xs)) " = " (expr (second xs)) ";")
      :var    (if (= 3 (count xs))                       ;; [:var name type expr] — annotated
                (str "var " (ident (first xs)) ": " (type-str (second xs)) " = " (expr (nth xs 2)) ";")
                (str "var " (ident (first xs)) " = " (expr (second xs)) ";"))   ;; [:var name expr] — inferred
      :decl   (str "var " (ident (first xs)) ": " (type-str (second xs)) ";")   ;; declaration only
      :set    (str (ident (first xs)) " = " (expr (second xs)) ";")
      :+=     (str (ident (first xs)) " += " (expr (second xs)) ";")
      :-=     (str (ident (first xs)) " -= " (expr (second xs)) ";")
      :++     (str (ident (first xs)) "++;")
      :--     (str (ident (first xs)) "--;")
      :return (if (seq xs) (str "return " (expr (first xs)) ";") "return;")
      :discard "discard;"
      :if     (str "if (" (expr (first xs)) ") {\n" (block (second xs)) "\n}"
                   (when (> (count xs) 2) (str " else {\n" (block (nth xs 2)) "\n}")))
      :for    (let [[init cnd step & body] xs]
                (str "for (" (str/replace (stmt init) #";$" "") "; " (expr cnd) "; " (for-step step) ") {\n"
                     (block body) "\n}"))
      (str (expr s) ";"))))   ;; a bare expression statement

(defn- attr-str [a]
  (cond (nil? a)       ""
        (:builtin a)   (str "@builtin(" (ident (:builtin a)) ") ")
        (:location a)  (str "@location(" (:location a) ") ")
        :else          ""))

(defn- param-str [[n t a]] (str (attr-str a) (ident n) ": " (type-str t)))

(defn func
  "Compile a function form to a WGSL function declaration.
   opts: {:stage :vertex|:fragment|:compute? :workgroup-size n-or-[x y z]? (compute)
          :params [[name type attr?] …] :ret type-or-[:loc n type]-or-[:builtin b type]}."
  [name {:keys [stage workgroup-size params ret]} & body]
  (let [ret* (cond
               (nil? ret) nil
               (and (vector? ret) (= :loc (first ret)))
               (str "@location(" (second ret) ") " (type-str (nth ret 2)))
               (and (vector? ret) (= :builtin (first ret)))
               (str "@builtin(" (ident (second ret)) ") " (type-str (nth ret 2)))
               :else (type-str ret))
        stage* (cond
                 (= stage :compute)
                 (str "@compute"
                      (when workgroup-size
                        (str " @workgroup_size(" (str/join ", " (if (vector? workgroup-size) workgroup-size [workgroup-size])) ")"))
                      "\n")
                 stage (str "@" (ident stage) "\n"))]
    (str stage*
         "fn " (ident name) "(" (str/join ", " (map param-str params)) ")"
         (when ret* (str " -> " ret*)) " {\n" (block body) "\n}")))

(defn struct*
  "[:struct] form: name + fields [[field type attr?] …] → a WGSL struct declaration."
  [name fields]
  (str "struct " (ident name) " { " (str/join ", " (map param-str fields)) " };"))

(defn binding*
  "A @group/@binding resource var. opts {:group :binding :space?(:uniform/:storage) :access?(:read/:read_write)}.
   With :access → var<space, access> (storage buffers); without → var<space> or bare var (textures)."
  [{:keys [group binding space access]} name type]
  (str "@group(" group ") @binding(" binding ") var"
       (when space (str "<" (ident space) (when access (str ", " (ident access))) ">"))
       " " (ident name) ": " (type-str type) ";"))

(defn shader
  "Assemble top-level items (struct*/binding*/func strings) into one WGSL source string."
  [& items]
  (str/join "\n" items))
