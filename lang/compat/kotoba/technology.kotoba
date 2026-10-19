(ns kotoba.technology
  "The technology registry, portable.

  ## Why this is `.cljc` and not `.clj`

  One line made this namespace JVM-only — `(slurp (io/resource …))` — and
  through it `kotoba.iso3166`, which requires this, and everything that
  would have wanted either. That is the wrong way round for this workspace:
  the runtime order is kotoba-wasm → clojurewasm → ClojureScript → nbb, with
  the JVM last. A registry of facts has no business being the thing that
  pins a consumer to a runtime.

  ## No runtime file access at all

  There is no portable `io/resource`, and the obvious `:cljs` substitute —
  reading `resources/<path>` relative to the working directory — is right
  only while this library is the root project. The registry is therefore
  compiled in, as the generated `kotoba.technology.embedded`, projected from
  `resources/kotoba/technology/registry.edn` by `tools/gen-embedded.cljs`.
  The EDN stays the thing a human edits; the projection is what the code
  reads; `--check` refuses to let them drift.

  **A registry handed in as nil still propagates as nil**, and that has not
  changed: `(into {} …)` over nil yields `{}`, so `by-id` would answer an
  empty index and `get-technology` nil for every id — a caller passing
  nothing must not receive a complete-looking lookup over no data."
  (:require [clojure.set :as set]
            [kotoba.technology.embedded :as embedded]))

(def registry-resource "kotoba/technology/registry.edn")

(defn- tx-data? [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn- unnamespace-item [entity]
  (into {} (map (fn [[k v]] [(keyword (name k)) v])) (dissoc entity :db/id)))

(defn- reconstitute-registry [tx]
  (let [by-id (into {} (map (juxt :db/id identity)) tx)
        root (first tx)
        tech-refs (:kotoba.registry/technologies root)
        technologies (mapv #(unnamespace-item (get by-id %)) tech-refs)]
    (-> root
        (dissoc :db/id :kotoba.registry/technologies)
        (assoc :technologies technologies))))

(defn registry
  "The technology registry.

  Reads `kotoba.technology.embedded`, a GENERATED projection of
  `resources/kotoba/technology/registry.edn`, and touches no file at
  runtime.

  ## Why not read the resource

  It did, until 2026-08-18, through a `:cljs` branch that read
  `resources/<path>` relative to the PROCESS's working directory. That is
  right when this library is the root project and wrong the moment it is a
  dependency — measured the same day: `kotoba.iso3166`'s suite under nbb
  produced **159 errors**, every one of them this registry coming back nil
  because nbb's cwd was iso3166's root and not this one. A portability fix
  that only works when you are the root is not one.

  So there is no read, and therefore no read that can fail. The `nil`
  discipline that guarded the old path is gone with the path — keeping a
  `readable?` that can only answer true would be a check with no failure
  mode. What replaced it is `tools/gen-embedded.cljs --check`, which guards
  the failure mode that now exists: the projection drifting from the EDN a
  human edits. `the-embedded-registry-matches-the-edn` asserts it in the
  suite too, and **fails rather than passes when it cannot read the EDN** —
  a check that could not run must not report what a check that ran and
  found nothing reports."
  []
  (let [content embedded/registry-tx]
    (if (tx-data? content)
      (reconstitute-registry content)
      content)))

(defn technologies
  ([] (technologies (registry)))
  ([reg] (:technologies reg)))

(defn by-id
  "Technologies indexed by `:id`, or **nil** when the registry could not be
  read.

  `(into {} …)` over nil yields `{}`, which is why this needs saying: an
  unreadable registry would otherwise index to an empty map, `get-technology`
  would answer nil for every id, and `resolve-stack` would report every id as
  unknown — three plausible-looking answers, all of them meaning only that a
  file was missing. Caught here by the test that redefines `registry` to nil,
  after the same class had already been found fourteen times elsewhere in
  this workspace."
  ([] (by-id (registry)))
  ([reg] (when-let [ts (technologies reg)]
           (into {} (map (juxt :id identity)) ts))))

(defn get-technology
  ([id] (get-technology (registry) id))
  ([reg id] (get (by-id reg) id)))

(defn capability-map
  "Return {technology-id matching-capabilities} for the requested capabilities."
  ([capabilities] (capability-map (registry) capabilities))
  ([reg capabilities]
   (when-let [ts (technologies reg)]
     (let [wanted (set capabilities)]
       (->> ts
          (keep (fn [{:keys [id capabilities]}]
                  (let [matches (set/intersection wanted capabilities)]
                      (when (seq matches) [id matches]))))
            (into {}))))))

(defn stack
  "Return ordered technology records for ids. Throws on an id this
  registry does not provide.

  Strict on purpose: a caller asking for a specific set of technologies
  and getting a shorter list back would silently proceed with less than
  it asked for. Callers that are ASKING WHETHER a technology exists want
  `resolve-stack` instead."
  ([ids] (stack (registry) ids))
  ([reg ids]
   (let [idx (by-id reg)]
     (mapv #(or (get idx %)
                (throw (ex-info "unknown technology" {:id %})))
           ids))))

(defn resolve-stack
  "Partition `ids` into the technology records this registry provides and
  the ids it does not.

  `{:resolved [record ..] :unknown [id ..]}`.

  This exists because a consumer registry naming a technology nobody has
  built yet is a NORMAL state, not a programming error — it is a
  backlog entry. `stack` throwing turned every such entry into an
  exception that took down the whole query, so a caller could not even
  ask what was missing. Measured 2026-08-03: 29 of the 651 industries in
  `kotoba-lang/industry` named at least one technology absent from here,
  and `execution-plan` was unusable for every one of them."
  ([ids] (resolve-stack (registry) ids))
  ([reg ids]
   (let [idx (by-id reg)]
     {:resolved (into [] (keep idx) ids)
      :unknown  (into [] (remove idx) ids)})))

(defn satisfies-capabilities?
  ([ids capabilities] (satisfies-capabilities? (registry) ids capabilities))
  ([reg ids capabilities]
   (let [provided (->> (stack reg ids) (mapcat :capabilities) set)]
     (set/subset? (set capabilities) provided))))
