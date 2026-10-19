(ns kotoba.protocol.ref
  "Public resource identity — git / nix / holochain / unison, not UnixFS.

  Identity is a hash. A name is a mutable pointer to a hash. A link is a
  hash inside a node. A path after a CID is a selector over that node,
  and this protocol does not put selectors in the public URI.

  Analogues (what we take, not what we copy):

    git blob/commit SHA     → ipfs://{cidv1}
    git ref (heads/main)    → ipns://{k51}
    git `HEAD:README.md`    → IPLD map key / host walk (not URI)
    nix store hash          → ipfs://{cidv1}
    flake.lock / output     → ipns://{k51} or :kotoba.app/latest
    holochain entry hash    → ipfs://{cidv1}
    holochain DNA/role name → local alias, not a path after the hash
    unison term hash        → ipfs://{cidv1}
    unison names            → :kotoba.app/id, IPLD map key

  Canonical URI grammar (exactly two forms):

    ipfs://{cidv1-base32}
    ipns://{k51}

  Rejected in the URI: path, query, fragment, CIDv0 (`Qm…`). HTTPS is a
  retrieval location (L4 projection / git remote analogue), parsed by
  `kotoba.protocol.app/parse-embed-url`, not by this ns.

  Two link kinds (`kotoba.protocol.layers/link-kinds`): IPLD/git-tree
  merkle links live *inside* the node (parent CID changes). Holochain
  CreateLink / datom assertions are signed metadata (parent CID does not
  change). Neither is a URI path. The leaf already has its own CID.

  Gateway `/ipfs/{cid}` is the IPFS HTTP namespace for 'get this block',
  not a UnixFS directory walk. `gateway-url` emits that projection; it
  is not identity."
  (:require [kotoba.lang.text :as str]))

(defn cidv1-base32?
  "Public-ref CID: CIDv1 multibase base32 (`b…`). CIDv0 (`Qm…`) is a
  historical L0 shape (`vocab/cid?`) and is not a public URI label."
  [s]
  (boolean (and (string? s) (re-matches #"b[a-z2-7]{20,}" s))))

(defn ipns-id?
  "Key-derived IPNS name (libp2p-key, base36 `k51…`)."
  [s]
  (boolean (and (string? s) (re-matches #"k51[a-z0-9]{50,}" s))))

(defn- extra-error
  "Anything after the id is a selector/location, not identity."
  [rest]
  (cond
    (str/includes? rest "/") :path-not-identity
    (str/includes? rest "?") :query-not-identity
    (str/includes? rest "#") :fragment-not-identity
    :else nil))

(defn parse
  "Canonical public ref → {:kind :cid|:ipns …} | {:error …}.

  Does not accept HTTPS, path, query, fragment, or CIDv0."
  [s]
  (cond
    (not (string? s)) {:error :not-a-string}

    (str/starts-with? s "ipfs://")
    (let [rest (subs s 7)]
      (if-let [err (extra-error rest)]
        {:error err :value rest}
        (if (cidv1-base32? rest)
          {:kind :cid :scheme :ipfs :cid rest}
          {:error :invalid-cid :value rest})))

    (str/starts-with? s "ipns://")
    (let [rest (subs s 7)]
      (if-let [err (extra-error rest)]
        {:error err :value rest}
        (if (ipns-id? rest)
          {:kind :ipns :scheme :ipns :name rest}
          {:error :invalid-ipns-name :value rest})))

    :else {:error :unknown-scheme :value s}))

(defn emit
  "Parsed canonical ref → URI string. Unknown kind → nil."
  [{:keys [kind cid name]}]
  (case kind
    :cid (str "ipfs://" cid)
    :ipns (str "ipns://" name)
    nil))

(defn canonical-ref-uri?
  [s]
  (boolean (and (string? s) (nil? (:error (parse s))))))

(defn gateway-url
  "L4 retrieval projection of a parsed ref. Not identity.

  `/ipfs/{cid}` / `/ipns/{k51}` here are the IPFS HTTP gateway namespaces
  (get this block / resolve this pointer). No path is appended."
  [parsed gateway]
  (when (and (string? gateway) (map? parsed) (not (:error parsed)))
    (let [kind (or (:kind parsed)
                   (case (:scheme parsed)
                     :ipfs :cid
                     :ipns :ipns
                     nil))]
      (case kind
        :cid (str gateway "/ipfs/" (:cid parsed))
        :ipns (str gateway "/ipns/" (:name parsed))
        nil))))
