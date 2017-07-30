(ns email-health-hackathon.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [email-health-hackathon.utils :as utils]
            [email-health-hackathon.chains :as chains]
            [email-health-hackathon.blasts :as blasts]
            [email-health-hackathon.sends :as sends]
            [email-health-hackathon.immediate :as immediate]
            [email-health-hackathon.distraction :as distraction]
            [clj-time.format :as f]
            [clj-time.core :as t])
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


(def built-in-formatter (f/formatters :date-time))

(defn calc-days [content]
  (let [sorted (sort-by :origin_timestamp content)
        first-timestamp (f/parse built-in-formatter (:origin_timestamp (first sorted)))
        last-timestamp (f/parse built-in-formatter (:origin_timestamp (last sorted)))]
    (t/in-days (t/interval first-timestamp last-timestamp))))

(defn ingest-csv [filename]
  (println (str "---" filename "---"))
  (with-open [reader (io/reader (str "logs/" filename))]
    (let [content (doall (csv/read-csv reader))
          first-row (mapv keyword (first content))
          data (rest content)
          structured-content (mapv #(zipmap first-row %) data)
          starting-emails (find-starting-emails structured-content)
          chains (parse-chains starting-emails structured-content)
          unique-emails-count (count (utils/determine-unique-emails structured-content))
          num-days (calc-days structured-content)]
      (println (str "Number of days: " num-days))
      (println (str "Chain Health: " (chains/chain-health chains)))
      (println (str "Blast Health: " (blasts/count-blast-emails structured-content)))
      (println (str "Send Health: " (sends/calculate-average-sends structured-content unique-emails-count num-days)))
      (println (str "Immediacy Health: " (immediate/proportion-immediate chains)))
      (println (str "Distraction Health: " (distraction/distracted-score structured-content num-days))))))

(def filenames 
  ["MTSummary_Tenant Message trace report May 15 to May 22_8eba2a87-9a2a-43ce-abbe-a882d512035c.csv"
   "MTSummary_Tenant Message trace report May 22 to May 29_4dea17cf-6e9b-4d8b-90fe-df61f07300fc.csv"
   "MTSummary_Tenant Message trace report May 29 to June 5_b0898d18-6436-48c5-9523-0c18caa75430.csv"
   "MTSummary_Tenant Message trace report June 5 to June 15_9af49a18-461a-4be9-8402-cafb95611498.csv"])

; TODO: we are assuming that all emails with the same original subject (taking out RE's) are part of a chain
; we could improve this by checking timestamps / making sure the recipients are consistent
; doign this would probably re-allocate some of teh longer "chains" (ex. "Question") to multiple shorter chains
; so, our numbers for chain length is artificially high.
; we toss out the highlighters by calculating median.
(defn -main
  [& args]
  (map ingest-csv filenames))
