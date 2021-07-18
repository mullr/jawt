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
      (-> s'
          (assoc :ui/selected-word (get-in s' (:ui/selected-word-path s'))))
      (-> s'
          (dissoc :ui/selected-word-path) 
          (dissoc :ui/selected-word)))))

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
        (assoc :ui/selected-word-path word-path)
        (assoc :ui/selected-word (get-in s word-path)))))

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
           {::load-text-info load-text-info
            ::set-text-info set-text-info
            ::load-visible-sentences load-visible-sentences
            ::set-visible-sentences set-visible-sentences
            ::next-page next-page
            ::prev-page prev-page
            ::select-word select-word
            ::post-knowledge post-knowledge})
  (ev/emit [::load-text-info])
  (ev/emit [::load-visible-sentences]))

(defn stop [state parameters]
  (let [this-ns (namespace ::foo)]
    (ev/unsub (fn [id] (= (namespace id) this-ns))))
  (reset! state nil))

(defn familiarity-check-box [curr-familiarity this-familiarity label lemma]
  [:span
   [:input {:id this-familiarity, :type :radio, :name :lemma-familiarity
            :onChange (fn [ev]
                        (let [is-checked (.-checked (.-target ev))]
                          (when is-checked
                            (ev/emit [::post-knowledge lemma this-familiarity]))))
            :checked (= this-familiarity curr-familiarity)}]
   [:label {:for this-familiarity} label]])

(defn selected-word-details-view [selected-word-state]
  (let [{:lemma/keys [reading writing pos] :knowledge/keys [familiarity]} @selected-word-state
        lemma (select-keys @selected-word-state [:lemma/reading :lemma/writing :lemma/pos])]
    (when @selected-word-state
      [:div
       [:div "Reading: " reading]
       [:div "Writing: " writing]
       [:div "POS: " pos]
       [:span "Familiarity: "
        [familiarity-check-box familiarity :new "New" lemma]
        [familiarity-check-box familiarity :learning "Learning" lemma]
        [familiarity-check-box familiarity :known "Known" lemma]]])))

(defn word-view [word word-path sentence-content]
  (let [{:word/keys [sentence-offset length]} word
        word-content (subs sentence-content sentence-offset (+ sentence-offset length))]
    [:span.word {:onClick (fn [_] (ev/emit [::select-word word-path]))
                 :class [(when (:ui/selected word) :selected)
                         (:knowledge/familiarity word)]}
     word-content]))

(defn sentence-view [sentence sentence-path]
  [:<> {:key (:sentence/id sentence)}
   (for [[i w] (map-indexed vector (:sentence/words sentence))
         :let [w (assoc w :key i)
               word-path (-> sentence-path
                             (conj :sentence/words)
                             (conj i))]]
     [word-view w word-path (:sentence/content sentence)])])

(defn view [state]
  (let [selected-word (r/cursor state [:ui/selected-word])]
    (fn []
      [:div
       [:h3 "Reading " (:text/name @state)]
       [:div 
        [:button {:onClick (fn [_] (ev/emit [::prev-page]))} "<- prev "]
        " Page " (:ui/page @state)
        "/" (Math/ceil (/ (:text/sentence-count @state) page-size))
        " "
        [:button {:onClick (fn [_] (ev/emit [::next-page]))} "next ->"]]
       [:div
        [:input {:type :checkbox, :id :mark-new-as-known}]
        [:label {:for :mark-new-as-known} "On next, mark 'new' words as 'known'"]]
       [:pre.reading-content
        (for [[i s] (map-indexed vector (:ui/sentences @state))
              :let [s (assoc s :key i)
                    sentence-path [:ui/sentences i]]]
          [sentence-view s sentence-path state])]
       [selected-word-details-view selected-word state]])))

