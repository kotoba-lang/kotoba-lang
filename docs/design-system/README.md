# Kotoba / Awai shared design-system preview

This directory is an isolated, read-only preview draft of a shared visual
foundation for Kotoba documentation surfaces and Awai Studio. It is a
portable example — three static files (`README.md`, `preview.html`,
`preview.css`) that open directly in a browser — not a production change,
not a second design authority, and not a published site route. The
normative design and migration register is owned separately.

Open `preview.html` in any modern browser. The page is `lang="ja"`, uses
only the local stylesheet, contains no script, makes no network requests,
and submits nothing.

## Shared palette and roles

The palette is declared once in `preview.css` under `:root` and adapts via
`prefers-color-scheme`. These are the exact tokens and their roles:

| Token | Light | Dark | Role |
|---|---|---|---|
| `--paper` | `#f6f3ec` | `#101918` | Page background; the "paper" layer of paired panels |
| `--surface` | `#fffdf7` | `#1b2926` | Raised layer for cards, notices, disclosures, buttons' parent surfaces |
| `--ink` | `#172c29` | `#f0f5ee` | Primary text |
| `--muted` | `#415e57` | `#b4c8bf` | Secondary text, descriptions, disabled labels |
| `--action` | `#00655b` | `#8ee0c5` | Deep green primary action and link color |
| `--on-action` | `#fffdf7` | `#0d241d` | Text and icons placed on `--action` |
| `--focus` | `#8e381e` | `#ffac84` | Warm accent for the focus ring and error emphasis |
| `--line` | `#71867e` | `#899e94` | Hairline borders, rules, and the fine hero texture |
| `--radius` | `.875rem` | `.875rem` | Corner radius for panels, cards, and controls |

`color-scheme: light dark` lets the browser render form controls and
scrollbars consistently with the active theme. Layout uses a `72rem` max
inline size, a two-column paired canvas above `44rem`, and a single column
below it; all sizes are in `rem`, grid children carry `min-width: 0`, and
interactive examples keep `min-block-size: 44px` with no fixed text heights.

## Interaction and state semantics

- **Focus.** `:focus-visible` draws a `3px solid var(--focus)` outline with
  `3px` offset. Focus is never indicated by color alone and no fixed or
  floating layer can cover it. A skip link is the first focusable element.
- **Action feedback.** Hover and press feedback is limited to
  `background-color`, `color`, and `border-color` over `120ms`; it never
  animates layout properties. Under `prefers-reduced-motion: reduce` all
  animation and transition is disabled.
- **Disabled.** Disabled controls keep their label, switch to a muted color
  with a dashed `--line` border and `not-allowed` cursor, and remain
  focusable-adjacent but inert. Disabled styling is never the only signal —
  the control's text states what it is.
- **Error.** Errors use the warm `--focus` accent plus an explicit text
  label (`エラー`) inside a `role="alert"` notice — never color alone.
- **Status notices.** Informational and success notices use `role="status"`
  with a text label (`情報`, `成功`) and a colored inline-start border;
  errors use `role="alert"`. Every notice carries a written label so state
  is conveyed without relying on hue.
- **Disclosure.** Status detail uses a native `<details>/<summary>` element,
  so keyboard (Enter/Space) and assistive-technology behavior come from the
  platform, not from script.

## Migration notes

This preview is documentation only; it changes no runtime behavior. For the
normative migration register, the working rules are:

- Freeze new dependencies on `jp-go-dds`; do not add new imports or build
  edges to it.
- Existing compatibility APIs stay explicitly available until their
  consumers and their replacements are verified; do not remove or silently
  re-scope them ahead of that verification.
- Preserve semantic operations across the migration: consent flows,
  Content-Security-Policy behavior, locale handling, and native build
  behavior must remain unchanged.
- This directory is not wired into any build, index, or site route; adding
  it must not alter generated output or qualification evidence.

## References

The palette and state treatment take general inspiration from platform
human-interface guidance — the [Apple Human Interface
Guidelines](https://developer.apple.com/design/human-interface-guidelines/),
the [Nintendo Switch CEDEC 2018
session](https://cedec.cesa.or.jp/2018/session/detail/s5abdde297aa0e.html) on
system-level UI consistency, and [WCAG 2.2](https://www.w3.org/TR/WCAG22/) as
an accessibility reference point.

## Limits of this draft

- Product names appear alone; there are no logos and no transplanted
  Apple or Nintendo styling, and no affiliation is claimed.
- This is a preview draft, not a complete or adopted design system.
- It uses no private Figma files, private repository content, internal
  data, branding, or external assets.
- WCAG 2.2 is linked as a reference; no WCAG conformance is demonstrated or
  claimed by this page.
