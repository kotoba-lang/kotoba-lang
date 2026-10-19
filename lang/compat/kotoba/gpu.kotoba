(ns kotoba.gpu
  "Unambiguous public GPU capability and render-graph contract.

   This namespace owns the implementation instead of re-exporting `kami.gpu`:
   other packages historically use that name for a different backend protocol,
   and classpath order must never change this contract's meaning.")

(def caps-webgpu  {:backend :webgpu  :compute true  :storage true  :instancing true})
(def caps-native  {:backend :native  :compute true  :storage true  :instancing true})
(def caps-console {:backend :console :compute true  :storage true  :instancing true})
(def caps-webgl2  {:backend :webgl2  :compute false :storage false :instancing true})

(def tiers {:webgpu caps-webgpu :native caps-native :console caps-console :webgl2 caps-webgl2})

(defn missing-caps [caps pass]
  (vec (remove #(true? (get caps %)) (:requires pass))))

(defn pass-runnable? [caps pass]
  (empty? (missing-caps caps pass)))

(defn resolve-graph [caps graph]
  (let [runnable (filterv #(pass-runnable? caps %) (:passes graph))
        skipped (vec (for [pass (:passes graph)
                           :when (not (pass-runnable? caps pass))]
                       {:pass (or (:id pass) (:pipeline pass))
                        :missing (missing-caps caps pass)}))]
    (assoc graph :passes runnable :caps caps :skipped skipped)))

(defn resolve-for [tier graph]
  (resolve-graph (get tiers tier caps-webgpu) graph))

(defn caps-from-device [backend flags]
  (merge {:backend backend :compute false :storage false :instancing true} flags))

(defn requires [pass & caps]
  (update pass :requires (fnil into []) caps))
