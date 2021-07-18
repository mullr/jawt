(ns jawt.event-bus
  "A simple global event bus"
  (:refer-clojure :exclude [subs])
  (:require
   [clojure.core.async :as async :refer [chan >! <!]]))

(defonce ch (async/chan))
(defonce subscriptions (atom {}))
(defonce dispatch-loop
  (async/go-loop []
    (when-let [msg (<! ch)]
      (doseq [{:keys [pred handler atom]} (vals @subscriptions)]
        (when (pred msg)
          (swap! atom #(handler % msg))))
      (recur))))

(defn emit [msg]
  (async/go (>! ch msg)))

(defn sub [{:keys [id event pred handler swap atom]}]
  (let [id (or id event)]
    (swap! subscriptions
           assoc id {:pred (or pred #(= (first %) event))
                     :handler handler
                     :atom swap})))

(defn subs [swap subs-map]
  (doseq [[k v] subs-map]
    (sub {:event k
          :handler v
          :swap swap})))

(defn unsub [id-or-pred]
  (if (fn? id-or-pred)
    (swap! subscriptions (fn [subscriptions]
                           (into {}
                                 (remove (fn [[k v]] (id-or-pred k)))
                                 subscriptions)))
    (swap! subscriptions dissoc id-or-pred)))

