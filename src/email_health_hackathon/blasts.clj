(ns email-health-hackathon.blasts
  (:require [clojure.core.matrix :refer [logistic]]
            [clojure.string :as s])
  (:gen-class))

(defn count-sent-to [{:keys [recipient_status] :as email}]
  (count (s/split recipient_status #";")))

(defn send-health [num-recipients]
  (logistic (- num-recipients 10)))

(defn count-blast-emails [content]
  (/ (reduce 
    (fn [counter email]
      (let [num-recipients (count-sent-to email)]
        #_(if (< 10 num-recipients) (inc counter) counter)
        (+ counter (send-health num-recipients))))
    0.0
    content) (count content)))