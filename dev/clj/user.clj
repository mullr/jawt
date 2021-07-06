(ns user
  (:require
   [jawt.db :as db]
   [jawt.http]
   [mount.core :as mount]
   [mount-up.core :as mu]
   [migratus.core :as migratus]
   [shadow.cljs.devtools.api]
   [shadow.cljs.devtools.server]))

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
  :start (shadow.cljs.devtools.server/start!)
  :stop (shadow.cljs.devtools.server/stop!))

(mount/defstate shadow-cljs-watch
  :start (shadow.cljs.devtools.api/watch :main)
  :stop (shadow.cljs.devtools.api/stop-worker :main))
