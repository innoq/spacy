(ns spacy.system
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :refer (go >! <! <!! >!! buffer dropping-buffer sliding-buffer chan take! mult tap)]
   [clojure.edn :as edn]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [modular.bidi :refer (new-router new-web-resources)]
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
      (spit "session.edn" (with-out-str (prn @session)))
      (dissoc component :session))))

(defn new-data []
  (-> (map->Data {})))

(defrecord Events [channel mult-channel]
  component/Lifecycle
  (start [component]
    (let [ch (chan (sliding-buffer 300))]
      (assoc component
             :channel ch
             :mult-channel (mult ch))))
  (stop  [component]
    (dissoc component :channel :mult)))

(defn new-events []
  (-> (map->Events {})))

(defn system []
  (component/system-map
   :data (new-data)
   :events (new-events)
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
