(ns spacy.app
  (:require
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [clojure.string :as string]
   [cheshire.core :as json]
   [bidi.bidi :as bidi]
   [net.cgrand.enlive-html :as html]
   [spacy.domain :as domain]
   [spacy.data :as data]
   [spacy.access :as access]
   [spacy.messages :as messages]
   [spacy.handler-util :as handler-util]
   [clojure.java.io :as io]))

(def routes
  "Configured routes for the application as a bidi data structure"
  ["/" {""    ::index
        "login" ::login
        [:event-slug "/"]  {""   ::event
                            "sse" ::sse
                            "submit-session" {:post {"" ::submit-session}}
                            "schedule-session" {:post {"" ::schedule-session}}
                            "move-session" ::move-session
                            "delete-session" {:post {"" ::delete-session}}}}])

(defn- slugs-to-paths [all-slugs]
  (for [[name slug] all-slugs]
    [name (bidi/path-for routes ::event :event-slug slug)]))

(html/deftemplate index-template "templates/index.html"
  [messages paths]
  [(html/attr? :lang)] (html/set-attr :lang (:lang messages))
  [:ul [:li html/first-of-type]] (html/clone-for
                                   [[caption url] paths]
                                   [:li :a] (html/content caption)
                                   [:li :a] (html/set-attr :href url))
  [:msg] (messages/transformer messages))

(defn index [{:keys [data]}]
  (handler-util/get-resource
   (fn [ctx]
     (let [paths (slugs-to-paths (data/all-slugs data))
           msgs (messages/messages (messages/language ctx))]
       (apply str (index-template msgs paths))))))

(defn is-up-next? [event current-user]
  (let [next-up (domain/next-up event)]
    (and next-up (= (::domain/sponsor next-up) current-user))))

(defn current-status [event current-user]
  (cond
    (is-up-next? event current-user) ::up-next
    (domain/next-up event)           ::please-wait
    :else                            ::nobody-in-queue))

(html/defsnippet up-next-snippet "templates/event/up-next.html"
  [:up-next]
  [{:keys [title status] :as event} current-user messages]
  [:up-next] (html/set-attr :current-user current-user
                            :up-next (is-up-next? event current-user))
  [(html/attr? :data-status)] (html/content (html/html [:msg {:key (str "up-next.status." (name status))}]))
  [:msg] (messages/transformer messages)
  [(html/attr= :data-slot "title")] (html/content title))

(defn up-next [event current-user messages]
  (let [next-up (domain/next-up event)
        title (get-in next-up [::domain/session ::domain/title] "")
        status (current-status event current-user)
        values (assoc event :title title :status status)]
    (up-next-snippet values current-user messages)))

(html/defsnippet new-session-snippet "templates/event/new-session.html"
  [:new-session]
  [{::domain/keys [slug]} current-user]
  [:hijax-form] (when (not= current-user "nobody") identity)
  [:form] (html/set-attr :action (bidi/path-for routes ::submit-session :event-slug slug))
  [:p :a] (html/set-attr :href (str (bidi/path-for routes ::login)
                                "?redirect=" (bidi/path-for routes ::event :event-slug slug)))
  [:p] (when (= current-user "nobody") identity))

(html/defsnippet session-snippet "templates/event/session.html"
  [:.session]
  [{::domain/keys [slug]}
   {::domain/keys [sponsor]
    {::domain/keys [title id description] :as session} ::domain/session}
   current-user
   & {:keys [move-action?]}]
  [(html/attr? :data-id)] (html/set-attr :data-id id)
  [(html/attr= :data-slot "title")] (html/content title)
  [(html/attr= :id "title")] (html/set-attr :id (str "title" id))
  [(html/attr= :aria-labelledby "title")] (html/set-attr :aria-labelledby (str "title" id))
  [(html/attr= :data-slot "sponsor")] (html/content sponsor)
  [(html/attr= :data-slot "description")] (html/content description)
  [(html/attr= :data-command "delete-session")] (html/set-attr :action (bidi/path-for routes ::delete-session :event-slug slug))
  [(html/attr= :data-command "move-session")] (when move-action?
                                                (html/set-attr :action (bidi/path-for routes ::move-session :event-slug slug)))
  [(html/attr= :name "id")] (html/set-attr :value id)
  [(html/attr? :is-sponsor)] (when (= current-user sponsor) identity))

(html/defsnippet waiting-queue-snippet "templates/event/waiting-queue.html"
  [:waiting-queue]
  [{::domain/keys [waiting-queue] :as event} current-user]
  [:ol [:li]] (html/clone-for [s waiting-queue]
                              [:li] (html/content (session-snippet event s current-user))))

(defn- strip-non-alphanumeric [s]
  (string/replace s #"[^A-Za-z0-9]" ""))

(defn- idgen [& strs]
  (string/join "-"
               (map strip-non-alphanumeric strs)))

(defn- slot-id [room time]
  (idgen "slot" room time))

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
  [(html/attr? :aria-describedby)] (html/set-attr :aria-describedby (slot-id room time)))

(html/defsnippet bulletin-board-snippet "templates/event/bulletin-board.html"
  [:bulletin-board]
  [{::domain/keys [schedule rooms times slug] :as event} current-user
   & {:keys [action active-session page-link]}]
  [:h-include] (html/set-attr :src page-link)
  [:.help] (fn [node] (when active-session node))
  [(html/attr? :title)] (html/set-attr :title (get-in active-session [::domain/session ::domain/title]))
  [:table :thead [(html/attr= :scope "col")]] (html/clone-for [r rooms]
                                                           [:th] (html/content r))
  [:table :tbody [:tr]] (html/clone-for [t times]
                                        [:tr] (html/set-attr :data-time t)
                                        [:th] (html/content t)
                                        [:td] (html/clone-for [r rooms]
                                                              [(html/attr? :data-time)] (html/set-attr :data-time t
                                                                                                       :data-room r)
                                                              [(html/attr? :room)] (html/set-attr :room r)
                                                              [(html/attr? :time)] (html/set-attr :time t)
                                                              [:slot-description]  (html/set-attr :id (slot-id r t))
                                                              [:slot-updater] (html/prepend (let [s (domain/find-session-for-slot event r t)]
                                                                                   (when s
                                                                                     (session-snippet event s current-user :move-action? true))))
                                                              [:.session] (html/set-attr :aria-describedby (slot-id r t))
                                                              [:slot-updater] (html/prepend (action event current-user active-session r t))
                                                              [:template] (html/content (schedule-session-snippet event {::domain/session {::domain/id "id"}} r t)))))

(defn schedule-session-action [event current-user session room time]
  (when (and (is-up-next? event current-user)
             (domain/is-open-slot? event room time))
    (schedule-session-snippet event session room time)))

(html/deftemplate event-template "templates/event.html"
  [{::domain/keys [name slug] :as event} current-user messages]
  [(html/attr? :lang)] (html/set-attr :lang (:lang messages))
  [:title] (html/content name)
  [:h1] (html/content name)
  [:up-next] (html/substitute (up-next event current-user messages))
  [:new-session] (html/substitute (new-session-snippet event current-user))
  [:bulletin-board] (html/substitute (bulletin-board-snippet event current-user
                                                             :action schedule-session-action
                                                             :active-session (domain/next-up event)
                                                             :page-link (bidi/path-for routes ::event :event-slug slug)))
  [:waiting-queue] (html/substitute (waiting-queue-snippet event current-user))
  [:template#session-template] (html/content (session-snippet event {::domain/sponsor current-user} current-user :move-action? true))
  [(html/attr? :current-user)] (html/set-attr :current-user current-user)
  [:fact-handler] (html/set-attr :uri (bidi/path-for routes ::sse :event-slug slug))
  [:msg] (messages/transformer messages))

(defn show-event [{:keys [data]}]
  (handler-util/get-resource
   (fn [ctx]
     (let [messages (messages/messages (messages/language ctx))
           slug (get-in ctx [:parameters :path :event-slug])
           current-user (access/current-user ctx)
           event (data/fetch data slug)]
       (apply str (event-template event current-user messages))))))

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

(html/defsnippet move-session-snippet "templates/event/commands.html"
  [(html/attr= :data-command "move-session")]
  [{::domain/keys [slug]}
   {::domain/keys [session]}
   room
   time]
  [:form] (html/set-attr :action (bidi/path-for routes ::move-session :event-slug slug))
  [(html/attr= :name "id")] (html/set-attr :value (::domain/id session))
  [(html/attr= :name "room")] (html/set-attr :value room)
  [(html/attr= :name "time")] (html/set-attr :value time)
  [(html/attr? :aria-describedby)] (html/set-attr :aria-describedby (slot-id room time)))

(defn move-session-action [event current-user session room time]
  (when (and session (domain/is-open-slot? event room time))
    (move-session-snippet event session room time)))

(html/deftemplate move-session-template "templates/move-session.html"
  [{::domain/keys [slug] :as event}
   current-user
   {{::domain/keys [id]} ::domain/session :as active-session}
   messages]
  [(html/attr? :lang)] (html/set-attr :lang (:lang messages))
  [:.session] (html/substitute (session-snippet event active-session current-user))
  [:bulletin-board] (html/substitute (bulletin-board-snippet event current-user
                                                             :action move-session-action
                                                             :active-session active-session
                                                             :page-link (str (bidi/path-for routes ::move-session :event-slug slug) "?id=" id)))
  [:.session :.toolbar] nil
  [:bulletin-board (html/attr= :data-id id)] (html/substitute (html/html [:small [:msg {:key "move-session.current-position"}]]))
  [:fact-handler] (html/set-attr :uri (bidi/path-for routes ::sse :event-slug slug))
  [:msg] (messages/transformer messages))

(defn move-session [{:keys [data]}]
  (fn [req]
    (let [handler (case (:request-method req)
                    :get (handler-util/get-resource
                          (fn [ctx]
                            (let [slug (get-in ctx [:parameters :path :event-slug])
                                  session-id (get-in ctx [:parameters :query :id])
                                  current-user (access/current-user ctx)
                                  event (data/fetch data slug)
                                  active-session (domain/find-in-schedule-by-id session-id event)
                                  messages (messages/messages (messages/language ctx))]
                              (apply str (move-session-template event current-user active-session messages))))
                          :parameters {:query {:id java.util.UUID}})
                    :post (handler-util/command
                           :data data
                           :parameters {:form {:room String :time String :id java.util.UUID}}
                           :command domain/move-session
                           :redirect-to event-path))]
      (handler req))))

(defn delete-session [{:keys [data]}]
  (handler-util/command
   :data data
   :parameters {:form {:id java.util.UUID}}
   :command domain/delete-session
   :redirect-to event-path))

(defn sse-for-event [{{:keys [mult-channel]} :fact-channel}]
  (handler-util/sse-stream mult-channel json/generate-string))

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
   ::move-session (fn [system] (move-session system))
   ::sse   (fn [system] (sse-for-event system))})

(defrecord App []
  bidi/RouteProvider
  (routes [component]
    (handler-util/route-generator component routes handler-map)))

(defn new-app []
  (-> (map->App {})))
