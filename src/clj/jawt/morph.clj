(ns jawt.morph
  (:require
   [jawt.kuromoji :as km]
   [clojure.string :as str])
  (:import
   (java.text BreakIterator)
   (java.util Locale)))

(defn sentence-break-seq [^String text ^Locale locale]
  (let [bi (BreakIterator/getSentenceInstance locale)]
    (.setText bi text)
    ((fn step []
       (let [offset (.next bi)]
         (when (not= offset BreakIterator/DONE)
           (cons offset (lazy-seq (step)))))))))

(defn text->sentence-offsets
  "Identify the sentences in a ja string. Report each sentence as
  :sentence/text-offset and :sentence/length in the source text."
  [^String text]
  (->> (sentence-break-seq text Locale/JAPAN)
       (cons 0)
       (partition 2 1)
       (map (fn [[start-offset end-offset]]
              {:sentence/text-offset start-offset
               :sentence/length (- end-offset start-offset)}))))
