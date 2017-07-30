(ns email-health-hackathon.utils
  (:require [clojure.string :as s])
  (:gen-class))


(def domains-set #{"msnpath.com"})

; given an email map, return a vector of emails
(defn parse-tos-and-froms [{:keys [sender_address recipient_status]}]
  (conj 
    (mapv first (mapv #(s/split % #"##") (s/split recipient_status #";"))) 
    sender_address))

(defn determine-unique-emails [content]
  (let [all-emails (into #{} (flatten (mapv parse-tos-and-froms content)))]
    (filter #(contains? domains-set (last (s/split % #"@"))) all-emails)))