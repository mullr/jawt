(ns jawt.db
  (:require
   [clojure.instant :refer [read-instant-timestamp]]
   [clojure.set :refer [rename-keys]]
   [hikari-cp.core :as hikari-cp]
   [jawt.kuromoji :as kuromoji]
   [jawt.morph :as morph]
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

(defn insert-text! [db {:text/keys [name content]}]
  (let [now (str (java.time.Instant/now))]
    (jdbc/execute-one! *db*
                       ["insert into text (name, content, created, modified) values (?, ?, ?, ?) returning id"
                        name content now now])))

(defn delete-text! [db {:text/keys [id]}]
  (jdbc/execute-one! *db* ["delete from text where id=?" id]))

(defn upsert-knowlege! [db {:lemma/keys [pos-int reading writing]
                            :knowledge/keys [familiarity]}]
  (let [now (str (java.time.Instant/now))]
    (jdbc/execute-one! *db*
                       ["insert into knowledge (lemma_pos, lemma_reading, lemma_wrting,
                                                familiarity, created, modified)
                         values (?, ?, ?, ?, ?, ?) returning id
                         on conflict do update set familiarity=?, modified=?"
                        pos-int reading writing familiarity now now
                        familiarity now])))

(comment
  (def ryoma (slurp "ryoma1_1.txt"))
  (insert-text! *db* #:text{:name "ryoma 1.1" :content ryoma})
  (analyze-text! *db* 2 ryoma)
  
  (->> (jdbc/execute! *db* ["select id, name, created, modified from text"])
       (map (fn [row]
              (-> row
                  (update :text/created clojure.instant/read-instant-timestamp)
                  (update :text/modified clojure.instant/read-instant-timestamp)))))

  (jdbc/execute! *db* ["insert into text (name, content, created, modified) 
                       values ('name', 'content',
                       ?,
                       ?)"
                       (str (java.time.Instant/now))
                       (str (java.time.Instant/now))])



  )
