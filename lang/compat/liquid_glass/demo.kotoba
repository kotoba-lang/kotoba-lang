(ns liquid-glass.demo
  "Static showcase page for GitHub Pages (docs/index.html). Pure dogfood: every
  element on the page is built from liquid-glass.components hiccup, rendered
  SSR via shitsuke.hiccup/->html — the same fn a JVM/babashka consumer would
  call. No build step; the glass material is CSS only (backdrop-filter) and
  needs no reagent mount to render correctly. The one exception is the
  pointer-tracking specular highlight, which is exactly the optional
  progressive enhancement it claims to be: a CLJ-owned inline script is emitted
  at the end of <body> (the same SSR-embed move as inline-style); remove the
  tag and the page loses only the pointer highlight.

  Usage: clojure -M -m liquid-glass.demo"
  (:require [clojure.java.io :as io]
            [shitsuke.hiccup :as h]
            [liquid-glass.style :as s]
            [liquid-glass.components :as lg]))

(def ^:private page-css
  "html{min-height:100%}
body{margin:0;min-height:100vh;min-height:100dvh;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
     color:#1c1c1e;background:linear-gradient(135deg,#ff6b6b 0%,#f7b733 22%,#4ecdc4 48%,#556fb5 72%,#7b2ff7 100%);
     background-attachment:fixed;}
.liquid-glass__nav-bar{padding-top:env(safe-area-inset-top,0px)}
.demo-shell{max-width:960px;margin:0 auto;padding:48px 24px 96px;}
.demo-header{color:#fff;text-shadow:0 2px 12px rgba(0,0,0,.35);margin-bottom:36px;}
.demo-header h1{font-size:28px;margin:0 0 10px;}
.demo-header p{margin:0;opacity:.92;max-width:680px;line-height:1.55;font-size:14px;}
.demo-header a{color:#fff;}
.demo-section{margin:40px 0;}
.demo-section h2{color:#fff;text-shadow:0 2px 10px rgba(0,0,0,.3);font-size:13px;text-transform:uppercase;
     letter-spacing:.08em;margin:0 0 14px;font-weight:700;}
.demo-row{display:flex;gap:16px;flex-wrap:wrap;align-items:flex-start;}
.demo-panel-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;}
.demo-panel-grid .liquid-glass__panel,.demo-row .liquid-glass__panel{color:#fff;}
.demo-panel-grid .liquid-glass__panel h3{margin:0 0 6px;font-size:15px;}
.demo-panel-grid .liquid-glass__panel p{margin:0;font-size:13px;opacity:.85;line-height:1.5;}
.demo-sheet-wrap{position:relative;max-width:360px;}
.demo-sheet-wrap .liquid-glass__scrim{position:static;border-radius:20px 20px 0 0;height:36px;}
.demo-sheet-wrap .liquid-glass__sheet{color:#fff;}
.demo-sheet-wrap .liquid-glass__sheet h3{margin:0 0 6px;}
.demo-sheet-wrap .liquid-glass__sheet p{margin:0 0 16px;font-size:13px;opacity:.85;line-height:1.5;}
.liquid-glass__button,.liquid-glass__icon-button,.liquid-glass__text-field,.liquid-glass__search-field,
.liquid-glass__text-area,.liquid-glass__menu-select,.liquid-glass__stepper,.liquid-glass__checkbox,
.liquid-glass__radio,.liquid-glass__toggle,.liquid-glass__nav-bar,.liquid-glass__alert,.liquid-glass__menu,
.liquid-glass__list,.liquid-glass__label,.liquid-glass__avatar,.liquid-glass__tooltip,.liquid-glass__chip,
.liquid-glass__disclosure,.liquid-glass__gauge{color:#fff;}
.liquid-glass__text-field input::placeholder,.liquid-glass__search-field input::placeholder,
.liquid-glass__text-area textarea::placeholder{color:rgba(255,255,255,.6);}
.liquid-glass__menu-select select option{color:#1c1c1e;}
.demo-form-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;align-items:start;}
.demo-inline{display:flex;gap:20px;flex-wrap:wrap;align-items:center;}
.demo-inline label{display:inline-flex;}
.demo-progress-wrap{max-width:280px;display:flex;flex-direction:column;gap:16px;}
.demo-overlay-wrap{position:relative;min-height:170px;}
.demo-overlay-wrap .liquid-glass__scrim{position:absolute;border-radius:16px;}
.demo-overlay-wrap .liquid-glass__alert{position:absolute;width:min(100%,320px);}
.demo-overlay-wrap .liquid-glass__menu{position:absolute;top:0;left:0;}
.demo-list-wrap{max-width:360px;}
.demo-dark{background:#0b0b10;border-radius:20px;padding:28px;
     --liquid-glass-surface-clear-tint:rgba(18,18,22,0.30);--liquid-glass-surface-clear-border:rgba(255,255,255,0.08);
     --liquid-glass-surface-regular-tint:rgba(20,20,24,0.42);--liquid-glass-surface-regular-border:rgba(255,255,255,0.12);
     --liquid-glass-surface-thick-tint:rgba(16,16,20,0.58);--liquid-glass-surface-thick-border:rgba(255,255,255,0.16);
     --liquid-glass-specular-highlight-opacity:0.30;
     --liquid-glass-specular-rim-top-opacity:0.5;--liquid-glass-specular-rim-bottom-opacity:0.02;}
.demo-dark .liquid-glass__panel,.demo-dark .liquid-glass__toolbar,.demo-dark .liquid-glass__button,
.demo-dark .liquid-glass__icon-button{color:#fff;}
.demo-note{color:#fff;opacity:.85;font-size:13px;line-height:1.5;max-width:680px;margin:0 0 14px;
     text-shadow:0 1px 6px rgba(0,0,0,.3);}
.demo-note code{font-size:12px;background:rgba(0,0,0,.25);padding:1px 5px;border-radius:4px;}
.demo-lens-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px;}
.demo-footer{color:#fff;opacity:.75;font-size:12px;text-align:center;margin-top:64px;}
.demo-footer a{color:#fff;}
@media (max-width:600px){
.demo-shell{padding:32px 16px 64px}
.demo-header h1{font-size:22px}
.demo-header p{font-size:13px}
}")

(def ^:private specular-js
  "(function(){'use strict';if(typeof document==='undefined'||!window.requestAnimationFrame)return;if(window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches)return;var script=document.currentScript;function derivedSelector(){var seen=Object.create(null);var marks=document.querySelectorAll('.liquid-glass__specular');for(var i=0;i<marks.length;i++){var host=marks[i].parentElement;if(!host)continue;for(var j=0;j<host.classList.length;j++){var c=host.classList[j];if(c.indexOf('liquid-glass__')===0&&c.indexOf('--')===-1)seen['.'+c]=true;}}return Object.keys(seen).join(',');}var selector=(script&&script.dataset&&script.dataset.lgSelector)||derivedSelector();if(!selector)return;document.documentElement.classList.add('liquid-glass-js');var active=null,pending=null,raf=0;function clearActive(){if(active){active.removeAttribute('data-lg-pointer');active=null;}}function frame(){raf=0;var e=pending;pending=null;if(!e)return;var host=e.target&&e.target.closest?e.target.closest(selector):null;if(host!==active)clearActive();if(!host)return;var r=host.getBoundingClientRect();if(!r.width||!r.height)return;var x=Math.min(1,Math.max(0,(e.clientX-r.left)/r.width));var y=Math.min(1,Math.max(0,(e.clientY-r.top)/r.height));host.style.setProperty('--liquid-glass-pointer-x',x.toFixed(3));host.style.setProperty('--liquid-glass-pointer-y',y.toFixed(3));host.setAttribute('data-lg-pointer','');active=host;}document.addEventListener('pointermove',function(e){pending=e;if(!raf)raf=requestAnimationFrame(frame);},{passive:true});document.addEventListener('pointerleave',clearActive,{passive:true});})();")

(defn- specular-script
  "Inline the optional pointer-tracking specular enhancer (see
  liquid-glass.style/specular-selector for the data-lg-selector contract) —
  the SSR-embed twin of s/inline-style-hiccup."
  []
  [:script {:data-lg-selector (s/specular-selector)}
   [:hiccup/raw specular-js]])

(defn- variant-panel [title blurb variant]
  (lg/panel [[:h3 title] [:p blurb]] {:surface variant}))

(defn page []
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, viewport-fit=cover"}]
    [:meta {:name "theme-color" :content "#ff6b6b" :media "(prefers-color-scheme: light)"}]
    [:meta {:name "theme-color" :content "#0b0b10" :media "(prefers-color-scheme: dark)"}]
    [:title "liquid-glass-ui — kotoba-lang"]
    [:link {:rel "icon" :href "data:,"}]
    ;; library CSS inside @layer kotoba.glass; the page's own CSS below stays
    ;; unlayered and therefore always outranks it (the cascade contract).
    (s/inline-style-hiccup)
    [:style page-css]]
   [:body
    [:div.demo-shell
     [:header.demo-header
      [:h1 "liquid-glass-ui"]
      [:p "kotoba-lang's shared liquid-glass visual skin — material tokens + a portable CSS style
           layer + pure-hiccup components, built on "
       [:a {:href "https://github.com/kotoba-lang/shitsuke" :target "_blank"} "shitsuke"]
       ". " [:a {:href "https://github.com/kotoba-lang/liquid-glass-ui"} "Source"]
       " · " [:a {:href "https://github.com/kotoba-lang/liquid-glass-ui/blob/main/docs/design.md"} "Design docs"]]]

     [:section.demo-section
      [:h2 "Toolbar"]
      (lg/toolbar [(lg/icon-button "☰") (lg/icon-button "🔍") (lg/badge "3") (lg/icon-button "⚙")])]

     [:section.demo-section
      [:h2 "Nav bar"]
      (lg/nav-bar "Settings" {:leading (lg/icon-button "‹") :trailing (lg/icon-button "＋")})]

     [:section.demo-section
      [:h2 "Tab bar"]
      (lg/tab-bar [[:visual "Visual"] [:edn "EDN"] [:preview "Preview"]] :visual)]

     [:section.demo-section
      [:h2 "Surface variants"]
      [:div.demo-panel-grid
       (variant-panel "clear" "Barely-there — scrims and tooltips." :clear)
       (variant-panel "regular" "Default control surface." :regular)
       (variant-panel "thick" "Toolbars/sheets over busy content." :thick)]]

     [:section.demo-section
      [:h2 "Elevation"]
      [:div.demo-row
       (lg/panel [[:p "flat"]] {:elevation :flat})
       (lg/panel [[:p "raised"]] {:elevation :raised})
       (lg/panel [[:p "overlay"]] {:elevation :overlay})
       (lg/panel [[:p "floating"]] {:elevation :floating})]]

     [:section.demo-section
      [:h2 "Buttons"]
      [:div.demo-row
       (lg/button "Continue" {:act :continue})
       (lg/button "Disabled" {:disabled true})
       (lg/icon-button "♥")]]

     [:section.demo-section
      [:h2 "Form controls"]
      [:div.demo-form-grid
       (lg/text-field {:id "name" :placeholder "Name"})
       (lg/search-field {:placeholder "Search"})
       (lg/menu-select [["visual" "Visual"] ["edn" "EDN"] ["preview" "Preview"]] {:value "visual"})]
      [:div.demo-inline {:style {:margin-top "16px"}}
       (lg/toggle {:checked true :act :dark-mode})
       (lg/checkbox "Remember me" {:checked true})
       (lg/radio "Option A" {:group "demo-radio" :value "a" :checked true})
       (lg/radio "Option B" {:group "demo-radio" :value "b"})]
      [:div.demo-inline {:style {:margin-top "16px"}}
       [:div {:style {:flex "1" :min-width "200px"}} (lg/slider {:value 60})]
       (lg/stepper 3 {:dec-act :dec :inc-act :inc})]]

     [:section.demo-section
      [:h2 "Feedback"]
      [:div.demo-inline
       [:div.demo-progress-wrap
        (lg/progress-bar 65)
        (lg/progress-bar 30)]
       (lg/progress-circle)
       (lg/gauge 72)
       (lg/label "♥" "Favorites")
       (lg/avatar "JK")
       (lg/badge "12")]
      (lg/divider)
      [:p {:style {:opacity ".7" :font-size "13px" :margin "0"}} "A hairline divider sits above this line."]]

     [:section.demo-section
      [:h2 "Chips"]
      [:div.demo-inline
       (lg/chip "Vegetarian" {:on-remove-act :remove-veg})
       (lg/chip "Gluten-free" {:on-remove-act :remove-gf})
       (lg/chip "Open now")]]

     [:section.demo-section
      [:h2 "Disclosure"]
      (lg/disclosure "Advanced settings"
                      [[:p {:style {:font-size "13px" :opacity ".85" :margin "0"}}
                        "Collapsible content — native <details>/<summary>, no JS needed for open/close."]]
                      {:open? true})]

     [:section.demo-section
      [:h2 "Overlay enter / exit"]
      [:p.demo-note
       "Scrim, sheet, alert, menu and tooltip animate in on first paint — pure CSS
        @keyframes on element presence, no JS state. Toggle the disclosure to remove and
        re-insert the alert below and replay its enter settle (fade + translateY + scale).
        Exit is an attribute contract: the caller sets " [:code "data-state=\"closing\""]
       ", waits for " [:code "animationend"] ", then removes the element — the matching
        exit keyframes ship in this stylesheet (see docs/design.md)."]
      (lg/disclosure "Toggle to replay the enter animation"
                     [[:div.demo-overlay-wrap
                       (lg/scrim)
                       (lg/alert [[:h3 "Enter animation"]
                                  [:p {:style {:font-size "13px" :opacity ".85" :margin "8px 0 0"}}
                                   "This alert just ran liquid-glass-alert-enter."]]
                                 {:label "Enter animation"})]]
                     {:open? true})]

     [:section.demo-section
      [:h2 "Sheet"]
      [:div.demo-sheet-wrap
       [:div.liquid-glass__scrim]
       (lg/sheet [[:h3 "Share"]
                  [:p "Bottom-sheet surface — thick material, floating elevation."]
                  (lg/button "Done" {:act :dismiss})]
                 {:label "Share"})]]

     [:section.demo-section
      [:h2 "Alert"]
      [:div.demo-overlay-wrap
       (lg/scrim {:act :dismiss})
       (lg/alert [[:h3 "Delete conversation?"]
                  [:p {:style {:font-size "13px" :opacity ".85" :margin "8px 0 16px"}}
                   "This action cannot be undone."]
                  [:div.demo-inline {:style {:justify-content "center"}}
                   (lg/button "Cancel" {:act :cancel})
                   (lg/button "Delete" {:act :confirm-delete})]]
                 {:label "Delete conversation?"})]]

     [:section.demo-section
      [:h2 "Menu"]
      [:div.demo-overlay-wrap
       (lg/menu [{:label "Rename" :act :rename}
                 {:label "Duplicate" :act :duplicate}
                 {:label "Delete" :act :delete :disabled true}])]]

     [:section.demo-section
      [:h2 "List"]
      [:div.demo-list-wrap
       (lg/list-view [(lg/list-row "Notifications" {:trailing "On" :act :open-notifications})
                      (lg/list-row "Privacy" {:trailing "›" :act :open-privacy})
                      (lg/list-row "About" {:trailing "›" :act :open-about})]
                     {:surface :thick})]]

     [:section.demo-section
      [:h2 "Pointer specular"]
      [:p.demo-note
       "Move the pointer across any glass surface: the inlined specular.js (~70 lines, one
        rAF-throttled pointermove listener) writes --liquid-glass-pointer-x/-y onto the
        element under the cursor and the hidden liquid-glass__specular span becomes a
        tracking radial highlight. Everything is gated behind the .liquid-glass-js class
        the script adds — no JS (or prefers-reduced-motion), no highlight."]
      (lg/panel [[:h3 "Try it here"]
                 [:p "A wide, calm surface makes the tracking highlight easy to see —
                      but every button, toolbar and field on this page responds too."]]
                {:surface :thick})]

     [:section.demo-section
      [:h2 "Lens"]
      (lg/lens-filter-defs)
      [:p.demo-note
       "Optional SVG displacement lens: feTurbulence + feDisplacementMap (filter id
        liquid-glass-lens, scale/frequency tokenized) appended to the backdrop chain under
        @supports (backdrop-filter: url(#…)). Chromium composites the refraction (partial
        support); Safari and Firefox fall back to the plain blurred material — the two
        panels below look identical there. Opt-in per surface via .liquid-glass--lens."]
      [:div.demo-lens-grid
       (lg/panel [[:h3 "Lens refraction"] [:p "backdrop-filter: blur(…) … url(#liquid-glass-lens)"]]
                 {:surface :thick :class "liquid-glass--lens"})
       (lg/panel [[:h3 "Plain blur"] [:p "The same thick surface without the lens modifier."]]
                 {:surface :thick})]]

     [:section.demo-section
      [:h2 "Dark"]
      [:div.demo-dark
       [:div.demo-row
        (lg/panel [[:p "thick / dark"]] {:surface :thick})
        (lg/toolbar [(lg/icon-button "☰") (lg/badge "3")])
        (lg/button "Continue")
        (lg/toggle {:checked true})
        (lg/text-field {:placeholder "Name"})]]]

     [:footer.demo-footer "kotoba-lang/liquid-glass-ui · ADR-2607011900"]]
    (specular-script)]])

(defn html []
  (str "<!doctype html>\n" (h/->html (page)) "\n"))

(defn write!
  ([] (write! "docs"))
  ([dir]
   (let [out (io/file dir "index.html")]
     (io/make-parents out)
     (spit out (html))
     out)))

(defn -main [& _]
  (println (str "wrote " (.getPath (write!)))))
