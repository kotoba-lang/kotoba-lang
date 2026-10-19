(ns shinkansen.maturity
  "Where shinkansen stands against Next.js / SvelteKit (the meta-framework
  face) and shadcn / Radix (the component face), as DATA with a measured
  column — rewritten 2026-09-16 after the map had gone stale (g0–g3 still
  said 'no route tree yet' while routes.cljc had shipped).

  The distinction this map is for: DECLARED vs DRIVEN. A namespace with a
  contract and tests but no host that calls it is declared; one a running
  host or a product calls is driven. Measured 2026-09-16 over cloud-kotoba /
  net-kotobase / cloud-itonami-app / cloud-kotoba-dds (grep of
  `[shinkansen.<ns>` require forms): 5 namespaces had consumers
  (interaction 10, theme 6, audit 6, viewport 3, coscientist 1) and eleven
  had none. The reference host (shinkansen.host + shinkansen.serve, the
  reference app, host-node-check) turned 'declared' into 'driven by the
  framework's own host'; later the same day kotoba.cloud (cloud-kotoba/
  app-kotoba-cloud) became the product consumer of routes / adapter /
  invoke / state identity — the map says which is which, and :consumers
  counts products, not the framework's own host.

  Status vocabulary (one of):
    :driven-by-product   a deployed product requires it (count in :consumers)
    :driven-by-host      the reference host runs it end to end (host-node-check)
    :declared            contract + tests, nobody calls it
    :absent              nothing
    :not-by-design       deliberately not built; the reason is in :why
    :unique              the comparison frameworks have no counterpart

  Every :ns named here must exist — maturity-test pins that, so this map
  cannot drift to naming namespaces that were renamed away.")

(def measured-at "2026-09-16")

(def rows
  [{:axis :a :name :routing-layouts
    :theirs "Next: file-system app router, nested/parallel layouts. SvelteKit: file routing, layout groups."
    :ns 'shinkansen.routes :status :driven-by-product :consumers 1
    :have "declared route tree, :param segments, nested layouts composed outside-in by the host, :invocation on the ok result; paths->tree from a flat emit. kotoba.cloud (app-kotoba-cloud content-identity, 2026-09-16) resolves every request name against the tree its render emitted — that is what decides a name is a document and gets its CID"
    :gap "layouts are still the app's own site.cljk; the tree names documents, it does not render them"}
   {:axis :b :name :data-loading
    :theirs "Next: RSC async + fetch cache. SvelteKit: load (server/universal), streamed promises."
    :ns 'shinkansen.load :status :driven-by-host :consumers 0
    :have ":build / :edge load, the result is a data-CID station, hydrate bundle rides in the document"
    :gap "no streaming of load results; no product consumer"}
   {:axis :c :name :mutations
    :theirs "Next: server actions. SvelteKit: form actions with use:enhance (works without JS)."
    :ns 'shinkansen.invoke :status :driven-by-product :consumers 1
    :have "one envelope (query/action/event), the authority seam, POST /invoke on the reference host, MCP lake_dispatch as a transport; kotoba.cloud POST /v1/invoke (app-kotoba-cloud.invoke) binds it to the authn Biscuit wire and the pinned root key — the first surface on that host that verifies a presented token"
    :gap "no no-JS form fallback (a form without the runtime GETs its own page); kotoba.cloud has no state chain, so :action is 422 by name there"}
   {:axis :c :name :browser-dispatch-surface
    :theirs "React / Svelte event systems."
    :ns 'shinkansen.interaction :status :driven-by-product :consumers 10
    :have "data-action + data-params, one delegated runtime, streamRun (SSE + NDJSON), hydrate"
    :gap "no client-side reactivity: the browser fills server-rendered mounts, it does not re-render (the re-frame guest exists in shitsuke and is not wired to the DOM — ADR-2609131808 D4)"}
   {:axis :d :name :rendering-modes
    :theirs "Next: SSR / SSG / ISR / PPR / streaming. SvelteKit: SSR / prerender / CSR per route."
    :ns 'shinkansen.render :status :driven-by-host :consumers 0
    :have ":ssg verbatim, :ssr as a query (CID computed after), :isr window recorded and honoured as Cache-Control by the host"
    :gap "no streaming SSR; production deploys are static documents only"}
   {:axis :d :name :client-navigation
    :theirs "Next: Link prefetch, soft navigation. SvelteKit: preload-data, client router."
    :ns nil :status :not-by-design :consumers 0
    :have "none — one document is one CID, a link is a full navigation"
    :why "a soft navigation across CIDs is a navigation across origins on the content host ({cid}.ipfs.*); prefetch on the NAME host is possible and unbuilt"}
   {:axis :e :name :responsive-contract
    :theirs "none in core (Tailwind / CSS)."
    :ns 'shinkansen.viewport :status :driven-by-product :consumers 3
    :have "viewport meta + phone band + fixed-width audit, enforced at publish"
    :gap nil}
   {:axis :e :name :theme
    :theirs "ecosystem (next-themes)."
    :ns 'shinkansen.theme :status :driven-by-product :consumers 6
    :have "light / dark / system, head-script before paint, theme/set framework action"
    :gap "preferences are a NAME-origin property: on {cid}.ipfs.* every document is its own origin (SPEC §1.8)"}
   {:axis :e :name :i18n
    :theirs "Next: middleware-based, DIY. SvelteKit: paraglide et al."
    :ns 'shinkansen.locale :status :driven-by-product :consumers 1
    :have "cookie negotiation with :explicit / :hint / :normalize, cookie attributes serialized in one place (:domain for a preference), substitute :exact?, format; app-kotoba-cloud's locale.cljk requires it (PR 294, 2026-09-16) instead of re-deriving the chain"
    :gap "one consumer; the language switch UI is still each product's"}
   {:axis :e :name :uiux-fitness-function
    :theirs "none (vitest / playwright ecosystems)."
    :ns 'shinkansen.audit :status :driven-by-product :consumers 6
    :have "21 axes over emitted HTML, every miss named, unmeasured ≠ pass; coscientist kaizen loop"
    :gap nil :unique true}
   {:axis :f :name :dev-experience
    :theirs "dev server + HMR + error overlay (both)."
    :ns 'shinkansen.serve :status :driven-by-host :consumers 0
    :have "node:http dev server, fs.watch rebuild, live-reload stream, a failed rebuild serves its error document as a 500 that names the problem"
    :gap "live-reload, not HMR (there is no module to hot-swap, only a new document CID); no error overlay beyond the error document"}
   {:axis :g :name :deploy-targets
    :theirs "Next: Vercel / standalone. SvelteKit: adapter-auto / node / cloudflare / static."
    :ns 'shinkansen.adapter :status :driven-by-product :consumers 1
    :have "adapter shape, receipts = CID set, diff, rollback plan; kotoba.cloud's render (site.cljk write-receipts!) runs every emitted document through adapt — the publish gate first (viewport, with the shared stylesheet), then a receipt with its CID — and ships the set at /.well-known/shinkansen/receipts.json"
    :gap "the adapter writes receipts, not the bytes (Cloudflare Static Assets carry them); the two-plane publish is still the root script"}
   {:axis :g :name :error-handling
    :theirs "Next: error.tsx / not-found. SvelteKit: +error.svelte."
    :ns 'shinkansen.host :status :driven-by-host :consumers 0
    :have "404 / 500 as named error documents that pass the viewport contract; :render-failed / :load-failed named"
    :gap "no per-route error document; the error page is the framework's, not the app's"}
   {:axis :g :name :images-fonts-metadata
    :theirs "Next: next/image, next/font, metadata API. SvelteKit: enhanced-img."
    :ns nil :status :not-by-design :consumers 0
    :have "none; audit :assets-resolve checks that images resolve"
    :why "a :document is one self-contained file (ADR-2609092600) — an optimizer that serves variants from a CDN contradicts the identity rule; metadata is the app's markup"}
   {:axis :h :name :authorization
    :theirs "none in core (Auth.js / Lucia)."
    :ns 'shinkansen.invoke :status :driven-by-product :consumers 1
    :have "DID / CID / grant / effect seam; verified under a real Ed25519 Biscuit + authority lattice (verify-invoke-authority, 16 cases); the reference host binds it; kotoba.cloud binds it to the authn wire (biscuit.wire) + the pinned root public key + biscuit.authority/->grant + authority.chain — a data:read token reads the receipt set, everything else is refused by name, measured in workerd"
    :gap ":chain/append / :app/query / :app/assert are not in capability-semantics :kinds (the product maps them to kotoba://can/data:read|write, authn's vocabulary)"
    :unique true}
   {:axis :h :name :agent-surface
    :theirs "none."
    :ns 'shinkansen.mcp :status :declared :consumers 0
    :have "4 MCP tools; lake_dispatch is an invoke transport (session identity, never an argument)"
    :gap "stdio process still answers R0 (no app attached)" :unique true}
   {:axis :h :name :content-addressing
    :theirs "none."
    :ns 'shinkansen.state :status :driven-by-product :consumers 1
    :have "identity = CID for documents, data, state (chain), route tree; ETag = CID on the name host — live on kotoba.cloud / docs.kotoba.cloud (every document answers ETag = its raw CIDv1, Link rel=canonical ipfs://, 304 from the receipt alone)"
    :gap "retrievability is a release step, not a property: kotoba.cloud's receipts reach {cid}.ipfs.yataverse.com only when npm run publish:receipts is run after a release (R2 ipld/{cid}; needs the wrangler OAuth session, so not in CI; a release that changes every document costs ~1 h through the wrangler CLI, measured 2026-09-17) — verify:live's canonical-resolves case is the check. The B2 archive / IPNI announcement is not written by it. Also: chain hash covers the db value only (:prev / :event / :principal are outside it) — SPEC §1.5"
    :unique true}
   ;; ── component face: jp-go-dds + cloud-kotoba-dds vs shadcn / Radix ──
   {:axis :ui :name :components
    :theirs "shadcn ≈ 50 components."
    :ns nil :status :driven-by-product :consumers nil
    :have "jp-go-dds core.cljk 49 (DADS) + cloud-kotoba-dds 18 app patterns (measured 2026-09-16)"
    :gap nil}
   {:axis :ui :name :headless-behaviors
    :theirs "Radix ≈ 28 primitives with focus management, portals, collision-aware positioning."
    :ns nil :status :driven-by-product :consumers nil
    :have "jp-go-dds.behavior 0.2.0, 12: dialog / menu / tabs / disclosure / radiogroup / toast / combobox + popover / tooltip / select / slider / table (2026-09-16); marker-driven, one runtime; place() flips up and clamps every float; audit :behaviors-delivered knows all 12"
    :gap "hover-card / switch (a cloud-kotoba-dds pattern, not a behaviour) / scroll-area / context-menu / navigation-menu / toggle-group / menubar / toolbar; no portal (data-chrome=float + audit :chrome-layers instead)"}
   {:axis :ui :name :forms
    :theirs "shadcn: react-hook-form + zod, field errors."
    :ns 'shinkansen.form :status :declared :consumers 0
    :have "schema as data (type / required / min / max / pattern / in / message), validate coerces what a form posts and names every failing rule by field, field-attrs puts aria-invalid + aria-describedby on the control, jp-go-dds form-field :error renders the text; a schema map is a valid :validate in an actions declaration and the refusal carries :errors / :messages"
    :gap "no product renders field errors through it yet"}
   {:axis :ui :name :data-table
    :theirs "shadcn: tanstack table (sort / filter / virtualize)."
    :ns nil :status :driven-by-product :consumers nil
    :have "jp-go-dds data-behavior=table: sort by column (numeric / locale, aria-sort), filter rows, count; dds/table :sortable? / :filter"
    :gap "no virtualisation — by design, a DADS table is a page of rows"}
   {:axis :ui :name :charts
    :theirs "shadcn: recharts."
    :ns nil :status :absent :consumers 0
    :have "none in the dds" :gap "none"}])

(defn by-status []
  (reduce (fn [m r] (update m (:status r) (fnil inc 0))) {} rows))

(defn declared-only
  "The rows nobody drives — the list a reference host or a product has to
  shorten."
  []
  (filterv #(= :declared (:status %)) rows))
