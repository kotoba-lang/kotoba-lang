(ns shinkansen.live
  "Live regions — the part of a document whose data changes while the
  document is open (a request log, a balance, a job list), measured and
  designed 2026-09-17 on console.kotoba.cloud/requests/:

    the document showed nothing for 5 s, then the header chips popped in
    (layout shift), then the tiles; every 15 s refresh replaced the whole
    table and rebuilt a <select> (scroll and selection lost — 「更新のたびに
    崩れる」); the list endpoint answered in 5–18 s because the authority
    decoded 2,977 multi-megabyte job records to produce 300-byte rows, and
    every other console call queued behind that walk.

  Three things were wrong, and they are three contracts:

  1. WIRE — a live region speaks FRAMES, and a frame is a delta.
       {:kind :snapshot  :cursor c :rows [...]}            the whole set, once
       {:kind :delta     :cursor c :upsert [...] :remove [ids]}   what changed since the cursor
       {:kind :heartbeat :cursor c}                        nothing changed
       {:kind :error     :reason \"…\"}                     named refusal
     The cursor is the server's monotonic clock (ms). The client resumes
     with it (`?since=`) — the transport is polling or an SSE stream, the
     frames are the same, one reader (`shinkansen.interaction/streamRun`
     for the stream, fetch for the poll). A refresh that re-sends the
     whole set is the anti-pattern `:full-refetch`.

  2. SERVER — a live list is served from a PROJECTION, never from the
     records. Rows are bytes, records are megabytes; the store keeps the
     row beside the record (an index entry per row, updated when the record
     is) and the list reads the index in one bounded operation. The
     budget is part of the contract: `frame` carries no field the row does
     not need, and the endpoint answers from the projection in
     O(rows), never O(records × record size).

  3. DOM — the runtime PATCHES a keyed set; it never replaces the region.
     `shinkansen.live(mount, opts)` keeps rows keyed by `opts.key`, creates
     a node once per key (`opts.create`), patches it in place when its
     row changed (`opts.patch`, text nodes only), inserts new keys where
     the order says, removes gone keys — and touches nothing else. Focus,
     selection and scroll survive because nodes survive. A control whose
     option set did not change is not rebuilt (`live.options`).
     The region reserves its space before data arrives (`live-attrs`
     → `min-block-size`), so the first frame fills the space instead of
     shifting the page; the first frame can be INLINED by the edge
     (`snapshot-script`) so the first paint already has the rows.

  The audit axis `:live-stable` (shinkansen.audit) measures what a static
  document can promise: every `[data-live]` region reserves its space and
  the runtime that patches it is shipped. `reconcile` is the pure half of
  the runtime — the same frame algebra in Clojure — so a server can build
  the inlined snapshot and a test can pin the outcome of a frame sequence
  without a DOM.

  Pure .cljc: data in, data out; `runtime-fragment` is a JS string the
  interaction runtime includes."
  (:require [clojure.string :as str]))

;; --- frames ------------------------------------------------------------------

(def kinds #{:snapshot :delta :heartbeat :error})

(defn check-frame
  "A frame's defects, named; empty when it is well-formed."
  [frame]
  (let [kind (keyword (name (or (:kind frame) "")))]
    (cond-> []
      (not (map? frame)) (conj :not-a-map)
      (not (contains? kinds kind)) (conj :unknown-kind)
      (and (contains? #{:snapshot :delta :heartbeat} kind) (not (number? (:cursor frame)))) (conj :cursor-not-a-number)
      (and (= :snapshot kind) (not (sequential? (:rows frame)))) (conj :snapshot-without-rows)
      (and (= :delta kind) (not (or (sequential? (:upsert frame)) (sequential? (:remove frame))))) (conj :delta-without-change)
      (and (= :error kind) (str/blank? (str (:reason frame)))) (conj :error-without-reason))))

(defn snapshot [cursor rows] {:kind :snapshot :cursor cursor :rows (vec rows)})
(defn delta [cursor upsert remove] {:kind :delta :cursor cursor :upsert (vec upsert) :remove (vec remove)})
(defn heartbeat [cursor] {:kind :heartbeat :cursor cursor})

(def empty-state {:rows {} :cursor nil :changed #{} :removed #{}})

(defn reconcile
  "Apply one frame to a keyed state {:rows {id row} :cursor c}. Answers the
   next state with :changed (ids whose row is new or different) and
   :removed (ids gone) — the same algebra the browser runtime runs, so a
   test can pin a frame sequence and a server can build the inlined
   snapshot. A frame with defects is refused by name (:refused)."
  [state key-fn frame]
  (let [defects (check-frame frame)]
    (if (seq defects)
      (assoc state :refused defects :changed #{} :removed #{})
      (let [kind (keyword (name (:kind frame)))
            rows (or (:rows state) {})]
        (case kind
          :snapshot
          (let [incoming (into {} (map (fn [r] [(key-fn r) r]) (:rows frame)))
                changed (into #{} (keep (fn [[id r]] (when (not= r (get rows id)) id)) incoming))
                removed (into #{} (remove #(contains? incoming %) (keys rows)))]
            {:rows incoming :cursor (:cursor frame) :changed changed :removed removed})
          :delta
          (let [upserts (into {} (map (fn [r] [(key-fn r) r]) (:upsert frame)))
                gone (set (:remove frame))
                changed (into #{} (keep (fn [[id r]] (when (not= r (get rows id)) id)) upserts))
                next-rows (as-> rows m (merge m upserts) (apply dissoc m gone))]
            {:rows next-rows :cursor (:cursor frame) :changed changed :removed (into #{} (filter #(contains? rows %) gone))})
          :heartbeat
          (assoc state :cursor (:cursor frame) :changed #{} :removed #{})
          :error
          (assoc state :error (:reason frame) :changed #{} :removed #{}))))))

(defn reconcile-all
  "Fold a frame sequence."
  [state key-fn frames]
  (reduce (fn [s f] (dissoc (reconcile s key-fn f) :changed :removed)) state frames))

;; --- document side ---------------------------------------------------------------

(defn live-attrs
  "The attributes of a live region: `data-live=<name>` and the space it
   reserves before its data arrives (`min-block-size`, a CSS length — the
   height the region will have once filled, so the first frame fills
   instead of shifting the page). `:aspect` may stand in for a chart.
   Optional :source (the poll URL) and :stream (the SSE URL) for the
   runtime to read; :interval in ms."
  [name {:keys [reserve aspect source stream interval]}]
  (cond-> {:data-live (clojure.core/name name)
           :style (cond-> {}
                    reserve (assoc :min-block-size reserve)
                    aspect (assoc :aspect-ratio aspect))}
    source (assoc :data-live-source source)
    stream (assoc :data-live-stream stream)
    interval (assoc :data-live-interval (str interval))))

(defn snapshot-script
  "The first frame, inlined by the edge into the document so the first
   paint already has the rows: `[:script {:type \"application/json\"
   :data-live-snapshot name} json]`. `json` is the frame serialised by the
   caller (the edge decides the serialiser). The runtime reads it before
   its first fetch and starts from its cursor."
  [name json]
  [:script {:type "application/json" :data-live-snapshot (clojure.core/name name)} json])

(def snapshot-placeholder
  "What the static document emits where the edge may inline the first
   frame: an empty snapshot script. The edge replaces it (or leaves it —
   the runtime then fetches)."
  (fn [name] [:script {:type "application/json" :data-live-snapshot (clojure.core/name name)} ""]))

(defn reserved?
  "Whether a live region's element (audit element model: :attrs) reserves
   its space: an inline min-block-size / min-height / aspect-ratio."
  [el]
  (let [style (str (get-in el [:attrs "style"]))]
    (boolean (re-find #"(?:min-block-size|min-height|aspect-ratio)\s*:" style))))

;; --- the runtime -----------------------------------------------------------------

(def runtime-fragment
  "JS: `shinkansen.live(mount, opts)` — the keyed patcher and its source loop.

   opts.key(row) → id                    required
   opts.create(row) → Element            required (called once per id)
   opts.patch(el, row, prev)             required (text nodes only; called when the row changed)
   opts.view(rows) → rows                optional filter / sort of the visible set
   opts.source → url or (cursor) => url  polling (GET, JSON frame or {rows})
   opts.stream → url or (cursor) => url  SSE via streamRun (frames), reconnects with the cursor
   opts.interval ms                      poll / reconnect cadence (default 15000)
   opts.onFrame(frame, live)             after each applied frame (tiles, counts)
   opts.onState({state, at, reason})     live | paused | stale | error | signed-out
                                         (paused: the document is hidden — nothing is fetched
                                         until it is shown again, then it connects at once)
   opts.snapshot                         name of an inlined [data-live-snapshot] to start from

   returns {apply(frame), rows(), row(id), cursor(), render(), stop(), refresh(), options(select, values, label)}"
  (str
   "function live(mount,o){"
   "var rows=new Map(),els=new Map(),seen=new Map(),cur=null,stopped=false,timer=null,errors=0,run=null;"
   "var interval=o.interval||15000;var key=o.key;"
   "function state(s,extra){if(o.onState)o.onState(Object.assign({state:s,at:Date.now(),cursor:cur},extra||{}));}"
   "function apply(f){if(!f||typeof f!=='object')return false;var kind=f.kind||(Array.isArray(f.rows)?'snapshot':null);if(!kind)return false;"
   "if(kind==='error'){state('error',{reason:f.reason});return false;}"
   "if(kind==='heartbeat'){if(typeof f.cursor==='number')cur=f.cursor;render();if(o.onFrame)o.onFrame(f,api);return true;}"
   "var changed=false;"
   "if(kind==='snapshot'){var keep=new Set();(f.rows||[]).forEach(function(r){var id=key(r);keep.add(id);var s=JSON.stringify(r);if(seen.get(id)!==s){rows.set(id,r);seen.set(id,s);changed=true;}});"
   "rows.forEach(function(_,id){if(!keep.has(id)){rows.delete(id);seen.delete(id);var e=els.get(id);if(e&&e.parentNode)e.parentNode.removeChild(e);els.delete(id);changed=true;}});}"
   "else if(kind==='delta'){(f.upsert||[]).forEach(function(r){var id=key(r);var s=JSON.stringify(r);if(seen.get(id)!==s){rows.set(id,r);seen.set(id,s);changed=true;}});"
   "(f.remove||[]).forEach(function(id){if(rows.has(id)){rows.delete(id);seen.delete(id);var e=els.get(id);if(e&&e.parentNode)e.parentNode.removeChild(e);els.delete(id);changed=true;}});}"
   "else return false;"
   "if(typeof f.cursor==='number')cur=f.cursor;if(changed)render();if(o.onFrame)o.onFrame(f,api);return true;}"
   ;; render: the visible order over the keyed set; patch in place, insert where the order says, detach what the view hides
   "var prevRows=new Map();"
   "function render(){if(!mount)return;var list=Array.from(rows.values());if(o.view)list=o.view(list);var want=new Set();var ref=mount.firstChild;"
   "for(var i=0;i<list.length;i++){var r=list[i],id=key(r);want.add(id);var el=els.get(id);"
   "if(!el){el=o.create(r);els.set(id,el);prevRows.set(id,r);mount.insertBefore(el,ref);continue;}"
   "if(prevRows.get(id)!==r){o.patch(el,r,prevRows.get(id));prevRows.set(id,r);}"
   "if(el.parentNode!==mount){mount.insertBefore(el,ref);continue;}"
   "if(el!==ref){mount.insertBefore(el,ref);continue;}"
   "ref=el.nextSibling;}"
   "els.forEach(function(el,id){if(!want.has(id)&&el.parentNode===mount)mount.removeChild(el);});}"
   ;; options: a <select> is rebuilt only when its option set changed; the chosen value survives
   "function options(sel,values,label){if(!sel)return false;var ops=Array.from(sel.options);var have=ops.map(function(op){return op.value;});var fixed=ops.filter(function(op){return op.hasAttribute('data-fixed');}).map(function(op){return op.value;});"
   "var next=fixed.concat(values.filter(function(v){return fixed.indexOf(v)<0;}));if(have.join('\\u0000')===next.join('\\u0000'))return false;"
   "var chosen=sel.value;ops.forEach(function(op){if(!op.hasAttribute('data-fixed'))op.remove();});next.forEach(function(v){if(fixed.indexOf(v)>=0)return;var op=document.createElement('option');op.value=v;op.textContent=label?label(v):v;sel.appendChild(op);});"
   "sel.value=next.indexOf(chosen)>=0?chosen:next[0];return true;}"
   ;; the inlined first frame
   "function inlined(){if(!o.snapshot||typeof document==='undefined')return null;var s=document.querySelector('script[type=\"application/json\"][data-live-snapshot=\"'+o.snapshot+'\"]');if(!s||!s.textContent.trim())return null;try{return JSON.parse(s.textContent);}catch(e){return null;}}"
   ;; sources: poll (GET with ?since=cursor) or stream (SSE frames, reconnect with the cursor)
   "function url(u){if(typeof u==='function')return u(cur);if(cur==null)return u;return u+(u.indexOf('?')>=0?'&':'?')+'since='+encodeURIComponent(cur);}"
   ;; what a scheduled wake-up does: reconnect the stream when there is one, else poll
   "function schedule(ms){if(stopped)return;if(timer)clearTimeout(timer);timer=setTimeout(function(){timer=null;connect();},ms);}"
   "function backoff(){errors++;return Math.min(interval*Math.pow(2,errors),interval*8);}"
   "function tick(){if(stopped)return;if(hidden()){state('paused',{reason:'hidden'});schedule(interval);return;}"
   "fetch(url(o.source),{credentials:'same-origin',headers:{accept:'application/json'}}).then(function(r){if(r.status===401){state('signed-out');stopped=true;return;}"
   "if(!r.ok){state('error',{reason:'http-'+r.status});schedule(backoff());return;}return r.json().then(function(f){errors=0;if(apply(f))state('live');else state('error',{reason:'not-a-frame'});schedule(interval);});})"
   ".catch(function(e){state('error',{reason:String(e&&e.message||e)});schedule(backoff());});}"
   "function stream(){if(stopped||!globalThis.shinkansen||!globalThis.shinkansen.streamRun)return false;if(hidden()){state('paused',{reason:'hidden'});schedule(interval);return true;}"
   "run=globalThis.shinkansen.streamRun(url(o.stream),{onEvent:function(f){errors=0;if(apply(f))state('live');},"
   "onClose:function(){run=null;if(!stopped&&!timer)schedule(interval);},"
   "onError:function(e){if(e&&e.status===401){state('signed-out');stopped=true;return;}state('error',{reason:e&&(e.status?'http-'+e.status:e.error)});schedule(backoff());}},{credentials:'same-origin'});return true;}"
   ;; hidden: nothing is fetched for a document nobody sees; shown again: connect now, not at the next wake-up
   "function hidden(){return typeof document!=='undefined'&&!!document.hidden;}"
   "function connect(){if(o.stream){if(!stream()&&o.source)tick();}else if(o.source)tick();}"
   "function wake(){if(stopped||hidden()||run)return;if(timer){clearTimeout(timer);timer=null;}connect();}"
   "function start(){var first=inlined();if(first)apply(first);if(typeof document!=='undefined'&&document.addEventListener&&(o.stream||o.source))document.addEventListener('visibilitychange',wake);"
   "if(o.stream)connect();else if(o.source){if(first)schedule(interval);else tick();}}"
   "var api={apply:apply,rows:function(){return Array.from(rows.values());},row:function(id){return rows.get(id);},cursor:function(){return cur;},render:render,options:options,"
   "stop:function(){stopped=true;if(timer)clearTimeout(timer);if(run)run.abort('stop');if(typeof document!=='undefined'&&document.removeEventListener)document.removeEventListener('visibilitychange',wake);},"
   "refresh:function(){if(o.stream&&run){run.abort('refresh');stream();}else tick();}};"
   "start();return api;}"))

(def anti-patterns
  "What a live region must not do — the catalogue the audit and the design
   review check against (each was measured on console.kotoba.cloud/requests/,
   2026-09-17)."
  [{:id :full-refetch :what "every tick re-fetches the whole set (195 KB / 15 s for 500 rows)" :instead "a cursor + delta frames"}
   {:id :replace-children :what "the region is rebuilt on every frame (scroll, focus and selection lost)" :instead "a keyed patch (shinkansen.live)"}
   {:id :walk-the-records :what "the list decodes every record to produce rows (5–18 s, and it blocks the object for every other call)" :instead "a projection index beside the record, read in one bounded operation"}
   {:id :nothing-then-everything :what "the region is hidden until its data arrives, then appears (the page shifts)" :instead "reserved space + the first frame inlined by the edge"}
   {:id :rebuild-controls :what "a <select> is rebuilt on every frame (the chosen value and the open list are lost)" :instead "live.options — rebuilt only when the option set changed"}])
