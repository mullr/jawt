(ns jawt.pages.read
  (:require
   [ajax.core :refer [GET POST]]
   [reagent.core :as r]))

(def page-size 4)

(defn load-text-info! [state]
  (let [{:text/keys [id]} @state]
    (GET (str "/texts/" id)
        {:handler #(swap! state merge %)})))

(defn set-visible-sentences! [state sentences sentence-count keep-selection]
  (swap! state (fn [state]
                 (let [state' (-> state
                                  (assoc :ui/sentences sentences)
                                  (assoc :text/sentence-count sentence-count))]
                   (if keep-selection
                     (-> state'
                         (assoc :ui/selected-word (get-in state' (:ui/selected-word-path state'))))
                     (-> state'
                         (dissoc :ui/selected-word-path) 
                         (dissoc :ui/selected-word)))))))

(defn load-visible-sentences! [state keep-selection]
  (let [{:text/keys [id] :ui/keys [page]} @state]
    (GET (str "/texts/" id "/sentences")
        {:params {:offset (* page-size (dec page)) 
                  :count page-size}
         :handler #(set-visible-sentences! state
                                           (:text/sentences %)
                                           (:text/sentence-count %)
                                           keep-selection)})))

(defn next-page! [state]
  (swap! state update :ui/page inc)
  (load-visible-sentences! state false))

(defn prev-page! [state]
  (swap! state update :ui/page #(max 1 (dec %)))
  (load-visible-sentences! state false))

(defn select-word! [state word-path]
  (swap! state
         (fn [state]
           (let [state' (if-let [last-selection-path (:ui/selected-word-path state)]
                          (update-in state last-selection-path dissoc :ui/selected)
                          state)]
             (-> state'
                 (assoc-in (conj word-path :ui/selected) true)
                 (assoc :ui/selected-word-path word-path)
                 (assoc :ui/selected-word (get-in state word-path)))))))

(defn post-knowledge! [state lemma new-familiarity]
  (POST "/knowledge"
      {:params (assoc lemma :knowledge/familiarity new-familiarity)
       ;; TODO handle error
       :handler (fn [_] (load-visible-sentences! state true))}))

(defn start [state parameters]
  (reset! state {:text/id (get-in parameters [:path :id])
                 :ui/page 1})
  (load-text-info! state)
  (load-visible-sentences! state false))

(defn stop [state parameters]
  (reset! state nil))

(defn familiarity-check-box [curr-familiarity this-familiarity label lemma state]
  [:span
   [:input {:id this-familiarity, :type :radio, :name :lemma-familiarity
            :onChange (fn [ev]
                        (let [is-checked (.-checked (.-target ev))]
                          (when is-checked
                            (post-knowledge! state lemma this-familiarity))))
            :checked (= this-familiarity curr-familiarity)}]
   [:label {:for this-familiarity} label]])

(defn selected-word-details-view [selected-word-state state]
  (let [{:lemma/keys [reading writing pos] :knowledge/keys [familiarity]} @selected-word-state
        lemma (select-keys @selected-word-state [:lemma/reading :lemma/writing :lemma/pos])]
    (when @selected-word-state
      [:div
       [:div "Reading: " reading]
       [:div "Writing: " writing]
       [:div "POS: " pos]
       [:span "Familiarity: "
        [familiarity-check-box familiarity :new "New" lemma state]
        [familiarity-check-box familiarity :learning "Learning" lemma state]
        [familiarity-check-box familiarity :known "Known" lemma state]]])))

(defn word-view [word word-path sentence-content state]
  (let [{:word/keys [sentence-offset length]} word
        word-content (subs sentence-content sentence-offset (+ sentence-offset length))]
    [:span.word {:onClick (fn [_] (select-word! state word-path))
                 :class [(when (:ui/selected word) :selected)
                         (:knowledge/familiarity word)]}
     word-content]))

(defn sentence-view [sentence sentence-path state]
  [:<> {:key (:sentence/id sentence)}
   (for [[i w] (map-indexed vector (:sentence/words sentence))
         :let [w (assoc w :key i)
               word-path (-> sentence-path
                             (conj :sentence/words)
                             (conj i))]]
     [word-view w word-path (:sentence/content sentence) state])])

(defn view [state]
  (let [selected-word (r/cursor state [:ui/selected-word])]
    (fn []
      [:div
       [:h3 "Reading " (:text/name @state)]
       [:button {:onClick (fn [_] (prev-page! state))} "<- prev "]
       " Page " (:ui/page @state)
       "/" (Math/ceil (/ (:text/sentence-count @state) page-size))
       " "
       [:button {:onClick (fn [_] (next-page! state))} "next ->"]
       [:pre.reading-content
        (for [[i s] (map-indexed vector (:ui/sentences @state))
              :let [s (assoc s :key i)
                    sentence-path [:ui/sentences i]]]
          [sentence-view s sentence-path state])]
       [selected-word-details-view selected-word state]])))

