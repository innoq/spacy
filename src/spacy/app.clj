(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]))

(defn dummy-index [req]
  {:status 200
   :body "Hi!"})

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    ["/" {"" (bidi/handler ::index dummy-index)}]))

(defn new-app []
  (-> (map->App {})))

