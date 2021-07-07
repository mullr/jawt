(ns jawt.kuromoji
  (:require
   [clojure.string :as str]
   [jawt.kuromoji-tables
    :refer [str->pos str->inflection str->inflection-type]])
  (:import
   (com.atilika.kuromoji TokenBase)
   (com.atilika.kuromoji.unidic Token Tokenizer)
   (com.atilika.kuromoji.dict DictionaryField)))

(defn split-dashes [s] (str/split s #"-"))

;; Wrapper for all methods in [1] and [2]
;; [1] UniDic-specific `Token` methods:
;;     https://github.com/atilika/kuromoji/blob/master/kuromoji-unidic/src/main/java/com/atilika/kuromoji/unidic/Token.java
;; [2] Parent `TokenBase` methods:
;;     https://github.com/atilika/kuromoji/blob/master/kuromoji-core/src/main/java/com/atilika/kuromoji/TokenBase.java
(defn token-to-map [token]
  {:lemma (.getLemma token)
   :lemma-reading (.getLemmaReadingForm token)
   :lemma-pronunciation (.getPronunciationBaseForm token)
   :literal-pronunciation (.getPronunciation token)
   :part-of-speech (->> [(.getPartOfSpeechLevel1 token)
                         (.getPartOfSpeechLevel2 token)
                         (.getPartOfSpeechLevel3 token)
                         (.getPartOfSpeechLevel4 token)]
                        (remove #(= % "*"))
                        (mapv #(or (str->pos %)
                                   :unknown-pos)))
   :conjugation (mapv #(or (str->inflection %)
                           :unknown-inflection)
                      (split-dashes (.getConjugationForm token)))
   :conjugation-type (mapv #(or (str->inflection-type %)
                                :unknown-inflection-type)
                           (filter #(not (= % "*"))
                                   (split-dashes (.getConjugationType token))))
   :written-form (.getWrittenForm token)
   :written-base-form (.getWrittenBaseForm token)
   :language-type (.getLanguageType token)
   :initial-sound-alternation-type (.getInitialSoundAlterationType token)
   :initial-sound-alternation-form (.getInitialSoundAlterationForm token)
   :final-sound-alternation-type (.getFinalSoundAlterationType token)
   :final-sound-alternation-form (.getFinalSoundAlterationForm token)
   ;; from TokenBase.java
   :literal (.getSurface token)
   :known? (.isKnown token)
   :user? (.isUser token)
   :position (.getPosition token)
   :all-features (str/split (.getAllFeatures token) #",")})

(def tokenizer
  (delay (Tokenizer.)))

(defn tokenize [s]
  (map token-to-map (.tokenize @tokenizer s)))

;; Seriously, you made the *id* private of all things?
(def token-word-id
  (let [word-id-field (.getDeclaredField TokenBase "wordId")]
    (.setAccessible word-id-field true)
    (fn [^TokenBase tok]
      (.get word-id-field tok))))

(defn token-to-minimal-map [token]
  #:lemma{:lemma/writing (.getLemma token)
          :lemma/reading (.getLemmaReadingForm token)
          :lemma/pos (->> [(.getPartOfSpeechLevel1 token)
                           (.getPartOfSpeechLevel2 token)
                           (.getPartOfSpeechLevel3 token)
                           (.getPartOfSpeechLevel4 token)]
                          (remove #(= % "*"))
                          (map #(or (str->pos %)
                                    :unknown-pos))
                          first)
          :word/sentence-offset (.getPosition token)
          :word/length (count (.getSurface token))})

(defn tokenize-mini [s]
  (map token-to-minimal-map (.tokenize @tokenizer s)))

(comment
  (use 'clojure.pprint)

  (def s "お寿司が食べたい、たべる、食べます。")
  (pprint
   (tokenize-mini s))

  DictionaryField/LEFT_ID
  DictionaryField/RIGHT_ID
  (count "asdf")

  )
