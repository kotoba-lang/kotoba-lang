(ns tamaki.observer.style
  (:require-macros [kotoba.css.shadow :refer [css]]))

(def app
  (css {:position :fixed :inset 0 :width "100%" :height "100%"
        :overflow :hidden :background "#090611" :color "#f6f2ff"
        :font "13px -apple-system, sans-serif"}
       ["*" {:box-sizing :border-box}]
       [".surface-hidden" {:visibility :hidden :pointer-events :none}]))

(def scene
  (css {:position :fixed :inset 0 :width "100%" :height "100%"}))

(def effects
  (css {:position :fixed :inset 0 :z-index 1
        :pointer-events :none :overflow :hidden}
       [".pulse" {:position :absolute :width 52 :height 52
                  :border "2px solid #70ffbc" :border-radius "50%"
                  :box-shadow "0 0 28px #4cff91"
                  :animation "tamaki-pulse .7s ease-out forwards"}]))

(def glass
  (css {:position :fixed :z-index 2
        :background "rgba(22,14,35,.72)"
        :border "1px solid rgba(210,170,255,.18)"
        :backdrop-filter "blur(18px)" :border-radius 16
        :box-shadow "0 20px 60px #0008"}))

(def header
  (css {:top 8 :left 8 :right 306 :padding "9px 12px"
        :display :grid :grid-template-columns "auto 1fr auto"
        :grid-template-rows "22px 28px 27px 43px 25px"
        :column-gap 12 :row-gap 4 :overflow :hidden}
       ["h1" {:grid-column 1 :grid-row 1 :font-size 16 :margin 0
              :white-space :nowrap}]
       [".metrics" {:grid-column "2 / 4" :grid-row 1 :align-self :center
                    :color "#bcb2cc" :white-space :nowrap :overflow :hidden
                    :text-overflow :ellipsis :font-size 11}]
       ["label" {:grid-column 1 :grid-row 2 :align-self :center
                 :margin 0 :color "#9f94af" :white-space :nowrap}]
       ["select" {:margin-left 7 :color "#eee" :background "#241733"
                  :border "1px solid #57416e" :border-radius 7
                  :padding "3px 7px"}]
       [".garden-views" {:grid-column 2 :grid-row 2 :display :flex
                         :align-items :center :gap 5 :margin 0}]
       [".garden-views span" {:margin-right 3 :color "#71dda0"
                              :font "10px ui-monospace, monospace"}]
       [".garden-views button" {:color "#c9e9d5" :background "#10251a"
                                :border "1px solid #376a4c"
                                :border-radius 999 :padding "3px 8px"
                                :font-size 10 :cursor :pointer}]
       [".garden-views button:hover" {:color "#07150d"
                                      :background "#70ffac"
                                      :border-color "#70ffac"}]
       [".garden-views button.selected" {:color "#07150d"
                                         :background "#70ffac"
                                         :border-color "#70ffac"
                                         :box-shadow "0 0 14px #42f58d55"}]
       [".bonsai-state" {:grid-column "1 / 4" :grid-row 3
                         :display :flex :align-items :center :gap 8
                         :margin 0 :padding "4px 8px"
                         :border "1px solid #d9a15b55" :border-radius 8
                         :background "#28180d88"
                         :font "10px ui-monospace, monospace"}]
       [".bonsai-state b" {:color "#ffc678"}]
       [".bonsai-state span" {:color "#d9c7ac"}]
       [".bonsai-state small" {:max-width 260 :overflow :hidden
                               :text-overflow :ellipsis :white-space :nowrap
                               :color "#ffad6c"}]
       [".model-usage" {:grid-column 3 :grid-row 4 :display :flex :gap 4
                        :margin 0 :min-width 0 :overflow :hidden}]
       [".actor-state" {:grid-column "1 / 3" :grid-row 4 :display :flex :gap 4
                        :margin 0 :min-width 0 :overflow :hidden}]
       [".actor-card" {:flex "1 1 0" :min-width 0 :padding "4px 6px"
                       :border-radius 7 :overflow :hidden
                       :white-space :nowrap :text-overflow :ellipsis
                       :border "1px solid #4cff9155"
                       :background "#10251a99"
                       :font "9px ui-monospace, monospace"}]
       [".actor-card b" {:display :inline :margin-right 5 :color "#77ffad"}]
       [".actor-card .pressure" {:color "#ffb45b"}]
       [".voice-row" {:grid-column 3 :grid-row 2 :display :flex
                      :align-items :center :justify-content :flex-end
                      :gap 5 :margin 0}]
       [".voice-button" {:color "#dfffea" :background "#173d2a"
                         :border "1px solid #48dd86" :border-radius 8
                         :padding "4px 7px" :font-size 10 :cursor :pointer}]
       [".sound-button" {:color "#e8dcff" :background "#2a1b3e"
                         :border "1px solid #8c67bc" :border-radius 8
                         :padding "4px 7px" :font-size 10 :cursor :pointer}]
       [".voice-status" {:display :none}]
       [".usage-card" {:min-width 72 :padding "4px 6px"
                       :border "1px solid #ffffff17" :border-radius 8
                       :background "#09061188"}]
       [".usage-card b" {:display :block :color "#bda7d8"
                         :font "11px ui-monospace, monospace"}]
       [".usage-card span" {:display :block :color "#d8cfdf"
                            :font "9px ui-monospace, monospace" :margin 0}]
       [".usage-card span:last-child" {:display :none}]
       [".usage-card em" {:color "#82778e" :font-style :normal}]
       [".organism-scopes" {:grid-column "1 / 4" :grid-row 5
                            :display :flex :align-items :center :gap 5
                            :overflow-x :auto}]
       [".organism-scopes button"
        {:flex "0 0 auto" :padding "3px 9px" :border-radius 999
         :border "1px solid #47725b" :background "#0c2117"
         :color "#bfe8ce" :font "9px ui-monospace, monospace"
         :cursor :pointer}]
       [".organism-scopes button:first-child"
        {:border-color "#d4b268" :color "#f2d58e" :background "#2b200e"}]
       [".organism-scopes button.selected"
        {:background "#ededf0" :color "#111115" :border-color "#ededf0"
         :box-shadow "0 0 0 1px #ffffff22"}]
       [".organism-scopes button:hover"
        {:background "#70ffac" :color "#07150d" :border-color "#70ffac"}]))

(def inspector
  (css {:right 8 :top 8 :bottom 8 :width 290
        :overflow :hidden :padding 12}
       ["h2" {:font-size 13 :margin "0 0 6px"}]
       [".details" {:max-height 125 :overflow-y :auto :color "#c8bfd5"
                    :font-size 11 :line-height 1.4 :word-break :break-word}]
       [".result-panel" {:display :grid :gap 5 :margin "0 0 10px"}]
       [".result-chain" {:padding 7 :border "1px solid #ffffff14"
                         :border-radius 8 :background "#08050e99"}]
       [".result-chain small" {:display :block :color "#8f849c"
                               :overflow :hidden :text-overflow :ellipsis
                               :white-space :nowrap :margin-bottom 5}]
       [".result-chain div" {:display :flex :align-items :center
                             :gap 3 :overflow-x :auto}]
       [".result-node" {:flex "0 0 auto" :padding "2px 5px"
                        :border-radius 5 :font "8px ui-monospace, monospace"
                        :color "#eee" :background "#241733"}]
       [".result-node.source" {:color "#63dfff"}]
       [".result-node.radicle" {:color "#dd86ff"}]
       [".result-node.github" {:color "#fff"}]
       [".result-node.review" {:color "#70ffac"}]
       [".result-node.merge" {:color "#5aa9ff"}]
       [".result-arrow" {:color "#5e536b"}]
       [".activity-title" {:margin-top 9}]
       [".activity-filters" {:display :flex :gap 5 :overflow-x :auto
                             :padding-bottom 8 :margin-bottom 2}]
       [".activity-filters button" {:flex "0 0 auto" :cursor :pointer
                                    :color "#b9aec7" :background "#140c20"
                                    :border "1px solid #473459"
                                    :border-radius 999 :padding "4px 8px"
                                    :font "10px ui-monospace, monospace"}]
       [".activity-filters button.selected"
        {:color "#07150d" :background "#70ffac" :border-color "#70ffac"
         :box-shadow "0 0 12px #42f58d55"}]
       [".event" {:display :grid :grid-template-columns "58px 1fr"
                  :gap "2px 6px" :padding "5px 0"
                  :border-top "1px solid #ffffff12"}]
       [".event time" {:color "#81778e"
                       :font "11px ui-monospace, monospace"}]
       [".event b" {:color "#79ffa8"
                    :font "11px ui-monospace, monospace"}]
       [".event-heading" {:display :flex :align-items :center
                          :justify-content :space-between :gap 6}]
       [".event .stream" {:color "#73bfff" :border "1px solid #3998db55"
                          :border-radius 999 :padding "1px 5px"
                          :font "9px ui-monospace, monospace"}]
       [".event small" {:grid-column 2 :color "#9f94af" :overflow :hidden
                        :text-overflow :ellipsis :white-space :nowrap}]))

(def legend
  (css {:left 8 :bottom 8 :width 304 :padding "7px 10px"
        :font-size 10 :color "#aaa0b8"}
       [".live" {:color "#4cff91"}]
       [".sync" {:color "#309bff"}]
       [".diff" {:color "#ff9d2e"}]
       [".loop" {:color "#c956ff"}]))

(def dynamics
  (css {:left 320 :right 306 :bottom 8 :height 112
        :width :auto :min-width 0 :transform :none
        :padding "8px 10px" :overflow :hidden}
       [".dynamics-heading" {:display :flex :align-items :baseline
                             :justify-content :space-between :gap 12}]
       [".dynamics-heading b" {:font-size 13}]
       [".dynamics-heading span" {:color "#91859f"
                                  :font "10px ui-monospace, monospace"}]
       [".business-control" {:display :flex :justify-content :space-between
                             :gap 10 :margin-top 5
                             :font "10px ui-monospace, monospace"}]
       [".business-control b" {:color "#73f4a1"}]
       [".business-control span" {:color "#bfb4cd"}]
       [".business-control .pressure" {:color "#ffae52"}]
       [".stock-row" {:display :grid
                      :grid-template-columns "repeat(auto-fit, minmax(92px, 1fr))"
                      :gap 5 :margin-top 5}]
       [".stock-card" {:position :relative :padding "6px 8px"
                       :border "1px solid color-mix(in srgb, var(--stock) 55%, transparent)"
                       :border-radius 8
                       :background "linear-gradient(180deg, color-mix(in srgb, var(--stock) 18%, transparent), #08050e99)"
                       :overflow :hidden}]
       [".stock-card::after" {:content "\"\"" :position :absolute
                              :left 0 :right 0 :bottom 0 :height 3
                              :background "var(--stock)"
                              :box-shadow "0 0 12px var(--stock)"}]
       [".stock-card.bottleneck" {:box-shadow
                                  "inset 0 0 0 1px #ffbb58, 0 0 12px #ff9f3333"}]
       [".stock-card small" {:display :block :color "#bfb4cd" :font-size 9}]
       [".stock-card strong" {:font "17px ui-monospace, monospace"
                              :color "#fff" :margin-right 4}]
       [".stock-card em" {:color "#8d8299"
                          :font "9px ui-monospace, monospace"
                          :font-style :normal}]
       [".flow-row" {:display :flex :gap 8 :margin-top 4 :color "#94899f"
                     :font "9px ui-monospace, monospace"
                     :white-space :nowrap}]
       [".flow-row b" {:color "#73f4a1"}]
       [".flow-row .pressure" {:color "#ffae52"}]
       [".flow-row .relief" {:color "#62e6b1"}]))

(def finance
  (css [".finance-dashboard"
        {:position :fixed :z-index 3 :left 8 :right 306 :top 198 :bottom 8
         :padding 18 :overflow-y :auto :border-radius 16
         :background "linear-gradient(145deg, rgba(8,18,14,.97), rgba(19,11,28,.97))"
         :border "1px solid #7ee6ab33" :box-shadow "0 24px 80px #000b"
         :color "#f2fff7"}]
       [".finance-dashboard.hidden" {:display :none}]
       [".finance-title" {:display :flex :align-items :end
                          :justify-content :space-between
                          :border-bottom "1px solid #ffffff18"
                          :padding-bottom 12}]
       [".finance-title small" {:color "#67e89d"
                                :font "10px ui-monospace, monospace"
                                :letter-spacing ".15em"}]
       [".finance-title h2" {:margin "3px 0 0" :font-size 24}]
       [".finance-period" {:color "#9c91aa"
                           :font "11px ui-monospace, monospace"}]
       [".finance-kpis" {:display :grid
                         :grid-template-columns "repeat(4, minmax(0, 1fr))"
                         :gap 8 :margin "14px 0"}]
       [".finance-kpi" {:padding "12px 14px" :border-radius 10
                        :background "#ffffff08"
                        :border "1px solid #ffffff13"}]
       [".finance-kpi small" {:display :block :color "#9e94aa"
                              :font-size 10 :margin-bottom 5}]
       [".finance-kpi strong" {:font "20px ui-monospace, monospace"}]
       [".finance-kpi strong.warning" {:color "#ffad62"}]
       [".finance-segments" {:display :grid
                             :grid-template-columns "repeat(3, minmax(0, 1fr))"
                             :gap 8 :margin-bottom 10}]
       [".finance-segment" {:display :grid
                            :grid-template-columns "1fr auto"
                            :align-items :baseline :padding "7px 10px"
                            :border-radius 9 :background "#071b1299"
                            :border "1px solid #70e99f2b"}]
       [".finance-segment span" {:color "#70e99f"
                                 :font "10px ui-monospace, monospace"}]
       [".finance-segment strong" {:font "12px ui-monospace, monospace"}]
       [".finance-segment small" {:grid-column "1 / 3" :color "#81788b"
                                  :font "8px ui-monospace, monospace"}]
       [".finance-statements" {:display :grid
                               :grid-template-columns "repeat(3, minmax(0, 1fr))"
                               :gap 10}]
       [".finance-card" {:padding 14 :border-radius 12
                         :background "#090c0bbb"
                         :border "1px solid #ffffff14"}]
       [".finance-card h3" {:margin "0 0 10px" :color "#77eba5"
                            :font "13px ui-monospace, monospace"}]
       [".finance-line" {:display :flex :justify-content :space-between
                         :gap 10 :padding "8px 0"
                         :border-top "1px solid #ffffff0d"
                         :color "#aaa1b2"}]
       [".finance-line strong" {:color "#f6fff9"
                                :font "12px ui-monospace, monospace"}]
       [".finance-line.total" {:border-top "1px solid #74eaa45c"
                               :color "#d7f8e2"}]
       [".finance-line.total strong" {:color "#7dffae"}]
       [".finance-empty" {:margin-top 12 :padding 10 :border-radius 8
                          :color "#ffbd72" :background "#37210f99"
                          :font "11px ui-monospace, monospace"}]))

(def operations
  (css {:position :fixed :z-index 3 :left 8 :right 8 :top 198 :bottom 8
        :display :grid :grid-template-rows "54px 62px minmax(0, 1fr)"
        :gap 8 :padding 10 :overflow :hidden :border-radius 16
        :background "#0d0d10"
        :border "1px solid #28282d" :box-shadow "0 24px 80px #000b"}
       ["&.hidden" {:display :none}]
       [".objective-strip" {:display :flex :align-items :center
                            :justify-content :space-between :gap 16
                            :padding "8px 12px" :border-radius 10
                            :background "#0a2117aa" :border "1px solid #5eea9633"}]
       [".objective-strip small" {:display :block :color "#62e99a"
                                  :font "8px ui-monospace, monospace"
                                  :letter-spacing ".16em"}]
       [".objective-strip strong" {:display :block :max-width "70vw"
                                   :overflow :hidden :white-space :nowrap
                                   :text-overflow :ellipsis :font-size 14}]
       [".objective-health" {:display :flex :align-items :center :gap 7
                             :font "10px ui-monospace, monospace"
                             :color "#9fffc0" :white-space :nowrap}]
       [".live-dot" {:width 7 :height 7 :border-radius "50%"
                     :background "#53ff94" :box-shadow "0 0 12px #53ff94"}]
       [".stale-count" {:padding "2px 6px" :border-radius 999
                        :color "#ffb46b" :background "#43230f"}]
       [".scope-label" {:padding "3px 7px" :border-radius 5
                        :background "#25252a" :color "#aaaab3"
                        :font "8px ui-monospace, monospace"
                        :letter-spacing ".08em"}]
       [".kpi-strip" {:display :grid
                      :grid-template-columns "repeat(2, minmax(0, 1fr))"
                      :gap 7 :min-width 0 :overflow :hidden}]
       [".board-kpi" {:position :relative :display :grid
                      :grid-template-columns "minmax(0,1fr) auto"
                      :align-items :baseline :gap 5 :padding "9px 11px"
                      :min-width 0
                      :border-radius 10 :background "#ffffff08"
                      :border "1px solid #ffffff12" :overflow :hidden}]
       [".board-kpi::after" {:content "\"\"" :position :absolute
                             :left 0 :right 0 :bottom 0 :height 2
                             :background "var(--tone)"}]
       [".board-kpi.green" {"--tone" "#54f69a"}]
       [".board-kpi.amber" {"--tone" "#ffb45c"}]
       [".board-kpi.blue" {"--tone" "#57b5ff"}]
       [".board-kpi.violet" {"--tone" "#c56cff"}]
       [".board-kpi small" {:color "#a9a0b3" :font-size 9}]
       [".board-kpi > div" {:display :flex :align-items :baseline :gap 5
                            :min-width 0}]
       [".board-kpi strong" {:font "21px ui-monospace, monospace"}]
       [".board-kpi em" {:color "#766e80" :font "8px ui-monospace, monospace"
                         :font-style :normal}]
       [".now-board,.flow-board" {:display :grid
                                  :grid-template-rows "39px minmax(0, 1fr)"
                                  :min-height 0}]
       [".section-heading" {:display :flex :align-items :center
                            :justify-content :space-between
                            :border-bottom "1px solid #ffffff12"}]
       [".section-heading small" {:color "#61e89a"
                                  :font "8px ui-monospace, monospace"
                                  :letter-spacing ".15em"}]
       [".section-heading h2" {:display :inline :margin "0 0 0 8px"
                               :font-size 14}]
       [".section-heading span" {:color "#7f7689"
                                 :font "9px ui-monospace, monospace"}]
       [".now-grid" {:display :grid
                     :grid-template-columns "repeat(auto-fill, minmax(235px, 1fr))"
                     :align-content :start :gap 8 :padding-top 8
                     :overflow-y :auto}]
       [".linear-workspace" {:display :grid
                             :grid-template-columns "184px minmax(0, 1fr)"
                             :min-height 0 :margin-top 8
                             :border "1px solid #27272c" :border-radius 10
                             :overflow :hidden :background "#101013"}]
       [".work-sidebar" {:display :flex :flex-direction :column :gap 2
                         :padding 10 :border-right "1px solid #27272c"
                         :background "#0b0b0e"}]
       [".work-sidebar > small" {:padding "7px 8px 5px" :color "#66666f"
                                 :font "8px ui-monospace, monospace"
                                 :letter-spacing ".12em"}]
       [".work-sidebar button" {:display :grid
                                :grid-template-columns "18px 1fr auto"
                                :align-items :center :gap 4 :width "100%"
                                :padding "7px 8px" :border 0 :border-radius 6
                                :background :transparent :color "#9b9ba4"
                                :text-align :left :font-size 10 :cursor :pointer}]
       [".work-sidebar button:hover" {:background "#18181c" :color "#e6e6e9"}]
       [".work-sidebar button.selected" {:background "#202024" :color "#f4f4f5"}]
       [".work-sidebar button span" {:color "#777780"
                                     :font "11px ui-monospace, monospace"}]
       [".work-sidebar button b" {:min-width 18 :padding "1px 5px"
                                  :border-radius 999 :background "#29292e"
                                  :color "#a9a9b1" :text-align :center
                                  :font "8px ui-monospace, monospace"}]
       [".work-sidebar button.attention" {:color "#d8a06d"}]
       [".sidebar-divider" {:height 1 :margin "7px 5px"
                            :background "#242429"}]
       [".work-list" {:display :grid
                      :grid-template-rows "31px minmax(0, 1fr)"
                      :min-width 0 :min-height 0}]
       [".work-list-header" {:display :grid
                             :grid-template-columns "24px minmax(220px, 2fr) minmax(150px, 1.25fr) 88px 120px 64px"
                             :align-items :center :padding "0 10px"
                             :border-bottom "1px solid #27272c"
                             :color "#65656e" :font-size 8}]
       [".work-list-body" {:overflow-y :auto}]
       [".work-row" {:display :grid
                     :grid-template-columns "24px minmax(220px, 2fr) minmax(150px, 1.25fr) 88px 120px 64px"
                     :align-items :center :width "100%" :min-width 0
                     :padding "8px 10px" :border 0
                     :border-bottom "1px solid #202025"
                     :background :transparent :color "#b8b8bf"
                     :text-align :left :cursor :pointer}]
       [".work-row:hover" {:background "#17171b"}]
       [".row-status" {:width 8 :height 8 :border-radius "50%"
                       :border "2px solid #4ee58d"
                       :box-shadow "0 0 7px #4ee58d55"}]
       [".work-row.quiet .row-status" {:border-color "#5caaff"
                                       :box-shadow :none}]
       [".work-row.stale .row-status" {:border-color "#d98248"
                                       :box-shadow :none}]
       [".row-title,.row-agent" {:display :flex :flex-direction :column
                                 :min-width 0}]
       [".row-title strong" {:overflow :hidden :text-overflow :ellipsis
                             :white-space :nowrap :color "#e6e6e9"
                             :font-size 10 :font-weight 560}]
       [".row-title small" {:margin-top 2 :overflow :hidden
                            :text-overflow :ellipsis :white-space :nowrap
                            :color "#6f6f78" :font-size 8}]
       [".row-project" {:overflow :hidden :text-overflow :ellipsis
                        :white-space :nowrap :padding-right 10
                        :color "#9a9aa3" :font-size 9}]
       [".row-stage" {:justify-self :start :padding "3px 6px"
                      :border-radius 5 :background "#242429"
                      :color "#aaaab2" :font-size 8}]
       [".row-stage.implement" {:color "#8ebcff" :background "#142237"}]
       [".row-stage.review" {:color "#c6a4ff" :background "#25183a"}]
       [".row-stage.blocked" {:color "#efae78" :background "#382013"}]
       [".row-agent b" {:overflow :hidden :text-overflow :ellipsis
                        :white-space :nowrap :color "#bcbcc4"
                        :font "9px ui-monospace, monospace"}]
       [".row-agent small" {:overflow :hidden :text-overflow :ellipsis
                            :white-space :nowrap :color "#65656d"
                            :font "8px ui-monospace, monospace"}]
       [".work-row time" {:justify-self :end :color "#686870"
                          :font "8px ui-monospace, monospace"}]
       [".work-list.organism,.work-list.actors,.work-list.loops,.work-list.attention"
        {:display :block :overflow-y :auto :padding 12}]
       [".organism-map" {:display :grid :grid-template-rows "minmax(210px,3fr) minmax(150px,2fr)"
                         :gap 14 :height "100%" :min-height 0}]
       [".organism-layer" {:display :grid
                           :grid-template-rows "38px minmax(0,1fr)"
                           :min-height 0}]
       [".organism-layer > header" {:display :flex :align-items :center
                                    :justify-content :space-between
                                    :border-bottom "1px solid #27272c"}]
       [".organism-layer > header small" {:color "#61e89a"
                                          :font "8px ui-monospace, monospace"
                                          :letter-spacing ".14em"}]
       [".organism-layer > header h3" {:display :inline :margin "0 0 0 8px"
                                       :font-size 13}]
       [".organism-layer > header > span" {:color "#777780"
                                           :font "9px ui-monospace, monospace"}]
       [".actor-grid" {:display :grid
                       :grid-template-columns "repeat(2,minmax(0,1fr))"
                       :align-content :start :gap 8 :padding-top 8
                       :overflow-y :auto}]
       [".actor-grid-full,.loop-list-full" {:height "100%" :padding-top 0}]
       [".organism-actor" {:display :grid :grid-template-rows "auto 1fr auto"
                           :min-height 116 :padding 10 :border-radius 9
                           :background "#121217" :border "1px solid #292930"
                           :border-left "3px solid #6b6b75"}]
       [".organism-actor.working" {:border-left-color "#54f69a"}]
       [".organism-actor.queued" {:border-left-color "#57b5ff"}]
       [".organism-actor.needs-agent" {:border-left-color "#ffb45c"}]
       [".organism-actor.blocked" {:border-left-color "#ff6b7d"}]
       [".organism-actor > header" {:display :grid
                                    :grid-template-columns "16px minmax(0,1fr) auto"
                                    :align-items :center :gap 5}]
       [".actor-glyph" {:color "#54f69a"}]
       [".organism-actor header div" {:display :flex :flex-direction :column
                                      :min-width 0}]
       [".organism-actor header strong,.organism-actor header small"
        {:overflow :hidden :text-overflow :ellipsis :white-space :nowrap}]
       [".organism-actor header strong" {:font-size 10}]
       [".organism-actor header small" {:color "#777780" :font-size 8}]
       [".organism-actor header em" {:padding "2px 5px" :border-radius 4
                                     :background "#292930" :color "#aaaab3"
                                     :font "7px ui-monospace, monospace"
                                     :font-style :normal}]
       [".organism-actor p" {:display "-webkit-box"
                             :-webkit-line-clamp 2
                             :-webkit-box-orient :vertical
                             :overflow :hidden :margin "8px 0"
                             :color "#a3a3ab" :font-size 9 :line-height 1.45}]
       [".organism-actor footer" {:display :flex :align-items :center :gap 6
                                  :min-width 0 :font "7px ui-monospace, monospace"
                                  :color "#6f6f78"}]
       [".actor-project" {:flex 1 :overflow :hidden :text-overflow :ellipsis
                          :white-space :nowrap}]
       [".organism-actor footer b" {:color "#ffb45c" :font-weight 600}]
       [".loop-list" {:display :grid :align-content :start :gap 5
                      :padding-top 7 :overflow-x :hidden :overflow-y :auto}]
       [".loop-agent" {:display :grid
                       :grid-template-columns "12px minmax(160px,2fr) minmax(110px,1.3fr) 85px 55px 70px"
                       :align-items :center :gap 8 :min-height 45
                       :padding "6px 9px" :border-radius 7
                       :background "#121217" :border "1px solid #292930"}]
       [".loop-pulse" {:width 8 :height 8 :border-radius "50%"
                       :background "#57b5ff" :box-shadow "0 0 8px #57b5ff66"}]
       [".loop-agent > div,.loop-model" {:display :flex :flex-direction :column
                                         :min-width 0}]
       [".loop-agent strong,.loop-agent small,.loop-project"
        {:overflow :hidden :text-overflow :ellipsis :white-space :nowrap}]
       [".loop-agent strong" {:font-size 9}]
       [".loop-agent small,.loop-project,.loop-model small,.loop-agent time"
        {:color "#73737c" :font-size 8}]
       [".loop-model b,.loop-cycles" {:font "8px ui-monospace, monospace"}]
       [".loop-agent time" {:justify-self :end}]
       [".organism-empty" {:margin :auto :color "#777780"}]
       [".flow-columns" {:display :grid
                         :grid-template-columns "repeat(4, minmax(0, 1fr))"
                         :gap 8 :padding-top 8 :min-height 0}]
       [".flow-column" {:display :grid :grid-template-rows "34px minmax(0, 1fr)"
                        :min-width 0 :min-height 0 :border-radius 10
                        :background "#ffffff05" :border "1px solid #ffffff0e"}]
       [".flow-column > header" {:display :grid
                                 :grid-template-columns "auto auto 1fr"
                                 :align-items :center :gap 6 :padding "6px 8px"
                                 :border-bottom "1px solid #ffffff10"}]
       [".flow-column > header span" {:padding "1px 6px" :border-radius 999
                                      :background "#ffffff12"
                                      :font "9px ui-monospace, monospace"}]
       [".flow-column > header small" {:justify-self :end :color "#776e81"
                                       :font-size 8}]
       [".flow-column > div" {:display :grid :align-content :start :gap 7
                              :padding 7 :overflow-y :auto}]
       [".flow-column p" {:margin 12 :text-align :center :color "#544d5d"
                          :font "10px ui-monospace, monospace"}]
       [".work-card" {:min-width 0 :padding 10 :border-radius 10
                      :background "#0c0a12" :border "1px solid #ffffff17"
                      :cursor :pointer :transition "border-color .15s, transform .15s"}]
       [".work-card:hover" {:transform "translateY(-1px)"
                            :border-color "#70ffac77"}]
       [".work-card.live" {:border-left "3px solid #53ff94"}]
       [".work-card.quiet" {:border-left "3px solid #55aaff"}]
       [".work-card.stale" {:border-left "3px solid #ff9b4d"
                            :background "#1b100aaa"}]
       [".work-card-head" {:display :grid
                           :grid-template-columns "8px 1fr auto"
                           :align-items :center :gap 6}]
       [".work-card-head .heartbeat" {:width 7 :height 7
                                      :border-radius "50%" :background "#53ff94"
                                      :box-shadow "0 0 9px #53ff94"}]
       [".work-card.quiet .heartbeat" {:background "#55aaff"
                                       :box-shadow "0 0 9px #55aaff"}]
       [".work-card.stale .heartbeat" {:background "#ff9b4d"
                                       :box-shadow :none}]
       [".work-card-head b" {:color "#c9ffd9"
                             :font "10px ui-monospace, monospace"}]
       [".work-card-head small" {:color "#756d7e"
                                 :font "8px ui-monospace, monospace"}]
       [".work-project" {:display :block :margin-top 8 :overflow :hidden
                         :white-space :nowrap :text-overflow :ellipsis
                         :font-size 12}]
       [".work-issue" {:display :block :margin-top 3 :color "#7ddfa2"
                       :font "9px ui-monospace, monospace"}]
       [".work-output" {:display :grid :grid-template-columns "1fr auto"
                        :gap 8 :margin-top 8 :padding-top 7
                        :border-top "1px solid #ffffff0d"}]
       [".work-output span" {:overflow :hidden :white-space :nowrap
                             :text-overflow :ellipsis :color "#a49aaa"
                             :font-size 9}]
       [".work-output em" {:color "#746c7c"
                           :font "8px ui-monospace, monospace"
                           :font-style :normal :white-space :nowrap}]
       [".board-empty" {:grid-column "1 / -1" :padding 40 :text-align :center
                        :color "#6f6678" :border "1px dashed #ffffff18"
                        :border-radius 12}]))

;; The default Observatory surface follows the same DADS-backed workspace
;; shell as cloud-itonami-app. The immersive Three.js surface deliberately
;; keeps the original dark glass controls through `header`.
(def dads-shell
  (css {:background "var(--color-neutral-solid-gray-50)"
        :color "var(--color-neutral-solid-gray-900)"
        :font "16px var(--font-family-sans), -apple-system, sans-serif"
        :color-scheme :light}
       [".surface-hidden" {:display :none}]
       ["button,select" {:font :inherit}]
       ["button:focus-visible,select:focus-visible"
        {:outline "4px solid var(--color-primitive-yellow-300)"
         :outline-offset 2}]))

(def navigation
  (css {:position :fixed :z-index 4 :left 0 :top 0 :bottom 0 :width 244
        :display :flex :flex-direction :column :gap 16
        :padding "24px 16px" :overflow-y :auto
        :background "var(--color-neutral-white)"
        :border-right "1px solid var(--color-neutral-solid-gray-200)"
        :color "var(--color-neutral-solid-gray-900)"}
       ["h1" {:margin 0 :font-size 22 :line-height 1.45}]
       [".metrics" {:color "var(--color-neutral-solid-gray-600)"
                    :font-size 13 :line-height 1.65}]
       ["label" {:display :grid :gap 6 :color
                 "var(--color-neutral-solid-gray-700)"
                 :font-size 13 :font-weight 700}]
       ["select" {:width "100%" :min-height 44 :padding "8px 34px 8px 10px"
                  :border "1px solid var(--color-neutral-solid-gray-300)"
                  :border-radius 8 :background "var(--color-neutral-white)"
                  :color "var(--color-neutral-solid-gray-900)"}]
       [".garden-views" {:display :grid :gap 6}]
       [".garden-views > span" {:color "var(--color-neutral-solid-gray-600)"
                                :font-size 13 :font-weight 700}]
       [".garden-views .dads-button" {:width "100%" :justify-content :flex-start}]
       [".garden-views .dads-button[data-selected='true']"
        {:box-shadow "inset 4px 0 0 var(--color-key-900)"}]
       [".bonsai-state" {:display :none}]
       [".organism-scopes" {:display :flex :flex-wrap :wrap :gap 6
                            :padding-top 14
                            :border-top "1px solid var(--color-neutral-solid-gray-200)"}]
       [".organism-scopes .dads-button" {:min-height 36 :padding "6px 10px"
                                         :font-size 12}]
       [".voice-row" {:display :grid :grid-template-columns "1fr 1fr" :gap 6}]
       [".voice-row .dads-button" {:min-height 44 :padding "8px 6px"
                                   :font-size 12}]
       [".voice-status" {:position :absolute :width 1 :height 1 :overflow :hidden
                         :clip "rect(0 0 0 0)"}]
       [".actor-state,.model-usage" {:display :grid :gap 6}]
       [".actor-card,.usage-card" {:min-width 0 :padding "8px 10px"
                                   :border-radius 8
                                   :background "var(--color-neutral-solid-gray-50)"
                                   :border "1px solid var(--color-neutral-solid-gray-200)"
                                   :font "11px ui-monospace, monospace"
                                   :overflow :hidden :text-overflow :ellipsis
                                   :white-space :nowrap}]
       [".actor-card b,.usage-card b"
        {:display :block :color "var(--color-key-900)"
         :overflow :hidden :text-overflow :ellipsis}]
       [".usage-card span" {:display :block :color
                            "var(--color-neutral-solid-gray-600)"}]
       [".usage-card span:last-child" {:display :none}]))

(def dads-operations
  (css {:left 244 :right 0 :top 0 :bottom 0 :z-index 3
        :width "calc(100vw - 244px)" :max-width "calc(100vw - 244px)"
        :min-width 0
        :grid-template-columns "minmax(0,1fr)"
        :grid-template-rows "82px 88px minmax(0,1fr)"
        :gap 16 :padding "28px clamp(20px,4vw,48px)"
        :border 0 :border-radius 0 :box-shadow :none
        :background "var(--color-neutral-solid-gray-50)"
        :color "var(--color-neutral-solid-gray-900)"}
       [".objective-strip" {:padding "14px 18px" :border-radius 12
                            :background "var(--color-neutral-white)"
                            :border "1px solid var(--color-neutral-solid-gray-200)"}]
       [".objective-strip small,.section-heading small"
        {:color "var(--color-key-900)" :font-size 11}]
       [".objective-strip strong" {:font-size 18 :line-height 1.55}]
       [".objective-health" {:color "var(--color-neutral-solid-gray-700)"
                             :font-size 12}]
       [".live-dot" {:background "var(--color-semantic-success-1)"
                     :box-shadow :none}]
       [".scope-label,.stale-count" {:background
                                     "var(--color-neutral-solid-gray-100)"
                                     :color "var(--color-neutral-solid-gray-700)"}]
       [".kpi-strip" {:gap 12}]
       [".board-kpi" {:padding "14px 16px" :border-radius 12
                      :background "var(--color-neutral-white)"
                      :border "1px solid var(--color-neutral-solid-gray-200)"}]
       [".board-kpi small" {:color "var(--color-neutral-solid-gray-600)"
                            :font-size 12}]
       [".board-kpi strong" {:color "var(--color-neutral-solid-gray-900)"
                             :font-size 25}]
       [".board-kpi em" {:color "var(--color-neutral-solid-gray-500)"
                         :font-size 10}]
       [".board-kpi.green" {"--tone" "var(--color-semantic-success-1)"}]
       [".board-kpi.amber" {"--tone"
                            "var(--color-semantic-warning-yellow-1)"}]
       [".board-kpi.blue" {"--tone" "var(--color-key-900)"}]
       [".board-kpi.violet" {"--tone" "var(--color-primitive-purple-700)"}]
       [".now-board,.flow-board" {:grid-template-rows "52px minmax(0,1fr)"}]
       [".section-heading" {:border-bottom
                            "1px solid var(--color-neutral-solid-gray-200)"}]
       [".section-heading h2" {:font-size 20}]
       [".section-heading span" {:color "var(--color-neutral-solid-gray-600)"
                                 :font-size 12}]
       [".linear-workspace" {:margin-top 12 :border-radius 12
                             :background "var(--color-neutral-white)"
                             :border "1px solid var(--color-neutral-solid-gray-200)"}]
       [".work-sidebar" {:padding 14
                         :background "var(--color-neutral-solid-gray-50)"
                         :border-right "1px solid var(--color-neutral-solid-gray-200)"}]
       [".work-sidebar > small" {:color "var(--color-neutral-solid-gray-500)"
                                 :font-size 11}]
       [".work-sidebar button" {:min-height 40 :color
                                "var(--color-neutral-solid-gray-700)"
                                :font-size 13}]
       [".work-sidebar button:hover,.work-sidebar button.selected"
        {:background "var(--color-key-50)"
         :color "var(--color-key-900)"}]
       [".work-sidebar button b" {:background
                                  "var(--color-neutral-solid-gray-200)"
                                  :color "var(--color-neutral-solid-gray-700)"
                                  :font-size 10}]
       [".sidebar-divider,.work-list-header,.work-row"
        {:border-color "var(--color-neutral-solid-gray-200)"}]
       [".work-list-header" {:color "var(--color-neutral-solid-gray-500)"
                             :font-size 11}]
       [".work-row" {:min-height 58 :color "var(--color-neutral-solid-gray-800)"}]
       [".work-row:hover" {:background "var(--color-neutral-solid-gray-50)"}]
       [".row-title strong" {:color "var(--color-neutral-solid-gray-900)"
                             :font-size 13}]
       [".row-title small,.row-project,.row-agent small,.work-row time"
        {:color "var(--color-neutral-solid-gray-600)" :font-size 11}]
       [".row-agent b" {:color "var(--color-neutral-solid-gray-800)"
                        :font-size 11}]
       [".row-stage" {:background "var(--color-neutral-solid-gray-100)"
                      :color "var(--color-neutral-solid-gray-700)"
                      :font-size 10}]
       [".row-stage.implement" {:background "var(--color-key-50)"
                                :color "var(--color-key-900)"}]
       [".row-stage.review" {:background
                             "var(--color-neutral-solid-gray-100)"
                             :color "var(--color-primitive-purple-800)"}]
       [".row-stage.blocked" {:background "var(--color-primitive-red-50)"
                              :color "var(--color-semantic-error-1)"}]
       [".organism-layer > header" {:border-color
                                    "var(--color-neutral-solid-gray-200)"}]
       [".organism-layer > header small" {:color "var(--color-key-900)"
                                          :font-size 10}]
       [".organism-layer > header h3" {:font-size 14}]
       [".organism-layer > header > span"
        {:color "var(--color-neutral-solid-gray-600)" :font-size 10}]
       [".organism-actor,.loop-agent" {:background "var(--color-neutral-white)"
                                       :border-top-color
                                       "var(--color-neutral-solid-gray-200)"
                                       :border-right-color
                                       "var(--color-neutral-solid-gray-200)"
                                       :border-bottom-color
                                       "var(--color-neutral-solid-gray-200)"}]
       [".loop-agent" {:background "var(--color-neutral-white)"
                       :border-top-color "var(--color-neutral-solid-gray-200)"
                       :border-right-color "var(--color-neutral-solid-gray-200)"
                       :border-bottom-color "var(--color-neutral-solid-gray-200)"}]
       [".organism-actor header strong,.loop-agent strong,.loop-model b"
        {:color "var(--color-neutral-solid-gray-900)"}]
       [".organism-actor header small,.organism-actor p,.organism-actor footer,
          .loop-agent small,.loop-project,.loop-model small,.loop-agent time"
        {:color "var(--color-neutral-solid-gray-600)"}]
       [".organism-actor header em"
        {:background "var(--color-neutral-solid-gray-100)"
         :color "var(--color-neutral-solid-gray-700)"}]
       [".organism-actor.working" {:border-left-color
                                   "var(--color-semantic-success-1)"}]
       [".organism-actor.queued,.loop-agent" {:border-left-color
                                              "var(--color-key-900)"}]
       [".organism-actor.needs-agent" {:border-left-color
                                       "var(--color-semantic-warning-yellow-1)"}]
       [".organism-actor.blocked" {:border-left-color
                                   "var(--color-semantic-error-1)"}]
       [".loop-pulse" {:background "var(--color-key-900)"
                       :box-shadow "0 0 0 4px var(--color-key-50)"}]
       [".flow-column" {:background "var(--color-neutral-white)"
                        :border "1px solid var(--color-neutral-solid-gray-200)"}]
       [".flow-column > header" {:border-bottom
                                 "1px solid var(--color-neutral-solid-gray-200)"}]
       [".flow-column > header span" {:background
                                      "var(--color-neutral-solid-gray-100)"}]
       [".work-card" {:background "var(--color-neutral-white)"
                      :border "1px solid var(--color-neutral-solid-gray-200)"}]
       [".work-card:hover" {:border-color "var(--color-key-600)"}]
       [".work-project" {:color "var(--color-neutral-solid-gray-900)"}]
       [".work-issue" {:color "var(--color-key-900)"}]
       [".work-output" {:border-color "var(--color-neutral-solid-gray-200)"}]
       [".work-output span,.work-output em,.work-card-head small"
        {:color "var(--color-neutral-solid-gray-600)"}]))

(def dads-finance
  (css {:left 244 :right 0 :top 0 :bottom 0 :padding
        "28px clamp(20px,4vw,48px)" :border 0 :border-radius 0
        :box-shadow :none :background "var(--color-neutral-solid-gray-50)"
        :color "var(--color-neutral-solid-gray-900)"}
       [".finance-title" {:border-color "var(--color-neutral-solid-gray-200)"}]
       [".finance-title small,.finance-card h3,.finance-segment span"
        {:color "var(--color-key-900)"}]
       [".finance-period,.finance-kpi small,.finance-line"
        {:color "var(--color-neutral-solid-gray-600)"}]
       [".finance-kpi,.finance-card,.finance-segment"
        {:background "var(--color-neutral-white)"
         :border "1px solid var(--color-neutral-solid-gray-200)"}]
       [".finance-kpi strong,.finance-line strong"
        {:color "var(--color-neutral-solid-gray-900)"}]
       [".finance-line" {:border-color "var(--color-neutral-solid-gray-200)"}]))
