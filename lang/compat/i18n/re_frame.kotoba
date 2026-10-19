(ns i18n.re-frame
  "re-frame event/sub wiring for locale state, built on the
  `shitsuke.re-frame.core` seam so the SAME registration code runs against
  real re-frame 1.4.3 in the browser (reagent reactions, async dispatch) and
  against shitsuke's synchronous mini-runtime on the JVM (SSR/tests) — see
  shitsuke's dual-render contract, proven by kami-mangaka-reader-clj.

  `i18n.core/*locale` (a plain atom) is the actual value `i18n.core/t` reads;
  it is not itself reactive. This namespace mirrors it into re-frame's
  app-db under `:i18n/locale` (and a `:i18n/catalog-version` counter bumped
  on every `register!`) purely so Reagent components re-render when the
  locale changes or a new catalog lands — `:i18n/t` subscribers depend on
  those db keys but delegate the actual lookup back to `i18n.core/t`, which
  stays the single source of truth so plain (non-reactive) callers — e.g.
  the top-level functions `i18n.messages/defmessages` generates — see the
  same locale immediately, with no re-frame dependency required to use them."
  (:require [shitsuke.re-frame.core :as rf]
            [i18n.core :as core]))

(defn init!
  "Register the :i18n/* event/subs. Call once at app start, before any
  `(rf/subscribe [:i18n/...])`. Idempotent (re-registration just overwrites
  the same handlers)."
  []
  (rf/reg-event-db
   :i18n/set-locale
   (fn [db [_ locale]]
     (core/set-locale! locale)
     (assoc db :i18n/locale locale)))

  (rf/reg-event-db
   :i18n/catalog-loaded
   (fn [db [_ locale catalog]]
     (core/register! locale catalog)
     (update db :i18n/catalog-version (fnil inc 0))))

  (rf/reg-sub
   :i18n/locale
   (fn [db _] (get db :i18n/locale (core/locale))))

  (rf/reg-sub
   :i18n/catalog-version
   (fn [db _] (get db :i18n/catalog-version 0)))

  (rf/reg-sub
   :i18n/t
   ;; Depends on :i18n/locale + :i18n/catalog-version so Reagent recomputes
   ;; on either change, even though the actual lookup delegates to
   ;; i18n.core/t (kept as the single source of truth — see namespace doc).
   (fn [db [_ k params]]
     (get db :i18n/locale)
     (get db :i18n/catalog-version)
     (core/t k params)))
  nil)

(defn set-locale!
  "Dispatch a locale switch. Prefer this (or `(rf/dispatch [:i18n/set-locale
  locale])` directly) over calling `i18n.core/set-locale!` in a re-frame app
  so subscribers re-render."
  [locale]
  (rf/dispatch [:i18n/set-locale locale]))
