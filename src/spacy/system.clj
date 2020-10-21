(ns spacy.system
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [modular.maker :refer (make)]
   [modular.bidi :refer (new-router)]
   [modular.aleph :refer (new-webserver)]))

(defn dummy-index [req]
  {:status 200
   :body "Hi!"})

(defrecord DummyWebsite []
  component/Lifecycle
  (start [component] component)
  (stop [component] component)

  bidi/RouteProvider
  (routes [component]
    ["/" {"" (bidi/handler ::index dummy-index)}]))

(defn new-website []
  (-> (map->DummyWebsite {})))

(defn routes []
  (make new-router))

(defn web-server []
  (let [port 9215]
    (log/info "Starting server on port: " port)
    (new-webserver :port port)))

(defn system []
  (component/system-map
   :website (make new-website)
   :router  (component/using
             (routes)
             [:website])
   :server  (component/using
             (web-server)
             [:router])))

