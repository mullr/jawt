(ns user
  (:require
   [jawt.db :as db]
   [jawt.http]
   [mount.core :as mount]
   [mount-up.core :as mu]
   [migratus.core :as migratus]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server :as shadow-server]))

(mu/on-upndown :info mu/log :before)

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

(mount/defstate shadow-cljs-server
  :start (shadow-server/start!)
  :stop (shadow-server/stop!))

(mount/defstate shadow-cljs-watch
  :start (shadow/watch :main)
  :stop (shadow/stop-worker :main))
