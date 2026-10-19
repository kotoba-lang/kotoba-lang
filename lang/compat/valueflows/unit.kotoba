(ns valueflows.unit
  "Units of measure: which spellings mean the same unit, and which quantities
   may be added at all.

   Every library here already refuses to add across units — `valueflows.event`
   will not put 3 hours into 10 kilograms. That refusal is only worth having if
   two callers agree on how a unit is SPELLED. `:kg` and `:kilogram` are the
   same unit; without a registry they are two, and the refusal fires on a
   quantity that was fine.

   `vf:Unit` is `rdfs:subClassOf om:Unit` and `vf:omUnitIdentifier` carries the
   om-2 identifier \"for standardization across networks\", so om-2 is the
   authority for physical units. Currencies are in om-2 too, and also carry an
   ISO 4217 code. A community unit with no external authority says so rather
   than being given a plausible-looking IRI.

   AN UNREGISTERED UNIT IS REPORTED, NOT GUESSED. `resolve` says
   `:source :unregistered`, `compatible?` allows it only against itself, and
   nothing here maps an unknown spelling onto a similar-looking known one.
   Guessing that `:kgs` means kilograms is right; guessing that `:m` means
   metres rather than minutes is how a bill of materials gets silently wrong."
  (:require [valueflows.unit-data :as data]))

(def registry data/registry)
(def om-2-namespace (:namespace data/om-2))

(defn resolve-unit
  "=> {:unit :kg :source :canonical|:alias|:unregistered}

   The `:source` is the point: a caller that wants to be strict can reject
   `:unregistered`, and one that does not still gets told."
  [u]
  (cond
    (nil? u) {:unit nil :source :absent}
    (contains? registry u) {:unit u :source :canonical}
    (contains? data/alias-index u) {:unit (get data/alias-index u) :source :alias}
    :else {:unit u :source :unregistered}))

(defn canonical
  "The canonical keyword for `u`, or `u` itself when unregistered. Use
   `resolve-unit` when the difference matters."
  [u]
  (:unit (resolve-unit u)))

(defn registered? [u]
  (not= :unregistered (:source (resolve-unit u))))

(defn same?
  "Do these two spellings mean one unit? Two unregistered spellings are the
   same only if identical — no fuzzy matching, ever."
  [a b]
  (let [ra (resolve-unit a) rb (resolve-unit b)]
    (and (= (:unit ra) (:unit rb))
         ;; nil is not a unit; two absent units are not 'the same unit'
         (some? (:unit ra)))))

(defn quantity-kind
  "What sort of quantity this measures — :mass :time :volume :length :count
   :ratio :currency :mutual-credit — or nil when unregistered.

   This classification is THIS REGISTRY'S, not om-2's. It exists so
   `compatible?` can answer without pulling a 2 MB ontology into a portable
   library, and is labelled so nobody cites it as a standard."
  [u]
  (:quantity-kind (get registry (canonical u))))

(defn compatible?
  "May quantities in these two units be compared or converted at all?

   True for the same unit, and for two units of the same quantity kind (kg and
   g). NOT true across kinds, and not true when either side is unregistered
   unless they are identical — an unknown unit has no known kind, and
   assuming one is how hours get added to kilograms."
  [a b]
  (or (same? a b)
      (let [ka (quantity-kind a) kb (quantity-kind b)]
        (boolean (and ka kb (= ka kb))))))

(defn om-2-iri
  "The om-2 identifier, or nil for a unit with no external authority.

   These IRIs do not dereference: measured 2026-08-15, the om-2 website
   returned 500 for its data file and 404 for every resource path. They are
   identifiers, which is all `vf:omUnitIdentifier` (an xsd:anyURI) asks for."
  [u]
  (when-let [n (:om-2 (get registry (canonical u)))]
    (str om-2-namespace n)))

(defn iso-4217 [u] (:iso-4217 (get registry (canonical u))))

(defn authority
  "=> :om-2 | :none | nil (unregistered). :none is a positive statement —
   this unit has no external authority and is not pretending to."
  [u]
  (:authority (get registry (canonical u))))

(defn symbol-of [u] (:symbol (get registry (canonical u))))
(defn label [u] (:label (get registry (canonical u))))

(defn describe
  "Everything known about a spelling, for an error message a human can act on."
  [u]
  (let [{:keys [unit source]} (resolve-unit u)]
    (merge {:given u :canonical unit :source source}
           (when-let [e (get registry unit)]
             (select-keys e [:label :symbol :quantity-kind :authority :om-2 :iso-4217])))))

(defn coverage
  "What the registry does and does not cover. Stated, so a count of 17 cannot
   read as 'units are handled'."
  []
  {:units (count registry)
   :quantity-kinds (into (sorted-set) (keep :quantity-kind) (vals registry))
   :with-om-2 (count (filter :om-2 (vals registry)))
   :without-authority (into (sorted-set)
                            (keep (fn [[k v]] (when (= :none (:authority v)) k)))
                            registry)
   :om-2-verified-at (:verified-at data/om-2)
   :note "Small on purpose: there is no divergent unit corpus in this workspace
          to reconcile. kotoba-lang/plm declares :plm.item/uom and populates it
          nowhere (measured 2026-08-15). Add a unit when something uses it."})
