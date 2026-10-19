(ns kami.atom
  "Atom syndication feeds (RFC 4287) as data — 'hiccup for feeds'. An Atom feed is an XML document of a
   <feed> with <entry> children, so it maps onto EDN directly — a blog/news feed is composable data you
   fork and diff. A fresh web-syndication domain in the kami.* family (pairs with kami.html), built on
   kami.xml. `.cljc`.

   Builders take a property map; `link` becomes <link href=…/>, `author` an <author><name>…, the rest
   <name>value</name>:
     (entry {:title \"Post\" :id \"urn:1\" :updated \"2026-06-25T00:00:00Z\"
             :link \"https://x/1\" :summary \"…\"})
     (feed {:title \"Blog\" :id \"urn:feed\" :updated \"…\" :link \"https://x/\"} entry…)
       ⇒ <?xml?> <feed xmlns=\"http://www.w3.org/2005/Atom\"> … </feed>"
  (:require [kami.xml :as xml]))

(defn- props->els
  "Turn an Atom property map into child element hiccup (order: title/subtitle, link, id, updated,
   author, summary, content) — omitting absent keys."
  [{:keys [title subtitle id updated link summary content author rights]}]
  (remove nil?
    [(when title    [:title title])
     (when subtitle [:subtitle subtitle])
     (when link     [:link {:href link}])
     (when id       [:id id])
     (when updated  [:updated updated])
     (when author   [:author [:name author]])
     (when rights   [:rights rights])
     (when summary  [:summary summary])
     (when content  [:content content])]))

(defn entry
  "An Atom <entry> element (hiccup) from a property map."
  [props] (into [:entry] (props->els props)))

(defn feed
  "Compile an Atom feed document. opts is the feed-level property map; the rest are entries."
  [opts & entries]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
       (xml/xml (into [:feed {:xmlns "http://www.w3.org/2005/Atom"}]
                      (concat (props->els opts) entries)))))   ;; entries are already (entry …) elements
