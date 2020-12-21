(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [cheshire.core :as json]
   [bidi.bidi :as bidi]
   [yada.yada :as yada]
   [selmer.parser :as selmer]
   [net.cgrand.enlive-html :as html]
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

(def events
  {"Strategie Event Open Space 2020" (bidi/path-for routes ::event :event-slug "dezember-2020-strategie-event")})

(html/deftemplate index-template "templates/index.html"
  []
  [:ul [:li html/first-of-type]] (html/clone-for [[caption url] events]
                                                 [:li :a] (html/content caption)
                                                 [:li :a] (html/set-attr :href url)))

(defn index [system]
  (handler-util/get-resource
   (fn [ctx]
     (apply str (index-template)))))

(defn current-status [is-next-up next-up]
  (cond
    is-next-up ::up-next
    next-up    ::please-wait
    :else      ::nobody-in-queue))

(defn status-map [title]
  {::up-next [:span "You are currently next in line! Please " [:a {:href "#sessions"} "select a slot"] " for your session \"" title "\""]
   ::please-wait "Please wait for others to present their session"
   ::nobody-in-queue "There are currently no sessions in the queue"})

(html/defsnippet up-next-snippet "templates/event/up-next.html"
  [:up-next]
  [{:keys [current-user is-next-up statuses status]}]
  [:up-next] (html/set-attr :current-user current-user
                            :up-next is-next-up)
  [:p] (html/content (html/html (get statuses status)))
  [:template] (html/clone-for [[status content] statuses]
                              [:template] (html/set-attr :data-template (str "spacy.ui/" (name status)))
                              [:template] (html/content (html/html content))))

(defn up-next [{:keys [is-next-up next-up] :as event}]
  (let [session-title (get-in next-up [:spacy.domain/session :spacy.domain/title])
        statuses (status-map session-title)
        status (current-status is-next-up next-up)
        values (assoc event :statuses statuses :status status)]
    (up-next-snippet values)))

(html/defsnippet new-session "templates/event/new-session.html"
  [:new-session]
  [{:spacy.domain/keys [slug]}]
  [:form] (html/set-attr :action (bidi/path-for routes ::submit-session :event-slug slug)))

(html/defsnippet session "templates/event/session.html"
  [:.session]
  [{:spacy.domain/keys [sponsor]
    {:spacy.domain/keys [title id description]} :spacy.domain/session}]
  [(html/attr? :data-id)] (html/set-attr :data-id id)
  [(html/attr= :data-slot "title")] (html/content title)
  [(html/attr= :data-slot "sponsor")] (html/content sponsor)
  [(html/attr= :data-slot "description")] (html/content description))

(html/defsnippet waiting-queue "templates/event/waiting-queue.html"
  [:waiting-queue]
  [{:spacy.domain/keys [waiting-queue]}]
  [:ol [:li]] (html/clone-for [s waiting-queue]
                              [:li] (html/content (session s))))

(html/defsnippet schedule-session-snippet "templates/event/bulletin-board.html"
  [:hijax-form]
  [{:spacy.domain/keys [slug]}
   {:spacy.domain/keys [session]}
   room
   time]
  [:form] (html/set-attr :action (bidi/path-for routes ::schedule-session :event-slug slug))
  [(html/attr= :name "id")] (html/set-attr :value (:spacy.domain/id session))
  [(html/attr= :name "room")] (html/set-attr :value room)
  [(html/attr= :name "time")] (html/set-attr :value time))

(html/defsnippet bulletin-board "templates/event/bulletin-board.html"
  [:bulletin-board]
  [{:spacy.domain/keys [schedule rooms times slug]
    :keys [is-next-up next-up]
    :as event}]
  [:table :thead [:th html/first-of-type]] (html/clone-for [r (cons "" rooms)]
                                                           [:th] (html/content r))
  [:table :tbody [:tr]] (html/clone-for [t times]
                                        [:tr] (html/set-attr :data-time t)
                                        [:th] (html/content t)
                                        [:td] (html/clone-for [r rooms]
                                                              [:td] (html/set-attr :data-time t
                                                                                   :data-room r)
                                                              [:td] (html/append (let [s (domain/find-session-for-slot event r t)]
                                                                                   (when s (session s))))
                                                              [:td] (html/append (when (and is-next-up
                                                                                            (domain/is-open-slot? event r t))
                                                                                   (schedule-session-snippet
                                                                                    event
                                                                                    next-up
                                                                                    r
                                                                                    t)))))
  [:h-include] (html/set-attr :src (bidi/path-for routes ::event :event-slug slug)))

(html/deftemplate event-template "templates/event.html"
  [{:keys [event-name current-user is-next-up]
    :spacy.domain/keys [slug]
    :as event}]
  [:title] (html/content event-name)
  [:h1] (html/content event-name)
  [:up-next] (html/substitute (up-next event))
  [:new-session] (html/substitute (new-session event))
  [:bulletin-board] (html/substitute (bulletin-board event))
  [:waiting-queue] (html/substitute (waiting-queue event))
  [:template#session-template] (html/content (session {}))
  [(html/attr? :current-user)] (html/set-attr :current-user current-user)
  [:fact-handler] (html/set-attr :uri (bidi/path-for routes ::sse :event-slug slug)))

(defn event-view-model [{:keys [current-user] :as event}]
  (let [next-up (first (:spacy.domain/waiting-queue event))]
    (-> event
        (assoc :event-name "Strategie Event Open Space 2020")
        (assoc :next-up next-up)
        (assoc :is-next-up (and next-up (= (:spacy.domain/sponsor next-up) current-user)))
        (assoc :available-slots (domain/available-slots event)))))

(defn show-event [{:keys [data]}]
  (handler-util/get-resource
   (fn [ctx]
     (let [slug (get-in ctx [:parameters :path :event-slug])
           current-user "joy" ;; TODO - replace with current user from system
           event (-> (data/fetch data slug)
                     (assoc :current-user current-user)
                     event-view-model)]
       #_(selmer/render-file
        "templates/event.html"
        (->
         event
         (assoc :current-user current-user)
         (assoc :uris {::event
                       (bidi/path-for routes ::event :event-slug slug)
                       ::sse
                       (bidi/path-for routes ::sse :event-slug slug)
                       ::submit-session
                       (bidi/path-for routes ::submit-session :event-slug slug)
                       ::schedule-session
                       (bidi/path-for routes ::schedule-session :event-slug slug)})))
       (apply str (event-template event))))))

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
