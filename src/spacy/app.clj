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
   [spacy.bidi-util :as bidi-util]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        [:event-slug "/"]  {""   ::event
                            "sse" ::sse
                            "submit-session" {:post {"" ::submit-session}}
                            "schedule-session" {:post {"" ::schedule-session}}}}])

(defn get-resource
  "Wrapper for yada resource"
  [response-fn]
  (yada/handler
   (yada/resource
    {:methods
     {:get
      {:produces {:media-type "text/html"}
       :response response-fn}}})))

(defn yada-redirect [ctx uri]
  (let [response (:response ctx)]
    (-> response
        (assoc :status 302)
        (assoc-in [:headers "Location"] uri))))

(defn reject-request [ctx]
  (let [response (:response ctx)]
    (-> response
        (assoc :status 400)
        (assoc-in [:headers "content-type"] "text/plain")
        (assoc :body "That request was invalid!"))))

(defn- drop-namespace-from-keywords [event]
  (letfn [(no-ns [kw] (keyword (name kw)))
          (walk [x]
            (cond
              (map-entry? x) (update x 0 no-ns)
              (keyword? x) (no-ns x)
              :else x))]
    (walk/postwalk walk event)))

(defn index [system]
  (get-resource
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
  (get-resource
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


(defn submit-session [{:keys [data]}]
  (yada/handler
   (yada/resource
    {:methods
     {:post
      {:consumes "application/x-www-form-urlencoded"
       :parameters {:form {:title String :description String}}
       :response (fn [ctx]
                   (let [current-user "joy"
                         slug (get-in ctx [:parameters :path :event-slug])
                         params  (get-in ctx [:parameters :form])
                         state (data/fetch data slug)
                         outcome (domain/suggest-session state current-user params)]
                     (data/persist! data outcome)
                     (yada-redirect ctx (bidi/path-for routes ::event :event-slug slug))))}}})))

(defn schedule-session [{:keys [data]}]
  (yada/handler
   (yada/resource
    {:methods
     {:post
      {:consumes "application/x-www-form-urlencoded"
       :parameters {:form {:room String :time String :id java.util.UUID}}
       :response (fn [ctx]
                   (let [slug (get-in ctx [:parameters :path :event-slug])
                         params (get-in ctx [:parameters :form])
                         state (data/fetch data slug)
                         outcome (domain/schedule-session state params)]
                     (if (::domain/error outcome)
                       (reject-request ctx)
                       (do
                         (data/persist! data outcome)
                         (yada-redirect ctx (bidi/path-for routes ::event :event-slug slug))))))}}})))

(defn sse-for-event [{{:keys [mult-channel]} :fact-channel}]
  (yada/handler
   (yada/resource
    {:methods
     {:get
      {:produces {:media-type "text/event-stream"}
       :response (fn [{:keys [response]}]
                   (let [ch (async/chan 256 (map json/generate-string))]
                     (async/tap mult-channel ch)
                     (-> response
                         (assoc-in [:headers "X-Accel-Buffering"] "no") ;; Turn off buffering in NGINX proxy for SSE
                         (assoc :body ch))))}}})))

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
    (bidi-util/route-generator component routes handler-map)))

(defn new-app []
  (-> (map->App {})))
