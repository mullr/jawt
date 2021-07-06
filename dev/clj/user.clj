(ns user
  (:require [mount.core :as mount]
            [migratus.core :as migratus]
            [jawt.db :as db]))

(defn start []
  (mount/start))

(defn stop []
  (mount/stop))

(defn restart []
  (stop)
  (start))

(defn migrate []
  (migratus/init db/migratus-config)
  (db/migrate))

(defn re-migrate []
  (migratus/rollback db/migratus-config)
  (db/migrate))

