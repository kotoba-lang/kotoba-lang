(ns authentication.schema
  "The canonical schema for everything this library persists, written once in
  Datomic's installation tx-data dialect.

  A real Datomic connection transacts these forms as-is;
  `langchain.db/schema-from-tx-data` converts them to the DataScript-style map
  the in-memory store reads. Neither host restates the attributes in its own
  dialect, which is how the two copies used to drift.

  Identity attributes are declared unique so an entity keeps its identity
  across transactions. `:db/id` alone never did that: a string `:db/id` is a
  *tempid*, scoped to one transaction, so persisting the same decision twice
  produced two entities rather than one upsert -- in real Datomic as much as
  here."
  (:refer-clojure :exclude [identity])
  (:require [langchain.db :as db]))

(def authn
  "Authentication requests, the factors offered against them, and the
  decisions issued. Timestamps are ISO-8601 strings (`:authn.factor/at`,
  `:authn.factor-request/created-at`, `:authn.decision/issued-at`)."
  [{:db/ident :authn.factor/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity
    :db/doc "Caller-assigned identity of one factor result."}
   {:db/ident :authn.factor/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor/ok? :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor/subject :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor/evidence-ref :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor/assurance :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor/at :db/valueType :db.type/string :db/cardinality :db.cardinality/one}

   {:db/ident :authn.factor-request/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :authn.factor-request/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor-request/subject :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor-request/challenge-ref :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor-request/purpose :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :authn.factor-request/created-at :db/valueType :db.type/string :db/cardinality :db.cardinality/one}

   {:db/ident :authn.decision/request-id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity
    :db/doc "The authentication request this decision answers, and the identity of the decision itself -- one decision per request is the replay invariant."}
   {:db/ident :authn.decision/subject :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :authn.decision/decision :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :authn.decision/level :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :authn.decision/factors :db/valueType :db.type/ref :db/cardinality :db.cardinality/many
    :db/doc "The factor entities this decision aggregated -- a reference, so the factors are reachable from the decision rather than copied into it as bare ids."}
   {:db/ident :authn.decision/issued-at :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])

(def identity
  "Durable identity state: users, the external identities linked to them, the
  tenants they belong to, and their memberships. Composite tuple keys give
  `(provider, subject)` and `(user, tenant)` a single uniqueness constraint
  instead of an application-level check."
  [{:db/ident :identity.user/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.user/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :identity.user/created-at :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :identity/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :identity/provider-subject :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :identity/key :db/valueType :db.type/tuple
    :db/tupleAttrs [:identity/provider :identity/provider-subject]
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity/user :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :identity/email :db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/index true}
   {:db/ident :identity/email-verified? :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
   {:db/ident :identity/created-at :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :identity.tenant/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.tenant/did :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.membership/user :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :identity.membership/tenant :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :identity.membership/role :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :identity.membership/permissions :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many}
   {:db/ident :identity.membership/key :db/valueType :db.type/tuple
    :db/tupleAttrs [:identity.membership/user :identity.membership/tenant]
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}

   {:db/ident :identity.session/id :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :identity.session/token-digest :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one :db/unique :db.unique/identity
    :db/doc "SHA-256 digest of an opaque session token. Raw credentials never enter Datomic."}
   {:db/ident :identity.session/user :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/tenant :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/application :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/created-at :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/expires-at :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one :db/index true}
   {:db/ident :identity.session/revoked? :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/revoked-at :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/user-agent-hash :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :identity.session/ip-prefix :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])

(def tx-data
  "Everything, for a host installing the whole schema at once."
  (vec (concat authn identity)))

(def authn-map
  "`authn` in the map dialect `langchain.db` reads."
  (db/schema-from-tx-data authn))

(def map-schema
  "`tx-data` in the map dialect `langchain.db` reads."
  (db/schema-from-tx-data tx-data))
