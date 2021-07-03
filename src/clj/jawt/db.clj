(ns jawt.db
  (:require
   [hikari-cp.core :as hikari-cp]
   [migratus.core :as migratus]
   [mount.core :as mount]
   [next.jdbc :as jdbc]))

(def jdbc-url "jdbc:sqlite:jawt.db")
(def migratus-config
  {:store :database
   :db {:connection-uri jdbc-url}})

(mount/defstate ^:dynamic *db*
  :start (hikari-cp/make-datasource {:jdbc-url jdbc-url
                                     :connection-init-sql "PRAGMA foreign_keys = true;"})
  :stop (hikari-cp/close-datasource *db*))


(defn migrate []
  (migratus/migrate migratus-config))


