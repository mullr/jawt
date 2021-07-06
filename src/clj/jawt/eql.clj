(ns jawt.eql
  (:require
   [clojure.instant :refer [read-instant-timestamp]]
   [clojure.set :refer [rename-keys]]
   [com.wsscode.pathom.connect :as pc]
   [com.wsscode.pathom.core :as p]
   [hikari-cp.core :as hikari-cp]
   [jawt.kuromoji :as kuromoji]
   [jawt.db :as db]
   [next.jdbc :as jdbc]))

(defn parse-timestamps [row]
  (-> row
      (update :text/created read-instant-timestamp)
      (update :text/modified read-instant-timestamp)))

;;; Lemmas
(pc/defresolver db-lemmas-resolver [env _]
  {::pc/output [{:db/lemmas [:lemma/id]}]}
  (let [lemmas (jdbc/execute! (:db env) ["select id from lemma"])]
    {:db/lemmas lemmas}))

(pc/defresolver lemma-info-resolver [env {:keys [lemma/id]}]
  {::pc/input #{:lemma/id}
   ::pc/output [:lemma/reading :lemma/writing :lemma/pos]}
  (if (nil? id)
    {:lemma/reading nil, :lemma/writing nil, :lemma/pos nil}
    (-> (jdbc/execute-one! (:db env) ["select reading, writing, pos from lemma where id = ?" id])
        (update :lemma/pos kuromoji/int->pos))))

(pc/defresolver lemma-definitions-resolver [env {lemma-id :lemma/id}]
  {::pc/input #{:lemma/id}
   ::pc/output [{:lemma/definitions [:definition/id :definition/language :definition/content]}]}
  (let [defs (->> (jdbc/execute! (:db env) ["select id, language, content from lemma_definition where lemma_id = ?" lemma-id])
                  (map #(rename-keys % {:lemma_definition/id :definition/id
                                        :lemma_language/id :definition/language
                                        :lemma_language/content :definition/content})))]
    {:lemma/definitions defs}))

(pc/defresolver lemma-knowledge-resolver [env {lemma-id :lemma/id}]
  {::pc/input #{:lemma/id}
   ::pc/output [:lemma/familiarity :definition/id]}
  (let [row (jdbc/execute-one! (:db env) ["select familiarity, preferred_definition lemma_knowledge where lemma_id = ?" lemma-id])]
    {:lemma/familiarity (:lemma_knowledge/familiarity row)
     :definition/id (:lemma_knowledge/preferred_definition row)}))

;;; Texts
(pc/defresolver db-texts-resolver [env _]
  {::pc/output [{:db/texts [:text/id]}]}
  {:db/texts (jdbc/execute! (:db env) ["select id from text"])})

(pc/defresolver text-info-resolver [env {:keys [text/id]}]
  {::pc/input #{:text/id}
   ::pc/output [:text/name :text/created :text/modified]}
  (some-> (jdbc/execute-one! (:db env) ["select name, created, modified from text where id = ?" id])
          parse-timestamps))

(pc/defresolver text-content-resolver [env {:keys [text/id]}]
  {::pc/input #{:text/id}
   ::pc/output [:text/content]}
  (jdbc/execute-one! (:db env) ["select content from text where id = ?" id]))

(pc/defresolver text-sentences-resolver [{:keys [db] :as env} {text-id :text/id}]
  {::pc/input #{:text/id}
   ::pc/output [{:text/sentences [:sentence/id :sentence/text-offset :sentence/length]}]}
  (let [sql (if-let [{:keys [offset limit]} (get-in env [:ast :params])]
              ["select id, text_offset, length from sentence where text_id = ?
                order by text_offset limit ? offset ?" text-id limit offset]
              ["select id, text_offset, length from sentence where text_id = ?" text-id])
        sentences (->> (jdbc/execute! (:db env) sql)
                       (map #(rename-keys % {:sentence/text_offset :sentence/text-offset})))]
    {:text/sentences sentences}))

(pc/defresolver sentence-info-resolver [env {sentence-id :sentence/id}]
  {::pc/input #{:sentence/id}
   ::pc/output [:sentence/length :sentence/text-offset]}
  (some-> (jdbc/execute-one! (:db env) ["select length, text_offset from sentence where id = ?"
                                        sentence-id])
          (rename-keys {:sentence/text_offset :sentence/text-offset})))

(pc/defresolver sentence-words-resolver [env {sentence-id :sentence/id}]
  {::pc/input #{:sentence/id}
   ::pc/output [{:sentence/words [:word/id :word/sentence-offset :word/length :lemma/id]}]}
  {:sentence/words 
   (->> (jdbc/execute! (:db env) ["select id, sentence_offset, length, lemma_id from word where sentence_id = ?"
                                  sentence-id])
        (map #(rename-keys % {:word/sentence_offset :word/sentence-offset
                              :word/lemma_id :lemma/id})))})

(pc/defresolver word-info-resolver [env {word-id :word/id}]
  {::pc/input #{:word/id}
   ::pc/output [:word/sentence-offset :word/length :lemma/id]}
  (some-> (jdbc/execute-one! (:db env) ["select sentence_offset, length, id from word where id = ?"
                                        word-id])
          (rename-keys {:word/sentence_offset :word/sentence-offset})))

;;; Mutations

(pc/defmutation create-text-mutation [env {:keys [text/name text/content] :as params}]
  {::pc/sym 'text/create
   ::pc/params [:text/name :text/content]
   ::pc/output [:text/id]}
  (let [{:keys [text/id]} (db/insert-text! (:db env) params)]
    (db/analyze-text! (:db env) id content)
    {:text/id id}))

(pc/defmutation delete-text-mutation [env {:keys [text/id] :as params}]
  {::pc/sym 'text/delete
   ::pc/params [:text/id]
   ::pc/output []}
  (jdbc/execute-one! (:db env) ["delete from text where id = ?" id]))


;;; Pathom setup
(def pathom-registry
  [db-lemmas-resolver lemma-info-resolver lemma-definitions-resolver lemma-knowledge-resolver
   db-texts-resolver text-info-resolver text-content-resolver text-sentences-resolver
   sentence-info-resolver sentence-words-resolver
   create-text-mutation delete-text-mutation])

(def pathom-parser
  (p/parser
    {::p/env {::p/reader [p/map-reader
                          pc/reader2
                          pc/open-ident-reader]}
     ::p/mutate pc/mutate
     ::p/plugins [(pc/connect-plugin {::pc/register pathom-registry})
                  p/error-handler-plugin
                  p/trace-plugin]}))

(defn eval-eql [eql]
  (pathom-parser {:db db/*db*} eql))

(comment
  (eval-eql [{:db/texts [:text/id :text/name]}])

  (eval-eql [{:db/lemmas [:lemma/id]}])

  (eval-eql [{[:text/id 2] [:text/name {:text/sentences [:sentence/id]}]}])
  (eval-eql [{[:text/id 3] [:text/name {:text/sentences [:sentence/id]}]}])

  (eval-eql '[{[:text/id 3] [:text/name
                             {:text/sentences
                              [:sentence/id
                               :sentence/length
                               :sentence/text-offset
                               {:sentence/words [:word/sentence-offset :word/length :lemma/reading :lemma/writing]}]}]}])


  (eval-eql [{[:sentence/id 13] [:sentence/length :sentence/text-offset
                                 {:sentence/words [:lemma/id :lemma/reading :lemma/writing]}]}])

  (eval-eql '[(text/create {:text/name "eqlllly", :text/content "als d ifjali e jfla iejf a lsidjf alsd"})])

  (eval-eql '[(text/delete {:text/id 3})])


  )
