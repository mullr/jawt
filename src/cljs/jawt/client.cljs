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

#_(defn load-texts! [db]
  (GET (str "/texts")
      {:handler (partial d/transact! db)}))

#_(defn prepare-idents-for-db [ent]
  (if (:word/id ent)
    (-> ent
        (assoc :word/sentence [:sentence/id (:sentence/id ent)])
        (dissoc :sentence/id))
    (-> ent
        (assoc :sentence/text [:text/id (:text/id ent)])
        (dissoc :text/id))))

#_(defn load-sentences! [conn text-id offset count]
  (GET (str "/texts/" text-id "/sentences")
      {:params {:offset offset
                :count count}
       :handler (fn [res]
                  ;; (doseq [e res]
                  ;;   (prn (prepare-idents-for-db e)))
                  (d/transact! conn (map prepare-idents-for-db res)))}))

#_(defn init-reading-progress [conn text-id]
  (let [existing (d/q '[:find ?p :in $ ?p
                        :where
                        [$ ?t :text/id ?text-id]
                        [$ ?p  :ui.reading-progress/text ?t]]
                      @conn text-id)]
    (when (empty? existing)
      (p/transact! conn [#:ui.reading-progress{:text [:text/id text-id]
                                               :sentence-number 0}]))))

#_(defn get-reading-progress [conn text-id]
  (first 
   (:ui.reading-progress/_text 
    @(p/pull conn [{:ui.reading-progress/_text [:db/id :ui.reading-progress/sentence-number]}]
             [:text/id 1]))))

(def page-size 4)

#_(defn load-visible-sentences [conn text-id]
  (let [{:ui.reading-progress/keys [sentence-number]} (get-reading-progress conn text-id)]
    (load-sentences! conn text-id sentence-number (+ sentence-number page-size))))

#_(defn update-reading-progress [conn text-id f]
  (let [p (get-reading-progress conn text-id)
        _ (prn p)
        p' (f p)]
    (prn p')
    (p/transact! conn [p'])))

#_(defn next-page! [conn text-id]
  (update-reading-progress conn text-id
                           #(update % :ui.reading-progress/sentence-number + page-size)))

#_(defn prev-page! [conn text-id]
  (update-reading-progress conn text-id
                           #(update % :ui.reading-progress/sentence-number - page-size)))


#_(defn read [route]
  (let [text-id (js/parseInt (get-in route [:path-params :id]))]
    (init-reading-progress conn text-id)
    (load-visible-sentences conn text-id)
    (fn []
      (let [{:ui.reading-progress/keys [sentence-number]}
            (load-visible-sentences conn text-id)

            visible-sentence-eids
            @(p/q '[:find ?s-num ?s-id ?s-content
                    :in $ ?text-id ?page-size
                    :where
                    [$ ?t :text/id ?text-id]
                    [$ ?rp :ui.reading-progress/text ?t]
                    [$ ?rp :ui.reading-progress/sentence-number ?min-s-num]
                    [$ ?s :sentence/text ?t]
                    [$ ?s :sentence/id ?s-id]
                    [$ ?s :sentence/number ?s-num]
                    [(<= ?min-s-num ?s-num)]
                    [(+ ?min-s-num ?page-size) ?max-s-num]
                    [(< ?s-num ?max-s-num)]
                    [$ ?s :sentence/content ?s-content]]
                  conn text-id page-size)

            ;; {sentences :sentence/_text}
            ;; @(p/pull conn
            ;;          [{:sentence/_text [:db/id :sentence/id :sentence/content]}]
            ;;          [:text/id text-id])
            ]
        ;; (when (empty? sentences)
        ;;   (load-sentences! conn text-id 0 4))
        [:div
         [:div "Reading " text-id]
         [:button {:onClick (fn [_] (prev-page! conn text-id))} "<- prev "]
         [:button {:onClick (fn [_] (next-page! conn text-id))} "next ->"]
         [:pre
          (for [[_ id content] (sort-by first visible-sentence-eids)]
            [:div {:key id} content])]]))))

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
