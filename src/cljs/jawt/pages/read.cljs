(ns jawt.pages.read
  (:require
   [ajax.core :refer [GET POST]]
   [jawt.event-bus :as ev]
   [reagent.core :as r]))

(def page-size 4)

(defn load-text-info [s _]
  (let [{:text/keys [id]} s]
    (GET (str "/texts/" id)
        {:handler #(ev/emit [::set-text-info %])}))
  s)

(defn set-text-info [s [_ info]]
  (merge s info))

(defn load-visible-sentences [s [_ keep-selection]]
  (let [{:text/keys [id] :ui/keys [page]} s]
    (GET (str "/texts/" id "/sentences")
        {:params {:offset (* page-size (dec page))
                  :count page-size}
         :handler #(ev/emit [::set-visible-sentences
                             (:text/sentences %)
                             (:text/sentence-count %)
                             keep-selection])}))
  s)

(defn set-visible-sentences [s [_ sentences sentence-count keep-selection]]
  (let [s' (-> s
               (assoc :ui/sentences sentences)
               (assoc :text/sentence-count sentence-count))]
    (if keep-selection
     (let [word-path (:ui/selected-word-path s')]
        (assoc-in s' (conj word-path :ui/selected) true))
      (dissoc s' :ui/selected-word-path))))

(defn next-page [s _]
  (ev/emit [::load-visible-sentences false])
  (update s :ui/page inc))

(defn prev-page [s _]
  (ev/emit [::load-visible-sentences false])
  (update s :ui/page #(max 1 (dec %))))

(defn select-word [s [_ word-path]]
  (let [s' (if-let [last-selection-path (:ui/selected-word-path s)]
                 (update-in s last-selection-path dissoc :ui/selected)
                 s)]
    (-> s'
        (assoc-in (conj word-path :ui/selected) true)
        (assoc :ui/selected-word-path word-path))))

(defn deselect-word [s _]
  (if-let [word-path (:ui/selected-word-path s)]
    (-> s
        (dissoc :ui/selected-word-path)
        (update-in word-path dissoc :ui/selected))
    s))

(defn post-knowledge [s [ _ lemma new-familiarity]]
  (POST "/knowledge"
      {:params (assoc lemma :knowledge/familiarity new-familiarity)
       ;; TODO handle error
       :handler (fn [_] (ev/emit [::load-visible-sentences true]))})
  s)

(defn start [state parameters]
  (reset! state
          {:text/id (get-in parameters [:path :id])
           :ui/page 1})
  (ev/subs state
           {::load-text-info #'load-text-info
            ::set-text-info #'set-text-info
            ::load-visible-sentences #'load-visible-sentences
            ::set-visible-sentences #'set-visible-sentences
            ::next-page #'next-page
            ::prev-page #'prev-page
            ::select-word #'select-word
            ::deselect-word #'deselect-word
            ::post-knowledge #'post-knowledge})
  (ev/emit [::load-text-info]
           [::load-visible-sentences]))

(defn stop [state _]
  (let [this-ns (namespace ::foo)]
    (ev/unsub (fn [id] (= (namespace id) this-ns))))
  (reset! state nil))

(defn familiarity-radio-button [curr-familiarity this-familiarity label lemma]
  [:a.familiarity-button
   {:role :button
    :id this-familiarity,
    :onClick (fn [e]
               (.stopPropagation e)
               (ev/emit [::post-knowledge lemma this-familiarity]))
    :class [this-familiarity
            (when-not (= this-familiarity curr-familiarity) :outline)]}
   label])

(defn selected-word-details-view [word]
  (let [{:lemma/keys [reading writing pos] :knowledge/keys [familiarity]} word
        lemma (select-keys word [:lemma/reading :lemma/writing :lemma/pos])]
    [:small.knowledge-popup
     ;; So the popup can be clicked on without dismissing it
     {:onClick (fn [e] (.stopPropagation e))}
     [:div "Reading: " reading]
     [:div "Writing: " writing]
     [:div "POS: " pos]
     [:div
      [familiarity-radio-button familiarity :new "New" lemma]
      [familiarity-radio-button familiarity :learning "Learning" lemma]
      [familiarity-radio-button familiarity :known "Known" lemma]]]))

(defn word-view [word word-path]
  (let [{:word/keys [sentence-offset length content]} word]
    [:span.word {:onClick (fn [_] (ev/emit [::select-word word-path]))
                 :class [(when (:ui/selected word) :selected)
                         (:knowledge/familiarity word)]}
     content
     (when (:ui/selected word)
       [selected-word-details-view word])]))

(defn sentence-view [sentence sentence-path]
  (let [sentence-content (:sentence/content sentence)]
    [:<> {:key (:sentence/id sentence)}
     (for [[i word] (map-indexed vector (:sentence/words sentence))
           :let [{:word/keys [sentence-offset length]} word
                 word (assoc word
                             :key i
                             :word/content (subs sentence-content sentence-offset (+ sentence-offset length)))
                 word-path (-> sentence-path
                               (conj :sentence/words)
                               (conj i))]]
       [word-view word word-path])]))

(defn view [state]
  (fn []
    [:div
     ;; this would be better on the body element, or something like that
     {:onClick (fn [e]
                 (when-not (= "word" (-> e .-target .-classList (aget 0)))
                   (println "!!!!!!!!!! DESELECT")
                   (ev/emit [::deselect-word])))}
     [:h3 "Reading: " (:text/name @state)]
     [:div
      [:div.grid
       [:button {:onClick (fn [_] (ev/emit [::prev-page]))} "Last Page"]
       [:button {:onClick (fn [_] (ev/emit [::next-page]))} "Next Page"]]]
     [:progress {:value (:ui/page @state)
                 :max (Math/ceil (/ (:text/sentence-count @state) page-size))}]
     [:div
      [:label {:for :mark-new-as-known}
       [:input {:type :checkbox, :id :mark-new-as-known, :role :switch}]
       "On next, mark 'new' words as 'known'"]]
     [:article.reading-content
      (for [[i s] (map-indexed vector (:ui/sentences @state))
            :let [s (assoc s :key i)
                  sentence-path [:ui/sentences i]]]
        [sentence-view s sentence-path state])]]))
