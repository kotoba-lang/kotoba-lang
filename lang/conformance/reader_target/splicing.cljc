(defn main [x]
  (+ x #?@(:kotoba [1 2]
           :clj [1 0]
           :cljs [2 0]
           :default [0 0])))
