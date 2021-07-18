(ns jawt.client
  (:require
   [ajax.core :refer [GET POST]]
   [jawt.pages.home]
   [jawt.pages.read]
   [reagent.core :as r]
   [reagent.dom]
   [reitit.frontend]
   [reitit.frontend.easy :as rfe]
   [reitit.frontend.controllers]
   [reitit.coercion.spec]))

(defn root
  "The root component dispatches to the right page, depending on the route."
  [router-state]
  (let [route (r/cursor router-state [:route])]
    (fn []
      (let [route @route
            route-data (:data route)]
        (if-let [view (:view route-data)]
          [view]
          [:pre "Invalid route:" (pr-str route)])))))

(defn page [app-state {:keys [key view start stop extra-opts
                              route-parameters controller-parameters]}]
  (let [_ (swap! app-state assoc key nil)
        state (r/cursor app-state [key])]
    (merge {:name key
            :view (partial view state)
            :controllers [(merge {:start (partial start state)
                                  :stop (partial stop state)}
                                 (when controller-parameters
                                   {:parameters controller-parameters}))]}
           (when route-parameters
             {:parameters route-parameters}))))

(defn routes [app-state]
  [["/" 
    (page app-state
          {:key :page/home
           :view #'jawt.pages.home/view
           :start #'jawt.pages.home/start
           :stop #'jawt.pages.home/stop})]
   ["/texts/:id/read"
    (page app-state
          {:key :page/read
           :view #'jawt.pages.read/view
           :start #'jawt.pages.read/start
           :stop #'jawt.pages.read/stop
           :route-parameters {:path {:id int?}}
           :controller-parameters {:path [:id]}})]])

(defn router [app-state]
  (reitit.frontend/router (routes app-state)
                          {:data {:coercion reitit.coercion.spec/coercion}}))

(defn handle-route-change [router-state new-match]
  ;; do this outside the swap! so start/stop can modify guts of the app atom
  (swap! router-state
         (fn [s]
           (-> s
               (update :controllers reitit.frontend.controllers/apply-controllers new-match)
               (assoc :route new-match)))))


(defonce router-state (r/atom {}))
(defonce app-state (r/atom {}))
(def dom-root (.getElementById js/document "app"))

(defn ^:export init []
  (reagent.dom/render [root router-state] dom-root)
  (rfe/start! (router app-state)
              (partial handle-route-change router-state)
              {:use-fragment true})
  (js/console.log ">> Loaded <<"))

(defn ^:export refresh []
  (reagent.dom/render [#'root router-state] dom-root)
  (reagent.dom/force-update-all)
  (prn "@app-state keys" (keys @app-state))
  (js/console.log ">> Hot reload <<"))

(comment
  (load-sentences conn 1 0 4)

  (init-reading-progress conn 1)

  (get-reading-progress conn 1)

  )
