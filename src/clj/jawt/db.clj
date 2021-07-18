(ns jawt.db
  (:require
   [clojure.instant :refer [read-instant-timestamp]]
   [hikari-cp.core :as hikari-cp]
   [jawt.kuromoji-tables :as kuromoji-tables]
   [migratus.core :as migratus]
   [mount.core :as mount]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set]))

(def jdbc-url "jdbc:sqlite:jawt.db")

(def migratus-config
  {:store :database
   :db {:connection-uri jdbc-url}})

(defn migrate []
  (migratus/migrate migratus-config))

(mount/defstate ^:dynamic *db*
  :start (hikari-cp/make-datasource {:jdbc-url jdbc-url
                                     :connection-init-sql "PRAGMA foreign_keys = true;"})
  :stop (hikari-cp/close-datasource *db*))

;;; text

(defn insert-text! [db text]
  (let [now (java.time.Instant/now)]
    (jdbc/execute-one! db
                       ["insert into text (name, content, created, modified)
                         values (?, ?, ?, ?)
                         returning id"
                        (:text/name text) (:text/content text) now now])))

(defn unmunge-text [row]
  (-> row
      (update :text/created clojure.instant/read-instant-timestamp)
      (update :text/modified clojure.instant/read-instant-timestamp)))

(defn list-texts [db]
  (->> (jdbc/execute! db ["select id, name, created, modified from text"])
       (map unmunge-text)))

(defn get-text [db id]
  (some-> (jdbc/execute-one! db ["select id, name, created, modified
                                  from text where id = ?"
                                 id])
          unmunge-text))

(defn get-text-content [db id]
  (some-> (jdbc/execute-one! db ["select id, name, created, modified, content
                                  from text where id = ?"
                                 id])
          unmunge-text))

;;; knowledge

(def familiarity->int
  {:new 0
   :learning 1
   :known 2})

(def int->familiarity
  (->> familiarity->int
       (map (fn [[k v]] [v k]))
       (sort-by first)
       (map second)
       (into [])))

(defn munge-lemma [k]
  (-> k
      (update :lemma/pos kuromoji-tables/pos->int)))

(defn munge-knowledge [k]
  (-> k
      (update :lemma/pos kuromoji-tables/pos->int)
      (update :knowledge/familiarity familiarity->int)))

(defn unmunge-knowledge [k]
  (-> k
      (update :lemma/pos kuromoji-tables/int->pos)
      (update :knowledge/familiarity int->familiarity)))

(defn unmunge-knowledge-partial [k]
  (update k :knowledge/familiarity int->familiarity))

(defn upsert-knowledge! [db k]
  (let [{:lemma/keys [pos reading writing]
         :knowledge/keys [familiarity]} (munge-knowledge k)
        now (java.time.Instant/now)]
    (jdbc/execute-one! db ["insert into knowledge (lemma_pos, lemma_reading, lemma_writing,
                                                   familiarity, created, modified)
                            values (?, ?, ?, ?, ?, ?)
                            on conflict do update set familiarity=?, modified=? "
                           pos reading writing familiarity now now
                           familiarity now])))

(defn get-knowledge [db lemma]
  (let [{:lemma/keys [pos reading writing]} (munge-lemma lemma)]
    (some-> (jdbc/execute-one! db ["select familiarity, created, modified from knowledge
                                    where lemma_pos = ? and lemma_reading = ? and lemma_writing = ?"
                                   pos reading writing])
            unmunge-knowledge-partial)))

(comment
  (def ryoma (slurp "ryoma1_1.txt"))

  (insert-text! *db* #:text{:name "Ryoma"
                            :content ryoma})

  (list-texts *db*)
  
  (get-text *db* 1)

  (get-text *db* 42)

  )
