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

(defn insert-text! [db {:keys [text/name text/content]}]
  (let [now (str (java.time.Instant/now))]
    (jdbc/execute-one! *db* ["insert into text (name, content, created, modified) values (?, ?, ?, ?) returning id"
                             name content now now])))

(defn insert-sentence! [db {text-id :text/id :keys [sentence/text-offset sentence/length]}]
  (prn [text-id text-offset length])
  (jdbc/execute-one! db ["insert into sentence (text_id, text_offset, length)
                     values (?, ?, ?) returning id"
                         text-id text-offset length]))

(defn upsert-lemma! [db {:keys [lemma/reading lemma/writing lemma/pos]}]
  (jdbc/execute-one! db ["insert into lemma (reading, writing, pos) values (?, ?, ?)
                         on conflict do nothing returning id"
                         reading writing (kuromoji/pos->int pos)]))

(defn insert-word! [db {sentence-id :sentence/id, lemma-id :lemma/id, :keys [word/sentence-offset word/length]}]
  (jdbc/execute-one! db ["insert into word (sentence_id, sentence_offset, length, lemma_id)
                      values (?, ?, ?, ?) returning id"
                         sentence-id sentence-offset length lemma-id]))

(defn analyze-text! [db text-id content]
  (jdbc/with-transaction [tx db]
    (doseq [sentence (morph/text->sentence-offsets content)
            :let [sentence (assoc sentence :text/id text-id)
                  sentence-id (:sentence/id (insert-sentence! tx sentence))
                  {text-offset :sentence/text-offset, sentence-length :sentence/length} sentence
                  sentence-content (subs content text-offset (+ text-offset sentence-length))]
            ;; token is a lemma, plus :word/sentence-offset and :word/length
            token (kuromoji/tokenize-mini sentence-content)]
      (let [{lemma-id :lemma/id} (upsert-lemma! tx token)
            word (-> (select-keys token [:word/sentence-offset :word/length])
                     (assoc :sentence/id sentence-id
                            :lemma/id lemma-id))]
        (insert-word! tx word)))))

(comment
  (eval-eql [{:db/texts [:text/id :text/name]}])

  (eval-eql [{:db/lemmas [:lemma/id]}])

  (insert-text! *db* #:text{:name "test5" :content "asdlkajsdklfasdjf"})

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
