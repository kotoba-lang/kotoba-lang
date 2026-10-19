(ns kami.geojson
  "GeoJSON as data — 'hiccup for geospatial'. GeoJSON is the JSON interchange for geographic features,
   and it is plain JSON, so an EDN structure maps onto it directly — a map layer is composable data you
   fork and diff. A fresh geospatial domain in the kami.* family, serialized via kami.json. `.cljc`.

   Builders return the GeoJSON object model; `geojson` serializes it to a JSON string.
     (point 102.0 0.5)                  → {type Point, coordinates [102.0, 0.5]}   (lng lat [alt])
     (line-string [[102 0] [103 1]])    → LineString
     (polygon [[[0 0] [1 0] [1 1] [0 0]]]) → Polygon (a vector of linear-ring coordinate lists)
     (feature (point 102 0.5) {:name \"A\"})
     (feature-collection f1 f2…)        →  (geojson (feature-collection …)) ⇒ JSON"
  (:require [kami.json :as json]))

(defn point
  "A Point geometry from longitude latitude [altitude]."
  [& coords] {:type "Point" :coordinates (vec coords)})

(defn line-string
  "A LineString geometry from a vector of [lng lat] positions."
  [coords] {:type "LineString" :coordinates coords})

(defn polygon
  "A Polygon geometry from a vector of linear rings (each a vector of [lng lat] positions)."
  [rings] {:type "Polygon" :coordinates rings})

(defn feature
  "A Feature wrapping a geometry with optional properties."
  ([geometry] (feature geometry {}))
  ([geometry props] {:type "Feature" :geometry geometry :properties props}))

(defn feature-collection
  "A FeatureCollection of features."
  [& features] {:type "FeatureCollection" :features (vec features)})

(defn geojson
  "Serialize a GeoJSON object to a JSON string."
  [x] (json/json x))
