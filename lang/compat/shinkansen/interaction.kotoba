(ns shinkansen.interaction
  "The browser side of a shinkansen document — the interaction increment
  (the client half of maturity axis C, and what every product had been
  writing for itself).

  Measured 2026-09-16 before this namespace existed: cloud-itonami-app's
  `interaction.js` is 14,352 lines and re-implements, by hand, what the
  shared components also each re-implement in their own `script` strings
  (cloud-kotoba-dds shell / chat / bot): finding elements by hook, wiring
  click and submit handlers one element at a time, rendering a list into a
  container, consuming a `data: <json>` run stream, hydrating mount points.
  Owner instruction the same day: 「shinkansen webframework も更新 —
  interaction などを共通化」.

  What the framework owns from here, and what stays with the host:

    framework  the CONTRACT — how a document names an action, how a run
               stream is read, how a mount point is found — and ONE runtime
               that implements it (`runtime`, a JS string the host ships as
               its own file or inlines).
    host       what an action DOES, what a stream event MEANS, what a mount
               renders. Handlers are registered by the host; the runtime
               never guesses.

  The contract, in three parts:

  1. Actions. A control names its event with `data-action=\"<event-id>\"`
     and, optionally, `data-params` (a JSON object). The runtime installs
     ONE delegated listener for click and submit and calls the handler the
     host registered for that id with `(params, element, event)`. Event
     ids are the same ids `shinkansen.actions` declares (`:cart/add` →
     `\"cart/add\"`), so a document's controls and its dispatch surface are
     one vocabulary — `undeclared-actions` measures the gap, and
     `shinkansen.audit` scores it when a declaration is supplied.
  2. Run streams. `streamRun(source, handlers, init)` reads a run body
     line by line and calls `handlers.onEvent(frame)` per JSON frame,
     `handlers.onClose()` at end of stream. Two wire shapes, one reader:
     Hermes-shaped SSE (`data: <json>` lines, `[DONE]` and comment lines
     skipped) and newline-delimited JSON (a line that is itself an
     object) — the JVM-free Hermes gateway speaks the first, the Bot loop's
     `/api/bots/:id/messages/stream` the second (cloud-itonami-app
     docs/hermes-bot-mode-compatibility.md), and a chat client should not
     carry two readers for one screen. `source` is a URL or a function
     `(init) => Promise<Response>` — the host owns authentication and
     retry, the framework owns the reading. A non-2xx response reaches
     `handlers.onError({status, response})` unread, so the host can read
     its own error body. Returns `{abort(reason)}`.
  3. Hydration. `hydrate(name, fn)` runs `fn(element)` for every
     `[data-hydrate=\"<name>\"]` not yet hydrated and marks it; the mount
     is server-rendered, the browser only fills.

  5. Live regions (shinkansen.live, 2026-09-17). `live(mount, opts)` keeps
     a keyed set of rows fed by frames (snapshot / delta / heartbeat) from
     a poll or an SSE stream and PATCHES the region — a node per key,
     created once, patched in place, moved or removed — instead of
     replacing it; a `<select>` is rebuilt only when its option set
     changed (`live.options`). The frame algebra is `shinkansen.live/
     reconcile`; the static promises (reserved space, runtime shipped)
     are the audit axis `:live-stable`.

  4. Framework actions. Two choices every product makes are the
     framework's: the theme (`theme/set {mode}`, shinkansen.theme) and the
     language (`locale/set {locale}`, shinkansen.locale). The runtime
     installs `shinkansen.theme` / `shinkansen.locale` and answers both
     actions itself; a host may override with `on`. `framework-actions` is
     their declaration, merged into every audit.

  Pure .cljc: the contract functions take and return data; `runtime` is a
  string. No js/ in this namespace."
  (:require [clojure.string :as str]
            [shinkansen.theme :as theme]
            [shinkansen.locale :as locale]
            [shinkansen.live :as live]))

(def framework-actions
  "The events the runtime answers on its own: theme/set and locale/set.
   `undeclared-actions` and the audit axis treat them as declared everywhere."
  {:events (merge (:events theme/actions) (:events locale/actions))})

;; ---------- contract (hiccup side) ----------

(defn event-id->action
  "`:cart/add` → \"cart/add\"; a string passes through."
  [event-id]
  (if (keyword? event-id)
    (str (when-let [n (namespace event-id)] (str n "/")) (name event-id))
    (str event-id)))

(defn action->event-id
  "\"cart/add\" → :cart/add; \"select\" → :select."
  [s]
  (let [s (str s)
        i (str/index-of s "/")]
    (if (and i (pos? i))
      (keyword (subs s 0 i) (subs s (inc i)))
      (keyword s))))

(defn- json-scalar [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (number? v) (str v)
    :else (str "\"" (-> (str v)
                        (str/replace "\\" "\\\\")
                        (str/replace "\"" "\\\"")
                        (str/replace "\n" "\\n"))
               "\"")))

(defn params-json
  "A flat map of scalars → the JSON object text `data-params` carries.
   Keys are keywords or strings; nested values are refused by name — a
   control's params are identifiers, not documents."
  [params]
  (doseq [[k v] params]
    (when (or (coll? v) (fn? v))
      (throw (ex-info "data-params holds scalars only"
                      {:type :interaction/params-not-scalar :key k}))))
  (str "{"
       (str/join "," (for [[k v] (sort-by (comp str first) params)]
                       (str (json-scalar (if (keyword? k) (name k) (str k))) ":" (json-scalar v))))
       "}"))

(defn action-attrs
  "The attributes a control carries to name its action:
     (action-attrs :bots/select {:id \"bot-1\"})
     → {:data-action \"bots/select\" :data-params \"{\\\"id\\\":\\\"bot-1\\\"}\"}
   Merge into the element's attrs; the runtime does the rest."
  ([event-id] (action-attrs event-id nil))
  ([event-id params]
   (cond-> {:data-action (event-id->action event-id)}
     (seq params) (assoc :data-params (params-json params)))))

;; ---------- contract (document side, for the audit) ----------

(defn document-actions
  "Every distinct `data-action` id a document's HTML names, as event ids."
  [html]
  (->> (re-seq #"data-action=[\"']([^\"']+)[\"']" (str html))
       (map second)
       distinct
       (mapv action->event-id)))

(defn undeclared-actions
  "The event ids a document's controls name that `decl` (the
   `shinkansen.actions` declaration, `{:events {…}}`) does not declare.
   Empty means every control has a dispatch target; a document with no
   controls is also empty — not a pass by itself, which is why the audit
   axis reports the count it scanned."
  [decl html]
  (let [declared (merge (:events framework-actions) (:events decl))]
    (vec (remove #(contains? declared %) (document-actions html)))))

;; ---------- the runtime ----------

(def runtime
  "The one browser runtime for the contract. Exposes
   `globalThis.shinkansen = {on, off, dispatch, streamRun, hydrate, params, theme, locale, live}`.
   The host ships it as its own file or inlines it once; nothing in it is
   product-specific."
  (str
   "(function(){"
   "var handlers={};"
   "function params(el){var raw=el.getAttribute('data-params');if(!raw)return {};try{return JSON.parse(raw);}catch(e){return {};}}"
   "function on(id,fn){handlers[id]=fn;return function(){if(handlers[id]===fn)delete handlers[id];};}"
   "function off(id){delete handlers[id];}"
   "function dispatch(id,p,el,ev){var fn=handlers[id];if(!fn)return false;fn(p||{},el||null,ev||null);return true;}"
   "function delegate(ev){var el=ev.target&&ev.target.closest?ev.target.closest('[data-action]'):null;if(!el)return;"
   "if(ev.type==='submit'&&el.tagName!=='FORM')return;"
   "if(ev.type==='click'&&el.tagName==='FORM')return;"
   "var id=el.getAttribute('data-action');if(!handlers[id])return;"
   "if(ev.type==='submit'||el.tagName==='A'||el.tagName==='BUTTON')ev.preventDefault();"
   "handlers[id](params(el),el,ev);}"
   "document.addEventListener('click',delegate);document.addEventListener('submit',delegate);"
   "function streamRun(src,h,init){var ctl=new AbortController();var opts=Object.assign({},init||{});opts.signal=ctl.signal;"
   "var closed=false;function close(){if(closed)return;closed=true;if(h&&h.onClose)h.onClose();}"
   "var started=typeof src==='function'?Promise.resolve().then(function(){return src(opts);}):fetch(src,opts);"
   "started.then(function(r){if(!r.ok){if(h&&h.onError)h.onError({status:r.status,response:r});close();return;}"
   "var reader=r.body.getReader(),dec=new TextDecoder(),buf='';"
   "function drain(final){var lines=buf.split('\\n');buf=final?'':lines.pop();"
   "for(var i=0;i<lines.length;i++){var line=lines[i].trim();if(!line)continue;var body=null;"
   "if(line.indexOf('data:')===0){body=line.slice(5).trim();if(body==='[DONE]')continue;}else if(line.charAt(0)==='{'){body=line;}else continue;"
   "var frame=null;try{frame=JSON.parse(body);}catch(e){continue;}if(h&&h.onEvent)h.onEvent(frame);}}"
   "function step(){return reader.read().then(function(res){if(res.done){drain(true);close();return;}buf+=dec.decode(res.value,{stream:true});drain(false);return step();});}"
   "return step();}).catch(function(e){if(h&&h.onError)h.onError({error:String(e&&e.message||e),aborted:ctl.signal.aborted});close();});"
   "return {abort:function(reason){ctl.abort(reason||'stop');}};}"
   "function hydrate(name,fn){var nodes=document.querySelectorAll('[data-hydrate=\"'+name+'\"]:not([data-hydrated])');"
   "for(var i=0;i<nodes.length;i++){nodes[i].setAttribute('data-hydrated','');fn(nodes[i]);}return nodes.length;}"
   theme/runtime-fragment
   locale/runtime-fragment
   ;; live regions (shinkansen.live, 2026-09-17): the keyed patcher + its source loop
   live/runtime-fragment
   ;; the framework's own actions, answered here; a host's on() replaces them
   "on('theme/set',function(p){theme.set(p.mode);});"
   "on('locale/set',function(p,el){locale.set(p.locale,{href:el&&el.getAttribute('href')});});"
   "globalThis.shinkansen={on:on,off:off,dispatch:dispatch,streamRun:streamRun,hydrate:hydrate,params:params,theme:theme,locale:locale,live:live};"
   "})();"))
