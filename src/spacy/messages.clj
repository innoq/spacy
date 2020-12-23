(ns spacy.messages
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]
            [org.tobereplaced.http-accept-headers :as accept-headers]))

(defn language [ctx]
  (let [accept-language (get-in ctx [:request :headers "accept-language"])]
    (first (accept-headers/parse-accept-language
            {"en-us" "en"
             "en-gb" "en"
             "*" "en-US"
             "de-de" "de"
             "de-ch" "de"} accept-language))))

(defn messages [language]
  (let [msgs (-> (slurp (io/resource "messages.edn"))
                 edn/read-string)]
    (get msgs (keyword language) (:en msgs))))

(defn replace [replacer message]
  (cond
    (string? message) (replacer message)
    (sequential? message) (map replacer message)
    :else message))

(defn transformer [messages]
  (fn [{{:keys [key] :as attrs} :attrs :as node}]
    (let [params (dissoc attrs :key)
          message (get messages (keyword key) key)
          with-vars (replace (html/replace-vars params) message)]
      (apply html/html with-vars))))
