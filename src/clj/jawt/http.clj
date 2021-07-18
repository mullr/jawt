(ns jawt.http
  (:require
   [jawt.db :as db]
   [jawt.morph :as morph]
   [jawt.kuromoji :as kuromoji]
   [mount.core :as mount]
   [muuntaja.core]
   [reitit.ring]
   [reitit.ring.middleware.muuntaja]
   [reitit.ring.middleware.parameters]
   [ring.adapter.jetty :refer [run-jetty]]))

;;; ping
(defn ping-handler [_]
  {:status 200, :body "ok"})

;;; texts

(defn list-texts [req]
  {:status 200
   :body (db/list-texts (::db req))})

(defn get-text [req]
  (let [id (Integer/parseInt (get-in req [:path-params :id]))]
    (if-let [text (db/get-text (::db req) id)]
      {:status 200, :body text}
      {:status 404})))

;; return both sentence and word entities
(defn get-sentences [req]
  (let [text-id (Integer/parseInt (get-in req [:path-params :id]))
        offset (or (some-> (get-in req [:query-params "offset"])
                           (Integer/parseInt))
                   0)
        count-param (some-> (get-in req [:query-params "count"])
                      (Integer/parseInt))
        text (db/get-text-content (::db req) text-id)]
    (if-let [{:text/keys [content]} text]
      (let [raw-sentences (jawt.morph/text-content->sentences content)
            total-sentence-count (count raw-sentences)
            sentences (->> raw-sentences
                           (map-indexed (fn [n s]
                                          (let [sentence-number (+ n offset)]
                                            (assoc s
                                                   :text/id text-id
                                                   :sentence/number sentence-number
                                                   :sentence/id (str text-id "/" sentence-number))))))
            sentences (drop offset sentences)
            sentences (if count-param (take count-param sentences) sentences)
            ;; Pull me under
            sentences-and-words
            (into []
                  (map (fn [{sentence-id :sentence/id, sentence-content :sentence/content :as s}]
                         (let [words (into []
                                          ;; word idents are [text-id sentence-id word-id]
                                           (map-indexed (fn [n w]
                                                          (merge w
                                                                 (or (db/get-knowledge (::db req) w)
                                                                     {:knowledge/familiarity :new})
                                                                 {:sentence/id sentence-id 
                                                                  :word/number n 
                                                                  :word/id (str sentence-id "/" n)})))

                                          (kuromoji/tokenize-mini sentence-content))]
                           (assoc s :sentence/words words))))
                 sentences)]
        {:status 200
         :body {:text/sentence-count total-sentence-count
                :text/sentences sentences-and-words}})
      {:status 404
       :body (str "text id=" text-id " not found")})))

(defn update-knowledge [req]
  (let [k (select-keys (:body-params req) [:lemma/reading :lemma/writing :lemma/pos :knowledge/familiarity])]
    (db/upsert-knowledge! (::db req) k)
    {:status 200}))

;;; server

(defn wrap-assoc-db [handler]
  (fn [req]
    (handler (assoc req ::db db/*db*))))

(def handler
  (reitit.ring/ring-handler
   (reitit.ring/router
    [["/ping" {:get ping-handler}]
     ["/texts/:id/sentences" {:get get-sentences}]
     ["/texts/:id" {:get get-text}]
     ["/texts" {:get list-texts}]
     ["/knowledge" {:post update-knowledge}]]
    {:data {:muuntaja muuntaja.core/instance
            :middleware [reitit.ring.middleware.muuntaja/format-middleware
                         reitit.ring.middleware.parameters/parameters-middleware
                         wrap-assoc-db]}})
   (reitit.ring/routes
    (reitit.ring/create-resource-handler {:path "/"})
    (reitit.ring/create-default-handler))))

(defn start-web-server []
  (run-jetty handler {:port 3000 :join? false}))

(mount/defstate web-server
  :start (start-web-server)
  :stop (.stop web-server))
