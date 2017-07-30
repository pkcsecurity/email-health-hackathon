(ns email-health-hackathon.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [email-health-hackathon.chains :as chains]
            [email-health-hackathon.blasts :as blasts]
            [email-health-hackathon.sends :as sends]
            [email-health-hackathon.immediate :as immediate])
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

; given an email map, return a vector of emails
(defn parse-tos-and-froms [{:keys [sender_address recipient_status]}]
  (conj 
    (mapv first (mapv #(s/split % #"##") (s/split recipient_status #";"))) 
    sender_address))

(defn determine-unique-emails [content domains-set]
  (let [all-emails (into #{} (flatten (mapv parse-tos-and-froms content)))]
    (filter #(contains? domains-set (last (s/split % #"@"))) all-emails)))

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
            chains (parse-chains starting-emails structured-content)
            unique-emails-count (count (determine-unique-emails structured-content #{"msnpath.com"}))]
        (println (str "Chain Health: " (chains/chain-health chains)))
        (println (str "Blast Health: " (blasts/count-blast-emails structured-content)))
        (println (str "Send Health: " (sends/calculate-average-sends structured-content unique-emails-count)))
        (println (str "Immediacy Health: " (immediate/proportion-immediate chains)))))))
