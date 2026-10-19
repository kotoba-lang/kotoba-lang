(ns shinkansen.theme
  "Light / dark / system — the theme contract, framework-wide (owner
  instruction 2026-09-16: 「dark, light, system theme switcher も統合」).

  Measured the same day: three implementations of the same choice were
  alive — jp-go-dds.theme-toggle (a two-state λ switch, storage key
  `kotoba-theme`, `data-theme` on <html>), cloud-itonami-app's
  `data-appearance` toggle (its own key, its own `:root:has(...)` scope,
  a JS cycle that still included appearances the server had pruned), and
  every product page that followed the OS on its own. One contract:

    mode      :light | :dark | :system        what the person chose
    storage   localStorage `kotoba-theme`     \"light\" | \"dark\"; ABSENT = system
    attribute <html data-theme=\"light|dark\">   absent = system (the OS decides)
    css       jp-go-dds.dark/dark-css          `:root:root[data-theme]` beats the
                                              `prefers-color-scheme` media block
    action    theme/set {mode}                 the switcher names it; the runtime
                                              (shinkansen.interaction) answers

  `system` is not a third palette: it is the ABSENCE of an override, so the
  stylesheet's media block decides and follows the OS live. That is why the
  attribute is removed for it rather than set to \"system\" — a value the CSS
  never matches would be a silent light.

  `head-script` applies the stored choice before first paint (after it is a
  flash on every navigation); the runtime keeps the attribute, the storage
  and the switchers in step afterwards. The key is jp-go-dds.theme-toggle's
  own, so a page that still carries the λ toggle and one that carries the
  three-state switcher agree on the choice.

  Pure .cljc; the two scripts are strings."
  (:require [clojure.string :as str]))

(def modes [:light :dark :system])
(def storage-key "kotoba-theme")
(def attribute "data-theme")
(def default-mode :system)

(defn mode?
  "Is `m` one of the three modes (keyword or its name)?"
  [m]
  (boolean (some #(= % (if (keyword? m) m (keyword (str m)))) modes)))

(defn normalize
  "\"dark\" / :dark / \" DARK \" → :dark; anything else → nil (never a guess)."
  [v]
  (let [s (some-> v (cond-> (keyword? v) name) str str/trim str/lower-case not-empty)]
    (when (and s (mode? s)) (keyword s))))

(defn resolve
  "What the page renders for a choice:
     {:mode :dark :effective :dark}
     {:mode :system :effective :light|:dark}   from `system-dark?`
   `stored` is the raw storage value (nil = nothing stored = system)."
  [{:keys [stored system-dark?]}]
  (let [m (or (normalize stored) :system)]
    {:mode m
     :effective (case m :system (if system-dark? :dark :light) m)}))

(defn attribute-value
  "The `data-theme` value for a mode: \"light\" / \"dark\", nil for :system
   (remove the attribute)."
  [mode]
  (case (normalize mode) :light "light" :dark "dark" nil))

(def actions
  "The event the switcher names. Merged into every declaration by
   shinkansen.interaction/framework-actions."
  {:events {:theme/set {:validate (fn [[_ {:keys [mode]}]] (boolean (normalize mode)))}}})

(def head-script
  "Runs in <head>, before paint: stored choice → attribute. Nothing else —
   the switcher and the change listener are the runtime's."
  (str "(function(){try{var v=localStorage.getItem(" (pr-str storage-key) ");"
       "if(v==='dark'||v==='light')document.documentElement.setAttribute(" (pr-str attribute) ",v);"
       "else document.documentElement.removeAttribute(" (pr-str attribute) ");}catch(e){}})();"))

(def runtime-fragment
  "The `shinkansen.theme` object the interaction runtime installs:
     get()        → 'light' | 'dark' | 'system'
     effective()  → 'light' | 'dark'   (what is painted now)
     set(mode)    → applies, stores, fires 'shinkansen:theme' on document
     onChange(fn) → fn({mode, effective}) now and on every change (incl. OS)"
  (str
   "var TK=" (pr-str storage-key) ",TA=" (pr-str attribute) ",R=document.documentElement;"
   "function sysDark(){return !!(window.matchMedia&&matchMedia('(prefers-color-scheme: dark)').matches);}"
   "function tget(){try{var v=localStorage.getItem(TK);return v==='dark'||v==='light'?v:'system';}catch(e){return 'system';}}"
   "function teff(){var a=R.getAttribute(TA);return a==='dark'||a==='light'?a:(sysDark()?'dark':'light');}"
   "function tfire(){document.dispatchEvent(new CustomEvent('shinkansen:theme',{detail:{mode:tget(),effective:teff()}}));}"
   "function tset(mode){mode=String(mode||'system');if(mode!=='dark'&&mode!=='light')mode='system';"
   "if(mode==='system'){R.removeAttribute(TA);try{localStorage.removeItem(TK);}catch(e){}}"
   "else{R.setAttribute(TA,mode);try{localStorage.setItem(TK,mode);}catch(e){}}tfire();return mode;}"
   "function tonChange(fn){fn({mode:tget(),effective:teff()});document.addEventListener('shinkansen:theme',function(e){fn(e.detail);});}"
   "if(window.matchMedia){var tmq=matchMedia('(prefers-color-scheme: dark)');var tf=function(){if(tget()==='system')tfire();};"
   "if(tmq.addEventListener)tmq.addEventListener('change',tf);}"
   "var theme={get:tget,effective:teff,set:tset,onChange:tonChange};"))
