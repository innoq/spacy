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
