(ns kotoba.site.catalog
  "Tagging the public repository catalogue.

  Two facts with two different strengths, kept apart on purpose:

  - A **plane** tag is read off the repository name using the workspace's own
    naming rule (ADR-2608040100), so it is exactly as reliable as the name.
  - A **domain** tag is matched against the name and the GitHub description.
    That is evidence, and evidence runs out: 1,160 of the 2,215 repositories
    have no description at all, so those can only be matched on their name.

  Therefore `tag-all` reports how many repositories it could NOT tag, and the
  page prints that number. A classifier that assigns every repository a
  nearest-guess tag returns the same shape as one that is actually right, and
  the reader has no way to tell which they are looking at. Untagged has to be
  a visible outcome, not a silent fallback to the closest label."
  (:require [kotoba.lang.text :as str]))

(defn- compiled-domains [taxonomy]
  (for [d (:domains taxonomy)]
    (assoc d :re (mapv re-pattern (:patterns d)))))

(defn- name-prefix
  "First `-`-separated segment, or nil for a bare name."
  [n]
  (when-let [i (str/index-of n "-")] (subs n 0 i)))

(defn plane-of
  "The single plane tag for a name, or nil. A name has exactly one plane, so
  the first matching rule wins and the order in the taxonomy is the decision."
  [taxonomy repo-name]
  (let [p (name-prefix repo-name)]
    (some (fn [{:keys [id prefixes]}]
            (when (and p (some #(= % p) prefixes)) id))
          (:planes taxonomy))))

(defn domains-of
  "Every domain tag whose evidence appears in the name or description.

  Matching is on a single lowercased haystack of `name` + `description` with
  word-boundary patterns — substring matching turns `os` into a hit on
  `kotobase`, `protocols` and `compose` alike (measured: 203 false hits)."
  [domains repo]
  (let [hay (str/lower (str (:name repo) " " (:description repo)))]
    (into [] (keep (fn [{:keys [id re]}]
                     (when (some #(re-find % hay) re) id))
                   domains))))

(defn tag-all
  "`{:repos [...] :counts {tag n} :untagged n :described n}`.

  Existing GitHub topics are carried through untouched under `:topics`: the
  catalogue never overwrites a topic a human set, it only proposes the ones a
  repository does not have."
  [taxonomy catalog]
  (let [ds (compiled-domains taxonomy)
        repos (mapv (fn [r]
                      (let [plane (plane-of taxonomy (:name r))
                            doms (domains-of ds r)]
                        (assoc r
                               :plane plane
                               :domains doms
                               :tags (vec (concat (when plane [plane]) doms)))))
                    (:repos catalog))]
    {:repos repos
     :counts (frequencies (mapcat :tags repos))
     :untagged (count (remove #(seq (:tags %)) repos))
     :described (count (filter :description repos))
     :total (count repos)}))

(defn topic-of
  "The GitHub repository topic a tag corresponds to. One vocabulary for the
  site filter and for the org's topics, so the two cannot drift apart."
  [taxonomy tag]
  (some (fn [{:keys [id topic]}] (when (= id tag) topic))
        (concat (:planes taxonomy) (:domains taxonomy))))

(defn label-of [taxonomy tag]
  (some (fn [{:keys [id label]}] (when (= id tag) label))
        (concat (:planes taxonomy) (:domains taxonomy))))
