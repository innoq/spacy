(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :refer (go >! <! <!! >!! buffer dropping-buffer sliding-buffer chan take! mult tap)]
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [ring.core.protocols :refer [StreamableResponseBody]]
   [yada.yada :as yada]
   [selmer.parser :as selmer]
   [ring.util.response :as resp]
   [spacy.domain :as domain]
   [spacy.data :as data]
   [spacy.bidi-util :as bidi-util]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        [:event-slug "/"]  {""   ::event
                            "sse" ::sse
                            "submit-session" {:post {"" ::submit-session}}}}])

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

(defn show-event [{:keys [data]}]
  (get-resource
   (fn [ctx]
     (let [slug (get-in ctx [:parameters :path :event-slug])
           session (-> (data/fetch data slug)
                       drop-namespace-from-keywords)]
       (selmer/render-file
        "templates/event.html"
        (->
         session
         (assoc :session-name "Strategie Event Open Space 2020")
         (assoc :current-user "joy") ;; TODO - replace with user from header
         (assoc :next-up (first (:waiting-queue session)))
         (assoc :waiting-queue (rest (:waiting-queue session)))
         (assoc :uris {::sse
                       (bidi/path-for routes ::sse :event-slug slug)
                       ::submit-session
                       (bidi/path-for routes ::submit-session :event-slug slug)})))))))

(defn submit-session [{:keys [data events]}]
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
                         session (data/fetch data slug)
                         new-state (domain/suggest-session session current-user params)
                         channel (:channel events)]
                     (data/persist! data new-state)
                     (yada-redirect ctx (bidi/path-for routes ::event :event-slug slug))))}}})))

(defmulti ^:private interpret-fact ::domain/fact)

(defmethod interpret-fact :default
  [fact]
  (log/warn ::unknown-fact fact))

(defmethod interpret-fact ::domain/session-suggested
  [fact]
  (let [{::domain/keys [session sponsor]} fact
        {::domain/keys [title description id]} session]
    {::fact :session-suggested,
     ::session {:id id
                :title title
                :description description
                :sponsor sponsor}}))

(defn sse-for-event [{{:keys [mult-channel]} :events :as system}]
  (yada/handler
   (yada/resource
    {:methods
     {:get
      {:produces {:media-type "text/event-stream"}
       :response (fn [ctx]
                   (let [response (:response ctx)]
                     (let [ch (chan 256 (map (comp json/generate-string
                                                   interpret-fact)))]
                       (tap mult-channel ch)
                       (-> response
                           (assoc-in [:headers "X-Accel-Buffering"] "no") ;; Turn off buffering in NGINX proxy for SSE
                           (assoc :body ch)))))}}})))

(def handler-map
  "Map route identifies to handler creator functions.
  Note: each creator function which will take the initialized system as an argument
  so that the routes can access the global application state."
  {::index (fn [system] (index system))
   ::event (fn [system] (show-event system))
   ::submit-session (fn [system] (submit-session system))
   ::sse   (fn [system] (sse-for-event system))})

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    (bidi-util/route-generator component routes handler-map)))

(defn new-app []
  (-> (map->App {})))
