(ns kototama.unspsc.capability
  "The capability library — the domain-logic half that makes every UNSPSC
  actor FUNCTIONAL (not a hollow stub).

  Generalized from the ~1,655 bespoke Gemini agents into reusable, data-driven
  domain capabilities keyed by UNSPSC segment, plus universal risk-tag-driven
  checks. Each actor's behaviour is individuated by its taxon:
    - required input fields = the commodity's own taxonomy spec-fields (taxonomy), or
      the segment capability's default;
    - domain checks = the segment capability's checks;
    - risk checks = driven by the commodity's risk-tags.

  No code is a stub: even a code with no segment capability validates its own
  spec-fields and enforces its own risk-tag requirements — genuinely
  commodity-specific work."
  (:require [kotoba.lang.text :as str]))

;; ── helpers ────────────────────────────────────────────────────────────────

(defn- present?
  "A field is present when the input has a non-blank / non-nil value."
  [input k]
  (let [v (get input (keyword k) (get input k))]
    (cond
      (nil? v) false
      (string? v) (not (str/blank? v))
      (coll? v) (seq v)
      :else true)))

(defn- check [id desc pred] {:id id :desc desc :pred pred})

;; ── universal risk-tag-driven checks ───────────────────────────────────────
;; A commodity's risk-tags activate extra requirements — functional and
;; commodity-specific because risk-tags differ per code.

(def risk-checks
  {"perishable"
   (check :cold-chain "perishable goods require a cold-chain / temperature record"
          (fn [input] (or (present? input "cold_chain")
                          (present? input "temperature_control"))))
   "high-value"
   (check :provenance "high-value goods require provenance / chain-of-custody"
          (fn [input] (or (present? input "provenance")
                          (present? input "chain_of_custody"))))
   "hazardous"
   (check :safety-data "hazardous goods require a safety data sheet (SDS)"
          (fn [input] (present? input "sds")))
   "controlled"
   (check :authorization "controlled goods require an authorization / licence"
          (fn [input] (or (present? input "authorization")
                          (present? input "licence"))))})

;; ── segment capabilities (representative; generalized from bespoke) ─────────

(def segment-capabilities
  "segment -> {:domain :required :checks}. :required is the default required
  input field set when the commodity carries no spec-fields of its own."
  {"10" {:domain :live-plant-and-animal
         :required ["source_country" "animal_health_certificate"]
         :checks [(check :health "health certification present & certified"
                         (fn [in] (let [h (get in :health_data (get in "health_data"))]
                                    (boolean (:certified h)))))
                  (check :quarantine "quarantine status cleared or not required"
                         (fn [in] (not= "pending" (get in :quarantine_status
                                                       (get in "quarantine_status")))))]}
   "11" {:domain :raw-materials-mineral-textile
         :required ["material_composition_or_grade" "origin" "quality_certification"]
         :checks [(check :material-grade "material composition / grade declared"
                         (fn [in] (present? in "material_composition_or_grade")))
                  (check :origin-quality "origin and quality certification declared"
                         (fn [in] (and (present? in "origin")
                                       (present? in "quality_certification"))))]}
   "14" {:domain :paper-materials-products
         :required ["material_grade" "sustainability_certification"]
         :checks [(check :paper-material-grade "paper material grade / basis weight / finish declared"
                         (fn [in] (or (present? in "material_grade")
                                      (present? in "basis_weight")
                                      (present? in "paper_finish"))))
                  (check :paper-source-cert "paper source / sustainability certification present (FSC / recycled / PEFC)"
                         (fn [in] (or (present? in "sustainability_certification")
                                      (present? in "fsc_certification")
                                      (present? in "recycled_content")
                                      (present? in "pefc_certification"))))]}
   "21" {:domain :farming-fishing-forestry-machinery
         :required ["machine_safety_certification" "rated_capacity"]
         :checks [(check :machine-safety-cert "machine safety certification present (CE / ISO-12100)"
                         (fn [in] (or (present? in "ce_mark")
                                      (present? in "iso_12100_certification")
                                      (present? in "machine_safety_certification"))))
                  (check :rated-capacity "rated capacity / working spec declared"
                         (fn [in] (or (present? in "rated_capacity")
                                      (present? in "working_spec")
                                      (present? in "operating_capacity")
                                      (present? in "capacity"))))]}
   "22" {:domain :building-construction-machinery
         :required ["machine_safety_certification" "rated_capacity"]
         :checks [(check :machine-safety-cert "machine safety certification present (CE / ISO-12100)"
                         (fn [in] (or (present? in "ce_mark")
                                      (present? in "iso_12100_certification")
                                      (present? in "machine_safety_certification"))))
                  (check :rated-capacity "rated capacity declared"
                         (fn [in] (or (present? in "rated_capacity")
                                      (present? in "load_capacity")
                                      (present? in "operating_capacity"))))]}
   "23" {:domain :industrial-process-machinery
         :required ["machine_safety_certification" "capacity_throughput_rating"]
         :checks [(check :machine-safety-cert "machine safety certification present (CE / ISO-12100)"
                         (fn [in] (or (present? in "ce_mark")
                                      (present? in "iso_12100_certification")
                                      (present? in "machine_safety_certification"))))
                  (check :capacity-throughput "capacity / throughput rating declared"
                         (fn [in] (or (present? in "capacity_rating")
                                      (present? in "throughput_rating")
                                      (present? in "capacity_throughput_rating"))))]}
   "24" {:domain :material-handling-storage
         :required ["rated_load_capacity" "machine_safety_certification"]
         :checks [(check :rated-load-capacity "rated load capacity declared"
                         (fn [in] (present? in "rated_load_capacity")))
                  (check :machine-safety-cert "machine safety certification present (CE / ISO-12100 / FEM)"
                         (fn [in] (or (present? in "machine_safety_certification")
                                      (present? in "ce_mark")
                                      (present? in "iso_12100_certification")
                                      (present? in "fem_certification"))))]}
   "27" {:domain :tools-general-machinery
         :required ["tool_safety_standard" "power_or_torque_rating"]
         :checks [(check :tool-safety-standard "tool safety standard present (ISO 1703 / EN 60900 / ANSI)"
                         (fn [in] (or (present? in "tool_safety_standard")
                                      (present? in "iso_1703_certification")
                                      (present? in "en_60900_certification")
                                      (present? in "ansi_certification"))))
                  (check :power-torque-rating "power or torque rating declared"
                         (fn [in] (or (present? in "power_rating")
                                      (present? in "torque_rating")
                                      (present? in "power_or_torque_rating"))))]}
   "39" {:domain :electrical-systems-lighting
         :required ["voltage_rating" "power_rating" "electrical_safety_certification"]
         :checks [(check :voltage-rating "voltage rating declared"
                         (fn [in] (present? in "voltage_rating")))
                  (check :power-rating "power rating declared"
                         (fn [in] (present? in "power_rating")))
                  (check :electrical-safety-cert "electrical safety certification present (IEC / UL)"
                         (fn [in] (or (present? in "iec_certification")
                                      (present? in "ul_certification")
                                      (present? in "electrical_safety_certification"))))]}
   "47" {:domain :cleaning-equipment
         :required ["sds" "dilution_or_usage_specification"]
         :checks [(check :sds "safety data sheet (SDS) present"
                         (fn [in] (present? in "sds")))
                  (check :dilution-or-usage-spec "dilution / usage specification present"
                         (fn [in] (present? in "dilution_or_usage_specification")))]}
   "48" {:domain :service-industry-machinery
         :required ["machine_safety_certification" "hygiene_or_food_contact_compliance"]
         :checks [(check :machine-safety-cert "machine safety certification present (CE / ISO-12100)"
                         (fn [in] (or (present? in "ce_mark")
                                      (present? in "iso_12100_certification")
                                      (present? in "machine_safety_certification"))))
                  (check :hygiene-food-contact "hygiene / food-contact compliance declared"
                         (fn [in] (or (present? in "hygiene_compliance")
                                      (present? in "food_contact_compliance")
                                      (present? in "hygiene_or_food_contact_compliance"))))]}
   "50" {:domain :food-beverage-safety
         :required ["temperature_control" "source_certification" "pathogen_testing"]
         :checks [(check :pathogen "pathogen testing recorded"
                         (fn [in] (present? in "pathogen_testing")))
                  (check :source-cert "source certification present"
                         (fn [in] (present? in "source_certification")))]}
   "51" {:domain :pharmaceutical
         :required ["gmp_certificate" "expiry_date" "lot_number"]
         :checks [(check :gmp "GMP certificate present"
                         (fn [in] (present? in "gmp_certificate")))
                  (check :not-expired "expiry date present (lifecycle tracked)"
                         (fn [in] (present? in "expiry_date")))]}
   "40" {:domain :fluid-distribution-conditioning
         :required ["pressure_rating" "material" "fluid_medium"]
         :checks [(check :pressure-rating "pressure rating declared"
                         (fn [in] (present? in "pressure_rating")))
                  (check :material-fluid-compat "material and fluid compatibility declared"
                         (fn [in] (and (present? in "material")
                                       (present? in "fluid_medium"))))]}
   "41" {:domain :laboratory-measuring-equipment
         :required ["measurement_type" "accuracy_specification" "calibration_certificate"]
         :checks [(check :calibration "calibration certificate present"
                         (fn [in] (present? in "calibration_certificate")))
                  (check :traceability "measurement traceability declared (NIST / ISO-17025)"
                         (fn [in] (or (present? in "nist_traceability")
                                      (present? in "iso_17025_accreditation"))))]}
   "42" {:domain :medical-device
         :required ["regulatory_class" "lot_number"]
         :checks [(check :reg-class "regulatory class declared"
                         (fn [in] (present? in "regulatory_class")))
                  (check :traceable "lot/UDI traceability present"
                         (fn [in] (or (present? in "lot_number") (present? in "udi"))))]}
   "12" {:domain :chemical
         :required ["ghs_classification" "sds"]
         :checks [(check :ghs "GHS classification declared"
                         (fn [in] (present? in "ghs_classification")))
                  (check :sds "safety data sheet present"
                         (fn [in] (present? in "sds")))]}
   "13" {:domain :polymer-elastomer-materials
         :required ["material_spec_or_grade" "sds"]
         :checks [(check :material-spec "material specification / grade declared"
                         (fn [in] (present? in "material_spec_or_grade")))
                  (check :sds "safety data sheet (SDS) present"
                         (fn [in] (present? in "sds")))]}
   "43" {:domain :information-technology
         :required ["spec" "warranty"]
         :checks [(check :spec "technical spec present"
                         (fn [in] (present? in "spec")))]}
   "44" {:domain :office-equipment
         :required ["product_safety_certification" "energy_efficiency_rating" "consumable_compatibility"]
         :checks [(check :product-safety-or-energy-cert
                         "product safety / energy-efficiency certification present (CE / UL / Energy Star / EPEAT)"
                         (fn [in] (or (present? in "product_safety_certification")
                                      (present? in "ce_mark")
                                      (present? in "ul_certification")
                                      (present? in "energy_efficiency_rating")
                                      (present? in "energy_star")
                                      (present? in "epeat_rating"))))
                  (check :consumable-compatibility
                         "consumable compatibility declared"
                         (fn [in] (present? in "consumable_compatibility")))]}
   "45" {:domain :printing-av-equipment
         :required ["product_safety_certification" "format_or_media_compatibility" "energy_efficiency_rating"]
         :checks [(check :product-safety-or-energy-cert
                         "product safety / energy-efficiency certification present (CE / UL / Energy Star / EPEAT)"
                         (fn [in] (or (present? in "product_safety_certification")
                                      (present? in "ce_mark")
                                      (present? in "ul_certification")
                                      (present? in "energy_efficiency_rating")
                                      (present? in "energy_star")
                                      (present? in "epeat_rating"))))
                  (check :format-media-compatibility
                         "format or media compatibility declared"
                         (fn [in] (present? in "format_or_media_compatibility")))]}
   "25" {:domain :vehicle
         :required ["emissions_tier" "safety_certification"]
         :checks [(check :emissions "emissions tier declared"
                         (fn [in] (present? in "emissions_tier")))
                  (check :safety "safety certification present"
                         (fn [in] (present? in "safety_certification")))]}
   "26" {:domain :power-generation-distribution
         :required ["power_rating" "voltage_rating" "electrical_safety_certification"]
         :checks [(check :power-rating "power rating declared"
                         (fn [in] (present? in "power_rating")))
                  (check :voltage-rating "voltage rating declared"
                         (fn [in] (present? in "voltage_rating")))
                  (check :electrical-safety-cert "electrical safety certification present (IEC / UL)"
                         (fn [in] (or (present? in "iec_certification")
                                      (present? in "ul_certification")
                                      (present? in "electrical_safety_certification"))))]}
   "52" {:domain :consumer-electronics-appliance
         :required ["safety_certification" "energy_efficiency_rating"]
         :checks [(check :safety-certification "product safety certification present (CE / UL / PSE)"
                         (fn [in] (or (present? in "safety_certification")
                                      (present? in "ce_mark")
                                      (present? in "ul_certification")
                                      (present? in "pse_mark"))))
                  (check :energy-efficiency "energy-efficiency rating declared"
                         (fn [in] (present? in "energy_efficiency_rating")))]}
   "53" {:domain :apparel-personal-care
         :required ["fiber_composition" "care_labeling"]
         :checks [(check :fiber-composition "fiber / material composition declared"
                         (fn [in] (present? in "fiber_composition")))
                  (check :care-labeling "safety and care labeling compliance declared"
                         (fn [in] (present? in "care_labeling")))]}
   "54" {:domain :timepieces-jewelry-gemstone
         :required ["material_composition_or_grade" "origin" "provenance"]
         :checks [(check :material-purity-cert
                         "material / purity certification declared (hallmark / karat / assay)"
                         (fn [in] (or (present? in "material_composition_or_grade")
                                      (present? in "metal_purity")
                                      (present? in "hallmark")
                                      (present? in "karat")
                                      (present? in "assay_certificate")
                                      (present? in "precious_metal_certification"))))
                  (check :provenance-origin
                         "provenance and origin disclosure declared"
                         (fn [in] (and (present? in "provenance")
                                       (present? in "origin"))))]}
   "55" {:domain :published-products
         :required ["format_edition_identifier" "content_licensing_metadata"]
         :checks [(check :format-edition-id
                         "format / edition identification present (ISBN / ISSN)"
                         (fn [in] (or (present? in "format_edition_identifier")
                                      (present? in "isbn")
                                      (present? in "issn"))))
                  (check :content-licensing-metadata
                         "content and licensing metadata present"
                         (fn [in] (or (present? in "content_licensing_metadata")
                                      (and (present? in "content_metadata")
                                           (present? in "licensing_metadata")))))]}
   "56" {:domain :furniture-furnishings
         :required ["material_type" "dimension_spec" "load_capacity_or_rated_weight"]
         :checks [(check :material-flammability-compliance
                         "furniture material type and flammability / fire-resistance compliance declared"
                         (fn [in] (and (present? in "material_type")
                                       (or (present? in "flammability_certification")
                                           (present? in "fire_resistance_rating")
                                           (present? in "material_compliance")))))
                  (check :dimensional-load-spec
                         "dimensional specification and rated load or weight declared"
                         (fn [in] (and (or (present? in "dimension_spec")
                                           (present? in "dimensions_mm")
                                           (present? in "dimensions"))
                                       (or (present? in "load_capacity")
                                           (present? in "rated_weight")
                                           (present? in "seating_capacity")))))]}
   "30" {:domain :structural-construction-material
         :required ["material_grade" "dimension_spec"]
         :checks [(check :material-grade "material grade declared with ASTM / EN / JIS standard"
                         (fn [in] (and (present? in "material_grade")
                                       (or (present? in "astm_standard")
                                           (present? in "en_standard")
                                           (present? in "jis_standard")))))
                  (check :dimensional-spec "dimensional specification present"
                         (fn [in] (present? in "dimension_spec")))]}
   "31" {:domain :manufactured-component
         :required ["material" "dimension" "standard"]
         :checks [(check :material "material declared"
                         (fn [in] (present? in "material")))
                  (check :standard "conforming standard declared"
                         (fn [in] (present? in "standard")))]}
   "32" {:domain :electronic-components
         :required ["rohs_status" "electrical_rating" "tolerance"]
         :checks [(check :rohs-reach "RoHS or REACH compliance declared"
                         (fn [in] (or (present? in "rohs_status")
                                      (present? in "reach_status"))))
                  (check :electrical-spec "electrical rating and tolerance declared"
                         (fn [in] (and (present? in "electrical_rating")
                                       (present? in "tolerance"))))]}
   "60" {:domain :teaching-aids-materials
         :required ["product_safety_certification" "age_or_curriculum_level"]
         :checks [(check :product-safety-cert "product safety certification present (CE / ASTM F963 / EN-71)"
                         (fn [in] (present? in "product_safety_certification")))
                  (check :age-curriculum-level "age or curriculum level grading declared"
                         (fn [in] (present? in "age_or_curriculum_level")))]}
   "49" {:domain :recreation-toys-instruments
         :required ["product_category" "safety_certification"]
         :checks [(check :safety "product safety certification present (EN71 / ASTM-F963 / CE)"
                         (fn [in] (present? in "safety_certification")))
                  (check :age-grading "age grading / age appropriateness declared"
                         (fn [in] (present? in "age_grading")))]}})

(def generic-capability
  {:domain :commodity-general
   :required []
   :checks []})

;; ── universal procurement baseline ─────────────────────────────────────────
;; Applies to EVERY commodity so no code is a hollow stub: a UNSPSC actor's
;; baseline job is to validate a procurement/transaction line for its commodity.
;; (Phase 2 deepens generic codes with per-commodity spec; this is the floor.)

(def universal-required ["quantity" "unit"])

(def universal-checks
  [(check :quantity "procurement line requires a quantity"
          (fn [in] (present? in "quantity")))
   (check :unit "procurement line requires a unit of measure"
          (fn [in] (present? in "unit")))])

(defn capability-for
  "Resolves the capability for a taxon (by segment), or the generic one."
  [taxon]
  (get segment-capabilities (:segment taxon) generic-capability))

(defn required-fields
  "The commodity's required fields: its own taxonomy spec-fields if present,
  else the segment capability default, else the universal procurement baseline.
  Never empty — every code requires real, validatable input."
  [cap taxon]
  (let [spec (:spec-fields taxon)
        seg  (:required cap)]
    (cond
      (seq spec) (vec spec)
      (seq seg)  (vec seg)
      :else      universal-required)))

(defn- run-checks [checks input]
  (mapv (fn [{:keys [id desc pred]}]
          {:id id :desc desc :pass (boolean (pred input))})
        checks))

(defn active-risk-checks
  "Risk checks activated by the taxon's risk-tags."
  [taxon]
  (keep risk-checks (:risk-tags taxon)))

(defn run
  "Runs the commodity's capability against an input map. Returns a verdict:
    {:domain :required [..] :missing [..] :checks [{:id :desc :pass}] :ok bool}
  ok = all required fields present AND all domain + risk checks pass.
  Always does commodity-specific work — never a no-op."
  [taxon input]
  (let [cap     (capability-for taxon)
        req     (required-fields cap taxon)
        missing (vec (remove #(present? input %) req))
        checks  (run-checks (concat universal-checks (:checks cap) (active-risk-checks taxon))
                            input)
        ok      (and (empty? missing) (every? :pass checks))]
    {:domain   (:domain cap)
     :required req
     :missing  missing
     :checks   checks
     :ok       ok}))
