(ns email-health-hackathon.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [email-health-hackathon.stats :as stats])
  (:gen-class))

(defn starting-email? [{subject :message_subject}]
  (not (or 
    (s/starts-with? subject "re:")
    (s/starts-with? subject "Re:")
    (s/starts-with? subject "rE:")
    (s/starts-with? subject "RE:")
    (s/starts-with? subject "\"re:")
    (s/starts-with? subject "\"Re:")
    (s/starts-with? subject "\"rE:")
    (s/starts-with? subject "\"RE:"))))

(defn find-starting-emails [content]
  (persistent!
    (reduce
      (fn [acc {:keys [message_subject] :as m}]
        (assoc! acc message_subject [m]))
      (transient {})
      (filter starting-email? content))))

(defn find-original-subject [{subject :message_subject}] ;blows away the "RE:"s in the beginning
  (s/replace subject #"^\"?([Rr][Ee]: )*" ""))

(defn parse-chains [starting-emails content]
  (reduce 
    (fn [acc potential-chain-email]
      (if (not (starting-email? potential-chain-email))
        (let [original-subject (find-original-subject potential-chain-email)]
          (if (contains? acc original-subject)
            (update acc original-subject #(conj % potential-chain-email))
            acc))
        acc))
    starting-emails
    content))

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

; TODO: we are assuming that all emails with the same original subject (taking out RE's) are part of a chain
; we could improve this by checking timestamps / making sure the recipients are consistent
; doign this would probably re-allocate some of teh longer "chains" (ex. "Question") to multiple shorter chains
; so, our numbers for chain length is artificially high.
; we toss out the highlighters by calculating median.
(defn -main
  [& args]
  (let [filename "MTSummary_Tenant Message trace report June 5 to June 15_9af49a18-461a-4be9-8402-cafb95611498.csv"]
    (with-open [reader (io/reader (str "logs/" filename))]
      (let [content (doall (csv/read-csv reader))
            first-row (mapv keyword (first content))
            data (rest content)
            structured-content (mapv #(zipmap first-row %) data)
            starting-emails (find-starting-emails structured-content)
            chains (parse-chains starting-emails structured-content)]
        (println (str "Chain Health: " (chain-health chains)))))))
