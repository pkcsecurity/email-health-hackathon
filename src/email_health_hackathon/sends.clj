(ns email-health-hackathon.sends
  (:require [clojure.string :as s])
  (:gen-class))

(defn calculate-average-sends [content unique-emails-count]
  (float (/ (count content) unique-emails-count)))