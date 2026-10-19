(ns i18n.plural
  "CLDR-*lite* plural-category rules: enough to route `{:select :count ...}`
  message maps to the right branch for a curated set of languages, without
  pulling in a full CLDR data dependency (no `Intl.PluralRules` either —
  that's JS-only and this namespace must run identically on the JVM).

  This is intentionally a simplified subset of the real CLDR plural rules
  (integer arithmetic only; CLDR's `i`/`v`/`f`/`t` operand distinctions for
  decimals are not modeled). Extend `family-of` / add a family below as new
  languages need exact rules; unlisted languages default to `:other-only`,
  which is always safe (every catalog is required to provide an `:other`
  branch).")

(defn- other-only [_n] :other)

(defn- one-other
  "en/de/nl/sv/da/it/es/pt/el/hu/fi/tr/... : 1 -> one, else other."
  [n]
  (if (= n 1) :one :other))

(defn- french
  "fr/pt(-PT informally grouped here too): 0 or 1 -> one, else other."
  [n]
  (if (contains? #{0 1} n) :one :other))

(defn- slavic-ru
  "ru/uk/sr/hr/bs (CLDR 'slavic' family, simplified to integers)."
  [n]
  (let [mod10  (mod n 10)
        mod100 (mod n 100)]
    (cond
      (and (= mod10 1) (not= mod100 11)) :one
      (and (<= 2 mod10 4) (not (<= 12 mod100 14))) :few
      :else :many)))

(defn- polish
  "pl: same shape as slavic-ru but `few`/`many` boundaries differ slightly."
  [n]
  (let [mod10  (mod n 10)
        mod100 (mod n 100)]
    (cond
      (= n 1) :one
      (and (<= 2 mod10 4) (not (<= 12 mod100 14))) :few
      :else :many)))

(defn- arabic
  "ar: the one language in default scope with all six CLDR categories."
  [n]
  (let [mod100 (mod n 100)]
    (cond
      (= n 0) :zero
      (= n 1) :one
      (= n 2) :two
      (<= 3 mod100 10)  :few
      (<= 11 mod100 99) :many
      :else :other)))

(defn- hebrew
  "he: simplified — CLDR's real rule also special-cases round multiples of
  10 as :many; not modeled here."
  [n]
  (cond
    (= n 1) :one
    (= n 2) :two
    :else :other))

(def ^:private family->fn
  {:other-only other-only
   :one-other  one-other
   :french     french
   :slavic-ru  slavic-ru
   :polish     polish
   :arabic     arabic
   :hebrew     hebrew})

(def ^:private lang->family
  {:ja :other-only :ko :other-only :zh :other-only :vi :other-only
   :th :other-only :id :other-only :ms :other-only
   :en :one-other :de :one-other :nl :one-other :sv :one-other
   :da :one-other :it :one-other :es :one-other :pt :one-other
   :el :one-other :hu :one-other :fi :one-other :tr :one-other
   :fr :french
   :ru :slavic-ru :uk :slavic-ru :sr :slavic-ru :hr :slavic-ru :bs :slavic-ru
   :pl :polish
   :ar :arabic
   :he :hebrew})

(defn family-of
  "Plural-rule family keyword for `lang` (a locale keyword, e.g. :ar). Falls
  back to `:other-only` for anything not in `lang->family`."
  [lang]
  (get lang->family lang :other-only))

(defn category
  "CLDR-lite plural category (:zero/:one/:two/:few/:many/:other) for integer
  `n` under `lang`. Non-integer `n` is truncated toward zero — good enough
  for count-style UI strings (\"N items\"); exact fractional CLDR rules are
  out of scope."
  [lang n]
  (let [f (get family->fn (family-of lang) other-only)]
    (f (long n))))
