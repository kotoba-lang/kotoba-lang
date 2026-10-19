(ns nfs.mount
  "The MOUNT protocol, version 3 (RFC 1813 Appendix I) — how a client turns
  a path into the file handle every later NFS call starts from.

  It is a separate RPC program from NFS itself (100005, not 100003) and
  historically a separate daemon on a separate port, which is why
  `mount_nfs` has both `port=` and `mountport=`. Here they are two programs
  on one listener, because there is no reason for two sockets and one fewer
  port is one fewer thing to explain to a client.

  `EXPORT` matters more than its size suggests: it is what Finder's Connect
  to Server calls to show the list of shares before anything is mounted. An
  export list that is empty produces a dialog that says the server has
  nothing to offer."
  (:require [xdr.core :as xdr]))

(def ^:const program 100005)
(def ^:const version 3)

(def ^:const MNT3_OK 0)
(def ^:const MNT3ERR_NOENT 2)
(def ^:const MNT3ERR_ACCES 13)
(def ^:const MNT3ERR_NOTDIR 20)

(def ^:const AUTH-NONE 0)
(def ^:const AUTH-SYS 1)

(def dirpath [:string 1024])
(def fhandle3 [:opaque* 64])

(def mount-res
  [:union [:enum]
   {MNT3_OK [:struct [:handle fhandle3] [:auth-flavors [:array* [:enum] 16]]]
    :default :void}])

(def export-node
  "The export list is a linked list on the wire — optional data all the way
  down — for the same reason READDIR's is: the server may stop at any
  point without having counted first."
  [:struct [:dir dirpath] [:groups [:array* [:string 255] 64]]])

(defn- export-list-bytes [exports]
  (let [parts (mapcat (fn [e]
                        [(xdr/encode [:struct [:present [:bool]]] {:present true})
                         (xdr/encode export-node
                                     {:dir (:dir e) :groups (or (:groups e) [])})])
                      exports)]
    (xdr/->bytes
     (mapcat (fn [p] (map #(bit-and (int %) 0xff) (seq p)))
             (concat parts
                     [(xdr/encode [:struct [:present [:bool]]] {:present false})])))))

(defn handlers
  "`exports` is `[{:dir \"/drive\" :handle <bytes>}]`.

  A mount request for a path that is not exported answers `MNT3ERR_NOENT`
  rather than silently offering the first export — a client that mounts
  something it did not ask for is worse than a client that fails."
  [exports]
  (let [by-dir (into {} (map (juxt :dir identity)) exports)]
    {0 (fn [_ _ _] (xdr/->bytes []))

     1 (fn [_ bs at]                                       ; MNT
         (let [path (xdr/decode-value dirpath bs at)
               e (get by-dir path)]
           (if e
             (xdr/encode mount-res
                         {:disc MNT3_OK
                          :value {:handle (:handle e)
                                  :auth-flavors [AUTH-NONE AUTH-SYS]}})
             (xdr/encode mount-res {:disc MNT3ERR_NOENT :value nil}))))

     3 (fn [_ _ _] (xdr/->bytes []))                       ; UMNT

     5 (fn [_ _ _] (export-list-bytes exports))}))         ; EXPORT

(defn program-map
  [exports] {program {version (handlers exports)}})
