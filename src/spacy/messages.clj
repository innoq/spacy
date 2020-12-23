(ns spacy.messages
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]))


(defn messages [language]
  (let [msgs (-> (slurp (io/resource "messages.edn"))
                 edn/read-string)]
    (get msgs (keyword language) (:en msgs))))

(defn transformer [messages]
  (fn [{:keys [attrs]}]
    (let [key (:key attrs)]
      (get messages (keyword key) key))))
