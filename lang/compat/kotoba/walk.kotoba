(ns kotoba.walk
  "Assembled from one repo per definition.

  This namespace holds no implementation. It re-exports the definitions
  that each live in their own repo, so a call site can require one name
  and a library can require only the definitions it actually uses."
  (:require [kotoba.walk.bounded-postwalk :as bounded-postwalk-ns]
            [kotoba.walk.bounded-prewalk :as bounded-prewalk-ns]
            [kotoba.walk.keywordize-keys :as keywordize-keys-ns]
            [kotoba.walk.postwalk :as postwalk-ns]
            [kotoba.walk.postwalk-replace :as postwalk-replace-ns]
            [kotoba.walk.prewalk :as prewalk-ns]
            [kotoba.walk.prewalk-replace :as prewalk-replace-ns]
            [kotoba.walk.stringify-keys :as stringify-keys-ns]
            [kotoba.walk.walk :as walk-ns]))

(def bounded-postwalk "See kotoba.walk.bounded-postwalk/bounded-postwalk." bounded-postwalk-ns/bounded-postwalk)
(def bounded-prewalk "See kotoba.walk.bounded-prewalk/bounded-prewalk." bounded-prewalk-ns/bounded-prewalk)
(def keywordize-keys "See kotoba.walk.keywordize-keys/keywordize-keys." keywordize-keys-ns/keywordize-keys)
(def postwalk "See kotoba.walk.postwalk/postwalk." postwalk-ns/postwalk)
(def postwalk-replace "See kotoba.walk.postwalk-replace/postwalk-replace." postwalk-replace-ns/postwalk-replace)
(def prewalk "See kotoba.walk.prewalk/prewalk." prewalk-ns/prewalk)
(def prewalk-replace "See kotoba.walk.prewalk-replace/prewalk-replace." prewalk-replace-ns/prewalk-replace)
(def stringify-keys "See kotoba.walk.stringify-keys/stringify-keys." stringify-keys-ns/stringify-keys)
(def walk "See kotoba.walk.walk/walk." walk-ns/walk)
