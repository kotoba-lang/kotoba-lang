(ns kotoba.css.shadow
  "shadow-css compatible static class DSL. CSS remains build-time data."
  (:require [clojure.string :as str]
            [css.utility :as utility]))

(defn class-name [ns-name line column]
  (str (str/replace (str ns-name) #"[^a-zA-Z0-9_-]" "_") "__L" line "C" column))

(defn- merge-css [parts]
  (reduce (fn [acc part]
            (cond
              (keyword? part) (if-let [rule (utility/utility-rule (name part))]
                                (merge acc rule)
                                (throw (ex-info "unknown CSS alias" {:alias part})))
              (map? part) (merge acc part)
              (or (string? part) (vector? part)) acc
              :else (throw (ex-info "CSS form must be static" {:form part}))))
          {} parts))

(def selector-aliases
  {:hover "&:hover" :focus "&:focus" :focus-visible "&:focus-visible"
   :active "&:active" :disabled "&:disabled" :before "&::before" :after "&::after"})

(defn- nested-selector [base sub]
  (let [sub (or (get selector-aliases sub) (name sub))]
    (if (str/includes? sub "&") (str/replace sub "&" base) (str base " " sub))))

(defn compile-form
  "Compile one static shadow-compatible form to an EDN sheet."
  [selector parts]
  (reduce
   (fn [sheet part]
     (if-not (vector? part)
       sheet
       (let [[sub & nested] part
             sub (if (keyword? sub) (or (get selector-aliases sub) (name sub)) (str sub))]
         (if (str/starts-with? sub "@media ")
           (assoc-in sheet [:media (subs sub 7) selector] (merge-css nested))
           (assoc-in sheet [:rules (nested-selector selector sub)] (merge-css nested))))))
   {:rules {selector (merge-css parts)} :media {}}
   parts))

(defmacro css
  "Return a source-position-derived classname. Accepts static utility keywords,
  declaration maps, and pass-through class strings, matching shadow-css usage."
  [& parts]
  (let [{:keys [line column]} (meta &form)
        generated (class-name (ns-name *ns*) line column)
        passthrough (filter string? parts)
        class-value (str/join " " (concat passthrough [generated]))]
    class-value))
