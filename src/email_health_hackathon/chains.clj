(ns email-health-hackathon.chains
  (:require [clojure.string :as s]
            [email-health-hackathon.stats :as stats])
  (:gen-class))


(def penultimate
  (comp second reverse))

; we removed all chains of length 1, and high outliers (likely not one chain) 
(defn chain-health [chains]
  (let [chain-counts (mapv #(count (second %)) (seq chains))
        sorted-chains-without-ones (sort (filterv #(not= 1 %) chain-counts))
        sorted-chains-without-ones-and-outlier (butlast sorted-chains-without-ones)
        largest-chain (penultimate (sort-by #(count (second %)) (seq chains)))
        histogram (reduce
                    (fn [acc chain]
                      (let [length (count (second chain))]
                        (if (contains? acc length)
                          (update acc length inc)
                          (assoc acc length 1))))
                    {}
                    (seq chains))
        sorted-histogram (sort-by second (seq histogram))]
    (float (stats/mean sorted-chains-without-ones-and-outlier))))