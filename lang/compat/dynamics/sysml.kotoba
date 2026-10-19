(ns dynamics.sysml
  "A generic Source -> Conversion -> Sink structural skeleton, real OMG
   SysML v2 (via kotoba-lang/org-omg-sysmlv2) -- the STRUCTURAL counterpart
   to `dynamics.xmile`'s DYNAMIC (stock/flow/simulated) model of the same
   kind of system. `dynamics.xmile/acquisition-model` answers 'how does the
   stock evolve over time'; this namespace answers 'what is this system
   actually made of, and what governance/design requirements is it
   accountable to' -- the same real acquisition-funnel loops this catalog
   keeps analyzing (etzhayyim's F2, or any of `loop-archetypes`) have BOTH
   a dynamic and a structural description, and conflating the two (e.g.
   writing a requirement as a comment in dynamics.core instead of a real,
   traceable SysML RequirementUsage) is exactly the kind of ad-hoc
   substitute this correction cycle exists to stop doing.

   Callers MUST require `org-omg-sysmlv2`'s own `sysml.model`/
   `sysml.validate` on their classpath and pass the needed fns in via
   `sysml-model-ns`, the same host-injects-dependencies pattern as
   `dynamics.xmile`."
  )

(defn acquisition-system
  "Build a real SysML v2 model: three PartDefinitions (Source, Conversion,
   Sink -- named via `source-name`/`conversion-name`/`sink-name`) nested
   inside a top-level System PartUsage, connected Source->Conversion->Sink
   via ConnectionUsages, with an optional RequirementDefinition/Usage
   attached (satisfied by the System) for each map in `requirements`
   ({:name ... :text ... :req-id ...})."
  [sysml-model-ns {:keys [system-name source-name conversion-name sink-name requirements]
                    :or {requirements []}}]
  (let [{:keys [model add-element part-definition part-usage nest
                connection-definition connection-usage
                requirement-definition requirement-usage with-subject
                satisfy-requirement-usage]}
        sysml-model-ns
        source-usage-name (str source-name "-usage")
        conversion-usage-name (str conversion-name "-usage")
        sink-usage-name (str sink-name "-usage")
        system-usage-name (str system-name "-usage")
        base
        (-> (model system-name)
            (add-element (part-definition system-name))
            (add-element (part-definition source-name))
            (add-element (part-definition conversion-name))
            (add-element (part-definition sink-name))
            (add-element (part-usage source-usage-name source-name))
            (add-element (part-usage conversion-usage-name conversion-name))
            (add-element (part-usage sink-usage-name sink-name))
            (add-element (-> (part-usage system-usage-name system-name)
                              (nest source-usage-name)
                              (nest conversion-usage-name)
                              (nest sink-usage-name)))
            (add-element (connection-definition (str system-name "-Flow")))
            (add-element (connection-usage (str source-name "-to-" conversion-name)
                                            (str system-name "-Flow")
                                            [source-usage-name conversion-usage-name]))
            (add-element (connection-usage (str conversion-name "-to-" sink-name)
                                            (str system-name "-Flow")
                                            [conversion-usage-name sink-usage-name])))]
    (reduce
     (fn [m {:keys [name text req-id]}]
       (let [def-name (str name "-def")
             usage-name (str name "-usage")]
         (-> m
             (add-element (requirement-definition def-name (cond-> {} text (assoc :sysml/text text)
                                                                    req-id (assoc :sysml/req-id req-id))))
             (add-element (-> (requirement-usage usage-name def-name)
                               (with-subject system-usage-name)))
             (add-element (satisfy-requirement-usage (str name "-satisfy") usage-name system-usage-name)))))
     base
     requirements)))

;; ---------------------------------------------------------------------------
;; fleet-model / add-fleet-requirement: a second, distinct generic shape
;; ---------------------------------------------------------------------------

(defn fleet-model
  "Build a real SysML v2 model of a different generic shape than
   `acquisition-system` above: not one 3-part funnel, but ONE
   PartDefinition (`member-definition-name`) with MANY real PartUsage
   instances (`members`, a seq of {:name ...}), all nested inside a
   top-level Fleet PartUsage. This is the right shape for any real,
   already-observed population of same-kind things this catalog needs to
   reason about member-by-member (e.g. cloud-itonami's 797 per-ISIC/ISCO-
   code blueprint repos) -- `acquisition-system` does not fit that case at
   all (it has exactly 3 fixed roles, not N interchangeable members)."
  [sysml-model-ns {:keys [fleet-name member-definition-name members]}]
  (let [{:keys [model add-element part-definition part-usage nest]} sysml-model-ns
        member-usage-name #(str % "-usage")
        fleet-usage-name (str fleet-name "-usage")
        member-names (map :name members)
        fleet-usage (reduce (fn [u n] (nest u (member-usage-name n)))
                             (part-usage fleet-usage-name fleet-name)
                             member-names)]
    (as-> (model fleet-name) m
      (add-element m (part-definition fleet-name))
      (add-element m (part-definition member-definition-name))
      (reduce (fn [m n] (add-element m (part-usage (member-usage-name n) member-definition-name)))
              m member-names)
      (add-element m fleet-usage))))

(defn add-fleet-requirement
  "Attach ONE shared RequirementDefinition (`name`/`text`/`req-id`) to an
   already-built `m` (from `fleet-model` or otherwise), with one
   RequirementUsage per entry in `members` ({:name ... :satisfied? _}) --
   subject = that member's own PartUsage, so each member's compliance is
   individually traceable rather than collapsed into one system-wide
   pass/fail.

   `members` may be a real SUBSET of the fleet's full member list, for a
   requirement that legitimately does not apply to every member (a member
   left out of this call gets no RequirementUsage for THIS requirement at
   all -- 'not applicable' stays structurally distinct from 'measured and
   failing', the same not-defaulting-to-zero discipline `resources/
   entities-seed.edn` already holds for stocks).

   A SatisfyRequirementUsage is added only where `:satisfied?` is true --
   never fabricated, and never omitted to make a number look better; an
   unsatisfied member simply has a RequirementUsage with no corresponding
   satisfy relationship, which `sysml.validate` leaves structurally valid
   (unsatisfied requirements are not themselves a structural error -- see
   README/`sysml.validate` scope)."
  [sysml-model-ns m {:keys [name text req-id members]}]
  (let [{:keys [add-element requirement-definition requirement-usage
                with-subject satisfy-requirement-usage]} sysml-model-ns
        def-name (str name "-def")
        base (add-element m (requirement-definition def-name
                                                      (cond-> {} text (assoc :sysml/text text)
                                                              req-id (assoc :sysml/req-id req-id))))]
    (reduce
     (fn [m {member-name :name satisfied? :satisfied?}]
       (let [subject-usage (str member-name "-usage")
             req-usage-name (str member-name "--" name "-usage")
             m (add-element m (-> (requirement-usage req-usage-name def-name)
                                   (with-subject subject-usage)))]
         (if satisfied?
           (add-element m (satisfy-requirement-usage (str member-name "--" name "-satisfy")
                                                       req-usage-name subject-usage))
           m)))
     base
     members)))
