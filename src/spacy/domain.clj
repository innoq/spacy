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
                ::schedule
                ::facts
                ::slug]))

(s/def ::slug
  string?)

(s/def ::times
  (s/coll-of ::time))

(s/def ::rooms
  (s/coll-of ::room))

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
                   :suggested ::suggested)))

(s/def ::scheduled
  (s/keys :req [::sponsor
                ::session
                ::room
                ::time
                ::at
                ::id]))

(s/def ::suggested
  (s/keys :req [::sponsor
                ::session
                ::at
                ::id]))

(s/def ::at
  inst?)

(defn- random-uuid []
  (java.util.UUID/randomUUID))

(defn suggest-session [state sponsor {:keys [title description]}]
  (let [session-id (random-uuid)
        fact-id (random-uuid)
        new-session {::sponsor sponsor
                     ::session {::id session-id
                                ::title title
                                ::description description
                                ::sponsor sponsor}}
        new-facts [(assoc new-session
                          ::fact ::session-suggested
                          ::at (java.util.Date.)
                          ::id fact-id)]]
    (-> state
        (update ::waiting-queue conj new-session)
        (update ::facts into new-facts))))

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

(defn can-schedule-session?
  "The slot must be open and the session must be the first in the queue"
  [state {:keys [id room time]}]
  (and (is-first-in-queue? state id)
       (is-open-slot? state room time)))

(defn schedule-session [state {:keys [id room time] :as data}]
  (if-not (can-schedule-session? state data)
    state ;; if no session can be scheduled, return state with no modification
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
      (-> state
          (update ::waiting-queue rest)
          (update ::schedule conj scheduled-session)
          (update ::facts into new-facts)))))

(defn event->ui-representation [event]
  (-> event
      (assoc ::next-up (first (::waiting-queue event)))
      (assoc ::waiting-queue (rest (::waiting-queue event)))
      (assoc ::available-slots (available-slots event))))

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
