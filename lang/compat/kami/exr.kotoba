(ns kami.exr
  "OpenEXR header as data — 'hiccup for the EXR header'. EXR pixels are binary (HDR scanlines/tiles),
   so — like Alembic/VDB — only the HEADER is structured, diff-able data: channel layout, data/display
   windows, compression, line order, and custom typed attributes. This compiles an EDN header map to
   the canonical `exrheader`-style attribute listing, so a render's channel/metadata contract is
   composable data you fork and review (the pixel payload stays external). `.cljc`.

   NOTE: this is the header/metadata layer only — authoring a real binary .exr needs an OpenEXR encoder
   (future work). `exrheader`/`exrinfo` read existing files; this emits the same textual view from EDN.

     (header {:channels [{:name \"R\" :type :half} {:name \"G\" :type :half} {:name \"B\" :type :half}]
              :dataWindow [[0 0] [1919 1079]] :compression :zip})"
  (:require [clojure.string :as str]))

(def ^:private ctype {:half "16-bit floating-point" :float "32-bit floating-point"
                      :uint "32-bit unsigned integer"})
(def ^:private comp* {:none "none" :rle "rle, individual scanlines"
                      :zips "zip, individual scanlines" :zip "zip, multi-scanline blocks"
                      :piz "piz" :pxr24 "pxr24" :b44 "b44" :b44a "b44a" :dwaa "dwaa" :dwab "dwab"})
(def ^:private lorder {:increasing-y "increasing y" :decreasing-y "decreasing y" :random-y "random y"})

(defn- box [[[x0 y0] [x1 y1]]] (str "(" x0 " " y0 ") - (" x1 " " y1 ")"))
(defn- v2  [[x y]] (str "(" x " " y ")"))
(defn- channel [{:keys [name type xSampling ySampling] :or {xSampling 1 ySampling 1}}]
  (str "    " name ", " (ctype type) ", sampling " xSampling " " ySampling))

(defn header
  "Compile an EXR header map to an exrheader-style attribute listing string. Recognised keys:
   :channels :compression :dataWindow :displayWindow :lineOrder :pixelAspectRatio
   :screenWindowCenter :screenWindowWidth :attributes ({key {:type … :value …}})."
  [{:keys [channels compression dataWindow displayWindow lineOrder pixelAspectRatio
           screenWindowCenter screenWindowWidth attributes]
    :or {compression :zip lineOrder :increasing-y pixelAspectRatio 1
         screenWindowCenter [0 0] screenWindowWidth 1}}]
  (str/join "\n"
    (concat
      ["channels (type chlist):"]
      (map channel channels)
      [(str "compression (type compression): " (comp* compression))
       (str "dataWindow (type box2i): " (box dataWindow))
       (str "displayWindow (type box2i): " (box (or displayWindow dataWindow)))
       (str "lineOrder (type lineOrder): " (lorder lineOrder))
       (str "pixelAspectRatio (type float): " pixelAspectRatio)
       (str "screenWindowCenter (type v2f): " (v2 screenWindowCenter))
       (str "screenWindowWidth (type float): " screenWindowWidth)]
      (for [[k {:keys [type value]}] attributes] (str (name k) " (type " type "): " value)))))
