(ns presentationml.core
  "EDN-first PresentationML package projection."
  (:require [clojure.string :as str]))

(def ns-a "http://schemas.openxmlformats.org/drawingml/2006/main")
(def ns-p "http://schemas.openxmlformats.org/presentationml/2006/main")
(def ns-r "http://schemas.openxmlformats.org/officeDocument/2006/relationships")

(def emu-per-px 9525)
(defn emu [px] (long (Math/round (double (* px emu-per-px)))))

(defn xml-decl [body]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" body))

(defn slide
  ([body] (slide {} body))
  ([attrs body] {:presentationml/type :slide :attrs attrs :body body}))

(defn slide-xml [{:keys [body]}]
  (xml-decl
   (str "<p:sld xmlns:a=\"" ns-a "\" xmlns:r=\"" ns-r "\" xmlns:p=\"" ns-p "\">"
        "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        body
        "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")))

(defn presentation-xml [slide-count {:keys [width height] :or {width 1280 height 720}}]
  (let [ids (apply str (for [i (range slide-count)]
                         (str "<p:sldId id=\"" (+ 256 i) "\" r:id=\"rId" (+ 2 i) "\"/>")))]
    (xml-decl
     (str "<p:presentation xmlns:a=\"" ns-a "\" xmlns:r=\"" ns-r "\" xmlns:p=\"" ns-p "\">"
          "<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>"
          "<p:sldIdLst>" ids "</p:sldIdLst>"
          "<p:sldSz cx=\"" (emu width) "\" cy=\"" (emu height) "\" type=\"screen16x9\"/>"
          "<p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"))))

(defn relationships-xml [rels]
  (xml-decl
   (str "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        (apply str (for [{:keys [id type target]} rels]
                     (str "<Relationship Id=\"" id "\" Type=\"" type "\" Target=\"" target "\"/>")))
        "</Relationships>")))

(defn content-types-xml [slide-count]
  (xml-decl
   (str "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
        "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>"
        "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>"
        "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>"
        (apply str (for [i (range slide-count)]
                     (str "<Override PartName=\"/ppt/slides/slide" (inc i) ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")))
        "</Types>")))

(def theme-xml
  (xml-decl
   (str "<a:theme xmlns:a=\"" ns-a "\" name=\"Kotoba\"><a:themeElements>"
        "<a:clrScheme name=\"Kotoba\"><a:dk1><a:srgbClr val=\"111827\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1></a:clrScheme>"
        "<a:fontScheme name=\"Kotoba\"><a:majorFont><a:latin typeface=\"Aptos Display\"/></a:majorFont><a:minorFont><a:latin typeface=\"Aptos\"/></a:minorFont></a:fontScheme>"
        "<a:fmtScheme name=\"Kotoba\"><a:fillStyleLst/><a:lnStyleLst/><a:effectStyleLst/><a:bgFillStyleLst/></a:fmtScheme>"
        "</a:themeElements></a:theme>")))

(def slide-layout-xml
  (xml-decl
   (str "<p:sldLayout xmlns:a=\"" ns-a "\" xmlns:r=\"" ns-r "\" xmlns:p=\"" ns-p "\" type=\"blank\" preserve=\"1\">"
        "<p:cSld name=\"Blank\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>"
        "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>")))

(def slide-master-xml
  (xml-decl
   (str "<p:sldMaster xmlns:a=\"" ns-a "\" xmlns:r=\"" ns-r "\" xmlns:p=\"" ns-p "\">"
        "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>"
        "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst></p:sldMaster>")))

(defn package-map
  [{:keys [slides size] :or {size {:width 1280 :height 720}}}]
  (let [slide-count (count slides)
        slide-rels (for [i (range slide-count)]
                     {:id (str "rId" (+ 2 i))
                      :type "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide"
                      :target (str "slides/slide" (inc i) ".xml")})]
    (into (sorted-map)
          (concat
           {"[Content_Types].xml" (content-types-xml slide-count)
            "_rels/.rels" (relationships-xml [{:id "rId1" :type "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" :target "ppt/presentation.xml"}])
            "ppt/presentation.xml" (presentation-xml slide-count size)
            "ppt/_rels/presentation.xml.rels" (relationships-xml (concat [{:id "rId1" :type "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" :target "slideMasters/slideMaster1.xml"}] slide-rels))
            "ppt/theme/theme1.xml" theme-xml
            "ppt/slideMasters/slideMaster1.xml" slide-master-xml
            "ppt/slideMasters/_rels/slideMaster1.xml.rels" (relationships-xml [{:id "rId1" :type "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" :target "../slideLayouts/slideLayout1.xml"}])
            "ppt/slideLayouts/slideLayout1.xml" slide-layout-xml
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels" (relationships-xml [{:id "rId1" :type "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" :target "../slideMasters/slideMaster1.xml"}])}
           (map-indexed (fn [i s] [(str "ppt/slides/slide" (inc i) ".xml") (slide-xml s)]) slides)
           (map-indexed (fn [i _] [(str "ppt/slides/_rels/slide" (inc i) ".xml.rels") (relationships-xml [])]) slides)))))

(defn valid-slide? [slide]
  (and (map? slide)
       (= :slide (:presentationml/type slide))
       (string? (:body slide))))

(defn valid-package-map? [pkg]
  (and (map? pkg)
       (contains? pkg "[Content_Types].xml")
       (contains? pkg "_rels/.rels")
       (contains? pkg "ppt/presentation.xml")
       (some #(re-matches #"ppt/slides/slide\d+\.xml" %) (keys pkg))
       (every? string? (vals pkg))))
