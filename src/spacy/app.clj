(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [cheshire.core :as json]
   [bidi.bidi :as bidi]
   [yada.yada :as yada]
   [selmer.parser :as selmer]
   [spacy.domain :as domain]
   [spacy.data :as data]
   [spacy.handler-util :as handler-util]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        [:event-slug "/"]  {""   ::event
                            "sse" ::sse
                            "submit-session" {:post {"" ::submit-session}}
                            "schedule-session" {:post {"" ::schedule-session}}}}])

(defn- drop-namespace-from-keywords [event]
  (letfn [(no-ns [kw] (keyword (name kw)))
          (walk [x]
            (cond
              (map-entry? x) (update x 0 no-ns)
              (keyword? x) (no-ns x)
              :else x))]
    (walk/postwalk walk event)))

(defn index [system]
  (handler-util/get-resource
   (fn [ctx]
     (selmer/render-file
      "templates/index.html"
      {:links [{:href (bidi/path-for routes ::event :event-slug "dezember-2020-strategie-event")
                :text "Strategie Event Open Space 2020"}]}))))

(defn event-view-model [event]
  (-> event
      (assoc :session-name "Strategie Event Open Space 2020")
      (assoc :next-up (first (:spacy.domain/waiting-queue event)))
      (assoc :available-slots (domain/available-slots event))))

(defn show-event [{:keys [data]}]
  (handler-util/get-resource
   (fn [ctx]
     (let [slug (get-in ctx [:parameters :path :event-slug])
           event (-> (data/fetch data slug)
                     event-view-model
                     drop-namespace-from-keywords)]
       (selmer/render-file
        "templates/event.html"
        (->
         event
         (assoc :current-user "joy") ;; TODO - replace with user from header
         (assoc :uris {::sse
                       (bidi/path-for routes ::sse :event-slug slug)
                       ::submit-session
                       (bidi/path-for routes ::submit-session :event-slug slug)
                       ::schedule-session
                       (bidi/path-for routes ::schedule-session :event-slug slug)})))))))

(defn event-path [slug]
  (bidi/path-for routes ::event :event-slug slug))

(defn submit-session [{:keys [data]}]
  (handler-util/command
   :data data
   :parameters {:form {:title String :description String}}
   :command domain/suggest-session
   :redirect-to event-path))

(defn schedule-session [{:keys [data]}]
  (handler-util/command
   :data data
   :parameters {:form {:room String :time String :id java.util.UUID}}
   :command domain/schedule-session
   :redirect-to event-path))

(defn sse-for-event [{{:keys [mult-channel]} :fact-channel}]
  (handler-util/sse-stream mult-channel (map json/generate-string)))

(def handler-map
  "Map route identifies to handler creator functions.
  Note: each creator function which will take the initialized system as an argument
  so that the routes can access the global application state."
  {::index (fn [system] (index system))
   ::event (fn [system] (show-event system))
   ::submit-session (fn [system] (submit-session system))
   ::schedule-session (fn [system] (schedule-session system))
   ::sse   (fn [system] (sse-for-event system))})

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    (handler-util/route-generator component routes handler-map)))

(defn new-app []
  (-> (map->App {})))
