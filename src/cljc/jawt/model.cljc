(ns jawt.model
  (:require [clojure.spec.alpha :as s]))

;; (s/def :lemma/id pos-int?)
(s/def :lemma/pos #{:adjectival :adjectival-noun :adjectival-noun-suffix :adjective-i :adjective-i-suffix :adnominal
                    :adverb :adverbial :adverbial-suffix :ascii-art :auxiliary :auxiliary-verb :binding :bound
                    :bracket-close :bracket-open :case :character :chinese-writing :comma :common :conjunction
                    :conjunctive :counter :counter-suffix :country :dialect :emoticon :errors-omissions :filler
                    :firstname :general :hesitation :interjection :katakana :latin-alphabet :name :new-unknown-words
                    :nominal :nominal-suffix :noun :numeral :particle :period :phrase-final :place :prefix :pronoun
                    :proper :suffix :supplementary-symbol :surname :symbol :tari :unknown-words :verb :verbal
                    :verbal-adjectival :verbal-suru :whitespace})
(s/def :lemma/pos-id int?)
(s/def :lemma/reading string?)
(s/def :lemma/writing string?)
;; (s/def :lemma/definitions (s/coll-of ::definition))
(s/def ::lemma
  (s/keys :req [:lemma/pos :lemma/reading :lemma/writing]))


(s/def :knowledge/familiarity #{:new :learning :known})
(s/def :knowledge/modified inst?)
(s/def :knowledge/created inst?)
(s/def ::knowledge
  (s/keys :req [:lemma/pos :lemma/reading :lemma/writing :knowledge/familiarity]
          :opt [:knowledge/created :knowledge/modified]))

(s/def :definition/id pos-int?)
(s/def :definition/content string?)
(s/def :definition/language string?)
(s/def ::definition
  (s/keys :req [:definition/id]
          :opt [:definition/content :definition/lanuguage]))

(s/def :text/id pos-int?)
(s/def :text/name string?)
(s/def :text/content string?)
(s/def :text/sentence-count pos-int?)
(s/def :text/modified inst?)
(s/def :text/created inst?)
(s/def :text/sentences (s/coll-of ::sentence))
(s/def ::text
  (s/keys :req [:text/id]
          :opt [:text/name :text/content :text/modified :text/created
                :text/sentences]))

(s/def :sentence/id pos-int?)
(s/def :sentence/text-offset pos-int?)
(s/def :sentence/length pos-int?)
(s/def :sentence/words (s/coll-of ::word))
(s/def ::sentence
  (s/keys :req [:sentence/id]
          :opt [:sentence/text-offset :sentence/length :sentence/words]))

(s/def :word/id pos-int?)
(s/def :word/length pos-int?)
(s/def :word/sentence-offset pos-int?)
(s/def :word/content string?)
(s/def ::word
  (s/keys :req [:word/id]
          :opt [:word/length :word/sentence-offset :word/content]))
