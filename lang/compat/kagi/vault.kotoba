(ns kagi.vault
  "vault のデータモデル(Datomic/DataScript 互換 schema と entity shape)。
  平文・鍵はここに**入らない**。graph に載るのは暗号文参照と公開素材のみ:
    - item     : title(任意暗号化) + 暗号文 blob の CID + nonce + version
    - wrap     : owner 用 DEK wrap(VMK 由来 KEK)
    - grant    : 共有先 hybrid 公開鍵への DEK encapsulate(PQC)
    - ledger   : append-only ハッシュ鎖 + hybrid 署名(改竄検知)")

(def schema
  {:item/id          {:db/unique :db.unique/identity}
   :item/compartment {}
   :item/category    {}                       ; 任意: kagitaba.category の正準 keyword(非機微、平文index)
   :item/title-enc   {}                       ; 任意: 暗号化タイトル(base64)
   :item/cid         {}                       ; SealedBlockStore content id
   :item/nonce       {}                       ; AES-GCM nonce(base64)
   :item/version     {}
   :item/key-epoch   {}
   :item/key-created-at {}
   :item/vmk-epoch {}
   :item/rotation-event {}
   :item/wrap        {}                       ; {:nonce :wrapped} owner DEK wrap
   :item/created-by  {}

   :grant/id         {:db/unique :db.unique/identity}
   :grant/item       {:db/valueType :db.type/ref}
   :grant/recipient  {}                       ; recipient did:key
   :grant/envelope   {}                       ; {:kem-ct :nonce :wrapped} PQC share
   :grant/cap        {}                       ; :viewer | :member
   :grant/expiry     {}
   :grant/revoked    {}
   :grant/key-epoch  {}

   :member/did       {:db/unique :db.unique/identity}
   :member/role      {}                       ; :owner | :member | :viewer
   :member/kem-pub   {}                       ; hybrid KEM 公開鍵(base64)
   :member/sign-pub  {}                       ; Ed25519+ML-DSA 公開鍵(base64)
   :member/sign-key  {}                       ; key lifecycle metadata
   :member/kem-key   {}                       ; key lifecycle metadata

   :ledger/seq       {:db/unique :db.unique/identity}
   :ledger/prev-hash {}                       ; ハッシュ鎖
   :ledger/hash      {}
   :ledger/sig       {}                       ; hybrid 署名(base64)
   :ledger/fact      {}})                      ; EDN(enc 可)

(def write-ops
  "副作用を伴う vault 操作。"
  #{:item/create :item/update :item/rotate :share/grant :share/revoke})

(def read-ops
  #{:item/reveal :item/list})

(defn item-aad
  "AEAD の AAD には item-cid を縛り、ブロックの取り違え/差し替えを検知。"
  ^bytes [item-id]
  (.getBytes (str "kagi/item/" item-id) "UTF-8"))
