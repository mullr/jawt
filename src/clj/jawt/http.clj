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
  (println "--------------------------------------------------------")
  (prn req)
  (let [eql (edn/read (java.io.PushbackReader. (io/reader (:body req))))
        _ (prn eql)
        res (eql/eval-eql eql)]
    (prn res)
    {:status 200
     :headers {"Content-Type" "text/edn"}
     :body (pr-str res)}))

(def router
  (reitit.ring/router
   [["/ping" {:get ping-handler}]
    ["/eql" {:post eql-handler} ]]))

(defn start-web-server []
  (run-jetty (reitit.ring/ring-handler router)
             {:port 3000
              :join? false}))

(mount/defstate web-server
  :start (start-web-server)
  :stop (.stop web-server))
