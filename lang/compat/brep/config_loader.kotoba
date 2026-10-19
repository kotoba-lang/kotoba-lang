(ns brep.config-loader
  "JVM-only classpath-resource loader for the tessellation EDN authority
  (`resources/brep/tessellation.edn`). Deliberately plain `.clj` (not
  `.cljc`) since it does local filesystem/classpath I/O — kept out of the
  portable `brep.*` domain namespaces, which stay I/O-free. Used only by
  `brep-test/edn-matches-resource` to verify `brep.config/config` (the
  inline cljc mirror) matches this resource."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def resource-path "brep/tessellation.edn")

(defn load-edn-resource [path]
  (let [r (io/resource path)]
    (when-not r
      (throw (ex-info "missing brep config resource" {:path path})))
    (edn/read-string (slurp r))))
