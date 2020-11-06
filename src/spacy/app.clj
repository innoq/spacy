(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :refer (go >! <! <!! >!! buffer dropping-buffer sliding-buffer chan take! mult tap)]
   [cheshire.core :as json]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [ring.core.protocols :refer [StreamableResponseBody]]
   [yada.yada :as yada]
   [selmer.parser :as selmer]
   [ring.util.response :as resp]
   [spacy.bidi-util :as bidi-util]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        [:event-id "/"]  {""   ::event
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

(defn index [system]
  (get-resource
   (fn [ctx]
     (selmer/render-file
      "templates/index.html"
      {:links [{:href (bidi/path-for routes ::event :event-id "dezember-2020-strategie-event")
                :text "Strategie Event Open Space 2020"}]}))))

(defn show-event [{:keys [data]}]
  (get-resource
   (fn [ctx]
     (let [session (deref (:session data))
           event-id (get-in ctx [:parameters :path :event-id])]
       (selmer/render-file
        "templates/event.html"
        (->
         session
         (assoc :session-name "Strategie Event Open Space 2020")
         (assoc :current-user "joy") ;; TODO - replace with user from header
         (assoc :next-up (first (:waiting-queue session)))
         (assoc :waiting-queue (rest (:waiting-queue session)))
         (assoc :uris {::sse
                       (bidi/path-for routes ::sse :event-id event-id)
                       ::submit-session
                       (bidi/path-for routes ::submit-session :event-id event-id)})))))))

(defn- random-uuid []
  (java.util.UUID/randomUUID))

;; TODO - probably implemented in Crux DB
(defn update-state [state {:keys [title description] :as session}]
  (let [id (random-uuid)
        current-user "joy" ;; TODO - retrieve from header
        new-session {:sponsor current-user :session (assoc session :id id)}
        new-facts [{::fact :session-suggested
                    ::session {:id id :title title :description description :sponsor current-user}}]]
    (-> state
        (update-in [:waiting-queue] #(concat % [new-session]))
        (update-in [:facts] #(concat % new-facts)))))

;; TODO - probably implemented as a listener for Crux
(defn publish-events! [channel old-state new-state]
  (let [old-count (count (:facts old-state))
        new-facts (drop old-count (:facts new-state))]
    (log/info :facts new-facts)
    (go (doseq [fact new-facts]
          (>! channel fact)))))

(defn submit-session [{:keys [data events]}]
  (yada/handler
   (yada/resource
    {:methods
     {:post
      {:consumes "application/x-www-form-urlencoded"
       :parameters {:form {:title String :description String}}
       :response (fn [ctx]
                   (let [event-id (get-in ctx [:parameters :path :event-id])
                         params  (get-in ctx [:parameters :form])
                         session (:session data)
                         channel (:channel events)]
                     (let [old-state @session
                           new-state (swap! session update-state params)]
                       (publish-events! channel old-state new-state)
                       (yada-redirect ctx (bidi/path-for routes ::event :event-id event-id)))))}}})))

(defn sse-for-event [{{:keys [mult-channel]} :events :as system}]
  (yada/handler
   (yada/resource
    {:methods
     {:get
      {:produces {:media-type "text/event-stream"}
       :response (fn [ctx]
                   (let [response (:response ctx)]
                     (let [ch (chan 256 (map json/generate-string))]
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
