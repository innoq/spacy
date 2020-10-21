(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :refer (go >! <! <!! >!! buffer dropping-buffer sliding-buffer chan take! mult tap)]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [ring.core.protocols :refer [StreamableResponseBody]]
   [yada.yada :as yada]))

(defn dummy-index [req]
  {:status 200
   :body "Hi!"})

(defn event-resource [{:keys [mult-channel]}]
  (yada/resource
   {:methods
    {:get
     {:produces {:media-type "text/event-stream"}
      :response (fn [ctx]
                  (let [ch (chan 256)]
                    (tap mult-channel ch)
                    ch))}}}))

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    (log/info :component component)
    ["/" {"" (bidi/handler ::index dummy-index)
          "sse" (bidi/handler ::sse (yada/handler (event-resource (:events component))))}]))

(defn new-app []
  (-> (map->App {})))

