(ns kotoba.dsl.problem
  "Shared validation problem helpers for portable kotoba DSL libraries.

  A problem is plain EDN with namespaced keys:
  {:<domain>/severity :error|:warn
   :<domain>/code     keyword
   :<domain>/id|path  subject
   :<domain>/msg      string}"
  (:refer-clojure :exclude [empty?]))

(def severities
  #{:error :warn})

(defn domain-key [domain k]
  (keyword (name domain) (name k)))

(defn problem
  ([domain severity code subject msg]
   (problem domain :id severity code subject msg))
  ([domain subject-key severity code subject msg]
   (when-not (contains? severities severity)
     (throw (ex-info "Invalid problem severity"
                     {:domain domain :severity severity :code code})))
   {(domain-key domain :severity) severity
    (domain-key domain :code) code
    (domain-key domain subject-key) subject
    (domain-key domain :msg) msg}))

(defn severity [domain problem]
  (get problem (domain-key domain :severity)))

(defn error? [domain problem]
  (= :error (severity domain problem)))

(defn warning? [domain problem]
  (= :warn (severity domain problem)))

(defn errors [domain problems]
  (filterv #(error? domain %) problems))

(defn warnings [domain problems]
  (filterv #(warning? domain %) problems))

(defn valid? [domain problems]
  (clojure.core/empty? (errors domain problems)))
