(ns slides.render
  (:require [slides.model :as model]
            [slides.routes :as routes]))

(defn esc [s]
  (-> (str (or s ""))
      (.replace "&" "&amp;")
      (.replace "<" "&lt;")
      (.replace ">" "&gt;")
      (.replace "\"" "&quot;")))

(defn app-card [{:keys [href label app]}]
  (let [cfg (routes/apps app)
        fallback-label (some-> app name)]
    (str "<a class=\"card\" href=\"" (esc href) "\">"
         "<span>" (esc (or label (:slides/title cfg) fallback-label)) "</span>"
         "<small>" (esc (:slides/host cfg)) "</small>"
         "</a>")))

(defn item-row [it]
  (let [it (if (map? it)
             it
             {:slides/id (str it)
              :slides/kind :unknown
              :slides/title ""})]
    (str "<tr><td>" (esc (:slides/id it)) "</td>"
         "<td>" (esc (:slides/kind it)) "</td>"
         "<td>" (esc (:slides/title it)) "</td></tr>")))

(defn- render-items [ws]
  (if (map? (:slides/items ws))
    (model/items ws)
    []))

(defn- item-sort-key [it]
  (str (if (map? it)
         (:slides/id it)
         it)))

(defn index-html
  ([] (index-html (model/seed-workspace)))
  ([ws]
   (str "<!doctype html><html lang=\"en\"><head>"
        "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        "<title>GFTD Workspace</title>"
        "<style>"
        "body{margin:0;font-family:Inter,system-ui,sans-serif;background:#f7f8fb;color:#17202a}"
        "main{max-width:1040px;margin:0 auto;padding:48px 20px}"
        "h1{font-size:40px;line-height:1.05;margin:0 0 14px}"
        "p{color:#4d5b6a;max-width:760px}"
        ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin:28px 0}"
        ".card{display:flex;flex-direction:column;gap:8px;text-decoration:none;color:#17202a;background:white;border:1px solid #d8dee8;border-radius:8px;padding:16px}"
        ".card:hover{border-color:#496b9a}"
        ".card span{font-weight:700}.card small{color:#64748b}"
        "table{width:100%;border-collapse:collapse;background:white;border:1px solid #d8dee8;border-radius:8px;overflow:hidden}"
        "th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #edf0f5;font-size:14px}"
        "th{background:#eef2f7;color:#334155}"
        "</style></head><body><main>"
        "<h1>GFTD Workspace</h1>"
        "<p>Portable CLJC model for slides, docs, drive, and sheets. The runtime surface is pure EDN; web hosts can render these four apps from the same workspace graph.</p>"
        "<section class=\"grid\">" (apply str (map app-card (routes/nav))) "</section>"
        "<h2>Seed Graph</h2><table><thead><tr><th>ID</th><th>Kind</th><th>Title</th></tr></thead><tbody>"
        (apply str (map item-row (sort-by item-sort-key (render-items ws))))
        "</tbody></table>"
        "</main></body></html>")))
