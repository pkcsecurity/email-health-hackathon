(ns email-health-hackathon.immediate
  (:require [clojure.string :as s]
            [email-health-hackathon.stats :as stats]
            [clj-time.format :as f]
            [clj-time.core :as t])
  (:gen-class))

(def built-in-formatter (f/formatters :date-time))

(def threshold-mins 10)

(defn proportion-immediate [chains]
  (let [[immediate-responds number-of-chains-greater-than-one]
           (reduce
             (fn [[acc1 acc2] email-chain]
               (if (< 1 (count email-chain))
                 (let [timestamps (mapv :origin_timestamp email-chain)
                       datetime-objects (mapv #(f/parse built-in-formatter %) (sort timestamps))
                       one (butlast datetime-objects)
                       two (rest datetime-objects)
                       pairs (map vector one two)]
                   [(+ acc1 (count (filter (fn [[one two]] (> threshold-mins (t/in-minutes (t/interval one two)))) pairs))) (inc acc2)])
                 [acc1 acc2]))
             [0 0]
             (vals chains))]
    (float (/ immediate-responds number-of-chains-greater-than-one))))