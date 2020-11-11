(ns spacy.main
  (:require [com.stuartsierra.component :as component]
            [spacy.system :as sys]))

(defn -main []
  (component/start (sys/system)))
