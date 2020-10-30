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
                ::id]))

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

(comment
  (defn- random-uuid []
    (java.util.UUID/randomUUID))

  (s/explain
    ::event
    {::waiting-queue [{::sponsor "joy"
                       ::session {::title "Responsive and Accessible"
                                  ::description "Responsible!"
                                  ::id (random-uuid)}}]
     ::rooms ["Berin" "Monheim"]
     ::times ["10:00 – 11:00"]
     ::schedule [{::sponsor "jans"
                  ::session {::title "Idris"
                             ::description "Ich liebe Typen"
                             ::id (random-uuid)}
                  ::room "Berlin"
                  ::time "10:00 – 11:00"}]
     ::facts [ #_to-be-determined ]
     ::id (random-uuid)}))
