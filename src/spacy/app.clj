(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :refer (go >! <! <!! >!! buffer dropping-buffer sliding-buffer chan take! mult tap)]
   [clojure.data.json :as json]
   [com.stuartsierra.component :as component]
   [bidi.bidi :as bidi]
   [ring.core.protocols :refer [StreamableResponseBody]]
   [yada.yada :as yada]
   [hiccup.core :as hiccup]
   [ring.util.response :as resp]
   [spacy.bidi-util :as bidi-util]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        "sse" ::sse
        "submit-session" {:post {"" ::submit-session}}}])

(defn index [system]
  {:status 200
   :body (hiccup/html
          [:ul [:li [:a {:href "/dezember-2020-strategie-event"} "Strategie Event Open Space 2020"]]])})

(defn show-session [{:keys [data]}]
  (let [session (deref (:session data))]
    {:status 200
     :body (hiccup/html
            [:h1 "Strategie Event Open Space 2020"]
            [:waiting-queue
             [:ol (for [el (:waiting-queue session)]
                    [:li [:details [:summary (str (:title (:session el)) " - " (:sponsor el))]
                          (:description (:session el))]])]])}))


(defn submit-session [req]
  (resp/redirect (bidi/path-for routes ::index)))

(defn event-resource [{{:keys [mult-channel]} :events :as system}]
  (yada/handler
   (yada/resource
    {:methods
     {:get
      {:produces {:media-type "text/event-stream"}
       :response (fn [ctx]
                   (let [response (:response ctx)]
                     (let [ch (chan 256 (map json/write-str))]
                       (tap mult-channel ch)
                       (-> response
                           (assoc-in [:headers "X-Accel-Buffering"] "no") ;; Turn off buffering in NGINX proxy for SSE
                           (assoc :body ch)))))}}})))

;; POST /suggest-session
(defn add-to-line [req]
  ;; ZUSTAND ANPASSEN
  ;; EVENT -> SSE
  ;; Redirect "/"
  )

(def handler-map
  "Map route identifies to handler creator functions.
  Note: each creator function which will take the initialized system as an argument
  so that the routes can access the global application state."
  {::index (fn [system] (index system))
   ::submit-session (constantly submit-session)
   ::sse   (fn [system] (event-resource system))})

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    (bidi-util/route-generator component routes handler-map)))

(defn new-app []
  (-> (map->App {})))
