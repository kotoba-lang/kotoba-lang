(ns valueflows.mapping
  "Where each Valueflows term already lives in this workspace, and where it
   does not.

   Why this is data and not prose: the workspace reached most of REA
   independently and under other names — MRP explosion, mutual credit,
   content-addressed receipts. Written as a paragraph, that correspondence
   rots the first time a repository moves. Written as data it can be queried,
   counted, and shown to be incomplete.

   Status vocabulary, and the distinctions worth keeping:

     :vf-native    implemented here, in Valueflows terms
     :mapped       another repository provides it; the mapping is exact
     :partial      provides part of it; :gap says which part
     :adjacent     structurally similar, DIFFERENT OBJECT. Not a mapping.
     :false-friend shares a word with Valueflows, means something else.
                   Recorded so nobody wires them together by name.
     :absent       nothing here does this

   THIS NAMESPACE CANNOT VERIFY ITSELF. The repositories it cites are west
   projects outside this repository's tree, so a check that read them would be
   a gate whose input is absent — which reports a pass by not looking
   (ADR-2608136000). Each entry therefore carries `:evidence`: the file that
   was read and the date it was read. Evidence is a citation, not a
   measurement, and is labelled as one."
  (:require [valueflows.vocabulary :as vocab]))

(def measured-at "2026-08-15")

(def classes
  "Valueflows class -> where its content already lives."
  {:Agent
   {:status :mapped
    :where ["orgs/cloud-itonami/cloud-itonami-lei-*" "orgs/com-junkawasaki/org-gleif-projections"]
    :join :company/lei
    :note "Legal entities carry an LEI, which is already the plane's join key."
    :evidence "CLAUDE.md ADR-2607252000 dataset list; :company/lei joins market-intel"}

   :EconomicResource
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "PLM items with BOTH registers: :erp.inventory/qty-on-hand is custody, :erp.inventory/qty-accounting is ownership. kotoba.plm.registers derives which one a movement touches from this vocabulary rather than hardcoding it, so consignment stock (transferCustody) and title sales in transit (transferAllRights) are now representable. MRP nets against custody."
    :was-gap :no-separate-accounting-quantity
    :gap-closed "2026-08-15"
    :evidence "kotoba-lang/plm src/kotoba/plm/registers.cljc + schema.cljc :erp.inventory/qty-accounting; 31 tests / 118 assertions, including an integration assertion through receive-goods! and complete-production! that fails when the posting is broken"}

   :ResourceSpecification
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "Item master; make/buy/phantom classification."
    :evidence "src/kotoba/plm/item.cljc make-buy, read 2026-08-15"}

   :Process
   {:status :partial
    :where ["orgs/kotoba-lang/plm"]
    :note "Routing gives work centres and operations, i.e. process specifications with cost."
    :gap :no-process-network-scheduling
    :evidence "src/kotoba/plm/routing.cljc work-center/operation/process-cost, read 2026-08-15"}

   :Recipe
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "The effective MBOM is a recipe: parent -> children with quantities, effective-dated."
    :evidence "src/kotoba/plm/item.cljc mbom-children, read 2026-08-15"}

   :Plan
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :note "MPS is time-phased demand, which is what a Plan carries."
    :evidence "src/kotoba/plm/mps.cljc mps-for-item/approved-demand-for-item, read 2026-08-15"}

   :EconomicEvent
   {:status :partial
    :where ["orgs/cloud-itonami/credits" "orgs/kotoba-lang/engi"]
    :note "ENGI transfers are two-party signed events with equal debit and credit — vf:transfer with accountingEffect decrementIncrement, in a ledger that also proves net supply is zero."
    :gap :only-the-transfer-action
    :evidence "orgs/cloud-itonami/credits/README.md constitutional boundary, read 2026-08-15"}

   :Commitment
   {:status :vf-native
    :was :absent
    :vf-native "valueflows.commitment"
    :note "A promise with an action, a quantity, a due date and a fulfilment link. Settled by `vf:fulfills` from economic events. `vf:finished` is kept distinct from fulfilled, per upstream: it means no more will be done, so a commitment closed at 60% is :closed-short rather than an error or a success."
    :note-on-absence "Before 2026-08-15 nothing here recorded a promise and then answered whether it was kept. Observed events existed (ENGI transfers, PLM postings) and planned quantities existed (MPS), but not the link between them."}

   :Intent
   {:status :vf-native
    :was :absent
    :vf-native "valueflows.commitment/satisfaction"
    :note "A wanted or offered flow, satisfied by commitments or events through `vf:satisfies`. `vf:minimumQuantity` is checked separately from the ratio: below the stated floor is its own fact, not merely low progress."}

   :Proposal
   {:status :vf-native
    :was :absent
    :vf-native "valueflows.proposal"
    :note "Offers and requests with what is expected in return. A one-sided proposal (gift, grant, mutual aid) is described, not rejected -- upstream's \"sometimes with what is expected in return\" is load-bearing. vf:unitBased gates multiplication: a price-list line scales into commitments, a one-off offer refuses to."}

   :Agreement
   {:status :vf-native
    :was :partial
    :was-gap :not-reciprocal-commitments
    :gap-closed "2026-08-15"
    :vf-native "valueflows.proposal/agreement-balance"
    :note "Primary commitments (vf:stipulates) against reciprocal ones (vf:stipulatesReciprocal), each settled through valueflows.commitment. Answers the question an agreement exists for: which party has delivered while the other still owes. Repaid and written-off are kept apart -- :both-closed? covers both, :both-fulfilled? only the first -- because a single :settled? flag would collapse a loan repaid with a loan lost."
    :note-on-cloud-itonami-app "Contracts and tenant connections in cloud-itonami-app still bind parties without being modelled as reciprocal bundles; that repository has not been migrated. This entry records that the SHAPE now exists, not that the app uses it."
    :evidence "orgs/cloud-itonami/cloud-itonami-app/docs/adr/0008-business-is-the-join-of-five-planes.md (Contracts view), read 2026-08-15"}

   :Unit
   {:status :partial
    :note "Units appear per-repository; there is no shared om-2 unit registry."
    :gap :no-shared-unit-registry}})

(def false-friends
  "Same word, different thing. The point of naming these is that a future
   integration reads the list instead of joining on the word."
  {:cloud-itonami-commitment-ledger
   {:their-word "commitment"
    :their-meaning "a LOAN commitment: a lender's governed offer of loan-based support to a self-registered business, V1 lending only, equity language is a hard governor violation"
    :vf-meaning "vf:Commitment — a planned economic flow that a future EconomicEvent fulfils"
    :do-not "treat the ledger's records as vf:Commitment instances"
    :sharper-since "2026-08-15: a real vf:Commitment now exists in valueflows.commitment, so the two can actually be wired together by mistake. Before, the confusion was only verbal."
    :the-right-shape "A loan is an vf:Agreement: the principal is a primary commitment (vf:stipulates) and the repayments are reciprocal ones (vf:stipulatesReciprocal). valueflows.proposal/agreement-balance then answers whether the lender is exposed, and keeps repaid apart from written-off. The ledger's own record is still not a vf:Commitment -- this is how the same business fact would be expressed in Valueflows terms."
    :evidence "orgs/cloud-itonami/cloud-itonami-commitment-ledger/README.md, read 2026-08-15"}

   :engi-economic-event
   {:their-word "economic event"
    :their-meaning "a signed mutual-credit transfer in the ENGI journal"
    :vf-meaning "vf:EconomicEvent — any action on a resource: produce, consume, use, work, transfer, ..."
    :do-not "read the ENGI journal as a complete vf:EconomicEvent log; it carries one action out of nineteen"
    :evidence "orgs/cloud-itonami/credits/test/credits/engi_r3_test.clj:120, read 2026-08-15"}

   :system-dynamics-flow
   {:their-word "flow"
    :their-meaning "a continuous RATE between stocks, integrated over time (Forrester/Meadows), in kotoba-lang/dynamics and org-oasis-open-xmile"
    :vf-meaning "a discrete typed event between agents and resources"
    :do-not "equate a vf network flow with an XMILE flow; both are directed graphs and neither converts to the other"
    :evidence "orgs/kotoba-lang/dynamics/README.md, read 2026-08-15"}})

(def algorithms
  "The eight network-based algorithms from https://www.valueflo.ws/algorithms/overview/
   and their standing here."
  {:dependent-demand
   {:status :mapped
    :where ["orgs/kotoba-lang/plm"]
    :vf-native "valueflows.algorithms.dependent-demand"
    :note "Multi-level MBOM explosion, netted against on-hand, raising purchase orders for the shortfall. Reached from the MRP-II lineage, not from Valueflows; upstream describes dependent demand as exactly this."
    :evidence "src/kotoba/plm/mrp.cljc gross-requirements/plan/mrp-run!, read 2026-08-15"}

   :critical-path
   {:status :vf-native
    :was :absent
    :vf-native "valueflows.algorithms.critical-path"
    :note "Nothing here scheduled a process network. plm has routing master data but no forward pass; murakumo's task/plan is greedy least-filled placement over hosts, which is a different problem."
    :evidence "grep -in 'critical.?path' over plm, murakumo, cloud-itonami on 2026-08-15 returned only latency prose and test-coverage prose"}

   :value-rollup
   {:status :partial
    :where ["orgs/kotoba-lang/plm"]
    :vf-native "valueflows.algorithms.value-rollup"
    :note "plm rolls cost through the BOM. A vf rollup implodes any value attribute over the recipe tree, including effort."
    :gap :cost-only
    :evidence "src/kotoba/plm/cost.cljc rolled-cost, read 2026-08-15"}

   :value-equation
   {:status :adjacent
    :where ["orgs/cloud-itonami/credits"]
    :vf-native "valueflows.algorithms.value-equation"
    :note "ENGI recognises contribution — including care work — through bounded multi-role Commons issuance and relational credit lines. That is a different mechanism from distributing an income over contributions, and the closest thing here in intent."
    :evidence "orgs/cloud-itonami/credits/README.md runnable social kernel, read 2026-08-15"}

   :track-and-trace
   {:status :adjacent
    :vf-native "valueflows.algorithms.track-trace"
    :where ["manifest/projection-verify.cljs" "orgs/kotoba-lang/kotobase"]
    :note "The workspace traces ARTEFACTS: content-addressed lineage, receipts binding input hash to output hash. Strong machinery, different object — it does not follow a resource through the events that consumed and produced it."
    :evidence "CLAUDE.md projection-verify contract; ADR-2608039700"}

   :provenance
   {:status :adjacent
    :vf-native "valueflows.algorithms.track-trace"
    :where ["orgs/kotoba-lang/ontology"]
    :note "kotoba-ontology stamps facts with :ontology/source, an ingestion provenance registry. Resource provenance in the vf sense is the constituent-input closure of a resource."
    :evidence "orgs/kotoba-lang/ontology/README.md, read 2026-08-15"}

   :cash-flow
   {:status :partial
    :where ["orgs/cloud-itonami/credits" "70-tools/bmc"]
    :vf-native "valueflows.algorithms.cash-flow"
    :note "Settlement and business metrics exist. Neither presents inflows and outflows on one timeline, historical and forecast, over resources other than money."
    :gap :money-only-and-not-time-phased
    :evidence "orgs/cloud-itonami/credits/src/credits/engi/netting.clj; CLAUDE.md BMC section"}

   :network-flows
   {:status :different-formalism
    :where ["orgs/kotoba-lang/dynamics" "orgs/kotoba-lang/org-oasis-open-xmile"]
    :note "System dynamics: stocks, continuous rates, Meadows leverage points. Both are directed graphs with flows; the semantics do not convert. Keep both."
    :evidence "orgs/kotoba-lang/dynamics/README.md, read 2026-08-15"}})

;; ── queries over the mapping ──────────────────────────────────────────────

(defn by-status [m status] (into (sorted-set) (keep (fn [[k v]] (when (= status (:status v)) k)) m)))

(defn gaps
  "Everything not yet expressed in Valueflows terms, with what is missing.
   A caller wanting 'what is left to do' reads this, not the prose."
  []
  (into (sorted-map)
        (keep (fn [[k v]]
                (when (contains? #{:absent :partial :adjacent :different-formalism} (:status v))
                  [k (select-keys v [:status :gap :note :where])])))
        (merge classes algorithms)))

(defn summary []
  {:measured-at measured-at
   :classes {:total (count classes)
             :by-status (into (sorted-map)
                              (map (fn [[s ks]] [s (count ks)]))
                              (group-by #(:status (val %)) classes))}
   :algorithms {:total (count algorithms)
                :by-status (into (sorted-map)
                                 (map (fn [[s ks]] [s (count ks)]))
                                 (group-by #(:status (val %)) algorithms))}
   :false-friends (into (sorted-set) (keys false-friends))
   ;; stated, not implied: the vocabulary has more classes than are mapped
   :vocabulary-classes (count (vocab/classes))
   :classes-considered (count classes)
   :coverage-note "classes-considered < vocabulary-classes: the rest are unexamined, which is not the same as absent"})
