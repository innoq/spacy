(ns spacy.domain
  (:require
    [clojure.spec.alpha :as s]))

(s/def ::id
  uuid?)

(s/def ::room
  string?)

(s/def ::time
  string?)

(s/def ::session
  (s/keys :req [::title
                ::description
                ::id]))

(s/def ::event
  (s/keys :req [::waiting-queue
                ::times
                ::rooms
                ::name
                ::schedule
                ::slug]))

(s/def ::slug
  string?)

(s/def ::times
  (s/coll-of ::time))

(s/def ::rooms
  (s/coll-of ::room))

(s/def ::name
  string?)

(s/def ::waiting-queue
  (s/coll-of ::queued-session))

(s/def ::schedule
  (s/coll-of ::scheduled-session))

(s/def ::queued-session
  (s/keys :req [::sponsor
                ::session]))

(s/def ::scheduled-session
  (s/keys :req [::sponsor
                ::session
                ::room
                ::time]))

(s/def ::sponsor
  string?)

(s/def ::facts
  (s/coll-of (s/or :scheduled ::scheduled
                   :suggested ::suggested
                   :moved     ::moved
                   :deleted   ::deleted)))

(defn fact-is [kind]
  (fn [fact] (= kind (::fact fact))))

(s/def ::scheduled
  (s/and (fact-is ::session-scheduled)
         (s/keys :req [::sponsor
                       ::session
                       ::room
                       ::time
                       ::at
                       ::id])))

(s/def ::suggested
  (s/and (fact-is ::session-suggested)
         (s/keys :req [::sponsor
                       ::session
                       ::at
                       ::id])))

(s/def ::moved
  (s/and (fact-is ::session-moved)
         (s/keys :req [::sponsor
                       ::session
                       ::at
                       ::id])))

(s/def ::deleted
  (s/and (fact-is ::session-deleted)
         (s/keys :req [::sponsor
                       ::session
                       ::at
                       ::id])))


(s/def ::at
  inst?)

(s/def ::outcome
  (s/or :success (s/keys :req [::event ::facts])
        :failure (s/keys :req [::error])))

(s/def ::error
  #{::cannot-delete-session
    ::cannot-move-session
    ::cannot-schedule-session})

(defn- random-uuid []
  (java.util.UUID/randomUUID))

(defn suggest-session [state current-user {:keys [title description]}]
  {:post [(s/valid? ::outcome %)]}
  (let [session-id (random-uuid)
        fact-id (random-uuid)
        new-session {::sponsor current-user
                     ::session {::id session-id
                                ::title title
                                ::description description}}
        new-facts [(assoc new-session
                          ::fact ::session-suggested
                          ::at (java.util.Date.)
                          ::id fact-id)]]
    {::facts new-facts
     ::event (-> state
                 (update ::waiting-queue #(conj (vec %) new-session)))}))

(defn next-up [state]
  (first (get-in state [::waiting-queue])))

(defn is-first-in-queue? [state id]
  (let [next-up (first (get-in state [::waiting-queue]))]
    (= (get-in next-up [::session ::id]) id)))

(defn is-valid-slot? [state room time]
  (and (some #{room} (::rooms state))
       (some #{time} (::times state))))

(defn slot-taken? [state room time]
  (some (fn [s] (and (= (::room s) room)
                     (= (::time s) time))) (::schedule state)))

(defn is-open-slot? [state room time]
  (and (is-valid-slot? state room time)
       (not (slot-taken? state room time))))

(defn available-slots [event]
  (for [time (::times event)
        room (::rooms event)
        :when (is-open-slot? event room time)]
    {:time time :room room}))

(defn find-session-for-slot [{::keys [schedule]} room time]
  (->> schedule
       (filter #(= room (::room %)))
       (filter #(= time (::time %)))
       first))

(defn can-schedule-session?
  "The slot must be open and the session must be the first in the queue"
  [state {:keys [id room time]}]
  (and (is-first-in-queue? state id)
       (is-open-slot? state room time)))

(defn schedule-session [state _current-user {:keys [id room time] :as data}]
  {:post [(s/valid? ::outcome %)]}
  (if-not (can-schedule-session? state data)
    {::error ::cannot-schedule-session}
    (let [queue (get-in state [::waiting-queue])
          session (first queue)
          fact-id (random-uuid)
          scheduled-session (assoc session
                                   ::room room
                                   ::time time)
          new-facts [(assoc scheduled-session
                            ::fact ::session-scheduled
                            ::at (java.util.Date.)
                            ::id fact-id)]]
      {::facts new-facts
       ::event (-> state
                   (update ::waiting-queue rest)
                   (update ::schedule conj scheduled-session))})))

(defn- find-by-id [id {::keys [waiting-queue schedule]}]
  (->> (concat waiting-queue schedule)
       (filter #(= id (get-in % [::session ::id])))
       first))

(defn find-in-schedule-by-id [id {::keys [schedule]}]
  (->> schedule
       (filter #(= id (get-in % [::session ::id])))
       first))

(defn delete-session [state current-user {:keys [id]}]
  {:post [(s/valid? ::outcome %)]}
  (let [session (find-by-id id state)
        is-the-sponsor? (= current-user (::sponsor session))]
    (if (and session is-the-sponsor?)
      {::event (-> state
                   (update ::waiting-queue (partial remove #{session}))
                   (update ::schedule (partial remove #{session})))
       ::facts [(assoc session
                       ::fact ::session-deleted
                       ::at (java.util.Date.)
                       ::id (random-uuid))]}
      {::error ::cannot-delete-session})))

(defn move-session [state current-user {:keys [id room time]}]
  {:post [(s/valid? ::outcome %)]}
  (let [session (find-in-schedule-by-id id state)
        is-the-sponsor? (= current-user (::sponsor session))
        can-move? (and session
                       is-the-sponsor?
                       (is-open-slot? state room time))
        moved (assoc session ::room room ::time time)]
    (if can-move?
      {::event (-> state
                   (update ::schedule (partial replace {session moved})))
       ::facts [(assoc moved
                       ::fact ::session-moved
                       ::at (java.util.Date.)
                       ::id (random-uuid))]}
      {::error ::cannot-move-session})))

(comment
  (s/explain
   ::event
   (let [sid (random-uuid)]
     {::waiting-queue [{::sponsor "joy"
                        ::session {::title "Responsive and Accessible"
                                   ::description "Responsible!"
                                   ::id (random-uuid)}}]
      ::rooms ["Berin" "Monheim"]
      ::times ["10:00 – 11:00"]
      ::name "example event"
      ::schedule [{::sponsor "jans"
                   ::session {::title "Idris"
                              ::description "Ich liebe Typen"
                              ::id sid}
                   ::room "Berlin"
                   ::time "10:00 – 11:00"}]
      ::facts [{::at #inst "2020-10-31T11:44-00:00"
                ::fact ::session-suggested
                ::sponsor "jans"
                ::session {::title "Idris"
                           ::description "Ich liebe Typen"
                           ::id sid}
                ::room "Berlin"
                ::time "10:00 – 11:00"
                ::id (random-uuid)}]
      ::slug "dezember-2020-strategie-event"})))
