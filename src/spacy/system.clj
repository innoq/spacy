(ns spacy.system
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [modular.bidi :refer (new-router new-web-resources)]
   [modular.aleph :refer (new-webserver)]
   [spacy.config :as config]
   [spacy.crux :as crux]
   [spacy.app :as app]))

(defn webserver [{:keys [port]}]
  (log/infof "Creating server listening on http://localhost:%s" port)
  (new-webserver :port port))

(defn new-data []
  (crux/map->Crux {}))

(defrecord FactChannel [channel mult-channel]
  component/Lifecycle
  (start [component]
    (let [ch (async/chan (async/sliding-buffer 300))]
      (assoc component
             :channel ch
             :mult-channel (async/mult ch))))
  (stop  [component]
    (dissoc component :channel :mult)))

(defn new-fact-channel []
  (-> (map->FactChannel {})))

(defn system [config]
  (component/system-map
   :fact-channel (new-fact-channel)
   :data (component/using
           (new-data)
           [:fact-channel])
   :app (component/using
         (app/new-app)
         [:fact-channel :data])
   :resources (new-web-resources :resource-prefix "public/")
   :router  (component/using
             (new-router)
             [:app :resources])
   :server  (component/using
             (webserver (config/webserver config))
             [:router])))
