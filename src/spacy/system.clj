(ns spacy.system
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [modular.bidi :refer (new-router new-web-resources)]
   [modular.aleph :refer (new-webserver)]
   [spacy.crux :as crux]
   [spacy.app :as app]))

(defn web-server []
  (let [port 9215]
    (log/info "Starting server on port: " port)
    (new-webserver :port port)))

(defn new-data []
  (crux/map->Crux {}))

(defrecord Events [channel mult-channel]
  component/Lifecycle
  (start [component]
    (let [ch (async/chan (async/sliding-buffer 300))]
      (assoc component
             :channel ch
             :mult-channel (async/mult ch))))
  (stop  [component]
    (dissoc component :channel :mult)))

(defn new-events []
  (-> (map->Events {})))

(defn system []
  (component/system-map
   :events (new-events)
   :data (component/using
           (new-data)
           [:events])
   :app (component/using
         (app/new-app)
         [:events :data])
   :resources (new-web-resources :resource-prefix "public/")
   :router  (component/using
             (new-router)
             [:app :resources])
   :server  (component/using
             (web-server)
             [:router])))
