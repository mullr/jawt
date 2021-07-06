(ns jawt.http
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [jawt.eql :as eql]
   [mount.core :as mount]
   [reitit.ring]
   [ring.adapter.jetty :refer [run-jetty]]))


(defn ping-handler [_]
  {:status 200, :body "ok"})

(defn eql-handler [req]
  (let [eql (edn/read (java.io.PushbackReader. (io/reader (:body req))))
        res (eql/eval-eql eql)]
    {:status 200
     :headers {"Content-Type" "text/edn"}
     :body (pr-str res)}))

(def handler
  (reitit.ring/ring-handler
   (reitit.ring/router [["/ping" {:get ping-handler}]
                        ["/eql" {:post eql-handler}]])
   (reitit.ring/routes
    (reitit.ring/create-resource-handler {:path "/"})
    (reitit.ring/create-default-handler))))

(defn start-web-server []
  (run-jetty handler {:port 3000 :join? false}))

(mount/defstate web-server
  :start (start-web-server)
  :stop (.stop web-server))
