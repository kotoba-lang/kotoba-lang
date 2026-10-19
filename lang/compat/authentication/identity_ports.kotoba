(ns authentication.identity-ports
  "Storage ports for the Identity BaaS. Implementations may use Datomic, KV,
  Durable Objects or test memory stores without changing provider/domain code.")

(defprotocol IIdentityStore
  (-find-user-by-identity [store provider provider-subject])
  (-find-user-by-verified-email [store normalized-email])
  (-create-account! [store account-tx])
  (-link-identity! [store user-id identity now])
  (-user-view [store user-id]))

(defprotocol IEphemeralStore
  (-put-once! [store key value ttl-seconds])
  (-get-value [store key])
  (-consume! [store key expected-version])
  (-delete! [store key]))

(defprotocol ISessionStore
  "Durable session records. Implementations persist only opaque-token digests,
  never bearer/cookie credentials. OAuth state and one-time challenges remain
  in IEphemeralStore because they have different consistency/lifecycle needs."
  (-create-session! [store session-record])
  (-find-session-by-digest [store token-digest])
  (-revoke-session! [store token-digest revoked-at]))

(defn identity-store? [value] (satisfies? IIdentityStore value))
(defn ephemeral-store? [value] (satisfies? IEphemeralStore value))
(defn session-store? [value] (satisfies? ISessionStore value))
