(ns spacy.system
  (:require
   [clojure.tools.logging :as log]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [modular.bidi :refer (new-router)]
   [modular.aleph :refer (new-webserver)]
   [spacy.app :as app]))

(defn web-server []
  (let [port 9215]
    (log/info "Starting server on port: " port)
    (new-webserver :port port)))

;; WIP solution to not wanting to implement a Database yet
(defrecord Data [session]
  component/Lifecycle
  (start [component]
    (let [session (edn/read-string (slurp "session.edn"))]
      (assoc component :session (atom session))))

  (stop  [component]
    (let [session (:session component)]
      (spit "session.edn" (print-str @session))
      (dissoc component :session))))

(defn new-data []
  (-> (map->Data {})))

(defn system []
  (component/system-map
   :app (app/new-app)
   :data (new-data)
   :router  (component/using
             (new-router)
             [:app])
   :server  (component/using
             (web-server)
             [:router])))

