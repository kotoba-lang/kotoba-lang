(ns cloud-kotoba-dds.code
  "Code block pattern (design.md: reference / docs): the block a person
   READS, then COPIES — a figure with a language label, a copy action and
   a <pre> whose tokens are coloured server-side.

   Owner observation 2026-09-15 on kotoba.cloud/docs (Quickstart): the code
   area was hard to read. One colour for everything; a block inverted
   against the page (label-on-system-background — on the dark theme a
   bright grey slab with a black border); the long curl line clipped at
   the block's right edge with no sign it scrolls; nothing to copy with.
   Each of those is now a property of the pattern, not of a page.

   Highlighting is a pure tokenizer over a rule table per language —
   no highlight.js: a script-src 'self' host cannot load one, and a
   document whose colours arrive with a runtime is not the document it
   claims to be (shinkansen :document = one self-contained file). The
   colours are DADS ramp steps chosen for AA contrast on BOTH surfaces
   (`syntax-tokens` carries the measurement) and follow jp-go-dds.dark's
   primitive mirror unmodified — no second palette.

   The block carries shinkansen.audit's markers: `data-lang` on the <pre>
   and `language-<x>` on the <code> (:code-language — a block that names
   no language is a wall of one colour and cannot be announced), and a
   rule that lets the <pre> scroll or wrap (:pre-overflow).

   Load the jp-go-dds token bridge before css. Pure hiccup + CSS; the
   browser side is `script`, one JS string the host ships as a file
   (the copy control is emitted hidden and revealed by it, so a host
   that ships no script shows no dead control)."
  (:require [kotoba.lang.text :as str]))

;; --- languages -----------------------------------------------------------
;; A rule is {:class kw :re regex [:inner kw]} tried in order at a token
;; boundary (start of text, or after a non-word character — so `-x` inside
;; `kc_pat-x` is not a flag and `#` inside a URL is not a comment). Every
;; regex is anchored with ^ and scanned against the remaining text.
;; :inner :vars re-tokenizes `$NAME` / `${NAME}` inside a double-quoted
;; shell string; :inner :json tokenizes a single-quoted shell string whose
;; body is a JSON document (the `-d '{…}'` of every curl example).

(def ^:private dq-string #"^\"(?:[^\"\\]|\\[\s\S])*\"")

(def languages
  "Language id → {:label display :rules [...] :cmd? bool :aliases #{...}}.
   :cmd? marks the first bare word of a line (and the one after | ; &&)
   as the command — shell only."
  {:bash {:label "Shell" :cmd? true
          :aliases #{:sh :shell :zsh :curl :console}
          :rules [{:class :comment :re #"^#[^\n]*"}
                  {:class :string :re dq-string :inner :vars}
                  {:class :string :re #"^'[^']*'" :inner :json}
                  {:class :var :re #"^\$(?:\{[^}]*\}|[A-Za-z_][A-Za-z0-9_]*|[0-9@#?*!$-])"}
                  {:class :flag :re #"^--?[A-Za-z][A-Za-z0-9-]*"}
                  {:class :url :re #"^[a-z][a-z0-9+.-]*://[^\s\"'\\)]+"}
                  {:class :number :re #"^\d+(?:\.\d+)?(?![A-Za-z0-9_.-])"}
                  {:class :op :re #"^(?:\|\||&&|[|;&<>]+|\\\n|\\$)"}]}
   :json {:label "JSON"
          :aliases #{:jsonc :json5}
          :rules [{:class :comment :re #"^(?://|#)[^\n]*"}
                  {:class :key :re #"^\"(?:[^\"\\]|\\[\s\S])*\"(?=\s*:)"}
                  {:class :string :re dq-string}
                  {:class :number :re #"^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?(?![A-Za-z0-9_.-])"}
                  {:class :literal :re #"^(?:true|false|null)(?![A-Za-z0-9_-])"}
                  {:class :punct :re #"^[{}\[\]:,]"}]}
   :clojure {:label "Clojure"
             :aliases #{:clj :cljs :cljc :cljk :edn :kotoba}
             :rules [{:class :comment :re #"^;[^\n]*"}
                     {:class :string :re dq-string}
                     {:class :key :re #"^:[^\s\[\]{}()\"'`,;]+"}
                     {:class :char :re #"^\\(?:newline|space|tab|return|u[0-9a-fA-F]{4}|.)"}
                     {:class :number :re #"^[+-]?\d+(?:\.\d+)?(?:[MN]|/\d+)?(?![A-Za-z0-9_.-])"}
                     {:class :literal :re #"^(?:true|false|nil)(?=[\s\[\]{}()\",;]|$)"}
                     {:class :special :re #"^(?:defn-|defn|defmacro|defmulti|defmethod|defprotocol|defrecord|deftype|def|ns|let|letfn|fn|if-let|if-not|if|when-let|when-not|when|cond->>|cond->|cond|case|do|loop|recur|require|import|try|catch|finally|throw|->>|->|as->|doto|for|doseq|dotimes|and|or|not)(?=[\s\[\]{}()\"]|$)"}
                     {:class :punct :re #"^[()\[\]{}]"}
                     {:class :op :re #"^(?:#_|#'|#\{|#\(|['`~@^]|~@)"}]}
   :http {:label "HTTP"
          :rules [{:class :comment :re #"^(?://|#)[^\n]*"}
                  {:class :number :re #"^[1-5]\d{2}(?=\s|$)"}
                  {:class :special :re #"^(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)(?=\s)"}
                  {:class :key :re #"^[A-Za-z][A-Za-z0-9-]*(?=:\s)"}
                  {:class :string :re dq-string}
                  {:class :url :re #"^[a-z][a-z0-9+.-]*://[^\s\"'\\)]+"}
                  {:class :literal :re #"^(?:true|false|null)(?![A-Za-z0-9_-])"}]}
   :text {:label nil :rules []}})

(defn- resolve-lang
  "keyword/string → the languages entry key; unknown → :text."
  [lang]
  (let [k (cond (keyword? lang) lang
                (string? lang) (keyword (str/lower (str/trim lang)))
                :else :text)]
    (or (when (contains? languages k) k)
        (some (fn [[id {:keys [aliases]}]] (when (contains? (or aliases #{}) k) id)) languages)
        :text)))

(defn- word-char? [c]
  (boolean (and c (re-find #"^[A-Za-z0-9_]$" (str c)))))

(defn- match-at
  "First rule whose regex matches at the start of `rest`; returns
   [rule text] or nil."
  [rules rest]
  (some (fn [{:keys [re] :as rule}]
          (when-let [m (re-find re rest)]
            (let [text (if (vector? m) (first m) m)]
              (when (pos? (count text)) [rule text]))))
        rules))

(defn- tokenize-vars
  "Split a double-quoted shell string into plain / :var tokens."
  [s]
  (loop [s s out []]
    (if-let [m (re-find #"\$(?:\{[^}]*\}|[A-Za-z_][A-Za-z0-9_]*)" s)]
      (let [i (str/index-of s m)]
        (recur (subs s (+ i (count m)))
               (cond-> out
                 (pos? i) (conj [nil (subs s 0 i)])
                 true (conj [:var m]))))
      (cond-> out (pos? (count s)) (conj [nil s])))))

(defn tokenize
  "Tokens of `src` for `lang`: a vector of [class text] where class is
   nil for plain text, or a rule :class, or [:string inner-tokens] when
   the rule re-tokenized the string's body. Concatenating the texts
   (recursively) yields `src` verbatim — the copy control reads the DOM
   text, so nothing may be lost or normalised here."
  [lang src]
  (let [lang (resolve-lang lang)
        {:keys [rules cmd?]} (get languages lang)
        src (str src)
        n (count src)]
    (loop [pos 0 plain-from 0 out [] expect-cmd cmd?]
      (if (>= pos n)
        (cond-> out (< plain-from n) (conj [nil (subs src plain-from)]))
        (let [prev (when (pos? pos) (nth src (dec pos)))
              cur (nth src pos)
              ;; a token may start after a word character unless it would
              ;; itself begin like one (`-x` in kc_pat-x, `#` in a#b, `$` in a$b)
              boundary? (or (not (word-char? prev))
                            (not (re-find #"^[A-Za-z0-9_$#-]$" (str cur))))
              flush (fn [out] (cond-> out (< plain-from pos) (conj [nil (subs src plain-from pos)])))
              hit (when boundary? (match-at rules (subs src pos)))]
          (cond
            hit
            (let [[{:keys [class inner]} text] hit
                  len (count text)
                  tok (case inner
                        :vars [class (tokenize-vars text)]
                        :json (if (re-find #"^'\s*[\[{]" text)
                                [class (into [[nil "'"]] (conj (tokenize :json (subs text 1 (dec len))) [nil "'"]))]
                                [class text])
                        [class text])
                  ;; after a pipe / ; / && the next bare word is a command again;
                  ;; after a continuation the line goes on (no command)
                  expect (boolean (and cmd? (= class :op) (re-find #"[|;&]" text)))]
              (recur (+ pos len) (+ pos len) (conj (flush out) tok) expect))

            ;; the command word of a shell line
            (and expect-cmd boundary? (word-char? cur))
            (let [m (re-find #"^[A-Za-z0-9_./-]+" (subs src pos))
                  len (count m)]
              (recur (+ pos len) (+ pos len) (conj (flush out) [:cmd m]) false))

            ;; a newline that is not a continuation starts a new line: expect a command
            (= cur \newline)
            (recur (inc pos) plain-from out (boolean (and cmd? (not= prev \\))))

            :else
            (recur (inc pos) plain-from out
                   (boolean (and expect-cmd (re-find #"^\s$" (str cur)))))))))))

(defn tokens->hiccup
  "[class text] tokens → hiccup children (strings and spans)."
  [toks]
  (mapv (fn [[class text]]
          (cond
            (nil? class) text
            (vector? text) (into [:span {:class (str "ck-tok-" (name class))}] (tokens->hiccup text))
            :else [:span {:class (str "ck-tok-" (name class))} text]))
        toks))

(defn tokens->text
  "The verbatim source a token vector stands for (the round-trip the
   tokenizer promises; tests pin it)."
  [toks]
  (apply str (map (fn [[_ text]] (if (vector? text) (tokens->text text) text)) toks)))

;; --- the block -----------------------------------------------------------

(defn- lang-label [lang]
  (get-in languages [(resolve-lang lang) :label]))

(defn block
  "A code block. opts:
     :lang        keyword/string — :bash :json :clojure :http :text (+ aliases)
     :title       caption text; defaults to the language's label
     :wrap?       true → long lines wrap (pre-wrap, anywhere); false → the
                  <pre> scrolls horizontally and is keyboard-focusable.
                  Default true: a clipped line was the measured failure,
                  and the copy control keeps the unbroken text
     :copy?       emit the copy control (default true)
     :copy-label / :copied-label  the control's labels (host-localised)
     :id          id for the <pre>
   Emits <figure class=ck-code data-lang> [figcaption bar] <pre data-lang>
   <code class=language-x>tokens</code></pre>."
  ([src] (block {} src))
  ([{:keys [lang title wrap? copy? copy-label copied-label id]
     :or {lang :text wrap? true copy? true copy-label "Copy" copied-label "Copied"}}
    src]
   (let [lang (resolve-lang lang)
         lname (name lang)
         caption (or title (lang-label lang))
         code (into [:code {:class (str "language-" lname)}]
                    (tokens->hiccup (tokenize lang src)))]
     [:figure {:class "ck-code" :data-lang lname :data-wrap (boolean wrap?)}
      (when (or caption copy?)
        [:figcaption {:class "ck-code__bar"}
         [:span {:class "ck-code__lang"} (or caption "")]
         (when copy?
           [:button {:type "button" :class "ck-code__copy dads-button"
                     :data-type "outline" :data-size "sm"
                     :data-ck-code "copy" :data-copied copied-label
                     :aria-live "polite" :hidden true}
            copy-label])])
      [:pre (cond-> {:data-lang lname}
              id (assoc :id id)
              (not wrap?) (assoc :tabindex "0"))
       code]])))

(defn inline
  "Inline code: [:code.ck-code-inline text]."
  [text]
  [:code {:class "ck-code-inline"} (str text)])

;; --- css -----------------------------------------------------------------

(def syntax-tokens
  "The syntax colours, one home. Values are DADS ramp steps (jp-go-dds
   vendored palette) with the --hig-* bridge colour as fallback; the dark
   theme mirrors each step through jp-go-dds.dark (900 → 300, 800 → 400)
   so there is no second palette. Measured 2026-09-15 against the two
   surfaces the block sits on — light gray-50 #f2f2f2 / dark gray-900
   #1a1a1a — WCAG contrast ratio light / dark:
     comment  neutral-gray-600 / -300   5.13 / 8.30
     string   green-900 / -300          7.39 / 8.41
     key      key-900 (blue) / -300     9.91 / 8.76
     var      purple-800 / -400         8.25 / 6.65
     flag     orange-900 / -300         5.45 / 9.06
     number   cyan-900 / -300           5.21 / 11.58
     literal  magenta-800 / -400        5.70 / 6.60
   (The bridge's --hig-palette-green = green-700 measures 3.84 on the
   light surface and is why the fallback is not the value.)"
  {"--ck-code-comment" "var(--hig-color-secondary-label)"
   "--ck-code-string" "var(--color-primitive-green-900,var(--hig-palette-green))"
   "--ck-code-key" "var(--hig-color-tint)"
   "--ck-code-var" "var(--color-primitive-purple-800,var(--hig-palette-purple))"
   "--ck-code-flag" "var(--color-primitive-orange-900,var(--hig-palette-orange))"
   "--ck-code-number" "var(--color-primitive-cyan-900,var(--hig-palette-cyan))"
   "--ck-code-literal" "var(--color-primitive-magenta-800,var(--hig-palette-pink))"})

(def css
  "Token-contract CSS for the code block. App CSS may restyle spacing and
   the bar; the syntax colours are the --ck-code-* properties above (override
   them on .ck-code, never the .ck-tok-* rules), and the overflow / wrap rule
   on the <pre> is what :pre-overflow measures."
  (str
   ".ck-code{" (apply str (map (fn [[k v]] (str k ":" v ";")) (sort syntax-tokens)))
   "margin-block:var(--hig-spacing-4);border:1px solid var(--hig-color-separator);border-radius:var(--hig-radius-sm);background:var(--hig-color-secondary-system-background);color:var(--hig-color-label);overflow:hidden;max-inline-size:100%;min-inline-size:0}"
   ".ck-code__bar{display:flex;align-items:center;justify-content:space-between;gap:var(--hig-spacing-2);min-block-size:2.5rem;padding:var(--hig-spacing-1) var(--hig-spacing-2) var(--hig-spacing-1) var(--hig-spacing-4);border-block-end:1px solid var(--hig-color-separator);background:var(--hig-color-tertiary-system-fill);font-size:var(--hig-text-caption1-font-size);color:var(--hig-color-secondary-label)}"
   ".ck-code__lang{font-weight:600;letter-spacing:.04em;text-transform:uppercase;min-inline-size:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
   ".ck-code__copy{flex:none;min-block-size:2rem}"
   ".ck-code__copy[data-state=copied]{color:var(--ck-code-string)}"
   ".ck-code__copy[data-state=failed]{color:var(--hig-palette-red,var(--hig-color-label))}"
   ;; the block itself: mono, readable size, scrolls sideways unless wrapping
   ".ck-code pre{margin:0;padding:var(--hig-spacing-4);overflow-x:auto;overscroll-behavior-x:contain;font-family:var(--hig-font-mono);font-size:var(--hig-text-subheadline-font-size);line-height:1.6;tab-size:2;white-space:pre;color:inherit;-webkit-text-size-adjust:100%}"
   ".ck-code pre:focus-visible{outline:2px solid var(--hig-color-tint);outline-offset:-2px}"
   ".ck-code[data-wrap] pre{white-space:pre-wrap;overflow-wrap:anywhere}"
   ".ck-code code{font:inherit;color:inherit;background:none;padding:0}"
   ;; tokens
   ".ck-tok-comment{color:var(--ck-code-comment);font-style:italic}"
   ".ck-tok-string{color:var(--ck-code-string)}"
   ".ck-tok-key{color:var(--ck-code-key)}"
   ".ck-tok-var{color:var(--ck-code-var)}"
   ".ck-tok-flag{color:var(--ck-code-flag)}"
   ".ck-tok-number{color:var(--ck-code-number)}"
   ".ck-tok-literal{color:var(--ck-code-literal);font-weight:600}"
   ".ck-tok-cmd{font-weight:700}"
   ".ck-tok-special{color:var(--ck-code-key);font-weight:600}"
   ".ck-tok-char{color:var(--ck-code-string)}"
   ".ck-tok-url{text-decoration:underline;text-decoration-color:var(--hig-color-separator);text-underline-offset:.15em}"
   ".ck-tok-punct,.ck-tok-op{color:var(--hig-color-secondary-label)}"
   ;; inline code
   ".ck-code-inline{font-family:var(--hig-font-mono);font-size:.9em;padding:.1em .4em;border-radius:var(--hig-radius-xs);background:var(--hig-color-tertiary-system-fill);color:inherit;overflow-wrap:anywhere}"
   ;; phone band: tighter padding, one step smaller
   "@media(max-width:480px){.ck-code pre{padding:var(--hig-spacing-3);font-size:var(--hig-text-footnote-font-size)}.ck-code__bar{padding-inline-start:var(--hig-spacing-3)}}"))

;; --- browser runtime -----------------------------------------------------

(def script
  "The copy control's runtime as ONE JS string the host ships as its own
   file (script-src 'self'). `cloudKotobaCode.enhance(root?)` reveals every
   [data-ck-code=copy] whose page can write the clipboard, copies the
   <pre>'s text on click, shows the copied label for a moment (aria-live
   announces it) and restores the original. Runs once on load."
  "(function(){var C=window.cloudKotobaCode=window.cloudKotobaCode||{};
C.enhance=function(root){root=root||document;var can=!!(navigator.clipboard&&navigator.clipboard.writeText);if(!can)return;
root.querySelectorAll('[data-ck-code=copy]').forEach(function(b){b.hidden=false;if(b.getAttribute('data-ck-bound'))return;b.setAttribute('data-ck-bound','1');var label=b.textContent;
b.addEventListener('click',function(){var fig=b.closest('.ck-code');var pre=fig&&fig.querySelector('pre');if(!pre)return;
navigator.clipboard.writeText(pre.textContent).then(function(){b.textContent=b.getAttribute('data-copied')||'Copied';b.setAttribute('data-state','copied');
setTimeout(function(){b.textContent=label;b.removeAttribute('data-state');},1600);},function(){b.setAttribute('data-state','failed');});});});};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',function(){C.enhance();});else C.enhance();})();")
