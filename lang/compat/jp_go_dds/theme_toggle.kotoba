(ns jp-go-dds.theme-toggle
  "Shared lambda yin-yang theme control. Pair with page :dark? true; place script in head before paint."
  (:require [jp-go-dds.core :as dds]))

(def css
 (str
   ;; ── the theme switch: the Lisp mark is the control ───────────────────────
   "#kot-theme{min-width:calc(44 / 16 * 1rem);min-height:calc(44 / 16 * 1rem);"
   "padding-inline:var(--hig-spacing-2,calc(8 / 16 * 1rem));color:var(--hig-color-tint,var(--color-key-900))}"
   ".kot-yy{display:inline-flex}"
   ".kot-yy-ring{fill:none;stroke:currentColor;stroke-width:1;opacity:.5}"
   ".kot-yy-yin{fill:currentColor}"
   ".kot-lam{fill:none;stroke-width:2.4;stroke-linecap:round}"
   ;; The lambda sitting on the inked half is knocked out in the page surface;
   ;; the other is inked in the tint. Swap them and each one vanishes into its
   ;; own background.
   ".kot-lam-cut{stroke:var(--hig-color-system-background,var(--color-neutral-white))}"
   ".kot-lam-ink{stroke:currentColor}"
   ".kot-yy-fig{transform-origin:12px 12px;transition:transform .55s cubic-bezier(.68,-0.3,.32,1.3)}"
   "#kot-theme[aria-checked=\"true\"] .kot-yy-fig{transform:rotate(180deg)}"
   "@media(prefers-reduced-motion:reduce){.kot-yy-fig{transition:none}}"
))

(def control
  "Light/dark toggle, drawn as a λ yin-yang.

  The Lisp logo is a circle split by an S-curve with a lambda in each half —
  one knocked out of the dark side, one inked on the light side. That figure
  is already a light/dark duality, so it does not need a sun and a moon bolted
  onto it: **the control is the mark, and toggling rotates it 180°**, which
  carries the coloured mass from one side to the other. This is our own
  geometry in the same family, not a copy of the logo file.

  The two λ positions are the roomiest point in each half — the point furthest
  from the dividing curve and from the rim — found by sampling the filled path
  rather than eyeballed, and the scale is the largest that keeps both strokes,
  stroke width included, inside their own half. A λ that crosses the boundary
  is invisible where it lands on its own colour.

  `role=switch` + `aria-checked` is the honest shape for a two-state control
  (a button with a label that changes says the opposite thing half the time).
  It ships `hidden`: without JavaScript there is nothing for it to do, and a
  dead control is worse than no control — the page still answers to
  `prefers-color-scheme` on its own."
  (let [yin "M12 1.4A10.6 10.6 0 0 1 12 22.6A5.3 5.3 0 0 1 12 12A5.3 5.3 0 0 0 12 1.4Z"
        lam (fn [x y rot cls]
              [:g {:transform (str "translate(" x " " y ") rotate(" rot ") scale(0.78)")}
               [:path {:class (str "kot-lam " cls) :d "M-2.2 -4 L2.4 4"}]
               [:path {:class (str "kot-lam " cls) :d "M-0.6 -1.2 L-2.8 4"}]])]
    (dds/button
     [:span {:class "kot-yy"}
      [:svg {:viewBox "0 0 24 24" :width 26 :height 26
             :aria-hidden "true" :focusable "false"}
       [:g {:class "kot-yy-fig"}
        [:circle {:class "kot-yy-ring" :cx 12 :cy 12 :r 10.6}]
        [:path {:class "kot-yy-yin" :d yin}]
        (lam 12.05 17.30 0 "kot-lam-cut")
        (lam 11.95 6.70 180 "kot-lam-ink")]]]
     {:type :text :size "sm"
      :attrs {:id "kot-theme" :role "switch"
              :aria-checked "false" :aria-label "Dark mode" :hidden true}})))


(def script
  "Theme choice, in one script that runs in <head> on every page.

  It has to be in the head and before paint: applying a stored `dark` after
  first paint is a white flash on every navigation. It has to be on every page
  or the choice does not survive a link.

  The click handler is delegated from `document`, so the same head script
  works even though the button does not exist yet when it runs — that is why
  there is one script instead of a head script plus a body script per page.

  With no stored choice the page follows `prefers-color-scheme`, which is what
  `jp-go-dds.dark` already does on its own; the toggle only ever writes an
  explicit override, and `:root[data-theme]` beats the media query in both
  directions."
  (str "(function(){"
       "var K='kotoba-theme',R=document.documentElement;"
       "function stored(){try{var v=localStorage.getItem(K);"
       "return v==='dark'||v==='light'?v:null;}catch(e){return null;}}"
       "var s=stored();if(s)R.setAttribute('data-theme',s);"
       "function sysDark(){return !!(window.matchMedia&&"
       "matchMedia('(prefers-color-scheme: dark)').matches);}"
       "function current(){var a=R.getAttribute('data-theme');"
       "return a==='dark'||a==='light'?a:(sysDark()?'dark':'light');}"
       "function paint(){var b=document.getElementById('kot-theme');if(!b)return;"
       "var d=current()==='dark';"
       "b.setAttribute('aria-checked',d?'true':'false');"
       "var ja=(R.lang||'').indexOf('ja')===0;"
       "b.setAttribute('aria-label',ja?(d?'ダークモード、オン':'ダークモード、オフ'):(d?'Dark mode, on':'Dark mode, off'));}"
       "document.addEventListener('click',function(e){"
       "var t=e.target,b=null;"
       "while(t&&t!==document){if(t.id==='kot-theme'){b=t;break;}t=t.parentNode;}"
       "if(!b)return;"
       "var n=current()==='dark'?'light':'dark';"
       "R.setAttribute('data-theme',n);"
       "try{localStorage.setItem(K,n);}catch(e2){}"
       "paint();});"
       "function ready(){var b=document.getElementById('kot-theme');"
       "if(b){b.hidden=false;paint();}}"
       "if(document.readyState==='loading')"
       "document.addEventListener('DOMContentLoaded',ready);else ready();"
       ;; While no explicit choice is stored the page still follows the system,
       ;; so the switch has to follow it too or it will show the wrong state.
       "if(window.matchMedia){var mq=matchMedia('(prefers-color-scheme: dark)');"
       "var f=function(){if(!stored())paint();};"
       "if(mq.addEventListener)mq.addEventListener('change',f);}"
       "})();"))

