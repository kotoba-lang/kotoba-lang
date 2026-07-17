(ns kotoba.lang.package-contract-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ed25519.core :as ed]
            [kotoba.lang.package-contract :as contract]
            [multiformats.core :as mf])
  (:import (java.security SecureRandom)
           (java.util Base64)))

(def manifest-path "lang/package-conformance/manifest.edn")

(defn read-edn
  [path]
  (edn/read-string (slurp (io/file path))))

;; lang/package-conformance/manifest.edn, lang/package.edn, and
;; lang/profile.edn are stored as Datomic/Datascript tx-data (see
;; schema.edn / scripts/edn-datomize.cljs `wrap-map-preserve-ns!`).
;; `unblob` reverses the pr-str blob-ification of non-scalar values.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

;; manifest.edn: :kotoba.lang.package.conformance/version was already
;; namespaced and kept as-is; the plain :cases key was prefixed to
;; :kotoba.lang.package.conformance/cases and blob-stringified.
(defn- reconstitute-package-conformance-manifest [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    {:kotoba.lang.package.conformance/version
     (:kotoba.lang.package.conformance/version e)
     :cases (unblob (:kotoba.lang.package.conformance/cases e))}))

;; profile.edn: every top-level key was already namespaced
;; (:kotoba.lang/*), so no key was renamed -- just unblob every value.
(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [k (unblob v)]))
        (dissoc (first tx-data) :db/id)))

;; package.edn: only :kotoba.lang.package/version was already namespaced
;; (kept as-is); every other originally-plain key (:status :summary
;; :identity :manifest :lock-entry :trust-rules :maturity) was prefixed to
;; :kotoba.lang.package/* by the same ns, so it must be stripped back to
;; plain explicitly (a blind namespace-strip would also strip :version).
(defn- reconstitute-package-edn [tx-data]
  (let [e (dissoc (first tx-data) :db/id)]
    (into {:kotoba.lang.package/version (:kotoba.lang.package/version e)}
          (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
          (dissoc e :kotoba.lang.package/version))))

(deftest package-conformance-fixtures-match-contract
  (let [manifest (reconstitute-package-conformance-manifest (read-edn manifest-path))]
    (is (= 1 (:kotoba.lang.package.conformance/version manifest)))
    (doseq [tc (:cases manifest)
            :let [data (read-edn (str "lang/package-conformance/" (:file tc)))
                  result (contract/validate-case tc data)]]
      (case (:kind tc)
        :accept
        (is (:valid? result) (:id tc))

        :expect-error
        (do
          (is (false? (:valid? result)) (:id tc))
          (is (str/includes? (:message result) (:error-contains tc)) (:id tc)))))))

(deftest cid?-genuinely-decodes-and-structurally-validates-a-cidv1
  (testing "a real CIDv1 (multiformats.core/cidv1-dag-cbor, the same function this repo's
            conformance fixtures now use) passes"
    (is (contract/cid? (mf/cidv1-dag-cbor (.getBytes "hello" "UTF-8"))))
    (is (contract/cid? (mf/cidv1-raw (.getBytes "hello" "UTF-8")))))
  (testing "the OLD naive (str/starts-with? x \"bafy\") check would have accepted every one of
            these -- they must all fail the real structural check instead"
    (is (false? (contract/cid? "bafyrepojson111111111111111111111111111111111111111111111111"))
        "contains characters (0/1/8/9) outside the base32 'b'-multibase alphabet")
    (is (false? (contract/cid? "bafynotreallyacid")) "decodes but isn't valid CID framing")
    (is (false? (contract/cid? "b")) "empty payload after the multibase prefix")
    (is (false? (contract/cid? "notbase32atall"))))
  (testing "non-CID inputs are rejected outright, not just malformed CIDs"
    (is (false? (contract/cid? "")))
    (is (false? (contract/cid? nil)))
    (is (false? (contract/cid? 42))))
  (testing "a CID with a corrupted/truncated digest (multihash declares a length the
            actual decoded bytes don't have) is rejected, not silently accepted"
    (let [real (mf/cidv1-dag-cbor (.getBytes "hello" "UTF-8"))
          truncated (subs real 0 (dec (count real)))]
      (is (false? (contract/cid? truncated))))))

(deftest profile-and-package-contract-are-machine-readable
  (let [profile (reconstitute-entity (read-edn "lang/profile.edn"))
        package (reconstitute-package-edn (read-edn "lang/package.edn"))]
    (is (= 3 (:kotoba.lang/profile-version profile)))
    (is (= :kotoba (:kotoba.lang/default-reader-target profile)))
    (is (= :m2 (get-in profile [:kotoba.lang/type-system :maturity])))
    (is (= :pending (get-in profile [:kotoba.lang/type-system :compiler-admission])))
    (is (= 1 (:kotoba.lang.package/version package)))
    (is (contains? (set (get-in package [:manifest :package-kinds :allow-kinds]))
                   :schema-contract))))

;; ---------------------------------------------------------------------------
;; Security fix 2607131500 (docs/issues/security-package-contract-conformance-
;; gap.md, F-001): signatures-error never cryptographically verified anything
;; (shape-only: non-blank :did/:sig strings + :alg = :ed25519), and :tree-cid
;; was validated only for CID-string *shape*, never by recomputing a hash of
;; real content. These tests prove the NEW checks actually fire -- each
;; adversarial case here is exactly the kind of input the OLD code would have
;; wrongly accepted.

(defn- b64 ^String [^bytes b] (.encodeToString (Base64/getEncoder) b))

(defn- rand-seed ^bytes []
  (let [b (byte-array 32)]
    (.nextBytes (SecureRandom.) b)
    b))

(defn- utf8 ^bytes [^String s] (.getBytes s "UTF-8"))

(defn- test-manifest
  "A minimal manifest that passes every OTHER package-manifest-error check,
  so each test below isolates exactly the signature/tree-cid behavior it
  exercises. TREE-CID and MANIFEST-CID are real multiformats CIDs (of
  whatever content the caller wants: doesn't have to be REPO-RID's exact
  content, just CID-shaped, to reach signatures-error / satisfy cid?)."
  [{:keys [repo-rid tree-cid manifest-cid signatures]}]
  {:kotoba.package/name "kotoba-lang/adversarial-test"
   :kotoba.package/version "0.1.0"
   :kotoba.package/repo-rid repo-rid
   :kotoba.package/source {:git-commit "0123456789abcdef0123456789abcdef01234567"
                            :tree-cid tree-cid
                            :manifest-cid manifest-cid}
   :kotoba.package/capabilities []
   :kotoba.package/dependencies []
   :kotoba.package/signatures signatures})

(def ^:private stub-repo-rid (delay (mf/cidv1-dag-cbor (utf8 "repo"))))
(def ^:private stub-tree-cid (delay (mf/cidv1-raw (utf8 "tree-content"))))

(deftest signatures-error-accepts-a-genuine-ed25519-signature
  (testing "a real Ed25519 signature, by the key the claimed :did actually
            encodes, over the manifest's own :manifest-cid, is accepted"
    (let [seed (rand-seed)
          did (ed/did-key-from-seed seed)
          manifest-cid (mf/cidv1-dag-cbor (utf8 "manifest-content"))
          sig (b64 (ed/sign seed (utf8 manifest-cid)))
          manifest (test-manifest {:repo-rid @stub-repo-rid
                                    :tree-cid @stub-tree-cid
                                    :manifest-cid manifest-cid
                                    :signatures [{:did did :alg :ed25519 :sig sig}]})]
      (is (nil? (contract/package-manifest-error manifest))))))

(deftest signatures-error-rejects-forged-signature-bytes
  (testing "the OLD shape-only check accepted ANY non-blank :sig string under ANY
            non-blank :did -- this is exactly that input (a plausible-looking did:key
            paired with garbage :sig bytes), and it must now be rejected"
    (let [did (ed/did-key-from-seed (rand-seed))
          manifest-cid (mf/cidv1-dag-cbor (utf8 "manifest-content"))
          manifest (test-manifest {:repo-rid @stub-repo-rid
                                    :tree-cid @stub-tree-cid
                                    :manifest-cid manifest-cid
                                    :signatures [{:did did :alg :ed25519 :sig "sig-json"}]})
          result (contract/package-manifest-error manifest)]
      (is (false? (:valid? result)))
      (is (= "signature verification failed" (:message result))))))

(deftest signatures-error-rejects-signature-from-the-wrong-signer
  (testing "a well-formed, genuinely-generated Ed25519 signature -- just made with a
            DIFFERENT key than the one the claimed :did encodes (an attacker signing
            with their own key while claiming a victim's did:key) -- must fail, not
            merely 'obviously garbage bytes'"
    (let [victim-did (ed/did-key-from-seed (rand-seed))
          attacker-seed (rand-seed)
          manifest-cid (mf/cidv1-dag-cbor (utf8 "manifest-content"))
          forged-sig (b64 (ed/sign attacker-seed (utf8 manifest-cid)))
          manifest (test-manifest {:repo-rid @stub-repo-rid
                                    :tree-cid @stub-tree-cid
                                    :manifest-cid manifest-cid
                                    :signatures [{:did victim-did :alg :ed25519 :sig forged-sig}]})
          result (contract/package-manifest-error manifest)]
      (is (false? (:valid? result)))
      (is (= "signature verification failed" (:message result))))))

(deftest signatures-error-rejects-signature-retargeted-to-different-content
  (testing "a real signature by the claimed signer, but over a DIFFERENT
            :manifest-cid than the one this manifest actually declares (the manifest
            was edited/retargeted after signing) -- must fail"
    (let [seed (rand-seed)
          did (ed/did-key-from-seed seed)
          signed-cid (mf/cidv1-dag-cbor (utf8 "original-content"))
          declared-cid (mf/cidv1-dag-cbor (utf8 "tampered-content"))
          sig (b64 (ed/sign seed (utf8 signed-cid)))
          manifest (test-manifest {:repo-rid @stub-repo-rid
                                    :tree-cid @stub-tree-cid
                                    :manifest-cid declared-cid
                                    :signatures [{:did did :alg :ed25519 :sig sig}]})
          result (contract/package-manifest-error manifest)]
      (is (false? (:valid? result)))
      (is (= "signature verification failed" (:message result))))))

(deftest manifest-content-tampering-slips-through-manifest-error-alone
  (testing "DOCUMENTED, NOT A REGRESSION (see package-manifest-error's docstring):
            signatures-error binds a valid signature to the manifest's self-declared
            :manifest-cid field, not to the manifest's actual content. A manifest whose
            OTHER fields (here: :kotoba.package/capabilities) are mutated AFTER
            signing, with :manifest-cid left untouched, still has a genuinely valid
            signature over that (untouched) CID -- package-manifest-error ALONE has no
            way to detect the mutation, since it never recomputes :manifest-cid from
            real content. This test exists so that fact is asserted and visible, not
            silently relied upon: full content-binding requires composing this with a
            SEPARATE check that recomputes :manifest-cid (kotoba-lang/kotoba's
            `kotoba.package-admission/manifest-integrity-error` does this, and its own
            test suite proves a mismatched :manifest-cid IS rejected there)."
    (let [seed (rand-seed)
          did (ed/did-key-from-seed seed)
          manifest-cid (mf/cidv1-dag-cbor (utf8 "manifest-content"))
          sig (b64 (ed/sign seed (utf8 manifest-cid)))
          legit (test-manifest {:repo-rid @stub-repo-rid
                                :tree-cid @stub-tree-cid
                                :manifest-cid manifest-cid
                                :signatures [{:did did :alg :ed25519 :sig sig}]})
          tampered (assoc legit :kotoba.package/capabilities ["kotoba://cap/host/fs-read"])]
      (is (nil? (contract/package-manifest-error legit))
          "the legitimately-signed, unmutated manifest passes, as a baseline")
      (is (nil? (contract/package-manifest-error tampered))
          "mutating :capabilities after signing, without touching :manifest-cid, still
           passes package-manifest-error ALONE -- this is the documented gap, not an
           assertion that it's safe in isolation"))))

(deftest tree-cid-error-accepts-real-matching-content
  (let [content (utf8 "real source tree bytes")
        declared (mf/cidv1-raw content)]
    (is (nil? (contract/tree-cid-error declared content)))))

(deftest tree-cid-error-rejects-tampered-content
  (testing "the OLD `cid?`-only check validates CID *shape*, never real content -- a
            structurally valid CID that simply doesn't match the actual tree content
            passed that check. tree-cid-error must catch what cid? structurally cannot."
    (let [content (utf8 "real source tree bytes")
          declared (mf/cidv1-raw content)
          tampered (utf8 "TAMPERED source tree bytes")
          result (contract/tree-cid-error declared tampered)]
      (is (false? (:valid? result)))
      (is (= "tree cid does not match tree content" (:message result))))))

(deftest tree-cid-error-requires-real-content
  (testing "absent tree content is a distinct, explicit rejection -- never a silent pass"
    (let [result (contract/tree-cid-error (mf/cidv1-raw (utf8 "x")) nil)]
      (is (false? (:valid? result)))
      (is (= "tree content required to verify tree cid" (:message result))))))

(deftest package-manifest-error-tree-bytes-opt-in-does-real-content-check
  (let [tree-content (utf8 "real source tree bytes")
        tree-cid (mf/cidv1-raw tree-content)
        seed (rand-seed)
        did (ed/did-key-from-seed seed)
        manifest-cid (mf/cidv1-dag-cbor (utf8 "manifest-content"))
        sig (b64 (ed/sign seed (utf8 manifest-cid)))
        manifest (test-manifest {:repo-rid @stub-repo-rid
                                  :tree-cid tree-cid
                                  :manifest-cid manifest-cid
                                  :signatures [{:did did :alg :ed25519 :sig sig}]})]
    (testing "omitting :tree-bytes (the 1-arity -- every existing caller and fixture)
              keeps the original shape-only :tree-cid behavior unchanged"
      (is (nil? (contract/package-manifest-error manifest))))
    (testing "supplying the REAL matching tree content is accepted"
      (is (nil? (contract/package-manifest-error manifest {:tree-bytes tree-content}))))
    (testing "supplying TAMPERED tree content -- a mismatch the shape-only cid? check
              could never catch -- is rejected"
      (let [result (contract/package-manifest-error manifest {:tree-bytes (utf8 "TAMPERED bytes")})]
        (is (false? (:valid? result)))
        (is (= "tree cid does not match tree content" (:message result)))))))

(deftest lockfile-error-tree-bytes-by-dep-opt-in-does-real-content-check
  (let [tree-content (utf8 "real dep tree bytes")
        tree-cid (mf/cidv1-raw tree-content)
        dep {:dep/name "kotoba-lang/json"
             :dep/version "0.1.0"
             :dep/repo-rid @stub-repo-rid
             :dep/commit "0123456789abcdef0123456789abcdef01234567"
             :dep/tree-cid tree-cid
             :dep/manifest-cid (mf/cidv1-dag-cbor (utf8 "manifest-content"))
             :dep/signers ["did:key:z6Mkexample"]
             :dep/capabilities []}
        lock {:kotoba.lock/version 1 :deps [dep]}
        tc {:declared-capabilities []}]
    (testing "omitting :tree-bytes-by-dep (the 2-arity -- every existing caller) keeps
              the original shape-only :dep/tree-cid behavior unchanged"
      (is (nil? (contract/lockfile-error lock tc))))
    (testing "supplying the REAL matching tree content for this dep is accepted"
      (is (nil? (contract/lockfile-error
                 lock tc {:tree-bytes-by-dep {"kotoba-lang/json" tree-content}}))))
    (testing "supplying TAMPERED tree content for this dep is rejected"
      (let [result (contract/lockfile-error
                    lock tc {:tree-bytes-by-dep {"kotoba-lang/json" (utf8 "TAMPERED")}})]
        (is (false? (:valid? result)))
        (is (= "tree cid does not match tree content" (:message result)))))))
