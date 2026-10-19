(ns kotoba.ekyc.jurisdiction-graph
  "Cross-repo regulatory-linkage graph connecting `kotoba.ekyc`'s
  non-face-to-face identity-verification method catalog (犯収法施行規則 第6条
  第1項, the ONLY jurisdiction this library models) to the
  `cloud-itonami-iso3166-*` family (223 candidate per-country \"Market-Entry
  Compliance\" actor repos in the `cloud-itonami` GitHub org). Pure EDN
  schema + data + query helpers -- no network, no I/O, `.cljc` portable
  across JVM / ClojureScript / SCI / GraalVM, exactly like `kotoba.ekyc`
  itself.

  Two schema variants of the SAME model are exposed:

    `schema-datomic`    -- full Datomic schema vocabulary (`:db/valueType`
                           on every attribute, `:db/fulltext` on the
                           free-text legal-basis attributes, `:db/doc`).
    `schema-datascript` -- `schema-datomic` with every Datomic-only key
                           stripped, so it loads into a REAL DataScript
                           conn without error. This is not a claimed
                           subset -- `derive-datascript-schema` is the
                           actual (mechanical, inspectable) transform, and
                           `test/kotoba/ekyc/jurisdiction_graph_test.cljc`
                           proves the result loads by transacting it into
                           `datascript.core` (the real library, not a
                           reimplementation) and running Datalog queries
                           against it. See docs/adr/0002 for the empirical
                           probe that discovered exactly which Datomic
                           schema keys DataScript rejects (`:db/valueType`
                           on anything but `:db.type/ref`/`:db.type/tuple`
                           throws \"Bad attribute specification\"; scalar
                           `:db/valueType` was NOT expected to be the
                           incompatible one going in -- `:db/fulltext` was
                           the only divergence assumed before testing).

  Three entity kinds:

    `:country/*`      -- one entity per cloud-itonami-iso3166-* country
                         repo (188 real ones as of 2026-07-14, see
                         `countries` and the honesty note below -- NOT
                         223; verified, not assumed, see docs/adr/0002).
    `:ekyc-method/*`  -- one entity per `kotoba.ekyc/method-catalog` entry
                         (10 today, all JPN), mirrored 1:1 from the live
                         catalog var via `method-entities` so this graph
                         can never drift from the source of truth.
    `:recognition/*`  -- a REIFIED EDGE entity, not a direct
                         country->method ref. A jurisdiction-specific
                         legal-basis citation lives on the edge, not on
                         the method, because the same method CONCEPT can
                         be recognized by different jurisdictions under
                         different domestic statutes (today every
                         recognition is JPN citing the JPN method's own
                         canonical citation, since JPN is the only
                         jurisdiction modeled -- the reification exists
                         for the day a second jurisdiction recognizes an
                         equivalent method under its own law).
                         `:country/recognizes-method` (ref, cardinality
                         many) points from a country to its recognition
                         entities, never straight to a method entity.

  Honesty (matches this fleet's `vcfund.facts/coverage` /
  `marketentry.facts/coverage` convention, and `kotoba.ekyc`'s own
  \"missing jurisdictions are uncovered, never fabricated\" rule):
  EVERY country entity that exists in the `cloud-itonami-iso3166-*` family
  gets a `:country/*` entity here (so the graph is structurally complete
  and queryable across the whole family), but `:country/recognizes-method`
  edges exist ONLY for JPN. `:country/coverage-status` is `:researched`
  for JPN and `:not-yet-researched` for every other country -- absence of
  an edge means 'not yet researched', never 'no requirements'."
  (:require [kotoba.ekyc :as ekyc]))

;; ---------------------------------------------------------------------------
;; Datomic schema (full vocabulary)
;; ---------------------------------------------------------------------------

(def schema-datomic
  "Datomic schema for the jurisdiction-linkage graph. Standard Datomic
  reference vocabulary (https://docs.datomic.com/schema/schema-reference.html)
  -- this workspace has no licensed Datomic peer to transact against (the
  rest of this fleet abstracts through `langchain.db`, a Datomic-API-
  compatible in-memory store, for the same reason -- see
  `cloud-itonami-isic-6492`'s `credit.store`); `schema-datascript` below IS
  empirically verified against a real engine (see the namespace docstring)."
  {;; ---- country ----
   :country/iso3166-alpha3
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "ISO 3166-1 alpha-3 code, e.g. \"JPN\"."}

   :country/iso3166-alpha2
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "ISO 3166-1 alpha-2 code, e.g. \"JP\" (IANA tz database iso3166.tab crosswalk)."}

   :country/name
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "English name as self-declared in the country repo's own blueprint.edn."}

   :country/repo-url
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/doc "github.com/cloud-itonami/cloud-itonami-iso3166-<code> -- the real repo URL."}

   :country/coverage-status
   {:db/valueType :db.type/keyword :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc ":researched (has >=1 :country/recognizes-method edge, cited) | :not-yet-researched."}

   :country/recognizes-method
   {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many
    :db/doc "-> :recognition/* entities (a REIFIED edge, not a direct ref to :ekyc-method/*; see namespace docstring)."}

   ;; ---- ekyc-method (mirrors kotoba.ekyc/method-catalog) ----
   :ekyc-method/id
   {:db/valueType :db.type/keyword :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Statute-letter id, mirrors :ekyc.method/id (:ho :he :to-1 :to-2 :ru :ka :wa :yo :corp-ro :corp-ho)."}

   :ekyc-method/jurisdiction
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "Origin jurisdiction of the method CONCEPT (mirrors :ekyc.method/jurisdiction; \"JPN\" for all ten catalog entries today)."}

   :ekyc-method/subject
   {:db/valueType :db.type/keyword :db/cardinality :db.cardinality/one
    :db/doc ":individual | :corporate, mirrors :ekyc.method/subject."}

   :ekyc-method/label
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/doc "Mirrors :ekyc.method/label."}

   :ekyc-method/legal-basis
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/fulltext true
    :db/doc "Mirrors :ekyc.method/citation -- the method's OWN canonical statute citation. Datomic-only :db/fulltext (full-text search over citation text) -- see schema-datascript."}

   :ekyc-method/ial-level
   {:db/valueType :db.type/keyword :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "NIST SP 800-63A-4 IAL approx mapping, mirrors :ekyc.method/ial-approx."}

   ;; ---- recognition (reified country<->method edge) ----
   :recognition/id
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "\"<ISO3166-ALPHA3>:<ekyc-method-id>\" composite key, e.g. \"JPN:ho\"."}

   :recognition/country
   {:db/valueType :db.type/ref :db/cardinality :db.cardinality/one
    :db/doc "-> the :country/* entity that legally recognizes this method."}

   :recognition/method
   {:db/valueType :db.type/ref :db/cardinality :db.cardinality/one
    :db/doc "-> the :ekyc-method/* entity naming the recognized method concept."}

   :recognition/legal-basis
   {:db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/fulltext true
    :db/doc "The JURISDICTION-SPECIFIC citation for this recognition -- may differ from :ekyc-method/legal-basis when a different jurisdiction incorporates the same method concept by its own statute. Equal to the method's own citation today (every recognition is JPN citing its own method)."}})

;; ---------------------------------------------------------------------------
;; DataScript schema -- a mechanical transform of schema-datomic, not a
;; hand-maintained parallel definition (so it can never silently drift).
;; ---------------------------------------------------------------------------

(defn derive-datascript-schema
  "Strips every Datomic-only schema key from `datomic-schema`, empirically
  determined (docs/adr/0002) against a real `datascript.core/create-conn`:
    - `:db/valueType` is dropped UNLESS it is `:db.type/ref` (DataScript
      throws \"Bad attribute specification ... expected one of
      #{:db.type/tuple :db.type/ref}\" for any other value -- scalar types
      like `:db.type/string`/`:db.type/keyword` are REJECTED outright, not
      silently ignored, which is stricter than assumed before probing).
    - `:db/fulltext` is dropped (DataScript has no full-text index; the
      key alone is enough to trip the same valueType rejection above
      since every :db/fulltext attribute in this schema is a
      :db.type/string).
    - `:db/doc`, `:db/cardinality`, `:db/unique`, `:db/index` pass through
      unchanged -- each verified standalone-safe against a real conn."
  [datomic-schema]
  (into {}
        (map (fn [[attr spec]]
               [attr (cond-> (dissoc spec :db/fulltext)
                       (not= :db.type/ref (:db/valueType spec))
                       (dissoc :db/valueType))]))
        datomic-schema))

(def schema-datascript
  "The DataScript-compatible variant of `schema-datomic`. See
  `derive-datascript-schema` and `test/kotoba/ekyc/jurisdiction_graph_test.cljc`
  for the real datascript.core proof (create-conn + transact + query all
  succeed against THIS value, not an aspirational claim)."
  (derive-datascript-schema schema-datomic))

;; ---------------------------------------------------------------------------
;; Country data -- 188 REAL cloud-itonami-iso3166-* country repos, enumerated
;; from each repo's own blueprint.edn (`:itonami.blueprint/iso3166` +
;; `:itonami.blueprint/name`), not fabricated or truncated.
;;
;; NOT 223: 223 is the raw count of directories matching
;; `cloud-itonami-iso3166-*` locally, but 35 of those are not additional
;; countries -- 1 is `cloud-itonami-iso3166-ind-clean-air` (a different
;; domain, :public-interest/airshed-clean-air, not market-entry-compliance)
;; and 34 are JAPAN/USA AGENCY-level sub-blueprints (`:parent` entries in
;; kotoba-lang/iso3166's registry.edn -- 19 gov.jpn.* + 15 gov.usa.* bodies,
;; e.g. cloud-itonami-iso3166-jpn-fsa, cloud-itonami-iso3166-usa-gsa), each
;; a child of a country rather than a country itself and not addressable by
;; a real ISO 3166-1 alpha-3 code. 188 is the real top-level per-country
;; count (verified 2026-07-14 by parsing all 222 non-ind-clean-air
;; blueprint.edn files and filtering to :itonami.blueprint/domain
;; :public-sector/market-entry-compliance). Of these 188, 182 are confirmed
;; live on GitHub (`gh api orgs/cloud-itonami/repos --paginate`); six
;; (KGZ LAO MMR TJK TKM UZB) exist as real local scaffolds
;; (blueprint.edn + docs + README) but have no `.git` of their own and
;; 404 on GitHub -- not yet pushed. :country/repo-url uses this family's
;; own exceptionless naming convention
;; (github.com/cloud-itonami/cloud-itonami-iso3166-<lowercase-alpha3>),
;; true for all 188 including those six once pushed.
;;
;; iso3166-alpha2 is cross-referenced against the IANA tz database's
;; public-domain ISO 3166-1 alpha-2 table (/usr/share/zoneinfo.default/
;; iso3166.tab on this machine) by country identity, not derived by
;; truncating the alpha-3 code (that heuristic is wrong in general --
;; e.g. CHN/China's alpha-2 is CN, not CH, which is Switzerland's).
;; ---------------------------------------------------------------------------

(def ^:private country-rows
  "[iso3166-alpha3 iso3166-alpha2 name repo-slug], sorted by alpha-3."
  [["AGO" "AO" "Republic of Angola" "cloud-itonami-iso3166-ago"]
   ["ALB" "AL" "Republic of Albania" "cloud-itonami-iso3166-alb"]
   ["AND" "AD" "Andorra" "cloud-itonami-iso3166-and"]
   ["ARE" "AE" "the United Arab Emirates" "cloud-itonami-iso3166-are"]
   ["ARG" "AR" "Argentina" "cloud-itonami-iso3166-arg"]
   ["ARM" "AM" "Armenia" "cloud-itonami-iso3166-arm"]
   ["ATG" "AG" "Antigua and Barbuda" "cloud-itonami-iso3166-atg"]
   ["AUS" "AU" "Australia" "cloud-itonami-iso3166-aus"]
   ["AUT" "AT" "Austria" "cloud-itonami-iso3166-aut"]
   ["AZE" "AZ" "Republic of Azerbaijan" "cloud-itonami-iso3166-aze"]
   ["BDI" "BI" "Burundi" "cloud-itonami-iso3166-bdi"]
   ["BEL" "BE" "Belgium" "cloud-itonami-iso3166-bel"]
   ["BEN" "BJ" "Republic of Benin" "cloud-itonami-iso3166-ben"]
   ["BFA" "BF" "Burkina Faso" "cloud-itonami-iso3166-bfa"]
   ["BGD" "BD" "Bangladesh" "cloud-itonami-iso3166-bgd"]
   ["BGR" "BG" "Republic of Bulgaria" "cloud-itonami-iso3166-bgr"]
   ["BHR" "BH" "Kingdom of Bahrain" "cloud-itonami-iso3166-bhr"]
   ["BHS" "BS" "The Bahamas" "cloud-itonami-iso3166-bhs"]
   ["BIH" "BA" "Bosnia and Herzegovina" "cloud-itonami-iso3166-bih"]
   ["BLR" "BY" "Republic of Belarus" "cloud-itonami-iso3166-blr"]
   ["BLZ" "BZ" "Belize" "cloud-itonami-iso3166-blz"]
   ["BOL" "BO" "Bolivia" "cloud-itonami-iso3166-bol"]
   ["BRA" "BR" "Brazil" "cloud-itonami-iso3166-bra"]
   ["BRB" "BB" "Barbados" "cloud-itonami-iso3166-brb"]
   ["BRN" "BN" "Brunei Darussalam" "cloud-itonami-iso3166-brn"]
   ["BTN" "BT" "Kingdom of Bhutan" "cloud-itonami-iso3166-btn"]
   ["BWA" "BW" "Botswana" "cloud-itonami-iso3166-bwa"]
   ["CAF" "CF" "Central African Republic" "cloud-itonami-iso3166-caf"]
   ["CAN" "CA" "Canada" "cloud-itonami-iso3166-can"]
   ["CHE" "CH" "Switzerland" "cloud-itonami-iso3166-che"]
   ["CHL" "CL" "Chile" "cloud-itonami-iso3166-chl"]
   ["CHN" "CN" "China" "cloud-itonami-iso3166-chn"]
   ["CIV" "CI" "Republic of Côte d'Ivoire" "cloud-itonami-iso3166-civ"]
   ["CMR" "CM" "Republic of Cameroon" "cloud-itonami-iso3166-cmr"]
   ["COD" "CD" "Democratic Republic of the Congo" "cloud-itonami-iso3166-cod"]
   ["COG" "CG" "Republic of the Congo" "cloud-itonami-iso3166-cog"]
   ["COL" "CO" "Colombia" "cloud-itonami-iso3166-col"]
   ["COM" "KM" "Comoros" "cloud-itonami-iso3166-com"]
   ["CPV" "CV" "Cape Verde" "cloud-itonami-iso3166-cpv"]
   ["CRI" "CR" "Costa Rica" "cloud-itonami-iso3166-cri"]
   ["CUB" "CU" "Republic of Cuba" "cloud-itonami-iso3166-cub"]
   ["CYP" "CY" "Republic of Cyprus" "cloud-itonami-iso3166-cyp"]
   ["CZE" "CZ" "Czech Republic" "cloud-itonami-iso3166-cze"]
   ["DEU" "DE" "Germany" "cloud-itonami-iso3166-deu"]
   ["DJI" "DJ" "Djibouti" "cloud-itonami-iso3166-dji"]
   ["DMA" "DM" "Dominica" "cloud-itonami-iso3166-dma"]
   ["DNK" "DK" "Denmark" "cloud-itonami-iso3166-dnk"]
   ["DOM" "DO" "Dominican Republic" "cloud-itonami-iso3166-dom"]
   ["DZA" "DZ" "People's Democratic Republic of Algeria" "cloud-itonami-iso3166-dza"]
   ["ECU" "EC" "Ecuador" "cloud-itonami-iso3166-ecu"]
   ["EGY" "EG" "Egypt" "cloud-itonami-iso3166-egy"]
   ["ERI" "ER" "Eritrea" "cloud-itonami-iso3166-eri"]
   ["ESP" "ES" "Spain" "cloud-itonami-iso3166-esp"]
   ["EST" "EE" "Estonia" "cloud-itonami-iso3166-est"]
   ["ETH" "ET" "Ethiopia" "cloud-itonami-iso3166-eth"]
   ["FIN" "FI" "Finland" "cloud-itonami-iso3166-fin"]
   ["FJI" "FJ" "Republic of Fiji" "cloud-itonami-iso3166-fji"]
   ["FRA" "FR" "France" "cloud-itonami-iso3166-fra"]
   ["FSM" "FM" "Federated States of Micronesia" "cloud-itonami-iso3166-fsm"]
   ["GAB" "GA" "Gabonese Republic" "cloud-itonami-iso3166-gab"]
   ["GBR" "GB" "the United Kingdom" "cloud-itonami-iso3166-gbr"]
   ["GEO" "GE" "Georgia" "cloud-itonami-iso3166-geo"]
   ["GHA" "GH" "Ghana" "cloud-itonami-iso3166-gha"]
   ["GIN" "GN" "Guinea" "cloud-itonami-iso3166-gin"]
   ["GMB" "GM" "The Gambia" "cloud-itonami-iso3166-gmb"]
   ["GNB" "GW" "Guinea-Bissau" "cloud-itonami-iso3166-gnb"]
   ["GNQ" "GQ" "Equatorial Guinea" "cloud-itonami-iso3166-gnq"]
   ["GRC" "GR" "Hellenic Republic" "cloud-itonami-iso3166-grc"]
   ["GRD" "GD" "Grenada" "cloud-itonami-iso3166-grd"]
   ["GTM" "GT" "Republic of Guatemala" "cloud-itonami-iso3166-gtm"]
   ["GUY" "GY" "Guyana" "cloud-itonami-iso3166-guy"]
   ["HND" "HN" "Republic of Honduras" "cloud-itonami-iso3166-hnd"]
   ["HRV" "HR" "Croatia" "cloud-itonami-iso3166-hrv"]
   ["HTI" "HT" "Haiti" "cloud-itonami-iso3166-hti"]
   ["HUN" "HU" "Hungary" "cloud-itonami-iso3166-hun"]
   ["IDN" "ID" "Indonesia" "cloud-itonami-iso3166-idn"]
   ["IND" "IN" "India" "cloud-itonami-iso3166-ind"]
   ["IRL" "IE" "Ireland" "cloud-itonami-iso3166-irl"]
   ["IRQ" "IQ" "Iraq" "cloud-itonami-iso3166-irq"]
   ["ISL" "IS" "Iceland" "cloud-itonami-iso3166-isl"]
   ["ISR" "IL" "Israel" "cloud-itonami-iso3166-isr"]
   ["ITA" "IT" "Italy" "cloud-itonami-iso3166-ita"]
   ["JAM" "JM" "Jamaica" "cloud-itonami-iso3166-jam"]
   ["JOR" "JO" "Jordan" "cloud-itonami-iso3166-jor"]
   ["JPN" "JP" "Japan" "cloud-itonami-iso3166-jpn"]
   ["KAZ" "KZ" "Kazakhstan" "cloud-itonami-iso3166-kaz"]
   ["KEN" "KE" "Kenya" "cloud-itonami-iso3166-ken"]
   ["KGZ" "KG" "Kyrgyzstan" "cloud-itonami-iso3166-kgz"]
   ["KHM" "KH" "Cambodia" "cloud-itonami-iso3166-khm"]
   ["KIR" "KI" "Kiribati" "cloud-itonami-iso3166-kir"]
   ["KNA" "KN" "Saint Kitts and Nevis" "cloud-itonami-iso3166-kna"]
   ["KOR" "KR" "South Korea" "cloud-itonami-iso3166-kor"]
   ["KWT" "KW" "Kuwait" "cloud-itonami-iso3166-kwt"]
   ["LAO" "LA" "Laos" "cloud-itonami-iso3166-lao"]
   ["LBN" "LB" "Lebanon" "cloud-itonami-iso3166-lbn"]
   ["LBR" "LR" "Liberia" "cloud-itonami-iso3166-lbr"]
   ["LBY" "LY" "Libya" "cloud-itonami-iso3166-lby"]
   ["LCA" "LC" "Saint Lucia" "cloud-itonami-iso3166-lca"]
   ["LIE" "LI" "Liechtenstein" "cloud-itonami-iso3166-lie"]
   ["LKA" "LK" "Sri Lanka" "cloud-itonami-iso3166-lka"]
   ["LSO" "LS" "Lesotho" "cloud-itonami-iso3166-lso"]
   ["LTU" "LT" "Lithuania" "cloud-itonami-iso3166-ltu"]
   ["LUX" "LU" "Grand Duchy of Luxembourg" "cloud-itonami-iso3166-lux"]
   ["LVA" "LV" "Latvia" "cloud-itonami-iso3166-lva"]
   ["MAR" "MA" "Morocco" "cloud-itonami-iso3166-mar"]
   ["MCO" "MC" "Monaco" "cloud-itonami-iso3166-mco"]
   ["MDA" "MD" "Moldova" "cloud-itonami-iso3166-mda"]
   ["MDG" "MG" "Republic of Madagascar" "cloud-itonami-iso3166-mdg"]
   ["MDV" "MV" "Maldives" "cloud-itonami-iso3166-mdv"]
   ["MEX" "MX" "Mexico" "cloud-itonami-iso3166-mex"]
   ["MHL" "MH" "Marshall Islands" "cloud-itonami-iso3166-mhl"]
   ["MKD" "MK" "North Macedonia" "cloud-itonami-iso3166-mkd"]
   ["MLI" "ML" "Republic of Mali" "cloud-itonami-iso3166-mli"]
   ["MLT" "MT" "Republic of Malta" "cloud-itonami-iso3166-mlt"]
   ["MMR" "MM" "Myanmar" "cloud-itonami-iso3166-mmr"]
   ["MNE" "ME" "Montenegro" "cloud-itonami-iso3166-mne"]
   ["MNG" "MN" "Mongolia" "cloud-itonami-iso3166-mng"]
   ["MOZ" "MZ" "Mozambique" "cloud-itonami-iso3166-moz"]
   ["MRT" "MR" "Mauritania" "cloud-itonami-iso3166-mrt"]
   ["MUS" "MU" "Mauritius" "cloud-itonami-iso3166-mus"]
   ["MWI" "MW" "Malawi" "cloud-itonami-iso3166-mwi"]
   ["MYS" "MY" "Malaysia" "cloud-itonami-iso3166-mys"]
   ["NAM" "NA" "Namibia" "cloud-itonami-iso3166-nam"]
   ["NER" "NE" "Republic of Niger" "cloud-itonami-iso3166-ner"]
   ["NGA" "NG" "Nigeria" "cloud-itonami-iso3166-nga"]
   ["NIC" "NI" "Nicaragua" "cloud-itonami-iso3166-nic"]
   ["NLD" "NL" "the Netherlands" "cloud-itonami-iso3166-nld"]
   ["NOR" "NO" "Norway" "cloud-itonami-iso3166-nor"]
   ["NPL" "NP" "Nepal" "cloud-itonami-iso3166-npl"]
   ["NRU" "NR" "Nauru" "cloud-itonami-iso3166-nru"]
   ["NZL" "NZ" "New Zealand" "cloud-itonami-iso3166-nzl"]
   ["OMN" "OM" "Oman" "cloud-itonami-iso3166-omn"]
   ["PAK" "PK" "Pakistan" "cloud-itonami-iso3166-pak"]
   ["PAN" "PA" "Panama" "cloud-itonami-iso3166-pan"]
   ["PER" "PE" "Peru" "cloud-itonami-iso3166-per"]
   ["PHL" "PH" "the Philippines" "cloud-itonami-iso3166-phl"]
   ["PLW" "PW" "Palau" "cloud-itonami-iso3166-plw"]
   ["PNG" "PG" "Papua New Guinea" "cloud-itonami-iso3166-png"]
   ["POL" "PL" "Poland" "cloud-itonami-iso3166-pol"]
   ["PRT" "PT" "Portuguese Republic" "cloud-itonami-iso3166-prt"]
   ["PRY" "PY" "Republic of Paraguay" "cloud-itonami-iso3166-pry"]
   ["QAT" "QA" "Qatar" "cloud-itonami-iso3166-qat"]
   ["ROU" "RO" "Romania" "cloud-itonami-iso3166-rou"]
   ["RUS" "RU" "Russian Federation" "cloud-itonami-iso3166-rus"]
   ["RWA" "RW" "Rwanda" "cloud-itonami-iso3166-rwa"]
   ["SAU" "SA" "Saudi Arabia" "cloud-itonami-iso3166-sau"]
   ["SDN" "SD" "Sudan" "cloud-itonami-iso3166-sdn"]
   ["SEN" "SN" "Senegal" "cloud-itonami-iso3166-sen"]
   ["SGP" "SG" "Singapore" "cloud-itonami-iso3166-sgp"]
   ["SLB" "SB" "Solomon Islands" "cloud-itonami-iso3166-slb"]
   ["SLE" "SL" "Sierra Leone" "cloud-itonami-iso3166-sle"]
   ["SLV" "SV" "El Salvador" "cloud-itonami-iso3166-slv"]
   ["SMR" "SM" "San Marino" "cloud-itonami-iso3166-smr"]
   ["SOM" "SO" "Somalia" "cloud-itonami-iso3166-som"]
   ["SRB" "RS" "Republic of Serbia" "cloud-itonami-iso3166-srb"]
   ["SSD" "SS" "South Sudan" "cloud-itonami-iso3166-ssd"]
   ["STP" "ST" "São Tomé and Príncipe" "cloud-itonami-iso3166-stp"]
   ["SUR" "SR" "Suriname" "cloud-itonami-iso3166-sur"]
   ["SVK" "SK" "Slovakia" "cloud-itonami-iso3166-svk"]
   ["SVN" "SI" "Republic of Slovenia" "cloud-itonami-iso3166-svn"]
   ["SWE" "SE" "Sweden" "cloud-itonami-iso3166-swe"]
   ["SWZ" "SZ" "Eswatini" "cloud-itonami-iso3166-swz"]
   ["SYC" "SC" "Seychelles" "cloud-itonami-iso3166-syc"]
   ["TCD" "TD" "Chad" "cloud-itonami-iso3166-tcd"]
   ["TGO" "TG" "Togolese Republic" "cloud-itonami-iso3166-tgo"]
   ["THA" "TH" "Thailand" "cloud-itonami-iso3166-tha"]
   ["TJK" "TJ" "Tajikistan" "cloud-itonami-iso3166-tjk"]
   ["TKM" "TM" "Turkmenistan" "cloud-itonami-iso3166-tkm"]
   ["TLS" "TL" "Timor-Leste" "cloud-itonami-iso3166-tls"]
   ["TON" "TO" "Tonga" "cloud-itonami-iso3166-ton"]
   ["TTO" "TT" "Trinidad and Tobago" "cloud-itonami-iso3166-tto"]
   ["TUN" "TN" "Tunisia" "cloud-itonami-iso3166-tun"]
   ["TUR" "TR" "Turkey" "cloud-itonami-iso3166-tur"]
   ["TUV" "TV" "Tuvalu" "cloud-itonami-iso3166-tuv"]
   ["TZA" "TZ" "Tanzania" "cloud-itonami-iso3166-tza"]
   ["UGA" "UG" "Uganda" "cloud-itonami-iso3166-uga"]
   ["UKR" "UA" "Ukraine" "cloud-itonami-iso3166-ukr"]
   ["URY" "UY" "Uruguay" "cloud-itonami-iso3166-ury"]
   ["USA" "US" "the United States" "cloud-itonami-iso3166-usa"]
   ["UZB" "UZ" "Uzbekistan" "cloud-itonami-iso3166-uzb"]
   ["VCT" "VC" "Saint Vincent and the Grenadines" "cloud-itonami-iso3166-vct"]
   ["VNM" "VN" "Vietnam" "cloud-itonami-iso3166-vnm"]
   ["VUT" "VU" "Vanuatu" "cloud-itonami-iso3166-vut"]
   ["WSM" "WS" "Samoa" "cloud-itonami-iso3166-wsm"]
   ["YEM" "YE" "Yemen" "cloud-itonami-iso3166-yem"]
   ["ZAF" "ZA" "South Africa" "cloud-itonami-iso3166-zaf"]
   ["ZMB" "ZM" "Zambia" "cloud-itonami-iso3166-zmb"]
   ["ZWE" "ZW" "Zimbabwe" "cloud-itonami-iso3166-zwe"]])

(def researched-jurisdictions
  "The only jurisdictions with real :country/recognizes-method edges today."
  #{"JPN"})

(def countries
  "188 country records: {:iso3 :iso2 :name :repo-url}."
  (mapv (fn [[iso3 iso2 name slug]]
          {:iso3 iso3 :iso2 iso2 :name name
           :repo-url (str "https://github.com/cloud-itonami/" slug)})
        country-rows))

;; ---------------------------------------------------------------------------
;; Entity construction
;; ---------------------------------------------------------------------------

(defn country-entities
  "One :country/* entity map per `countries` row."
  []
  (mapv (fn [{:keys [iso3 iso2 name repo-url]}]
          {:country/iso3166-alpha3 iso3
           :country/iso3166-alpha2 iso2
           :country/name name
           :country/repo-url repo-url
           :country/coverage-status (if (contains? researched-jurisdictions iso3)
                                       :researched
                                       :not-yet-researched)})
        countries))

(defn method-entities
  "One :ekyc-method/* entity per `kotoba.ekyc/method-catalog` entry --
  derived from the live catalog, never hand-copied, so this graph can't
  drift from the source of truth as the catalog evolves (e.g. the
  2027-04-01 abolition of ホ, see kotoba.ekyc's docstring)."
  []
  (mapv (fn [m]
          {:ekyc-method/id (:ekyc.method/id m)
           :ekyc-method/jurisdiction (:ekyc.method/jurisdiction m)
           :ekyc-method/subject (:ekyc.method/subject m)
           :ekyc-method/label (:ekyc.method/label m)
           :ekyc-method/legal-basis (:ekyc.method/citation m)
           :ekyc-method/ial-level (:ekyc.method/ial-approx m)})
        ekyc/method-catalog))

(defn- recognition-id [iso3 method-id]
  (str iso3 ":" (name method-id)))

(defn jpn-recognition-entities
  "One reified :recognition/* edge entity per kotoba.ekyc/method-catalog
  entry, all citing JPN (the method's own origin jurisdiction and the
  only jurisdiction modeled). Refs use lookup refs against
  :country/iso3166-alpha3 / :ekyc-method/id -- callers MUST transact
  `country-entities` + `method-entities` in a PRIOR transaction (or
  earlier in the same tx-data vector processed by an engine that resolves
  forward lookup refs within one tx -- empirically, DataScript does
  NOT, see docs/adr/0002 -- `base-tx-data` / `edge-tx-data` below are
  split for exactly this reason)."
  []
  (mapv (fn [m]
          (let [mid (:ekyc.method/id m)]
            {:recognition/id (recognition-id "JPN" mid)
             :recognition/country [:country/iso3166-alpha3 "JPN"]
             :recognition/method [:ekyc-method/id mid]
             :recognition/legal-basis (:ekyc.method/citation m)}))
        ekyc/method-catalog))

(defn jpn-recognizes-tx
  "The :country/recognizes-method backfill onto the JPN country entity,
  pointing at every recognition entity above via lookup ref. Must be
  transacted AFTER `jpn-recognition-entities` exist (see `edge-tx-data`)."
  []
  [{:country/iso3166-alpha3 "JPN"
    :country/recognizes-method
    (mapv (fn [m] [:recognition/id (recognition-id "JPN" (:ekyc.method/id m))])
          ekyc/method-catalog)}])

(defn base-tx-data
  "Phase 1: country + method entities, no cross-refs yet. Transact this
  first (into either a Datomic conn or a DataScript conn -- schema
  permitting)."
  []
  (vec (concat (country-entities) (method-entities))))

(defn edge-tx-data
  "Phase 2: JPN's recognition edges + the :country/recognizes-method
  backfill. Transact AFTER `base-tx-data` has landed (the lookup refs
  inside these entities resolve against entities `base-tx-data` created)."
  []
  (vec (concat (jpn-recognition-entities) (jpn-recognizes-tx))))

;; ---------------------------------------------------------------------------
;; Coverage report -- matches this fleet's `vcfund.facts/coverage` /
;; `marketentry.facts/coverage` honesty convention.
;; ---------------------------------------------------------------------------

(defn coverage-report
  "Honest coverage summary: how many of the 188 real country entities have
  an actual researched :country/recognizes-method edge set (today: JPN
  only). Never implies uncovered == 'no requirements'."
  []
  {:total-countries (count countries)
   :researched (count researched-jurisdictions)
   :researched-jurisdictions (vec (sort researched-jurisdictions))
   :not-yet-researched (- (count countries) (count researched-jurisdictions))
   :note (str "kotoba.ekyc.jurisdiction-graph R0: " (count researched-jurisdictions)
              " of " (count countries) " real cloud-itonami-iso3166-* country "
              "repos have a researched eKYC regulatory-method linkage "
              "(JPN, from kotoba.ekyc/method-catalog's real 犯収法施行規則 "
              "citations). Every other country entity exists (the graph is "
              "structurally complete and queryable across all 188) but "
              "carries ZERO :country/recognizes-method edges -- that absence "
              "means 'not yet researched', never 'no requirements'. 188, not "
              "223: see the `country-rows` docstring above for the verified "
              "reconciliation.")})

;; ---------------------------------------------------------------------------
;; Example Datalog queries -- IDENTICAL syntax against a Datomic peer or a
;; DataScript conn (both support this query vector shape); see the test
;; namespace for both actually running against a real DataScript db.
;; ---------------------------------------------------------------------------

(def queries
  {:jpn-recognized-methods
   '[:find ?method-id ?ial ?basis
     :where
     [?c :country/iso3166-alpha3 "JPN"]
     [?c :country/recognizes-method ?r]
     [?r :recognition/method ?m]
     [?r :recognition/legal-basis ?basis]
     [?m :ekyc-method/id ?method-id]
     [?m :ekyc-method/ial-level ?ial]]

   :countries-and-coverage-status
   '[:find ?iso3 ?status
     :where
     [?c :country/iso3166-alpha3 ?iso3]
     [?c :country/coverage-status ?status]]

   :researched-country-count
   '[:find (count ?c) .
     :where
     [?c :country/coverage-status :researched]]

   :not-yet-researched-country-count
   '[:find (count ?c) .
     :where
     [?c :country/coverage-status :not-yet-researched]]

   :individual-methods-jpn-recognizes
   '[:find ?method-id ?label
     :where
     [?c :country/iso3166-alpha3 "JPN"]
     [?c :country/recognizes-method ?r]
     [?r :recognition/method ?m]
     [?m :ekyc-method/id ?method-id]
     [?m :ekyc-method/subject :individual]
     [?m :ekyc-method/label ?label]]})
