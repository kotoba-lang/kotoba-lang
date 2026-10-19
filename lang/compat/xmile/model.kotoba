(ns xmile.model
  "OASIS XMILE 1.0 (system dynamics) stock-and-flow model as EDN. Zero
  third-party deps -- portable .cljc (JVM, ClojureScript, SCI). A model is a
  plain namespaced-key map you can assoc, diff, store in Datomic, or
  generate; this namespace adds a threading-friendly builder and the
  structural queries xmile.validate/xmile.execute need.

  A model (OASIS XMILE v1.0 sec 3.1 stocks/flows/auxiliaries, sec 3.7.1
  sim_specs):
    {:xmile/name \"bathtub\"
     :xmile/sim-specs {:xmile/start 0 :xmile/stop 10 :xmile/dt 0.25 :xmile/method :euler}
     :xmile/variables
     {\"Inventory\"  {:xmile/kind :stock :xmile/name \"Inventory\" :xmile/eqn \"100\"
                     :xmile/inflows #{\"Production\"} :xmile/outflows #{\"Shipping\"}}
      \"Production\" {:xmile/kind :flow :xmile/name \"Production\" :xmile/eqn \"10\"}
      \"Shipping\"   {:xmile/kind :flow :xmile/name \"Shipping\" :xmile/eqn \"Inventory / 4\"}}}

  Variable kinds:
    :stock -- an accumulator. `:xmile/eqn` is its INITIAL value, evaluated
      once at t=start. `:xmile/inflows`/`:xmile/outflows` name the flows
      that increase/decrease it each step (sec 3.1.1). Defaults to
      `:xmile/stock-type :stock`; `:conveyor`/`:queue` (sec 3.7.2/3.7.3) are
      modeled here as data but not yet simulated by xmile.execute (see
      README Follow-ups).
    :flow  -- a stock's rate of change. `:xmile/eqn` is re-evaluated every step.
    :aux   -- an algebraic converter (a.k.a. auxiliary). `:xmile/eqn` ditto.

  `:xmile/eqn` is always the raw XMILE equation STRING exactly as it appears
  on the wire (sec 3.3) -- this namespace does not parse or evaluate it; see
  xmile.expr. A `:xmile/gf` (graphical/lookup function, sec 3.2.2) may be
  attached to any variable kind.")

;; --- builders (all return plain maps; thread with add-variable) ---

(defn sim-specs
  "Build a :xmile/sim-specs map (OASIS XMILE v1.0 sec 3.7.1). `start`/`stop`
  are simulation time bounds; `opts` may add :xmile/dt, :xmile/dt-reciprocal?,
  :xmile/method (:euler default, or :rk4; :rk2/:rk45/:gear are valid per spec
  but not yet supported by xmile.execute), :xmile/time-units, :xmile/pause."
  ([start stop] (sim-specs start stop nil))
  ([start stop opts]
   (merge {:xmile/start start :xmile/stop stop :xmile/method :euler} opts)))

(defn- variable
  [kind nm eqn opts]
  (merge {:xmile/kind kind :xmile/name nm :xmile/eqn eqn} opts))

(defn stock
  "Build a :stock variable map. `eqn` is the initial-value equation string."
  ([nm eqn] (stock nm eqn nil))
  ([nm eqn opts]
   (variable :stock nm eqn
             (merge {:xmile/inflows #{} :xmile/outflows #{} :xmile/stock-type :stock} opts))))

(defn flow
  "Build a :flow variable map. `eqn` is the rate equation string."
  ([nm eqn] (flow nm eqn nil))
  ([nm eqn opts] (variable :flow nm eqn opts)))

(defn aux
  "Build an :aux (auxiliary/converter) variable map."
  ([nm eqn] (aux nm eqn nil))
  ([nm eqn opts] (variable :aux nm eqn opts)))

(defn gf
  "Build a :xmile/gf graphical-function map (OASIS XMILE v1.0 sec 3.2.2).
  `gf-type` is one of :continuous (default), :discrete, :extrapolate.
  Either pass explicit `:xmile/xpts`+`:xmile/ypts` in `opts`, or `:xmile/xscale`
  `[min max]` + `:xmile/ypts` (x-values are then evenly spaced over the scale)."
  ([ypts] (gf :continuous ypts))
  ([gf-type ypts] (gf gf-type ypts nil))
  ([gf-type ypts opts]
   (merge {:xmile/gf-type gf-type :xmile/ypts ypts} opts)))

(defn model
  "Build an empty (or opts-seeded) :xmile/model map."
  ([nm] (model nm nil))
  ([nm opts]
   (merge {:xmile/name nm :xmile/variables {}} opts)))

(defn add-variable
  "assoc `v` (a stock/flow/aux map) into `m`'s :xmile/variables, keyed by its name."
  [m v]
  (assoc-in m [:xmile/variables (:xmile/name v)] v))

(defn set-sim-specs [m ss] (assoc m :xmile/sim-specs ss))

;; --- queries ---

(defn variables [m] (vals (:xmile/variables m {})))
(defn variable-names [m] (set (keys (:xmile/variables m {}))))
(defn lookup [m nm] (get (:xmile/variables m {}) nm))

(defn kind? [k v] (= k (:xmile/kind v)))
(defn stock? [v] (kind? :stock v))
(defn flow?  [v] (kind? :flow v))
(defn aux?   [v] (kind? :aux v))

(defn stocks [m] (filter stock? (variables m)))
(defn flows  [m] (filter flow?  (variables m)))
(defn auxs   [m] (filter aux?   (variables m)))

(defn inflows-of  [m stock-name] (:xmile/inflows  (lookup m stock-name) #{}))
(defn outflows-of [m stock-name] (:xmile/outflows (lookup m stock-name) #{}))

(defn dimensioned? [v] (contains? v :xmile/dims))
(defn special-stock? [v] (contains? #{:conveyor :queue} (:xmile/stock-type v)))
