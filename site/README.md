# site — kotoba-lang.org

The language's public static site. It has no third-party runtime dependency.
Small inline scripts filter the generated reference index and run the Play
surface; Play fetches only the same-origin, digest-bound Wasm artifact. Queries
stay in the browser and no telemetry is emitted.

## What makes this page different from a README

`generate.cljs` **derives the language claims from this repository's own authority files**
rather than restating them in prose. The eight safety claims and their residual
risks, the deliberately-absent surface, the elaboration pipeline, the deny
rules, the Component Model / WASI pins and the identity non-goals are all read
at build time from:

```
lang/safety-claims.edn          lang/wasm-component-platform.edn
lang/surface-status.edn         lang/elaboration-pipeline.edn
lang/typed-eval.edn
lang/docs-release.edn           lang/product-defaults.edn
site/sponsorship.edn
docs/search-index.edn
```

Change the spec and the page changes with it. The page cannot drift into
claiming more than the spec claims, which is the property a safety-oriented
language's marketing page most needs to have.

The layout is mobile-first: narrow screens are the base CSS, while wider header
and action layouts are added with `min-width` queries. Public navigation points
to the language docs, the normative protocol and spec repositories, Kotobase,
Murakumo, and the distinct language-authority and implementation repositories.

The first screen is deliberately a 30-second narrative: **Why → What → Proof →
Architecture**. It states the product thesis, shows the language feel, separates
internal production dogfooding from customer traction, and then carries the
boundary through checked KIR, capability/effect admission, content-addressed
artifacts, and host enforcement. The 33-core figure is an explicitly bounded
internal-operations statement, not a language qualification claim, customer
count, paid-adoption metric, or revenue claim. Product safety claims remain
derived from the repository authorities above.

The page also retains the generated local documentation search: its embedded
index is built from the repository authority and no query leaves the browser.
The developer journey connects documentation, the exact checked source sample,
a digest-bound browser Play artifact, libraries, roadmap, public community
channels, the generated engineering blog, and related cloud products.
`site/assets/play/` contains the source, Wasm, provenance, and publication
manifest copied to `site/dist/play/`; the UI explicitly labels this as a
precompiled example rather than an arbitrary in-browser compiler.
The displayed Kotoba source is tokenized at build time by
`kotoba-lang/grammar`'s portable `kotoba.grammar.highlight/tokenize` API; the
site does not maintain a tokenizer, keyword list, or forbidden-form list.
Stable TextMate-compatible scopes are adapted to presentation classes, and the
generator fails if concatenating the highlighted tokens does not reproduce the
exact `.kotoba` source. The generated TextMate artifact remains digest-checked
as the editor scope contract. The deployed page needs no client-side syntax
highlighter or third-party runtime dependency.
`site/dependencies.edn` records the grammar revision, artifact digest, scope,
other build-time repositories, and the intentionally small browser runtime
dependency surface. The generator verifies the grammar digest and scope before
rendering and publishes the manifest at `/dependencies.edn`.
## Dark mode

Both themes come from one knob: `jp-go-dds.page` is called with `:dark? true`,
which inserts `jp-go-dds.dark`'s inversion layer and writes `color-scheme` and
`theme-color` for both modes. Application CSS defines no palette — every mark,
including the charts, reads a `--hig-*` token — so the whole page follows the
mirrored DADS ramps without a second set of colours.

Two things had to be fixed for it, and both were invisible in light mode:

- The hero canvas cleared to **opaque white** and painted its dots in a baked
  `#0017c1`. On a dark page that is a white slab with invisible dots. It now
  clears to `(0,0,0,0)` — the WebGPU output is then already premultiplied and
  the WebGL canvas composites over the page — and reads its dot colour from
  `getComputedStyle(wrap).color`, which `.kot-hero-canvas` sets to
  `var(--hig-color-tint)`. The SVG fallback already followed the token.
- DADS's blue text chip resolves to a step whose dark mirror measures
  **4.42:1** against the dark surface, below WCAG AA for its own 16px normal
  weight. One rule points it at `--hig-color-tint` instead (11.10:1 light,
  8.76:1 dark). That is an upstream `jp-go-dds.dark` gap, not a local
  preference, and it also stops the page carrying two different blues.

Measured contrast, both themes, every chart mark and every piece of chart text:
marks ≥ 3:1, text ≥ 4.5:1.

## Charts

`site/src/kotoba/site/chart.cljc` holds three pure hiccup forms, chosen per
report rather than applied uniformly:

- `ranked-bars` — magnitude across named toolchains. Bars carry **time**, so
  the fastest lane is the *shortest* bar and every chart says so in its axis
  note. Plotting a speed ratio would put the winner on the longest bar and
  would also quietly replace the measured quantity with a derived one.
  Row layout answers to the width of the **chart**, not the window
  (`container-type: inline-size`): the same component is 60rem wide in the hero
  and 17rem wide in a small-multiple card, and a viewport media query squeezed
  the narrow card's bars to a sliver.
- `diverging-cell` — one signed comparison grown from a centre baseline, used
  for all 30 runtime pairs. Sign is carried by three channels at once (side of
  the line, hue, signed number), so neither colour vision nor a greyscale print
  is a single point of failure. The two directions are scaled separately
  because the wins reach +92% and the losses only −12%; the caption says so.
- `ranked-bars` is also used for the six **native runtime** panels, in absolute
  milliseconds. The grid of 30 pairs reports margins, which is what perfgate
  rules on, but a percentage does not say whether a workload runs in five
  milliseconds or five hundred. The candidate median is one value per workload
  (the suite rotates each engine pair ABBA/BAAB, so the same Amu artifact is
  timed once and compared against each arm in turn).
- `log-lines` — build time against source size on two log axes. Only the
  released Kotoba lane is coloured and every line is labelled at its own end,
  so no categorical palette is introduced: blue/purple/cyan failed the CVD
  separation check (ΔE 4.8 protan) against this palette's own ramps. Labels are
  spread apart and connected by dotted hairline leaders — four of the seven
  lanes finish within 9 vertical units of each other.

A lane that did not build, or a capability a target does not have, renders as
text in the track. It never becomes a zero-length bar: the fastest way to emit
an artifact is to emit a broken one.

Three end marks, because they are three different events: a dot where the run
ended, a cross where the lane emitted an artifact that is not the program, a
bar where the toolchain refused to build.

The reveal is an enhancement and never a precondition. The final state is what
the CSS declares; a `<head>` script adds `.kot-anim` only when it is about to
observe and `prefers-reduced-motion` is not set, and that class is what
collapses the marks. Verified in all three states: with motion, an off-screen
bar measures `0px` and reaches `30px` on scroll; with reduced motion the class
is never added and bars are full width immediately; with JavaScript disabled
the charts render complete and the line dash offset is `0`.

## The header says `Lisp`

The page said `Clojure-shaped` in prose and showed tokenized Kotoba further
down, but nothing above the fold said *this is a Lisp*. Two marks, both drawn
rather than fetched — no request, and both stroke/colour `--hig-color-tint`,
so they follow the design system into dark mode like everything else:

- `( KOTOBA )` — the wordmark read as one form. Text, not an image, so it
  scales with the type. The glyphs are `aria-hidden`; the link's `aria-label`
  already names it.
- Four nested opening parens in each page margin, the right one mirrored, so
  the header reads as one enclosing form `(((( … ))))`.

The margin placement is the whole point and the first attempt got it wrong
twice: a wide nest stretched across the header was clipped into unrelated
curves, and centred it landed directly behind the nav, so a texture became
scratch marks over the links. Below 64rem the container fills the viewport and
there is no margin, so it is not drawn at all rather than drawn on top of
something. Verified by geometry rather than by eye — the nest's bounding boxes
intersect **zero** nav links or wordmark boxes at 1440px, and it is absent at
420px.

## The page as five chapters, and one menu

The information did not change: the same twenty sections, the same words, the
same links. What changed is the order they are placed in and how you get at
them.

**The order.** The body used to run why → what → defaults → docs → code →
libraries → roadmap → community → sponsor → cloud → proof → architecture, so
the mechanism and the evidence arrived *after* the sponsorship page and the
reader met the ecosystem before the argument. `chapters` now states the
narrative once, and the body is placed in that order: the problem → how the
boundary works → the evidence → start using it → around the language. Each
chapter's anchor is its first section's existing id, so no ids moved and no
links broke.

**The contents strip.** A forty-screen document needs a contents list more than
it needs ten links in a header. It is generated from `chapters`, so the strip
and the order of the sections cannot disagree.

**Where the program sits.** The hero opens with the program, above the eyebrow
and the headline: before any claim about the language, the page shows one and
lets it resolve into its own hashes, which is the claim.

**The menu.** The header carried ten controls in a flat row — three rows and
**202px** of header on a 390px screen, with nothing to say which of the ten
mattered. They are now one native `<details>`, grouped: the five chapters
under *On this page*, then Learn / Project / Elsewhere. `Docs` and `Play` stay
in the bar from 48rem up; they also appear in the menu, which is what lets one
DOM copy serve every width without leaving anything unreachable on a phone.

Measured, closed: **390px → 3 visible controls in a 68px header** (was 10 in
202px); **1440px → 5 in 68px** (was 10 in 112px). It is a native disclosure, so
it opens with JavaScript off; the only script closes it again after a link
inside it is followed, so an open panel does not cover the section just jumped
to.

**Where the panel is anchored.** To `.kot-header__inner`, which is exactly the
container's content box, and not to the disclosure. Anchored to the disclosure
it ended 52px short of the header's right edge at 1440px, and at 390px — where
the panel was in flow inside a flex item — it came out 210px wide, floating
mid-row instead of spanning the header. Both are the same mistake: the
containing block was the control rather than the row it belongs to. Measured
after: the panel's edges match the header's content box at 390, 768 and 1440.

**GitHub is a bar control, not a menu row.** The repository is where the claims
on this page can be checked, so it sits in the header rather than two taps
away. Outline rather than text, because it is the one control that leaves the
site and it should not look like the ones that do not.

It appears from **23rem** up. 320px is the single width where the bar cannot
hold a fourth control beside the wordmark without clipping it, and there it
stays listed under *Elsewhere* in the menu — reachable, just not in the bar.
From 34rem the menu gets its word back and the wordmark steps up again; from
48rem `Docs` and `Play` rejoin it.

The menu's glyph follows the same logic: three lines while the label is
hidden, a caret once the word `Menu` is beside it. A bare caret reads as
"something expands", not as "this is the menu".

Measured at 320, 360, 375, 390, 414, 430, 480, 600, 768, 1024 and 1440: header
68px at every width, the switch flush with the header's content box, the
wordmark never clipped, and no horizontal page scroll. GitHub is in the bar
from 375 up and in the menu at 320 and 360.

**Which way the controls sit.** `.kot-nav` is `justify-content: flex-end` and
`flex-wrap: nowrap` at every width. The nav box already reached the right edge;
only its *content* was left-aligned, so the menu sat against the wordmark with
78px of empty rail after the theme switch. `nowrap` matters too: with the nav
allowed to wrap, a 320px header became 120px because the controls dropped below
the wordmark instead of sharing the row. Measured: the theme switch's right edge
is flush with the header's content box at 320, 360, 390, 430, 500, 768 and
1440, header 68px throughout.

**The wordmark.** It is an image at a fixed height, so it does not shrink on
its own, and once the header became a single row it overlapped the menu at
390px. It now steps down twice on the way to a 320px screen. Measured: zero
overlap between the wordmark and the nav at 320, 360, 390, 430, 768 and 1440.

**One upstream fix came out of this.** `.dds-ext-grid` used
`minmax(var(--dds-ext-grid-min,16rem),1fr)`, and a track that cannot go
narrower than its declared minimum makes the whole page scroll sideways on a
screen narrower than that. At 320px this page had 32px of horizontal scroll and
every offender was a grid whose consumer passed `:min "19rem"` or `"21rem"`.
Fixed in `jp-go-digital-design-system` with `min(<declared>, 100%)` rather than
re-derived in this app's CSS, because that is the layer that owns the rule.
Measured after: zero horizontal scroll at 320, 360, 390, 430, 768 and 1440.

Two things the measurement corrected. The page does **not** overflow
horizontally on a phone: `scrollWidth - clientWidth` is 0 at 360, 390 and
430px, and every element wider than the viewport sits inside a scroll
container that is supposed to hold it — an element-by-element check reports
643px of "overflow" and is measuring the wrong thing. And `overflow: clip` on
the header, added for the paren nest, also clipped the menu panel: it rendered
behind the hero and could not be clicked. The clip belongs on the nest boxes,
which are the only things that spill.

## The theme switch is the Lisp mark

The Lisp logo is a circle split by an S-curve with a lambda in each half — one
knocked out of the dark side, one inked on the light side. That figure is
already a light/dark duality, so the toggle does not bolt a sun and a moon
onto it: **the control is the mark, and pressing it rotates the figure 180°**,
carrying the coloured mass from one side to the other. The geometry is ours,
in the same family, not a copy of the logo file.

Both λ positions are the roomiest point in each half — furthest from the
dividing curve and from the rim — found by sampling the filled path rather
than placed by eye, and the scale is the largest that keeps both strokes,
stroke width included, inside their own half. A λ that crosses the boundary is
invisible where it lands on its own colour; the first attempt did exactly that
at scale 0.85 and the check caught it.

`role="switch"` with `aria-checked` is the honest shape for two states (a
button whose label changes says the opposite thing half the time). The script
is one head script on every page: in the head because applying a stored `dark`
after first paint is a white flash on every navigation, on every page because
otherwise the choice does not survive a link, and delegated from `document` so
one script can serve a button that does not exist yet when it runs. With no
stored choice the page follows `prefers-color-scheme`, and the switch follows
it too. The button ships `hidden` and JavaScript reveals it — without a script
there is nothing for it to do, and a dead control is worse than none.

Measured both ways: from a light system, one click gives `data-theme="dark"`,
body `rgb(26,26,26)`, `aria-checked="true"`, the figure at `rotate(180deg)`,
`kotoba-theme=dark` stored, and it survives a navigation to `/libraries/`;
from a dark system it starts checked and one click gives light. With
JavaScript off the button is hidden and the page still honours the system.

## The hero program, and the hashes it turns into

The first screen carries a real Kotoba program — the same
`site/assets/play/double-21.kotoba` the Play section runs — tokenized at build
time by the grammar. Hovering it, or simply leaving it on screen, dissolves
every character into hex and reassembles it as one of the program's identities,
then puts the code back. It cycles through three, because they are three
different hashes of three different things, all read out of the checked-in
provenance receipt:

| | |
|---|---|
| `source bytes` | sha-256 of the exact file shown |
| `checked KIR` | the typed, effect-checked representation the compiler admitted |
| `artifact identity` | binds source, policy, compiler contract and target ABI |

The effect is the argument rather than decoration: a Kotoba definition is
addressed by what it is, and none of these hashes is authority to run
anything. The three digests are ordinary text in the page, so with JavaScript
off or `prefers-reduced-motion` set nothing is lost — the animation is an
overlay on content that is already there.

**One rendering trap, measured.** Wrapping each character in its own span makes
a token's children *all* elements, and `html.core` indents an element whose
children are all elements. Inside `white-space: pre` each of those indents is a
real line break, so the hero rendered one character per line. `<pre>` is on the
renderer's preserve-whitespace list; `<code>` is not, so the safety net does
not reach the element that needed it. `highlighted-kotoba-chars` therefore
emits one pre-rendered, escaped string. The grammar's round-trip assertion
still runs on the tokens before they are split, so the displayed bytes are
still exactly the file's bytes.

## The `Fastest` claim on the first screen

`cold-start` in `generate.cljs` derives it once, from
`bench/public-build-scaling/latest.json`. Four of the five public reports
record a **failed** quiet-host gate and therefore may not rank anything, and
the page says so next to each of them. The build-scaling report is the
exception for a structural reason: its lanes are interleaved on one host and
every ordering goes through perfgate at its own unrelaxed default policy, which
refuses any gap falling inside the two arms' combined spread — so a gap that
survives survives the host being busy.

At K=1 the released CLI passes that test against all four comparators the host
could build (2.5x Clang, 3.3x rustc to Wasm, 4.8x rustc to native, 14.6x
javac). Bounded to that host, that size and that run, it is the only thing this
page calls fastest. `:qualified?` is read out of the report, not asserted: if a
rerun loses one ordering, the chip, the heading and the sentence step down
together. `lang/product-defaults.edn` still forbids
`:universal-speed-rank`, and nothing here claims one.

The public benchmark section reads the checked-in compile report at
`bench/public-compile-comparison/latest.json` and the bounded native comparison
summary at `bench/public-runtime-comparison/latest.json`. The generator copies
these reports plus the stage-separated developer-loop report at
`bench/public-end-to-end-comparison/latest.json` and the string, collection,
allocation, I/O, concurrency, and real-application report at
`bench/public-domain-comparison/latest.json` alongside `llms.txt`,
`llms-full.txt`, and the executable agent
quickstart. Compile-startup evidence and steady-state runtime evidence remain
visibly separate from dependency resolution, checking, clean/no-change builds,
first-result latency, and workload-domain behavior. Unsupported capabilities
remain reasoned N/A rather than zero. An unqualified host-load result cannot
become a fastest claim through presentation.

## Regenerate

Run from the **repository root**, with `jp-go-digital-design-system`, `grammar`,
`css`, and `html` checked out as west siblings (`orgs/kotoba-lang/*`):

```sh
JP_GO_DDS_ROOT=../jp-go-digital-design-system \
KOTOBA_GRAMMAR_ROOT=../grammar \
KOTOBA_IDENTITY_ROOT=../identity \
nbb --classpath "site/src:../grammar/src:../jp-go-digital-design-system/src:../css/src:../html/src" site/generate.cljs
```

The generator reads the vendored DADS stylesheet from
`JP_GO_DDS_ROOT/resources/jp_go_dds/dds.css` and inlines it. The resulting page
has no external font, script, analytics, or design-system request at runtime;
the small documentation-filter and Play scripts are inline.

The header uses the same six-circle `K O T O B A` artwork as the implementation
repository README. Its authoritative source is
`kotoba-lang/kotoba@cfdc08b71c4053f80d42a24d7bb418204a84b369:docs/assets/header.png`
(SHA-256 `76040326b828217845181068d3cf14ab856f671c5525bf8eb1182c40c4116a35`).
`site/assets/kotoba-wordmark.png` is a 480 x 68 mobile-sized derivative
(SHA-256 `0126a8791d4181d102892215e50379014af6bc7e36bbee7c5aa5002a6f25778a`).
The generator copies that local asset into `dist/`, so the deployed page makes
no external image request.

`site/assets/kotoba-og-card.png` (1200x630, SHA-256
`6e4b107fc8dd8623df36a31f15e7889d3672f9e932dcc9ddd8d74745b8959fc9`) and
`site/assets/kotoba-favicon.png` (64x64, SHA-256
`1d654a287062943236f9ce8eea3c1818c7b5a06e25975ccae4363e7ee967e7e2`) is a
deterministic derivative of the sources in `site/assets/meta-src/`: render
with headless Chrome at 64x64 (`--window-size=64,64
--default-background-color=00000000`), screenshot, and copy over the
checked-in PNG. `site/assets/kotoba-favicon.ico` (SHA-256
`deb4ad01047e65ffa90891b7b57ee000b25a08797d200a579cf286c36573e2ce`) is the
same 64x64 PNG wrapped in a single-entry ICO container (png_to_ico script,
FORMAT-spec ICONDIR + ICONDIRENTRY); the generator copies it to
`dist/favicon.ico` because legacy agents request `/favicon.ico` without an
explicit `<link>`. The OG card reuses the hero ring geometry (stroke arc
sampled at 360 points) so the share card matches the live hero. The
generator refuses to run when any asset is missing.

Output: `site/dist/index.html`, `site/dist/blog/index.html`,
`site/dist/legal/index.html`, `site/dist/sponsor/index.html`,
`site/dist/ja/sponsor/index.html`, `site/dist/sponsorship.edn`, the Play artifact
and evidence, the wordmark,
AI-agent text surfaces, and the raw benchmark JSON (committed, so a clean
checkout can deploy without running the generator).

The generator also publishes identity's canonical external-trust artifacts at
`/schemas/trust-profile/v1`,
`/policies/trust/human-passport/itonami-v1.json`, and
`/policies/trust/eas/kotobase-v1.json`, and
`/policies/trust/erc8004/murakumo-v1.json` plus its detached
`.signature.json` quorum envelope. The source remains
`kotoba-lang/identity`; regeneration fails if that west sibling is absent, so a
stale handwritten site copy cannot silently deploy.

**The committed artifact is a deploy input, not a build by-product**: `wrangler
deploy` here has no build step, so whatever is in `dist/` at deploy time is what
the live zone serves. Regenerate and check `git diff` before shipping. An
*absent* `dist/` fails loudly, but an *empty* one does not — wrangler reports
"Read 0 files" and uploads them, which on a custom domain replaces the live page
with nothing. Measured 2026-08-13 with wrangler 4.103.0.

## Where each benchmark lives

`benchmark-provenance` names the harness, method and report for all five
benchmarks, and the table renders them as links. URLs the reports carry
themselves are preferred over URLs written here — the runtime suite and the
build-scaling harness both publish their own harness, manifest and method — and
the three that run from this repository have their paths passed through
`check-local!`, so **a harness that moves fails the build instead of shipping a
dead link**. Verified in both directions: renaming one entry's `:harness`
exits non-zero naming the missing path, and all 20 rendered links return 200.

The `Bottom line` paragraph used to say that *no* current run qualified a speed
ranking because its quiet-host gate failed. That was true of three of the five
reports and false of the two that matter: the runtime suite records
`qualified-host-load`, and the build-scaling orderings clear perfgate at K=1.
It now reads the verdicts out of the reports so it cannot go stale again.

## The repository catalogue

`/libraries/#catalog` lists every public repository in the `kotoba-lang`
organisation and filters it in the browser. Two committed inputs:

- `site/library-catalog.edn` — the snapshot (name, description, existing
  GitHub topics, primary language, archived flag, last push). Committed so the
  generator is deterministic and needs no network. Refresh it with the
  documented `gh api graphql` pagination in this section rather than a script:
  new operations tooling is kbb-first (owner instruction 2026-09-07) and that
  migration is out of scope for this page.
- `site/library-taxonomy.edn` — the tag vocabulary. Each tag names the GitHub
  repository **topic** it corresponds to, so the site filter and the org's
  topics are one vocabulary instead of two that drift.

`site/src/kotoba/site/catalog.cljc` applies it, and keeps two strengths of fact
apart. A **plane** tag is read off the repository name using the workspace's
own naming rule (ADR-2608040100) and is as reliable as the name. A **domain**
tag is matched against the name and description with word-boundary patterns —
substring matching turns `os` into a hit on `kotobase`, `protocols` and
`compose` alike, 203 false hits measured. That is evidence, and it runs out:
1,160 of these repositories carry no description at all, and some names are
deliberate metaphors (`kuro`, `kobo`, `byoubu`) that say nothing about the
function.

So the page publishes the number it could not tag. A classifier that assigns
every repository a nearest-guess tag returns the same shape as one that is
right, and the reader cannot tell which they are looking at.

The list is server-rendered, not built from an embedded JSON blob: with
JavaScript off the complete catalogue is still the page, and the controls —
the part that needs a script — are the part hidden until one runs. Text
narrows, tags widen (a repository matching *any* selected tag is shown): with
24 domain tags an AND of two of them is almost always empty, which reads as a
broken filter rather than a precise one.

`.kot-lib[hidden]{display:none}` is not cosmetic. A `display` declaration beats
the `hidden` attribute's UA `display:none`, so without it every filtered-out
row stayed laid out: `el.hidden` read `true`, the counter said 31, and the
reader still saw all 2,215. Filtering is therefore verified by **rendered
geometry**, not by the property — unfiltered 2,215 rows / 166,120px tall,
`#tag=simulation` 31 rows / 8,100px.

A repository is discovery, not a package. Exactly one library is published
through the content-addressed registry; the catalogue caption says so.

### Refresh the snapshot

```sh
# 23 pages of 100; write name/description/topics/language/archived/pushed
gh api graphql -f query='
{ organization(login:"kotoba-lang") {
    repositories(first:100, privacy:PUBLIC, after:CURSOR, orderBy:{field:NAME,direction:ASC}) {
      pageInfo { hasNextPage endCursor }
      nodes { name description isArchived isFork stargazerCount pushedAt
              primaryLanguage { name }
              repositoryTopics(first:20){ nodes { topic { name } } } } } } }'
```

Forks and `.github` are excluded. Then push the derived tags back as GitHub
topics so the correspondence holds in both directions; the topics endpoint
**replaces** the whole set, so send the union of derived and existing topics
and never send an empty list.

## Score it

An unmeasured page is theater. `kotoba-lang/design-quality` scores the actual
rendered HTML against a deterministic HIG/WCAG rubric — no LLM, no browser:

```sh
cd ../design-quality
nbb --classpath src -m design-quality.cli score ../kotoba-lang/site/dist/index.html --min 100
```

Current score: **100.00** (converged, no findings). Do not lower the floor to
make a regression pass.

## Deploy

```sh
cd site
npx wrangler deploy
```

Static assets only — `wrangler.jsonc` declares no Worker script. The zone is
served on the apex and `www`. The Cloudflare account that currently holds the
zone is an infrastructure label, not the published operator.

The published operator on this zone is **Kotoba Labs Inc.** Public contact
is `support@kotoba-lang.org`. Sales contact is Ryo Awai. The footer, `/legal/`,
and `security.txt` name only those facts. Do not publish a Specified
Commercial Transactions Act table, Delaware file number, address, phone, or
any other mailbox from this generator.

## GitHub Sponsors activation

`site/sponsorship.edn` is the public status authority. Keep it at
`:status :preparing` until GitHub has approved the `kotoba-lang` sponsored
organization profile and `https://github.com/sponsors/kotoba-lang` resolves to
that live profile. In this state, the generated pages explain the flow but do
not show a payment action.

After approval, change the status to `:live`, update `:checked-at`, regenerate
the committed `site/dist/` artifacts, and change `.github/FUNDING.yml` from the
status-aware custom project page to `github: kotoba-lang`. Verify the homepage,
`/sponsor/`, `/ja/sponsor/`, and the repository Sponsor button before deploy.

The page is built with `jp-go-dds` (the Digital Agency Design System mirror).
Application CSS uses the shared `--hig-*` token contract through
`jp-go-dds.tokens/skin-css`; it does not define a separate palette.

## Hero source on IPFS

The visible `hello.kotoba` filename links to the exact `double-21.kotoba` source
bytes using a CIDv1 raw/sha2-256 identifier derived by the generator. A matching
HTTP block mirror is generated under `/ipfs/<CID>`. On 2026-09-08 the source was
pinned on the local online Kubo node and announced through routing; both
`ipfs.io/ipfs/<CID>` and `ipfs.kotobase.net/ipfs/<CID>` returned byte-identical
source. This is observed retrieval, not a permanent availability guarantee.

Source CID: `bafkreiaeohkv2zuo2x4yq745euqm5wijrub5xpkxbbw5gre6tdp3sxzonm`.
If the sample changes, publish/pin the new generated CID and verify external
retrieval before deploying its link. Renaming the visible filename does not
change the file bytes or its CID.
