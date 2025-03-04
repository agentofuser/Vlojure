(ns vlojure.pages.settings
  (:require [vlojure.graphics :as graphics]
            [vlojure.storage :as storage]
            [vlojure.util :as u]
            [vlojure.formbar :as formbar]
            [vlojure.geometry :as geom]
            [vlojure.constants :as constants]))

;;; This file contains the logic for the "settings" page. This page lets the
;;; user create and delete projects as well as renaming and switching between
;;; them, adjust the color scheme and different aspects of the app's visual
;;; appearance, and add or remove formbars for the current project.

(defonce dropdown-element (atom nil))
(defonce rename-element (atom nil))
(defonce ideal-scroll-pos (atom 0))
(defonce scroll-pos (atom nil))



(defn set-ideal-scroll-pos! [pos]
  (reset! ideal-scroll-pos pos))

(defn settings-circle [index]
  (let [center-circle (update (geom/circle-within (graphics/app-rect))
                              :radius (partial * (storage/base-zoom)))
        center-radius (:radius center-circle)]
    (geom/add-points center-circle
                     (geom/scale-point (storage/attr :scroll-direction)
                                       (* (- index @scroll-pos)
                                          2
                                          center-radius
                                          constants/outer-form-spacing)))))

(defn settings-circle-at [pos]
  (some #(when (geom/in-circle? (settings-circle %)
                                pos)
           %)
        (range constants/settings-pages)))

(defn on-stage? [index]
  (and @scroll-pos
       (< (Math/abs (- @ideal-scroll-pos index)) 0.01)))

(defn settings-button-circles []
  (vec
   (let [center-circle (settings-circle 0)
         center-radius (:radius center-circle)
         bar-width (* 2 center-radius constants/settings-project-dropdown-width)
         button-radius (* bar-width constants/settings-project-button-radius)
         base-button-circle (-> center-circle
                                (update :y (partial +
                                                    (* center-radius
                                                       (+ constants/settings-project-dropdown-y
                                                          constants/settings-project-dropdown-height))
                                                    (* button-radius
                                                       (inc constants/settings-project-buttons-y-spacing))))
                                (assoc :radius button-radius))]
     (for [index (range (count constants/settings-project-buttons))]
       (update base-button-circle
               :x
               #(+ %
                   (* (- index
                         (* 0.5
                            (dec
                             (count constants/settings-project-buttons))))
                      2 button-radius
                      (inc constants/settings-project-buttons-x-spacing))))))))

(defn settings-bar-scroll-circle []
  (let [center-circle (settings-circle 1)]
    (update (geom/add-points center-circle
                             (geom/scale-point (storage/attr :scroll-direction)
                                               (* (:radius center-circle)
                                                  constants/settings-bar-scroll-circle-pos)))
            :radius
            (partial * constants/settings-bar-scroll-circle-radius))))

(defn settings-slider-at [pos]
  (some (fn [index]
          (let [settings-circle (settings-circle 1)
                left (-> settings-circle
                         (update :radius (partial * constants/settings-slider-radius))
                         (update :x #(- % (* (:radius settings-circle)
                                             constants/settings-slider-width)))
                         (update :y (partial +
                                             (* (:radius settings-circle)
                                                (+ constants/settings-top-slider-y
                                                   (* constants/settings-slider-spacing index))))))
                right (-> settings-circle
                          (update :radius (partial * constants/settings-slider-radius))
                          (update :x (partial +
                                              (* (:radius settings-circle)
                                                 constants/settings-slider-width)))
                          (update :y (partial +
                                              (* (:radius settings-circle)
                                                 (+ constants/settings-top-slider-y
                                                    (* constants/settings-slider-spacing index))))))
                slider-rect [(update left :y #(- % (* (:radius settings-circle)
                                                      constants/settings-slider-radius)))
                             {:x (apply - (map :x [right left]))
                              :y (* 2 (:radius settings-circle)
                                    constants/settings-slider-radius)}]]
            (when (or (geom/in-circle? right
                                       pos)
                      (geom/in-circle? left
                                       pos)
                      (geom/in-rect? slider-rect
                                     pos))
              index)))
        (range (count constants/settings-sliders))))

(defn color-scheme-index-at [pos]
  (if (on-stage? 2)
    (let [center-circle (settings-circle 2)
          center-radius (:radius center-circle)]
      (some (fn [index]
              (let [y (* center-radius
                         (+ constants/settings-color-text-y
                            (* index constants/settings-color-spacing)))
                    height (* center-radius
                              constants/settings-color-height)
                    width (* center-radius
                             2
                             constants/settings-color-width)]
                (when (or (geom/in-rect? [(-> center-circle
                                              (update :y (partial + y (* -0.5 height)))
                                              (update :x (partial + (* -0.5 width))))
                                          {:x width
                                           :y height}]
                                         pos)
                          (geom/in-circle? (-> center-circle
                                               (update :y (partial + y))
                                               (update :x (partial + (* -0.5 width)))
                                               (assoc :radius (* 0.5 height)))
                                           pos)
                          (geom/in-circle? (-> center-circle
                                               (update :y (partial + y))
                                               (update :x (partial + (* 0.5 width)))
                                               (assoc :radius (* 0.5 height)))
                                           pos))
                  index)))
            (range (count constants/color-schemes))))
    nil))



(defn refresh-dropdown-names []
  (let [dropdown @dropdown-element
        option-names (mapv :name (storage/attr :projects))]
    (when dropdown
      (while (.-firstChild dropdown)
        (.removeChild dropdown
                      (.-lastChild dropdown)))
      (doseq [index (range (count option-names))]
        (let [name (nth option-names index)
              option (.createElement js/document "option")
              option-style (.-style option)]
          (set! (.-innerHTML option) name)
          (set! (.-backgroundColor option-style)
                (graphics/html-color (:highlight (storage/color-scheme))))
          (set! (.-border option-style) "none")
          (set! (.-outline option-style) "none")
          (set! (.-box-shadow option-style) "none")
          (.appendChild dropdown option))))))

(defn hide-rename []
  (set! (.-display (.-style @rename-element)) "none"))

(defn hide-dropdown []
  (set! (.-display (.-style @dropdown-element)) "none"))

(defn activate-project-rename-input []
  (let [rename @rename-element]
    (set! (.-value rename) (storage/project-attr :name))
    (set! (.-display (.-style rename)) "block")))



(def page
  {:init
   (fn []
     (let [dropdown (.createElement js/document "select")
           style (.-style dropdown)]
       (set! (.-onchange dropdown)
             #(do (storage/load-project (.-selectedIndex dropdown))
                  (refresh-dropdown-names)))
       (.appendChild (.-body js/document) dropdown)
       (set! (.-textAlign style) "center")
       (set! (.-position style) "absolute")
       (set! (.-fontFamily style) constants/font-name)
       (set! (.-background style) "transparent")
       (set! (.-border style) "none")
       (set! (.-outline style) "none")
       (reset! dropdown-element dropdown))
     (let [project-rename-input (.createElement js/document "input")
           style (.-style project-rename-input)]
       (set! (.-onchange project-rename-input)
             #(do (storage/set-project-attr! :name (.-value project-rename-input))
                  (refresh-dropdown-names)
                  (hide-rename)
                  (hide-dropdown)))
       (.appendChild (.-body js/document) project-rename-input)
       (set! (.-textAlign style) "center")
       (set! (.-position style) "absolute")
       (set! (.-fontFamily style) constants/font-name)
       (set! (.-background style) "transparent")
       (set! (.-border style) "none")
       (set! (.-outline style) "none")
       (reset! rename-element project-rename-input)
       (hide-rename)))

   :enter
   (fn []
     (refresh-dropdown-names)
     (reset! scroll-pos @ideal-scroll-pos))

   :exit
   (fn []
     (hide-rename)
     (hide-dropdown)
     (reset! scroll-pos nil))

   :resize-html
   (fn []
     (let [current-size (graphics/app-size)
           dropdown @dropdown-element
           rename @rename-element]
       (when dropdown
         (let [{:keys [x y radius]} (settings-circle 0)
               dropdown-width (* current-size
                                 radius
                                 constants/settings-project-dropdown-width)
               new-text-size (* current-size
                                constants/text-scale-factor
                                constants/html-text-size-factor
                                constants/settings-project-name-size
                                radius)
               style (.-style dropdown)]
           (set! (.-display style)
                 (if (and (= (.-display (.-style rename))
                             "none")
                          (on-stage? 0))
                   "block"
                   "none"))
           (set! (.-left style)
                 (str (- (graphics/screen-x x)
                         dropdown-width)
                      "px"))
           (set! (.-top style)
                 (str (graphics/screen-y
                       (+ y (* constants/settings-project-dropdown-y radius)))
                      "px"))
           (set! (.-width style)
                 (str (* 2 dropdown-width)
                      "px"))
           (set! (.-height style)
                 (str (* current-size
                         radius
                         constants/settings-project-dropdown-height)
                      "px"))
           (set! (.-fontSize style)
                 (str new-text-size
                      "px"))))
       (when rename
         (let [{:keys [x y radius]} (settings-circle 0)
               dropdown-width (* current-size
                                 radius
                                 constants/settings-project-dropdown-width)
               new-text-size (* current-size
                                constants/text-scale-factor
                                constants/html-text-size-factor
                                constants/settings-project-name-size
                                radius)
               style (.-style rename)]
           (when (and (not (on-stage? 0))
                      (= (.-display (.-style rename))
                         "block"))
             (refresh-dropdown-names)
             (hide-rename))
           (set! (.-left style)
                 (str (- (graphics/screen-x x)
                         dropdown-width)
                      "px"))
           (set! (.-top style)
                 (str (graphics/screen-y
                       (+ y (* constants/settings-project-dropdown-y radius)))
                      "px"))
           (set! (.-width style)
                 (str (* 2 dropdown-width)
                      "px"))
           (set! (.-height style)
                 (str (* current-size
                         radius
                         constants/settings-project-dropdown-height)
                      "px"))
           (set! (.-fontSize style)
                 (str new-text-size
                      "px"))))))

   :refresh-html-colors
   (fn []
     (refresh-dropdown-names)
     (doseq [html-object [@dropdown-element @rename-element]]
       (set! (.-color (.-style html-object))
             (graphics/html-color (:text (storage/color-scheme))))))

   :mouse-zone
   (fn [mouse]
     (let [[app-pos] (graphics/app-rect)
           button-circles (settings-button-circles)]
       (or (when (on-stage? 0)
             (some (fn [index]
                     (when (geom/in-circle? (nth button-circles index)
                                            mouse)
                       (nth constants/settings-project-buttons index)))
                   (range (count button-circles))))
           (cond
             (<= (geom/point-magnitude
                  (geom/subtract-points app-pos
                                        mouse))
                 constants/upper-corner-zone-radius)
             :settings-icon

             (formbar/formbar-path-at mouse)
             :formbar

             (formbar/new-formbar-circle-path-at mouse)
             :new-formbar

             (and (on-stage? 1)
                  (geom/in-circle? (settings-bar-scroll-circle) mouse))
             :scroll-circle

             (and (on-stage? 1)
                  (settings-slider-at mouse))
             :settings-slider

             (color-scheme-index-at mouse)
             :color-scheme

             (settings-circle-at mouse)
             :settings-circle

             :else :empty))))

   :render
   (fn [mouse mouse-zone]
     (formbar/render-formbars mouse)
     (doseq [i (range constants/settings-pages)]
       (graphics/circle (settings-circle i)
                        (:foreground (storage/color-scheme))
                        :background))

        ;; Sliders
     (let [center-circle (settings-circle 1)
           center-radius (:radius center-circle)]
       (doseq [slider-index (range (count constants/settings-sliders))]
         (let [[slider-name slider-key] (nth constants/settings-sliders slider-index)
               y (* center-radius
                    (+ constants/settings-top-slider-y
                       (* slider-index constants/settings-slider-spacing)))
               left (geom/add-points center-circle
                                     {:x (* -1
                                            center-radius
                                            constants/settings-slider-width)
                                      :y y})
               right (geom/add-points center-circle
                                      {:x (* center-radius
                                             constants/settings-slider-width)
                                       :y y})]
           (graphics/line left right
                          (* center-radius
                             constants/settings-slider-radius
                             2)
                          (:background (storage/color-scheme))
                          :background)
           (doseq [p [right left]]
             (graphics/circle (assoc p
                                     :radius (* center-radius
                                                constants/settings-slider-radius))
                              (:background (storage/color-scheme))
                              :background))
           (graphics/circle (assoc (geom/tween-points left right (storage/attr slider-key))
                                   :radius (* center-radius
                                              constants/settings-slider-radius
                                              constants/settings-slider-inner-radius-factor))
                            (:foreground (storage/color-scheme))
                            :background)
           (graphics/text slider-name
                          (geom/add-points center-circle
                                           {:y (+ y (* center-radius constants/settings-slider-text-y))})
                          (* center-radius
                             constants/settings-slider-text-size
                             (count slider-name))
                          (:text (storage/color-scheme))
                          :background))))

        ;; Render project dropdown
     (let [center-circle (settings-circle 0)
           center-radius (:radius center-circle)
           bar-width (* 2 center-radius constants/settings-project-dropdown-width)
           bar-height (* center-radius constants/settings-project-dropdown-height)]
       (graphics/rect [(-> center-circle
                           (update :x #(+ (- % (* center-radius constants/settings-project-dropdown-width))
                                          (* bar-height
                                             constants/settings-project-dropdown-x-shrink-factor)))
                           (update :y (partial +
                                               (* center-radius constants/settings-project-dropdown-y))))
                       {:x (- bar-width
                              (* bar-height
                                 2
                                 constants/settings-project-dropdown-x-shrink-factor))
                        :y bar-height}]
                      (:highlight (storage/color-scheme))
                      :background)
       (graphics/circle (-> center-circle
                            (update :x #(+ (- % (* center-radius constants/settings-project-dropdown-width))
                                           (* bar-height
                                              constants/settings-project-dropdown-x-shrink-factor)))
                            (update :y (partial +
                                                (* center-radius constants/settings-project-dropdown-y)
                                                (* 0.5 bar-height)))
                            (assoc :radius (* 0.5 bar-height)))
                        (:highlight (storage/color-scheme))
                        :background)
       (graphics/circle (-> center-circle
                            (update :x #(+ (- %
                                              (* center-radius constants/settings-project-dropdown-width)
                                              (* bar-height
                                                 constants/settings-project-dropdown-x-shrink-factor))
                                           bar-width))
                            (update :y (partial +
                                                (* center-radius constants/settings-project-dropdown-y)
                                                (* 0.5 bar-height)))
                            (assoc :radius (* 0.5 bar-height)))
                        (:highlight (storage/color-scheme))
                        :background)
       (graphics/text "Active Project"
                      (-> center-circle
                          (update :y (partial + (* center-radius (+ constants/settings-project-dropdown-y
                                                                    constants/settings-project-text-y)))))
                      (* center-radius constants/settings-project-text-size)
                      (:text (storage/color-scheme))
                      :background)
       (when (not (on-stage? 0))
         (graphics/text (storage/project-attr :name)
                        (-> center-circle
                            (update :y (partial + (* center-radius
                                                     (+ constants/settings-project-dropdown-y
                                                        (* 0.5
                                                           constants/settings-project-dropdown-height))))))
                        (* center-radius
                           constants/settings-project-name-size
                           (count (storage/project-attr :name)))
                        (:text (storage/color-scheme))
                        :background))

       (let [button-circles (settings-button-circles)]
         (doseq [index (range (count constants/settings-project-buttons))]
           (let [type (nth constants/settings-project-buttons index)
                 button-circle (nth button-circles index)
                 button-radius (:radius button-circle)]
             (graphics/circle button-circle
                              (:background (storage/color-scheme))
                              :background)
             (graphics/circle (update button-circle
                                      :radius
                                      (partial * constants/settings-project-button-inner-radius-factor))
                              (if (= mouse-zone type)
                                (:highlight (storage/color-scheme))
                                (:foreground (storage/color-scheme)))
                              :background)
             (case type
               :rename-project
               (let [eraser-offset (update (geom/scale-point geom/unit
                                                             (* button-radius
                                                                (Math/sqrt 0.5)
                                                                constants/settings-project-button-inner-radius-factor
                                                                constants/rename-icon-size))
                                           :y -)
                     width (* button-radius
                              constants/settings-project-button-inner-radius-factor
                              constants/rename-icon-width)
                     line-width (* button-radius
                                   constants/settings-button-line-width)
                     eraser (geom/add-points button-circle
                                             eraser-offset)
                     eraser-top (geom/add-points eraser
                                                 (geom/scale-point geom/unit
                                                                   (* (Math/sqrt 0.5)
                                                                      -0.5
                                                                      width)))
                     eraser-bottom (geom/add-points eraser
                                                    (geom/scale-point geom/unit
                                                                      (* (Math/sqrt 0.5)
                                                                         0.5
                                                                         width)))
                     eraser-edge-top (geom/add-points eraser-top
                                                      (geom/scale-point eraser-offset
                                                                        (- constants/rename-icon-eraser-size)))
                     eraser-edge-bottom (geom/add-points eraser-bottom
                                                         (geom/scale-point eraser-offset
                                                                           (- constants/rename-icon-eraser-size)))
                     tip-top (geom/add-points eraser-top
                                              (geom/scale-point eraser-offset
                                                                (- constants/rename-icon-tip-size
                                                                   (inc constants/rename-icon-tip-factor))))
                     tip-bottom (geom/add-points eraser-bottom
                                                 (geom/scale-point eraser-offset
                                                                   (- constants/rename-icon-tip-size
                                                                      (inc constants/rename-icon-tip-factor))))
                     tip (geom/add-points button-circle
                                          (geom/scale-point eraser-offset
                                                            (- constants/rename-icon-tip-factor)))]
                 (graphics/polyline [tip
                                     tip-bottom
                                     eraser-bottom
                                     eraser-top
                                     tip-top
                                     tip]
                                    line-width
                                    (:background (storage/color-scheme))
                                    :background)
                 (graphics/line eraser-edge-top
                                eraser-edge-bottom
                                line-width
                                (:background (storage/color-scheme))
                                :background)
                 (graphics/line tip-top
                                tip-bottom
                                line-width
                                (:background (storage/color-scheme))
                                :background))

               :new-project
               (let [size (* button-radius
                             constants/settings-project-button-inner-radius-factor
                             constants/new-icon-size)
                     width (* button-radius
                              constants/settings-project-button-inner-radius-factor
                              constants/new-icon-width)]
                 (doseq [dim [:x :y]]
                   (graphics/line (update button-circle
                                          dim
                                          (partial + (- size)))
                                  (update button-circle
                                          dim
                                          (partial + size))
                                  width
                                  (:background (storage/color-scheme))
                                  :background)))

               :duplicate-project
               (let [width (* button-radius
                              constants/duplicate-icon-width)
                     height (* button-radius
                               constants/duplicate-icon-height)
                     line-width (* button-radius
                                   constants/settings-button-line-width)
                     corner {:x width
                             :y height}
                     base-offset (geom/scale-point geom/unit
                                                   (* button-radius
                                                      constants/duplicate-icon-offset))]
                 (doseq [offset [(geom/scale-point base-offset -1)
                                 base-offset]]
                   (let [base (geom/add-points button-circle offset)]
                     (graphics/rect [(geom/add-points base
                                                      (geom/scale-point corner -1))
                                     (geom/scale-point corner 2)]
                                    (if (= mouse-zone :duplicate-project)
                                      (:highlight (storage/color-scheme))
                                      (:foreground (storage/color-scheme)))
                                    :background)
                     (graphics/polyline (mapv (fn [dims]
                                                (geom/add-points base
                                                                 (reduce #(update %1 %2 -)
                                                                         corner
                                                                         dims)))
                                              [[]
                                               [:x]
                                               [:x :y]
                                               [:y]
                                               []])
                                        line-width
                                        (:background (storage/color-scheme))
                                        :background))))

               :delete-project
               (let [offset (geom/scale-point geom/unit
                                              (* button-radius
                                                 (Math/sqrt 0.5)
                                                 constants/settings-project-button-inner-radius-factor
                                                 constants/new-icon-size))
                     width (* button-radius
                              constants/settings-project-button-inner-radius-factor
                              constants/new-icon-width)]
                 (doseq [offset-modifier [identity #(update % :y -)]]
                   (let [modified-offset (offset-modifier offset)]
                     (graphics/line (geom/add-points button-circle
                                                     (geom/scale-point modified-offset
                                                                       -1))
                                    (geom/add-points button-circle
                                                     modified-offset)
                                    width
                                    (:background (storage/color-scheme))
                                    :background)))))))))

        ;; Render scroll circle
     (let [scroll-circle (settings-bar-scroll-circle)
           scroll-direction (storage/attr :scroll-direction)
           triangle-base (geom/add-points scroll-circle
                                          (geom/scale-point scroll-direction
                                                            (* (:radius scroll-circle)
                                                               constants/settings-bar-scroll-triangle-pos)))
           color (if (= mouse-zone :scroll-circle)
                   (:highlight (storage/color-scheme))
                   (:foreground (storage/color-scheme)))]
       (graphics/circle scroll-circle
                        color
                        :background)
       (graphics/circle (update scroll-circle
                                :radius
                                (partial * constants/settings-bar-scroll-circle-inner-radius))
                        (:background (storage/color-scheme))
                        :background)
       (graphics/polygon (mapv #(geom/add-points triangle-base
                                                 (geom/scale-point %
                                                                   (:radius scroll-circle)))
                               [(geom/scale-point scroll-direction
                                                  constants/settings-bar-scroll-triangle-height)
                                (geom/scale-point (assoc scroll-direction
                                                         :x (:y scroll-direction)
                                                         :y (- (:x scroll-direction)))
                                                  constants/settings-bar-scroll-triangle-width)
                                (geom/scale-point (assoc scroll-direction
                                                         :x (- (:y scroll-direction))
                                                         :y (:x scroll-direction))
                                                  constants/settings-bar-scroll-triangle-width)])
                         color
                         :background))

        ;; Render color scheme page
     (let [center-circle (settings-circle 2)
           center-radius (:radius center-circle)]
       (graphics/text "Color Scheme"
                      (-> center-circle
                          (update :y
                                  (partial +
                                           (* center-radius
                                              constants/settings-color-header-text-y))))
                      (* center-radius constants/settings-color-header-text-size)
                      (:text (storage/color-scheme))
                      :background)

       (doseq [i (range (count constants/color-schemes))]
         (let [color-scheme (nth constants/color-schemes i)
               name (:name color-scheme)
               y (* center-radius
                    (+ constants/settings-color-text-y
                       (* i constants/settings-color-spacing)))
               display-colors (mapv #(% color-scheme)
                                    [:background :foreground :highlight])]
           (doseq [[color layer]
                   (if (= i (color-scheme-index-at mouse))
                     [[(:highlight color-scheme) 0]]
                     (mapv vector
                           display-colors
                           (range (count display-colors))))]
             (let [width (* 2
                            (- 1 (* layer constants/settings-color-width-factor))
                            constants/settings-color-width
                            center-radius)
                   height (* constants/settings-color-height
                             (- 1 (* layer constants/settings-color-height-factor))
                             center-radius)]
               (graphics/rect [(-> center-circle
                                   (update :y #(- (+ % y)
                                                  (* 0.5
                                                     height)))
                                   (update :x #(- % (* 0.5 width))))
                               {:x width
                                :y height}]
                              color
                              :background)
               (doseq [side [1 -1]]
                 (graphics/circle (-> center-circle
                                      (update :y (partial + y))
                                      (update :x (partial + (* 0.5
                                                               side
                                                               width)))
                                      (assoc :radius (* height 0.5)))
                                  color
                                  :background))))
           (graphics/text name
                          (-> center-circle
                              (update :y
                                      (partial +
                                               y)))
                          (* center-radius
                             constants/settings-color-text-size
                             (count name))
                          (:text color-scheme)
                          :background))))


        ;; Render new formbar circles
     (doseq [[new-formbar-circle] (formbar/new-formbar-circles)]
       (graphics/circle new-formbar-circle
                        (:highlight (storage/color-scheme))
                        :settings-overlay)
       (let [line-size (* (storage/formbar-radius)
                          constants/new-formbar-circle-radius
                          constants/new-icon-size)]
         (doseq [dim [:x :y]]
           (graphics/line (update new-formbar-circle dim (partial + line-size))
                          (update new-formbar-circle dim #(- % line-size))
                          (* (storage/formbar-radius)
                             constants/new-formbar-circle-radius
                             constants/new-icon-width)
                          (:background (storage/color-scheme))
                          :settings-overlay))))
     (let [formbar-path (formbar/formbar-path-at mouse)]
       (when formbar-path
         (let [current-formbar-arrangement (formbar/formbar-arrangement)
               hovered-formbar (get-in current-formbar-arrangement formbar-path)
               {:keys [width height]} hovered-formbar
               center (geom/add-points hovered-formbar
                                       (geom/scale-point {:x width
                                                          :y height}
                                                         0.5))]
           (graphics/circle (assoc hovered-formbar
                                   :radius (storage/formbar-radius))
                            (:highlight (storage/color-scheme))
                            :settings-overlay)
           (when (pos? width)
             (graphics/circle (-> hovered-formbar
                                  (update :x (partial + width))
                                  (assoc :radius (storage/formbar-radius)))
                              (:highlight (storage/color-scheme))
                              :settings-overlay)
             (graphics/rect [(update hovered-formbar
                                     :y #(- % (storage/formbar-radius)))
                             {:x width
                              :y (* 2 (storage/formbar-radius))}]
                            (:highlight (storage/color-scheme))
                            :settings-overlay))
           (when (pos? height)
             (graphics/circle (-> hovered-formbar
                                  (update :y (partial + height))
                                  (assoc :radius (storage/formbar-radius)))
                              (:highlight (storage/color-scheme))
                              :settings-overlay)
             (graphics/rect [(update hovered-formbar
                                     :x #(- % (storage/formbar-radius)))
                             {:x (* 2 (storage/formbar-radius))
                              :y height}]
                            (:highlight (storage/color-scheme))
                            :settings-overlay))
           (let [offset (geom/scale-point geom/unit
                                          (* (Math/sqrt 0.5)
                                             constants/new-icon-size
                                             (storage/formbar-radius)))
                 upside-down-offset (update offset :y -)]
             (graphics/line (geom/add-points center
                                             offset)
                            (geom/add-points center
                                             (geom/scale-point offset -1))
                            (* (storage/formbar-radius) constants/new-icon-width)
                            (:background (storage/color-scheme))
                            :settings-overlay)
             (graphics/line (geom/add-points center
                                             upside-down-offset)
                            (geom/add-points center
                                             (geom/scale-point upside-down-offset -1))
                            (* (storage/formbar-radius) constants/new-icon-width)
                            (:background (storage/color-scheme))
                            :settings-overlay))))))

   :update
   (fn [delta mouse]
     (swap! ideal-scroll-pos
            #(max 0
                  (min constants/settings-pages
                       %)))
     (when @scroll-pos
       (swap! scroll-pos
              #(u/tween @ideal-scroll-pos
                        %
                        (Math/pow (:move (storage/camera-speed 0))
                                  delta)))))

   :scroll
   (fn [diff]
     (swap! ideal-scroll-pos (partial + diff)))})