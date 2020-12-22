(ns spacy.main
  (:require [com.stuartsierra.component :as component]
            [spacy.config :as config]
            [spacy.system :as sys]))

(defn -main []
  (component/start (sys/system (config/config :prod))))
