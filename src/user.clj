(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [spacy.config :as config]
            [spacy.system :as sys]))

(def system nil)

(defn init []
  (alter-var-root #'system
                  (fn [_] (sys/system (config/config :dev)))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))
