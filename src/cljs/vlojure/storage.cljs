(ns vlojure.storage
  (:require [vlojure.util :as u]
            [vlojure.constants :as constants]
            [vlojure.evaluation :as evaluation]
            [vlojure.vedn :as vedn]
            [clojure.edn :as edn]
            [clojure.string :as string]))

;;; This file defines ways of interacting with browsers' local storage, which
;;; allows a user's code and preferences to be saved once the page is closed.
;;; All of a user's projects and settings are saved in local storage, and can
;;; be accessed and modified using the functions defined in this file.

(def storage js/window.localStorage)

(defonce app-state (atom {}))

(defn landscape? []
  true)

(defn saved-state []
  (.getItem storage "state"))

(defn load-state! []
  (reset! app-state
          (edn/read-string (saved-state))))

(defn save-state! []
  (.setItem storage
            "state"
            (str @app-state)))

(defn clear-saved-state! []
  (.setItem storage
           "state"
            ""))

(defn attr [key]
  (get @app-state key))

(defn set-attr! [key value]
  (swap! app-state
         #(assoc % key value))
  (save-state!)
  value)

(defn update-attr! [key value]
  (swap! app-state
         #(update % key value))
  (save-state!)
  (attr key))

(defn active-project []
  (attr :active-project))

(defn project-attr [key]
  (get-in @app-state
          [:projects (active-project) key]))

(defn set-project-attr! [key value]
  (swap! app-state
         #(assoc-in %
                    [:projects (active-project) key]
                    value))
  (save-state!)
  value)

(defn update-project-attr! [key value]
  (swap! app-state
         #(update-in %
                     [:projects (active-project) key]
                     value))
  (save-state!)
  (project-attr key))

(defn track-discard [form]
  (update-project-attr! :discard-history
                        #(conj % form))
  (save-state!))

(defn delete-project-formbar-form-at [path]
  (let [[side stage substage _ form-index] path]
    (swap! app-state
           (fn [state]
             (update-in state
                        [:projects (active-project) :formbars side stage substage :forms]
                        #(u/vector-remove % form-index)))))
  (save-state!))

(defn add-project-formbar-form-at [form formbar-path insertion-index]
  (swap! app-state
         (fn [state]
           (update-in state
                      (concat [:projects (active-project) :formbars] formbar-path [:forms])
                      #(u/vector-insert % insertion-index form))))
  (save-state!))

(defn delete-project-formbar-at [path]
  (update-project-attr! :formbars
                        (fn [formbars]
                          (let [stage (get-in formbars (butlast path))]
                            (if (> (count stage) 1)
                              (update-in formbars
                                         (butlast path)
                                         #(u/vector-remove % (last path)))
                              (update formbars
                                      (first path)
                                      #(u/vector-remove % (second path))))))))

(defn add-project-formbar-at [path]
  (update-project-attr! :formbars
                        (fn [formbars]
                          (let [[side stage-index formbar-index] path]
                            (update formbars
                                    side
                                    (fn [side-formbars]
                                      (if (>= stage-index (count side-formbars))
                                        (conj side-formbars [{:forms []}])
                                        (update side-formbars
                                                stage-index
                                                #(u/vector-insert % formbar-index {:forms []})))))))))

(defn camera-speed [diff]
  (let [speed-param (attr :camera-speed)
        speed-factor (/ (- 1 speed-param))
        base-speed-exp (* speed-factor 4.5)
        speed-boost-exp (* speed-factor 1.5)
        slow-speed (/ 1 (Math/pow 10 base-speed-exp))
        fast-speed (/ 1 (Math/pow 10 (+ base-speed-exp
                                        (* (Math/abs diff)
                                           speed-boost-exp))))]
    (cond (pos? diff)
          {:move fast-speed :zoom slow-speed}

          (neg? diff)
          {:move slow-speed :zoom fast-speed}

          :else {:move slow-speed :zoom slow-speed})))

(defn base-zoom []
  (u/map-range 0 1
               0.3 1
               (attr :base-zoom)))

(defn formbar-radius []
  (u/map-range 0 1
               0.03 0.1
               (attr :formbar-radius)))

(defn load-project [index]
  (update-attr! :projects
                (fn [projects]
                  (vec (concat [(nth projects index)]
                               (u/vector-remove projects
                                                index))))))

(defn duplicate-project []
  (update-attr! :projects
                (fn [projects]
                  (vec (conj (seq projects)
                             (update (first projects)
                                     :name
                                     (let [names (set (mapv :name projects))]
                                       (fn [name]
                                         (let [is-copy? (re-matches #".* copy($| \d+)$" name)]
                                           (some (fn [index]
                                                   (let [suffix (if (zero? index)
                                                                  " copy"
                                                                  (str " copy " index))
                                                         new-name (if is-copy?
                                                                    (string/replace name
                                                                                    #" copy($| \d+)$"
                                                                                    suffix)
                                                                    (str name suffix))]
                                                     (when-not (names new-name)
                                                       new-name)))
                                                 (range)))))))))))

(defn blank-project []
  (let [project-names (mapv :name (attr :projects))
        name (some (fn [index]
                     (let [untitled-name (if (zero? index)
                                           "Untitled"
                                           (str "Untitled " (inc index)))]
                       (when (not (u/in? project-names untitled-name))
                         untitled-name)))
                   (range))]
    {:name name

     :form
     (vedn/clj->vedn "()")

     :formbars
     (let [primary [[{:forms (mapv (comp first
                                         :children
                                         vedn/clj->vedn)
                                   ["()"
                                    "[]"
                                    "{}"
                                    "#{}"])}]
                    [{:forms (mapv (comp first
                                         :children
                                         vedn/clj->vedn)
                                   ["'"
                                    "@"
                                    "`"
                                    "~@"
                                    "~"
                                    "#_"
                                    "^"
                                    "#'"])}]
                    [{:forms (mapv (comp first
                                         :children
                                         vedn/clj->vedn)
                                   ["#()"
                                    "%"
                                    "(fn [x] ())"
                                    "(let [] ())"])}]]
           secondary [[{:forms (mapv (comp first
                                           :children
                                           vedn/clj->vedn)
                                     ["1"
                                      "10"])}
                       {:forms (mapv (comp first
                                           :children
                                           vedn/clj->vedn)
                                     ["+"
                                      "-"
                                      "*"
                                      "/"
                                      "mod"])}]]]
       (merge {:bottom []
               :right []
               :left []
               :top []}
              (if landscape?
                {:right primary :left secondary}
                {:top primary :bottom secondary})))}))

(defn new-project []
  (update-attr! :projects
                #(conj % (blank-project)))
  (load-project (dec (count (attr :projects)))))

(defn delete-project []
  (when (> (count (attr :projects)) 1)
    (update-attr! :projects
                  #(vec (rest %)))
    (load-project 0)))

(defn fill-empty-project []
  (when (zero? (count (:children (project-attr :form))))
    (set-project-attr! :form
                       (vedn/clj->vedn "nil"))))

(defn project-form-count []
  (count (:children (project-attr :form))))

(defn color-scheme []
  (nth constants/color-schemes
       (or (:color-scheme @app-state)
           0)))

(defn default-app-state []
  (merge
   (zipmap (mapv second constants/settings-sliders)
           (repeat 0))
   {:base-zoom 0.5}
   {:active-project 0
    :color-scheme 0
    :scroll-direction {:x 1}
    :projects [{:name "Calculator"

                :form
                (vedn/clj->vedn "(+ 1 (* 5 10))")

                :formbars
                (let [primary [[{:forms (mapv (comp first
                                                    :children
                                                    vedn/clj->vedn)
                                              ["()"
                                               "[]"
                                               "{}"])}
                                {:forms (mapv (comp first
                                                    :children
                                                    vedn/clj->vedn)
                                              ["#()"
                                               "%"
                                               "(fn [x] ())"
                                               "(let [] ())"])}]
                               [{:forms (mapv (comp first
                                                    :children
                                                    vedn/clj->vedn)
                                              ["apply"
                                               "map"
                                               "mapv"
                                               "reduce"
                                               "some"
                                               "filter"
                                               "nth"
                                               "range"
                                               "count"])}]]
                      secondary [[{:forms (mapv (comp first
                                                      :children
                                                      vedn/clj->vedn)
                                                ["Math/pow"
                                                 "Math/sqrt"
                                                 "Math/PI"])}]
                                 [{:forms (mapv (comp first
                                                      :children
                                                      vedn/clj->vedn)
                                                ["1"
                                                 "10"])}
                                  {:forms (mapv (comp first
                                                      :children
                                                      vedn/clj->vedn)
                                                ["+"
                                                 "-"
                                                 "*"
                                                 "/"
                                                 "mod"])}]]]
                  (merge {:bottom []
                          :right []
                          :left []
                          :top []}
                         (if landscape?
                           {:right primary :left secondary}
                           {:top primary :bottom secondary})))}
               {:name "Fibonacci"

                :form
                (vedn/clj->vedn "(nth (iterate (fn [f] (conj f (+ (last f) (last (butlast f))))) [0 1]) 10)")

                :formbars
                (let [primary [[{:forms (mapv (comp first
                                                    :children
                                                    vedn/clj->vedn)
                                              ["()"
                                               "[]"
                                               "{}"
                                               "#()"
                                               "#{}"])}]
                               [{:forms (mapv (comp first
                                                    :children
                                                    vedn/clj->vedn)
                                              ["mapv"
                                               "reduce"])}
                                {:forms (mapv (comp first
                                                    :children
                                                    vedn/clj->vedn)
                                              ["(let [] ())"])}]]
                      secondary [[{:forms (mapv (comp first
                                                      :children
                                                      vedn/clj->vedn)
                                                ["+"
                                                 "-"
                                                 "*"
                                                 "/"
                                                 "mod"])}]]]
                  (merge {:bottom [] :right [] :left [] :top []}
                         (if landscape?
                           {:right primary :left secondary}
                           {:bottom primary :top secondary})))}]}))

(defn init []
  (js/console.log "Initializing...")
  (if (> (count (saved-state)) 1)
    (load-state!)
    (reset! app-state (default-app-state))))