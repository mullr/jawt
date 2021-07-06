(ns jawt.core
  (:require
   [jawt.db]
   [jawt.http]
   [mount.core :as mount]
   [mount-up.core :as mu]
   [signal.handler :as signal]
   [clojure.tools.logging :as log]))

(defn -main [& args]
  (signal/with-handler :int
    (log/info "caught SIGINT, quitting")
    (mount/stop))

  (signal/with-handler :term
    (log/info "caught SIGTERM, quitting")
    (mount/stop))

  (mu/on-upndown :info mu/log :before)
  (mount/start))
