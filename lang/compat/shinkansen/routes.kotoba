(ns shinkansen.routes
  "Declared route tree — the routing increment (maturity axis A).

  shinkansen does NOT derive routes from a filesystem. The route tree is
  DATA the app declares: a nested structure of path segments, layout fns
  and document entries. The edge resolves a request path to the document
  CID by walking the tree — layouts nest by composition, and every node
  carries its own CID so a route tree is itself addressable content.

  Why data, not filesystem: a route tree you can diff is a route tree you
  can review, version and content-address. The layout chrome the app
  already owns (app-kotoba-cloud's site-layout + app-sidebar) maps onto
  ONE root node's :layout fn; nested sections add child nodes.

  What a path IS (SPEC §1.7, 2026-09-16): a name. `resolve-path` turns a
  name into WHAT it names — the artifact — and the captured params into a
  :query. The ok result therefore also carries an `:invocation`
  (`shinkansen.invoke` envelope, minus principal and grant, which the
  host adds from the request): the same document is reachable through
  N names on N origins, and none of them is its identity or its
  authority. The path never reaches the authorizer.

  Pure .cljc. The matcher does not touch the network; the host owns
  fetch/publish. Fail-closed: an unresolvable path returns
  {:ok false :reason ...} with the deepest matched segment named."
  (:require [clojure.string :as str]))

(defn- segmentize
  "Split a request path into segments: /apps/security → [apps security].
  Trailing slashes and the empty root collapse away."
  [path]
  (->> (str/split (str path) #"/")
       (remove str/blank?)
       vec))

(defn- match-node
  "Match one tree node against the remaining segments.
  Returns {:node … :consumed n :params {…}} or nil. Every pattern segment
  is consumed — a `:param` segment captures, a static one must equal."
  [node segs]
  (let [pattern (->> (str/split (str (:path node)) #"/") (remove str/blank?) vec)
        n (count pattern)]
    (when (>= (count segs) n)
      (loop [i 0 params {}]
        (cond
          (>= i n) {:node node :consumed n :params params}
          (str/starts-with? (nth pattern i) ":")
          (recur (inc i) (assoc params (keyword (subs (nth pattern i) 1)) (nth segs i)))
          (= (nth pattern i) (nth segs i))
          (recur (inc i) params)
          :else nil)))))

(defn resolve-path
  "Resolve a request path against the route tree. Returns
    {:ok true :document <leaf> :layouts [layout-fn …] :params {…}
     :invocation {:artifact <leaf> :input {:kind :query :params {…}}}}
  or {:ok false :reason :no-match :deepest <last matched segment>}.
  Layout fns accumulate root-first so the host can compose them
  outside-in around the document. The walk descends while segments
  remain and answers at the node where they run out — a node's own
  :document never short-circuits a deeper path (0cd79e3 returned the root
  document for every path; its tests were required but never run)."
  [tree path]
  (let [segs (segmentize path)]
    (loop [node tree segs segs layouts [] params {} matched []]
      (let [m (match-node node segs)]
        (if-not m
          {:ok false :reason :no-match :deepest (last matched)
           :attempted (:path node)}
          (let [node (:node m)
                segs (subvec segs (:consumed m))
                params (merge params (:params m))
                matched (conj matched (:path node))
                layouts (if (:layout node) (conj layouts (:layout node)) layouts)]
            (if (empty? segs)
              (if (:document node)
                {:ok true :document (:document node)
                 :layouts layouts :params params
                 :invocation {:artifact (:document node)
                              :input {:kind :query :params params}}}
                {:ok false :reason :no-document-at-node :deepest (last matched)})
              (if-let [child (some #(when (match-node % segs) %) (:children node))]
                (recur child segs layouts params matched)
                {:ok false :reason :no-match :deepest (last matched)}))))))))

(defn- insert-document [node segs document]
  (if (empty? segs)
    (assoc node :document document)
    (let [[s & more] segs
          children (vec (or (:children node) []))
          idx (some (fn [[i c]] (when (= (:path c) s) i)) (map-indexed vector children))
          child (insert-document (if idx (nth children idx) {:path s}) more document)]
      (assoc node :children (if idx (assoc children idx child) (conj children child))))))

(defn paths->tree
  "A route tree from a flat list of documents `[{:path \"/a/b/\" :document x} …]`
  — what a static emit (or an adapter's receipts) already knows. Every
  segment becomes a node; a document sits on the node its path ends at.
  \"/\" is the root's own document. Nodes without a document are pass-
  through (resolve-path answers :no-document-at-node for them, by name).
  Trailing slashes are the same name (\"/a/b/\" = \"/a/b\")."
  [documents]
  (reduce (fn [tree {:keys [path document]}]
            (insert-document tree (segmentize path) document))
          {:path "/"}
          documents))
