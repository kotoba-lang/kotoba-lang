(ns connector.validate
  "Structural validation of a connector descriptor. Pure: returns a vector of
  problem maps `{:connector/severity :error|:warn :connector/code …
  :connector/id … :connector/msg …}` so a caller decides how to surface them.
  `valid?` is true iff there are no :error-level problems.

  Deliberately checks the two things a connector can get wrong silently:

    - a tool that declares no scopes. It looks fine, works during development
      under a broad grant somebody else requested, and fails at the provider
      the first time a host computes consent from the enabled set.
    - a repository name that does not follow from `:connector/origin-domain`.
      The origin plane is a mechanical function of the authority's real domain
      (ADR-2608040100), so a mismatch is a lookup that never happened."
  (:require [clojure.string :as str]
            [connector.model :as m]))

(defn- problem [severity code id msg]
  {:connector/severity severity :connector/code code
   :connector/id id :connector/msg msg})

(def ^:private auth-kinds #{:oauth2 :bearer :none :url-credential})

(defn- validate-auth [id auth]
  (let [ps (transient [])
        kind (:connector.auth/kind auth)]
    (cond
      (nil? auth)
      (conj! ps (problem :error :auth/missing id
                         (str id " declares no :connector/auth — say :none explicitly")))

      (not (auth-kinds kind))
      (conj! ps (problem :error :auth/unknown-kind id
                         (str id " has unknown auth kind " (pr-str kind))))

      (= :oauth2 kind)
      (do
        (doseq [k [:connector.auth/authorization-endpoint :connector.auth/token-endpoint]]
          (when (str/blank? (str (get auth k)))
            (conj! ps (problem :error :auth/no-endpoint id
                               (str id " oauth2 profile has no " (name k))))))
        (doseq [k [:connector.auth/authorization-endpoint :connector.auth/token-endpoint]]
          (let [v (str (get auth k))]
            (when (and (seq v) (not (str/starts-with? v "https://")))
              (conj! ps (problem :error :auth/insecure-endpoint id
                                 (str id " " (name k) " is not https"))))))
        (when-not (contains? auth :connector.auth/pkce?)
          (conj! ps (problem :error :auth/pkce-undeclared id
                             (str id " does not say whether the provider verifies PKCE")))))

      (= :bearer kind)
      (when (str/blank? (str (:connector.auth/token-env auth)))
        (conj! ps (problem :error :auth/no-token-env id
                           (str id " bearer profile names no token env var"))))

      (= :url-credential kind)
      (when (str/blank? (str (:connector.auth/url-env auth)))
        (conj! ps (problem :error :auth/no-url-env id
                           (str id " url-credential profile names no url env var")))))
    (persistent! ps)))

(defn- validate-tool [id tool-key tool]
  (let [ps (transient [])
        name (:connector/name tool)]
    (when (not= tool-key name)
      (conj! ps (problem :error :tool/name-mismatch id
                         (str id " tool key " (pr-str tool-key)
                              " disagrees with :connector/name " (pr-str name)))))
    (when-not (#{:read :write} (:connector/effect tool))
      (conj! ps (problem :error :tool/no-effect id
                         (str id " tool " name " declares no :read/:write effect"))))
    (let [schema (:connector/input-schema tool)]
      (when (and schema (nil? (get schema :type)))
        (conj! ps (problem :error :tool/schema-no-type id
                           (str id " tool " name " input-schema has no :type"))))
      (when (and (= "object" (get schema :type))
                 (not (map? (get schema :properties))))
        (conj! ps (problem :error :tool/schema-bad-properties id
                           (str id " tool " name " input-schema :properties is not a map"))))
      (when (= "object" (get schema :type))
        (let [props (set (keys (get schema :properties)))]
          (doseq [r (get schema :required [])]
            (when-not (props r)
              (conj! ps (problem :error :tool/schema-missing-required id
                                 (str id " tool " name " requires " (pr-str r)
                                      " which is not in :properties"))))))))
    (persistent! ps)))

(defn problems
  "Structural problems with `descriptor`, as a vector."
  [descriptor]
  (let [id (:connector/id descriptor)
        ps (transient [])
        ;; Per-tool scopes are demanded only where the provider HAS scopes.
        ;; Notion's authorization endpoint takes no scope parameter at all, and
        ;; requiring a descriptor to invent them would make it claim a
        ;; narrowing it cannot perform.
        scoped? (m/scoped? descriptor)]
    (when (str/blank? (str id))
      (conj! ps (problem :error :connector/no-id nil "connector has no :connector/id")))
    (when (and (seq (str id)) (not (re-matches #"[a-z0-9]+(\.[a-z0-9-]+)+" (str id))))
      (conj! ps (problem :error :connector/bad-id id
                         (str "connector id " (pr-str id)
                              " is not reverse-DNS (lower-case, dot-separated)"))))
    (when (str/blank? (str (:connector/name descriptor)))
      (conj! ps (problem :error :connector/no-name id "connector has no :connector/name")))
    (when (str/blank? (str (:connector/origin-domain descriptor)))
      (conj! ps (problem :warn :connector/no-origin-domain id
                         (str id " records no :connector/origin-domain — the repository"
                              " name cannot be checked against the origin plane"))))
    (when (empty? (:connector/tools descriptor))
      (conj! ps (problem :warn :connector/no-tools id (str id " declares no tools"))))
    (doseq [p (validate-auth id (:connector/auth descriptor))] (conj! ps p))
    (doseq [[k t] (:connector/tools descriptor)
            p (validate-tool id k t)]
      (conj! ps p))
    ;; Per-tool scopes only mean anything when the profile actually has scopes.
    (if scoped?
      (doseq [t (m/tools descriptor)]
        (when (empty? (:connector/scopes t))
          (conj! ps (problem :error :tool/no-scopes id
                             (str id " tool " (:connector/name t)
                                  " declares no scopes; consent computed from the"
                                  " enabled set would not request anything for it")))))
      ;; And the reverse: a scope on a provider that has none is a claim the
      ;; consent screen would print and the provider would ignore.
      (doseq [t (m/tools descriptor)]
        (when (seq (:connector/scopes t))
          (conj! ps (problem :error :tool/unexpected-scopes id
                             (str id " tool " (:connector/name t)
                                  " declares scopes, but this provider has no"
                                  " scope mechanism"))))))
    (persistent! ps)))

(defn errors [descriptor]
  (filterv #(= :error (:connector/severity %)) (problems descriptor)))

(defn valid? [descriptor]
  (empty? (errors descriptor)))

(defn origin-prefix
  "The origin plane's prefix for a registrable domain: reverse the labels and
  join with '-'. google.com => com-google. One-way on purpose — a name cannot
  be parsed back into a domain (ADR-2608040100)."
  [domain]
  (when (seq (str domain))
    (->> (str/split (str domain) #"\.") reverse (str/join "-"))))

(defn name-conformant?
  "Whether `repo-name` is an admissible origin-plane name for this descriptor:
  equal to the derived prefix, or the prefix followed by '-' and a subject.

  Returns nil — not false — when no origin domain is recorded. Absence is
  UNVERIFIED, never CONFORMANT; a caller that treats nil as a pass is making
  the same mistake origin-domains.edn warns about."
  [descriptor repo-name]
  (when-let [prefix (origin-prefix (:connector/origin-domain descriptor))]
    (boolean (or (= repo-name prefix)
                 (str/starts-with? (str repo-name) (str prefix "-"))))))
