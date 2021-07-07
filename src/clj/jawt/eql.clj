(ns jawt.eql
  (:require
   [clojure.instant :refer [read-instant-timestamp]]
   [clojure.set :refer [rename-keys]]
   [com.wsscode.pathom3.connect.built-in.resolvers :as p.resolvers]
   [com.wsscode.pathom3.connect.operation :as pco]
   [com.wsscode.pathom3.connect.indexes :as pci]
   [com.wsscode.pathom3.interface.eql :as p.eql]
   [hikari-cp.core :as hikari-cp]
   [jawt.kuromoji :as kuromoji]
   [jawt.kuromoji-tables :as kuromoji-tables]
   [jawt.db :as db]
   [jawt.morph :as morph]
   [next.jdbc :as jdbc]))

(defn parse-timestamps [row]
  (-> row
      (update :text/created read-instant-timestamp)
      (update :text/modified read-instant-timestamp)))

;;; Texts
(pco/defresolver texts [env _]
  {::pco/output [{:texts [:text/id]}]}
  {:texts (jdbc/execute! (:db env) ["select id from text"])})

(pco/defresolver text-id->details [env {:text/keys [id]}]
  {::pco/output [:text/name :text/created :text/modified]}
  (some-> (jdbc/execute-one! (:db env) ["select name, created, modified from text where id = ?" id])
          parse-timestamps))

(pco/defresolver text-id->content [env {:text/keys [id]}]
  {::pco/output [:text/content]}
  (jdbc/execute-one! (:db env) ["select content from text where id = ?" id]))

(pco/defresolver text-content->sentences [env {:text/keys [content]}]
  {::pco/output [{:text/sentences [:sentence/text-offset :sentence/length :sentence/content]}]}
  (let [sentences (->> (morph/text->sentence-offsets content)
                          (map (fn [{:sentence/keys [text-offset length] :as s}]
                                 (assoc s :sentence/content
                                        (subs content text-offset (+ text-offset length))))))
        sentences (if-some [offset (get (pco/params env) :offset)]
                    (drop offset sentences)
                    sentences)
        sentences (if-some [limit (get (pco/params env) :limit)]
                    (take limit sentences)
                    sentences)]
    {:text/sentences sentences}))

(pco/defresolver sentence-content->words [env {:sentence/keys [content]}]
  {::pco/output [{:sentence/words [:word/length :word/sentence-offset :word/content
                                   :lemma/pos :lemma/reading :lemma/writing]}]}
  {:sentence/words (kuromoji/tokenize-mini content)})

;; TODO definitions

(def lemma-int->pos (p.resolvers/single-attr-resolver
                     :lemma/pos-int :lemma/pos
                     kuromoji-tables/int->pos))

(def lemma-pos->int (p.resolvers/single-attr-resolver
                     :lemma/pos :lemma/pos-int 
                     kuromoji-tables/pos->int))

(pco/defresolver lemma->knowledge [env {:lemma/keys [pos-int reading writing]}]
  {::pco/output [{:sentence/words [:knowledge/familiarity :knowledge/created :knowledge/modified]}]}
  (jdbc/execute-one! (:db env) ["select familiarity, created, modified from knowledge
                                 where lemma_pos = ? and lemma_reading = ? and lemma_writing = ?",
                                pos-int reading writing]))

;;; Mutations

(pco/defmutation create-text [env {:text/keys [name content] :as params}]
  {::pco/output [:text/id]}
  (db/insert-text! (:db env) params))

(pco/defmutation delete-text [env {:text/keys [id] :as params}]
  {::pco/output [:text/id]}
  (db/delete-text! (:db env) params))

(pco/defmutation record-knowledge [env {:lemma/keys [pos-int reading writing]
                                        :knowledge/keys [familiarity]
                                        :as params}]
  (db/upsert-knowlege! params))


;;; eql interface

(def base-env
  (pci/register [texts text-id->details text-id->content text-content->sentences
                 sentence-content->words
                 lemma-int->pos lemma-pos->int lemma->knowledge
                 create-text delete-text record-knowledge]))

(defn eval-eql [eql]
  (p.eql/process (assoc base-env :db db/*db*)
                 eql))

(comment
  (def ryoma-content (slurp "ryoma1_1.txt"))

  (eval-eql [`(create-text {:text/name "ryoma"
                            :text/content ~ryoma-content})])

  (eval-eql [`(delete-text {:text/id 3})])

  (eval-eql [{:texts [:text/id :text/name]}])

  (eval-eql [{:texts [:text/name]}])

  (eval-eql '[{:texts [{(:text/sentences {:limit 4}) [:sentence/length]}]}])

  (eval-eql '[{:texts [{(:text/sentences {:offset 4 :limit 2}) [:sentence/words]}]}])

  (eval-eql '[{[:text/id 1]
               [:text/name
                {(:text/sentences {:limit 2})
                 [:sentence/id
                  :sentence/length
                  :sentence/text-offset
                  {:sentence/words [:word/sentence-offset :word/length :lemma/reading :lemma/writing]}]}]}])

  )
