(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [cheshire.core :as json]
   [bidi.bidi :as bidi]
   [net.cgrand.enlive-html :as html]
   [spacy.domain :as domain]
   [spacy.data :as data]
   [spacy.access :as access]
   [spacy.handler-util :as handler-util]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        "login" ::login
        [:event-slug "/"]  {""   ::event
                            "sse" ::sse
                            "submit-session" {:post {"" ::submit-session}}
                            "schedule-session" {:post {"" ::schedule-session}}
                            "delete-session" {:post {"" ::delete-session}}}}])

(def events
  {"Februar Event 2021" (bidi/path-for routes ::event :event-slug "februar-2021-event")})

(html/deftemplate index-template "templates/index.html"
  []
  [:ul [:li html/first-of-type]] (html/clone-for [[caption url] events]
                                                 [:li :a] (html/content caption)
                                                 [:li :a] (html/set-attr :href url)))
(defn index [system]
  (handler-util/get-resource
   (fn [ctx]
     (apply str (index-template)))))

(defn is-up-next? [event current-user]
  (let [next-up (domain/next-up event)]
    (and next-up (= (::domain/sponsor next-up) current-user))))

(defn current-status [event current-user]
  (cond
    (is-up-next? event current-user) ::up-next
    (domain/next-up event)           ::please-wait
    :else                            ::nobody-in-queue))

(defn status-map [title]
  {::up-next [:span "You are currently next in line! Please " [:a {:href "#sessions"} "select a slot"] " for your session \"" title "\""]
   ::please-wait "Please wait for others to present their session"
   ::nobody-in-queue "There are currently no sessions in the queue"})

(html/defsnippet up-next-snippet "templates/event/up-next.html"
  [:up-next]
  [{:keys [statuses status] :as event} current-user]
  [:up-next] (html/set-attr :current-user current-user
                            :up-next (is-up-next? event current-user))
  [:p] (html/content (html/html (get statuses status)))
  [:template] (html/clone-for [[status content] statuses]
                              [:template] (html/set-attr :data-template (str "spacy.ui/" (name status)))
                              [:template] (html/content (html/html content))))

(defn up-next [event current-user]
  (let [next-up (domain/next-up event)
        title (get-in next-up [::domain/session ::domain/title])
        statuses (status-map title)
        status (current-status event current-user)
        values (assoc event :statuses statuses :status status)]
    (up-next-snippet values current-user)))

(html/defsnippet new-session-snippet "templates/event/new-session.html"
  [:new-session]
  [{::domain/keys [slug]} current-user]
  [:form] (when (not= current-user "nobody")
            (html/set-attr :action (bidi/path-for routes ::submit-session :event-slug slug)))
  [:p :a] (html/set-attr :href (str (bidi/path-for routes ::login)
                                "?redirect=" (bidi/path-for routes ::event :event-slug slug)))
  [:p] (when (= current-user "nobody") identity))

(html/defsnippet session-snippet "templates/event/session.html"
  [:.session]
  [{::domain/keys [slug]}
   {::domain/keys [sponsor]
    {::domain/keys [title id description]} ::domain/session}
   current-user]
  [(html/attr? :data-id)] (html/set-attr :data-id id)
  [(html/attr= :data-slot "title")] (html/content title)
  [(html/attr= :data-slot "sponsor")] (html/content sponsor)
  [(html/attr= :data-slot "description")] (html/content description)
  [:form] (html/set-attr :action (bidi/path-for routes ::delete-session :event-slug slug))
  [(html/attr= :name "id")] (html/set-attr :value id)
  [(html/attr? :is-sponsor)] (when (= current-user sponsor) identity))

(html/defsnippet waiting-queue-snippet "templates/event/waiting-queue.html"
  [:waiting-queue]
  [{::domain/keys [waiting-queue] :as event} current-user]
  [:ol [:li]] (html/clone-for [s waiting-queue]
                              [:li] (html/content (session-snippet event s current-user))))

(html/defsnippet schedule-session-snippet "templates/event/commands.html"
  [(html/attr= :data-command "schedule-session")]
  [{::domain/keys [slug]}
   {::domain/keys [session]}
   room
   time]
  [:form] (html/set-attr :action (bidi/path-for routes ::schedule-session :event-slug slug))
  [(html/attr= :name "id")] (html/set-attr :value (::domain/id session))
  [(html/attr= :name "room")] (html/set-attr :value room)
  [(html/attr= :name "time")] (html/set-attr :value time)
  [(html/attr= :data-slot "room")] (html/content room)
  [(html/attr= :data-slot "time")] (html/content time))

(html/defsnippet bulletin-board-snippet "templates/event/bulletin-board.html"
  [:bulletin-board]
  [{::domain/keys [schedule rooms times slug] :as event} current-user]
  [:h-include] (html/set-attr :src (bidi/path-for routes ::event :event-slug slug))
  [:table :thead [(html/attr= :scope "col")]] (html/clone-for [r rooms]
                                                           [:th] (html/content r))
  [:table :tbody [:tr]] (html/clone-for [t times]
                                        [:tr] (html/set-attr :data-time t)
                                        [:th] (html/content t)
                                        [:td] (html/clone-for [r rooms]
                                                              [:td] (html/set-attr :data-time t
                                                                                   :data-room r)
                                                              [:td] (html/append (let [s (domain/find-session-for-slot event r t)]
                                                                                   (when s
                                                                                     (session-snippet event s current-user))))
                                                              [:td] (html/append (when (and (is-up-next? event current-user)
                                                                                            (domain/is-open-slot? event r t))
                                                                                   (schedule-session-snippet
                                                                                    event
                                                                                    (domain/next-up event)
                                                                                    r
                                                                                    t))))))

(html/deftemplate event-template "templates/event.html"
  [{:keys [event-name] ::domain/keys [slug] :as event} current-user]
  [:title] (html/content event-name)
  [:h1] (html/content event-name)
  [:up-next] (html/substitute (up-next event current-user))
  [:new-session] (html/substitute (new-session-snippet event current-user))
  [:bulletin-board] (html/substitute (bulletin-board-snippet event current-user))
  [:waiting-queue] (html/substitute (waiting-queue-snippet event current-user))
  [:template#session-template] (html/content (session-snippet event {::domain/sponsor current-user} current-user))
  [(html/attr? :current-user)] (html/set-attr :current-user current-user)
  [:fact-handler] (html/set-attr :uri (bidi/path-for routes ::sse :event-slug slug)))

(defn event-view-model [{:keys [current-user] :as event}]
  (let [next-up (first (::domain/waiting-queue event))]
    (-> event
        (assoc :event-name "Februar 2021 Event"))))

(defn show-event [{:keys [data]}]
  (handler-util/get-resource
   (fn [ctx]
     (let [slug (get-in ctx [:parameters :path :event-slug])
           current-user (access/current-user ctx)
           event (-> (data/fetch data slug)
                     (assoc :current-user current-user)
                     event-view-model)]
       (apply str (event-template event current-user))))))

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

(defn delete-session [{:keys [data]}]
  (handler-util/command
   :data data
   :parameters {:form {:id java.util.UUID}}
   :command domain/delete-session
   :redirect-to event-path))

(defn sse-for-event [{{:keys [mult-channel]} :fact-channel}]
  (handler-util/sse-stream mult-channel (map json/generate-string)))

(def handler-map
  "Map route identifies to handler creator functions.
  Note: each creator function which will take the initialized system as an argument
  so that the routes can access the global application state."
  {::index (fn [system] (index system))
   ::login (fn [system] (access/login system))
   ::event (fn [system] (show-event system))
   ::submit-session (fn [system] (submit-session system))
   ::schedule-session (fn [system] (schedule-session system))
   ::delete-session (fn [system] (delete-session system))
   ::sse   (fn [system] (sse-for-event system))})

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    (handler-util/route-generator component routes handler-map)))

(defn new-app []
  (-> (map->App {})))
