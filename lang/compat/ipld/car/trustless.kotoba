(ns ipld.car.trustless
  "CAR response core for trustless IPLD selection and pathing.

  HTTP parsing, redirects, Cache-Control, Range, and status mapping belong to
  the gateway. This namespace owns the verifiable payload: run a bounded
  selector through CID-verified blocks, then encode exactly the touched blocks
  as a root-first CARv1."
  (:require [ipld.car :as car]
            [ipld.graph :as graph]
            [ipld.selector :as selector]))

(def content-type "application/vnd.ipld.car")

(defn selection-car
  "Execute `selector` at `root-cid` and encode the proof blocks as CARv1.
  Returns traversal matches/stats plus CAR bytes and offsets."
  [get-fn root-cid selector limits]
  (let [selection (graph/select-blocks get-fn root-cid selector limits)
        archive (car/encode {:roots [root-cid] :blocks (:blocks selection)})]
    (assoc selection
           :content-type content-type
           :car archive)))

(defn path-car
  "Resolve a logical Data Model path through Links and return a CAR proof.
  A missing path fails closed; this function does not claim an absence proof."
  [get-fn root-cid path limits]
  (let [result (selection-car get-fn root-cid (graph/path-selector path) limits)]
    (when (empty? (:matches result))
      (throw (ex-info "trustless path did not resolve"
                      {:type :ipld/path-not-found
                       :root root-cid :path (vec path)})))
    (assoc result :path (vec path) :value (get-in result [:matches 0 :value]))))

;; ── replay: the verifier side ────────────────────────────────────────────────
;;
;; `selection-car` is the producer. It runs a selector against a live store and
;; emits the blocks it touched. `replay-selection` is its counterpart, and the
;; two are not symmetric: the producer may trust its own store, and the
;; verifier may trust nothing -- not the archive's roots, not its frame CIDs,
;; and not that it contains everything the traversal needs.
;;
;; What makes that worth a separate function rather than a note in a docstring
;; is that all three of those failures are silent by default:
;;
;;   - `car/decode` does NOT verify. It keys blocks by the CID the frame
;;     DECLARES. Measured 2026-09-06: a CAR whose frame claims CID A while
;;     carrying B's bytes decodes without error, and the bytes returned under A
;;     recompute to B. `car/verify-block` is a separate step, and its own
;;     docstring names a CAR fetched over HTTP as exactly the boundary that
;;     requires it. (CARv2's `read-frame` does verify; v1 `decode` does not.
;;     Do not carry a habit from one to the other.)
;;   - a CAR's `roots` header is a claim by whoever wrote the archive. Believing
;;     it makes the verifier replay the graph the SENDER chose.
;;   - a CAR missing a block the traversal needs is not a shorter answer. It is
;;     no answer, and it must not return one.
;;
;; So every one of those ends in a throw. A `:complete? false` in a returned map
;; is a value a caller can drop on the floor, and the whole point of this
;; namespace is that an incomplete retrieval must not read as a completed one.

(defn- car-blocks!
  "Decode `car-bytes` and bind the graph to the caller's root, not the archive's.

  The roots header says where the sender thinks the graph starts. Replaying
  from it verifies that the sender's archive is internally consistent, which is
  not the question -- the question is whether it proves something about the
  root the CALLER asked for."
  [car-bytes expected-root]
  (let [{:keys [roots blocks]} (car/decode car-bytes)]
    (when-not (some #{expected-root} roots)
      (throw (ex-info "trustless: CAR roots do not include the expected root"
                      {:type :ipld/car-root-mismatch
                       :expected-root expected-root :roots (vec roots)})))
    blocks))

(defn replay-selection
  "Replay `selector-bytes` from `expected-root` against ONLY the blocks in
  `car-bytes`, and return what the traversal proves.

  `selector-bytes` is canonical DAG-CBOR rather than the executable form on
  purpose: it is what a verifier can be handed, hash, and agree with a producer
  about. Decoding it is also where an unsupported form is rejected -- the
  supported subset is whatever `ipld.selector/decode` admits, and a selector
  outside it throws `:ipld/invalid-selector` instead of quietly matching less.

  `limits` needs positive `:max-blocks`, `:max-bytes`, `:max-depth` and
  `:max-matches`. Exhausting one throws `:ipld/resource-limit`, because
  reaching a work limit is incomplete retrieval and not a smaller success.

  Returns `{:root :selector :matches :loaded :unused :stats}`. `:loaded` is the
  blocks the traversal actually needed, in root-first order; `:unused` is what
  the archive carried but the traversal never reached. Unused blocks are not
  an error -- logical selection legitimately loads shared blocks that contain
  other rows -- but they are reported because they are unverified: only blocks
  the traversal touched were rehashed, so an archive is not evidence about
  bytes nobody asked for.

  Failure modes, all typed apart and all thrown:

      :ipld/car-root-mismatch   the archive is about a different graph
      :ipld/invalid-selector    a form outside the supported subset
      :ipld/missing-block       the archive omits a block the traversal needs
      :ipld/cid-mismatch        a frame's bytes are not what its CID claims
      :ipld/resource-limit      a budget was exhausted mid-traversal

  Deduplication is by CID for bytes and limit accounting only. The same block
  reached again by a different path is decoded and traversed again, because
  identity of bytes is not identity of traversal state -- suppressing the
  second visit would drop matches the selector genuinely selects."
  [car-bytes expected-root selector-bytes limits]
  (let [blocks (car-blocks! car-bytes expected-root)
        selector-data (selector/decode selector-bytes)
        ;; `get-fn` returns nil for a CID the archive does not carry, which is
        ;; what `select-blocks` turns into `:ipld/missing-block`. It performs no
        ;; verification itself: `select-blocks` rehashes every block it fetches
        ;; via `ipld/get-verified-block`, so substituted bytes fail there. That
        ;; is deliberate -- verifying here as well would rehash the same bytes
        ;; twice and, worse, would make it look as though the guarantee lives
        ;; in this closure rather than in the fetch path.
        selection (graph/select-blocks #(get blocks %) expected-root
                                       selector-data limits)
        loaded (into #{} (map :cid) (:blocks selection))]
    (assoc selection
           :loaded (:blocks selection)
           :unused (vec (remove loaded (keys blocks))))))

(defn verify-selection-car
  "Replay a CAR produced by `selection-car` and confirm it proves its matches.

  The round trip this closes: `selection-car` says which blocks it touched, and
  this re-derives that set from the archive alone. A producer that under-sends
  fails with `:ipld/missing-block` rather than returning fewer matches."
  [car-bytes expected-root selector limits]
  (replay-selection car-bytes expected-root (selector/encode selector) limits))
