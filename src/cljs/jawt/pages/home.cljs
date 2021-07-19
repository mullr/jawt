(ns jawt.pages.home
  (:require
   [ajax.core :refer [GET POST]]
   [jawt.event-bus :as ev]
   [reagent.core :as r]
   [reitit.frontend.easy :as rfe]))

(defn load-texts [s _]
  (GET "/texts"
      {:handler #(ev/emit [::set-texts %])})
  s)

(defn set-texts [s [_ texts]]
  (assoc s :ui/texts texts))

(defn add-text [s _]
  (assoc s :ui/is-adding true))

(defn add-text-cancel [s _]
  (dissoc s :ui/is-adding))

(defn add-text-update [s [_ key val]]
  (assoc-in s [:ui/adding key] val))

(defn add-text-save [s _]
  (POST "/texts"
      {:params (:ui/adding s)
       :handler #(ev/emit [::add-text-finish %])})
  s)

(defn add-text-finish [s _]
  (ev/emit [::load-texts])
  (-> s
      (dissoc :ui/is-adding)
      (dissoc :ui/adding)))

(defn start [state _]
  (reset! state {:texts []})
  (ev/subs state
           {::load-texts #'load-texts
            ::set-texts #'set-texts
            ::add-text #'add-text
            ::add-text-update #'add-text-update
            ::add-text-cancel #'add-text-cancel
            ::add-text-save #'add-text-save
            ::add-text-finish #'add-text-finish})
  (ev/emit [::load-texts]))

(defn stop [state parameters]
  (let [this-ns (namespace ::foo)]
    (ev/unsub (fn [id] (= (namespace id) this-ns))))
  (reset! state nil))

(defn texts-list-view [texts-state]
  [:ul
   (for [t @texts-state]
     [:li {:key (:text/id t)}
      [:a {:href (rfe/href :page/read {:id (:text/id t)})}
       (:text/name t)]])])

(defn add-text-view [adding-state]
  [:div
   [:hr]
   [:h3 "Add Text"]
   [:div
    [:label {:for :add-text-name} "Name "]
    [:input {:type :text
             :id :add-text-name
             :onChange #(ev/emit [::add-text-update :text/name
                                  (-> % .-target .-value)])
             :value (:text/name @adding-state)}]]
   [:div 
    [:label {:for :add-text-content} "Content"]]
   [:div
    [:textarea {:id :add-text-content
                :rows 20
                :cols 80
                :onChange #(ev/emit [::add-text-update :text/content
                                     (-> % .-target .-value)])
                :value (:text/content @adding-state)}]]
   [:button {:onClick (fn [_] (ev/emit [::add-text-cancel]))}
    "Cancel"]
   " "
   [:button {:onClick (fn [_] (ev/emit [::add-text-save]))}
    "Save"]])

(defn view [state]
  (let [texts (r/cursor state [:ui/texts])
        adding (r/cursor state [:ui/adding])]
    (fn []
      [:div
       [:h3 "Texts"]
       [texts-list-view texts]
       
       (if (:ui/is-adding @state)
         [add-text-view adding]
         [:button {:onClick (fn [_] (ev/emit [::add-text]))} "+ Add Text"])])))
