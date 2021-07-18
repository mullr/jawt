(ns jawt.pages.home
  (:require
   [ajax.core :refer [GET POST]]
   [reagent.core :as r]
   [reitit.frontend.easy :as rfe]))

(defn load-texts! [state]
  (GET (str "/texts")
      {:handler #(swap! state assoc :texts %)}))

(defn start [state parameters]
  (prn ">>>>>>>>>>>>>> Home start")
  (reset! state {:texts []})
  (load-texts! state))

(defn stop [state parameters]
  (prn ">>>>>>>>>>>>>> Home stop")
  (reset! state nil))

(defn view [state]
  (let [texts (r/cursor state [:texts])]
    (fn []
      [:div
       [:h3 "foobar"]
       [:ul
        (for [t @texts]
          [:li {:key (:text/id t)}
           [:a {:href (rfe/href :page/read {:id (:text/id t)})}
            (:text/name t)]])]])))
