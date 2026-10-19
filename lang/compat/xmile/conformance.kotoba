(ns xmile.conformance
  "Executable OASIS XMILE 1.0 XSD validation for emitted documents."
  (:require [xmile.model :as model]
            [xmile.xml :as xml])
  (:import (java.io StringReader)
           (java.net URL)
           (javax.xml XMLConstants)
           (javax.xml.transform.stream StreamSource)
           (javax.xml.validation SchemaFactory)))

(def official-xsd-url
  "https://docs.oasis-open.org/xmile/xmile/v1.0/os/schemas/xmile.xsd")

(defn validate-xml
  "Validate `xml-text` with the XSD at `schema-url`. Returns true or throws
  the validator's precise SAX exception."
  ([xml-text] (validate-xml official-xsd-url xml-text))
  ([schema-url xml-text]
   (let [factory (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
         schema (.newSchema factory (URL. schema-url))
         validator (.newValidator schema)]
     (.validate validator (StreamSource. (StringReader. xml-text)))
     true)))

(defn canonical-document []
  {:xmile/header {:xmile/vendor "kotoba-lang"}
   :xmile/sim-specs (model/sim-specs 0.0 10.0
                                      {:xmile/dt 0.25 :xmile/method :rk4})
   :xmile/models
   [(-> (model/model "bathtub")
        (model/add-variable
         (model/stock "Inventory" "100"
                      {:xmile/inflows #{"Production"}
                       :xmile/outflows #{"Shipping"}}))
        (model/add-variable (model/flow "Production" "10"))
        (model/add-variable (model/flow "Shipping" "Inventory / 4")))]})

(defn check-official-xsd []
  (let [document (canonical-document)
        xml-text (xml/emit-string document)]
    {:xsd-valid? (validate-xml xml-text)
     :round-trip? (= document (xml/parse-string xml-text))
     :emitted-character-count (count xml-text)}))

(defn -main [& _]
  (let [result (check-official-xsd)]
    (prn result)
    (when-not (and (:xsd-valid? result) (:round-trip? result))
      (throw (ex-info "XMILE official-XSD conformance failed" result)))))
