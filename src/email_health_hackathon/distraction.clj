(ns email-health-hackathon.distraction
  (:require [clojure.string :as s]            
            [clj-time.format :as f]
            [clj-time.core :as t]
            [email-health-hackathon.utils :as utils])
  (:gen-class))

(def built-in-formatter (f/formatters :date-time))

; the maximum amount of time between consecutive emails to still be considered a "chunk" 
(def threshold-mins 15)

(defn set->hashmap [s]
  (reduce (fn [acc k] (assoc acc k [])) {} s))

(defn construct-sender-data-structure [unique-emails content]
  (reduce
    (fn [acc email]
      (let [sender (:sender_address email)]
        (update acc sender #(conj % (:origin_timestamp email)))))
    unique-emails
    content))

(defn chunk-health [datetime-objects]
  (let [one (butlast datetime-objects)
        two (rest datetime-objects)
        pairs (map vector one two)]
    (count (filter (fn [[one two]] (< threshold-mins (t/in-minutes (t/interval one two)))) pairs))))

; average email-writing chunks per person, over a time period (10 days)
; lower is better
(defn distracted-score [content days]
  (let [unique-emails (set->hashmap (utils/determine-unique-emails content))
        senders (construct-sender-data-structure unique-emails content)] 
    (let [chunks
           (reduce
             (fn [acc sender-timestamps]
               (+ (if (< 1 (count sender-timestamps))
                    (let [datetime-objects (mapv #(f/parse built-in-formatter %) (sort sender-timestamps))]
                      (+ acc (chunk-health datetime-objects)))
                    acc)
                  1))
             0
             (vals senders))]
    (float (/ (/ chunks (count (filter not-empty (vals senders)))) days)))))