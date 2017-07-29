(ns email-health-hackathon.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s])
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

(defn parse-chains [content]
  (doseq [email content]
    (when (starting-email? email)
      (println (:message_subject email)))))

(defn -main
  [& args]
  (let [filename "MTSummary_Tenant Message trace report June 5 to June 15_9af49a18-461a-4be9-8402-cafb95611498.csv"]
    (with-open [reader (io/reader (str "logs/" filename))]
      (let [content (doall (csv/read-csv reader))
            first-row (mapv keyword (first content))
            data (rest content)
            structured-content (mapv #(zipmap first-row %) data)]
        (parse-chains structured-content)))))
