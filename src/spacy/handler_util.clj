(ns spacy.handler-util
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as async]
   [bidi.bidi :as bidi]
   [yada.yada :as yada]
   [spacy.domain :as domain]
   [spacy.access :as access]
   [spacy.data :as data]
   [chime.core :as chime]
   [chime.core-async :as chime-async])
  (:import [java.time Instant Duration]))

(defn handler-creator
  "Create a function which will retrieve the correct handler from the handler-map
  and call the initialization function with the system that is passed in as an argument."
  [system handler-map]
  (fn [handler]
    (let [creator (get handler-map handler)]
      (bidi/handler handler (creator system)))))

(defn route-reducer
  "Creates a reducing function to transform a single entry in the handler-map so that
  it points to the correct bidi handler."
  [system handler-map]
  (let [update-handler (handler-creator system handler-map)]
    (fn [handler-map {:keys [path]}]
      (update-in handler-map path update-handler))))

(defn route-generator
  "Generates the bidi routes with a designated handler based on the handlers which are
  specified in the handler-map."
  [system routes handler-map]
  (let [all-routes (bidi/route-seq routes)
        route-map (into {} [routes])]
    (first (reduce (route-reducer system handler-map) route-map all-routes))))

(defn get-resource
  "Wrapper for yada resource"
  [response-fn & {:keys [parameters]
                  :or {parameters {}}}]
  (yada/handler
    (yada/resource
     {:access-control access/control
      :parameters parameters
       :methods
       {:get
        {:produces {:media-type "text/html"}
         :response response-fn}}})))

(defn redirect [ctx uri]
  (let [response (:response ctx)]
    (-> response
        (assoc :status 302)
        (assoc-in [:headers "Location"] uri))))

(defn bad-request [ctx]
  (let [response (:response ctx)]
    (-> response
        (assoc :status 400)
        (assoc-in [:headers "content-type"] "text/plain")
        (assoc :body "That request was invalid!"))))

(defn command
  "A special yada resource representing a command"
  [& {:keys [data parameters command redirect-to]}]
  (yada/handler
   (yada/resource
    {:access-control access/control
     :methods
     {:post
      {:consumes "application/x-www-form-urlencoded"
       :parameters parameters
       :response (fn [ctx]
                   (let [current-user (access/current-user ctx)
                         slug (get-in ctx [:parameters :path :event-slug])
                         params (get-in ctx [:parameters :form])
                         modify (fn [state]
                                  (let [outcome (command state current-user params)]
                                    (if-let [err (::domain/error outcome)]
                                      (throw (ex-info "Domain error"
                                                      {:error err}))
                                       outcome)))]
                     (try
                       (data/update! data slug modify)
                       (redirect ctx (redirect-to slug))
                       (catch Exception ex
                         (log/error ex "failed to update!")
                         (bad-request ctx)))))}}})))

(defn chimes
  "Creates a core.async channel which will 'chime' at intervals
  specified by `duration`. Optionally supply a `transform-fn`"
  ([duration] (chimes duration identity))
  ([duration transform-fn]
   (chime-async/chime-ch
    (chime/periodic-seq (Instant/now) duration)
    {:ch (async/chan 1 (map transform-fn))})))

(defn sse-stream [channel transform-fn]
  (yada/handler
   (yada/resource
    {:access-control access/control
     :methods
     {:get
      {:produces {:media-type "text/event-stream"}
       :response (fn [{:keys [response]}]
                   (let [ch (async/chan 256 (map transform-fn))
                         chimes (chimes (Duration/ofSeconds 15) (constantly :keepalive))]
                     (async/tap channel ch)
                     (async/tap (async/mult chimes) ch)
                     (-> response
                         (assoc-in [:headers "X-Accel-Buffering"] "no") ;; Turn off buffering in NGINX proxy for SSE
                         (assoc :body ch))))}}})))
