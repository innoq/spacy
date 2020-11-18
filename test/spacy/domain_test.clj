(ns spacy.domain-test
  (:require [spacy.domain :as sut]
            [clojure.test :as t :refer [deftest]]))

(defn- random-uuid []
  (java.util.UUID/randomUUID))

(defn test-event [& {:keys [next-up scheduled] :or {next-up (random-uuid)
                                                         scheduled (random-uuid)}}]
  {:spacy.domain/waiting-queue [#:spacy.domain{:sponsor "joy"
                                                :session
                                                #:spacy.domain{:title "Responsive and Accessible"
                                                             :description "Responsible!"
                                                             :id next-up}}]
   :spacy.domain/rooms ["Berlin" "Monheim"]
   :spacy.domain/times ["10:00 - 11:00" "11:00 - 12:00"]
   :spacy.domain/schedule [#:spacy.domain{:sponsor "jans"
                                          :session
                                          #:spacy.domain{:title "Idris"
                                                         :description "Ich liebe Typen"
                                                         :id scheduled}
                                          :room "Berlin"
                                          :time "10:00 - 11:00"}]
   :spacy.domain/facts []
   :spacy.domain/slug "dezember-2020-strategie-event"})

(deftest test-can-schedule-session?
  (t/testing "Session with correct session id, room, and time can be scheduled"
    (t/is (some? (let [sid (random-uuid)]
                   (sut/can-schedule-session?
                    (test-event :next-up sid) {:id sid :room "Berlin" :time "11:00 - 12:00"})))))
  (t/testing "Session to schedule must be the next in line"
    (t/is (not (let [sid (random-uuid)]
                 (sut/can-schedule-session?
                  (test-event :next-up (random-uuid)) {:id sid :room "Berlin" :time "11:00 - 12:00"})))))
  (t/testing "Session must be for a valid room"
    (t/is (not (let [sid (random-uuid)]
                 (sut/can-schedule-session?
                  (test-event :next-up sid) {:id sid :room "Non-existent room" :time "11:00 - 12:00"})))))
  (t/testing "Session must be for a valid time"
    (t/is (not (let [sid (random-uuid)]
                 (sut/can-schedule-session?
                  (test-event :next-up sid) {:id sid :room "Berlin" :time "Non-existent time"})))))
  (t/testing "Session cannot be placed in a slot which is already taken"
    (t/is (not (let [sid (random-uuid)]
                 (sut/can-schedule-session?
                  (test-event :next-up sid) {:id sid :room "Berlin" :time "10:00 - 11:00"}))))))


(deftest test-schedule-session
  (t/testing "Test scheduling of event"
    (let [sid (random-uuid)
          _user nil
          outcome (-> (test-event :next-up sid)
                      (sut/schedule-session _user {:id sid :room "Berlin" :time "11:00 - 12:00"}))
          {:spacy.domain/keys [event facts]} outcome]
      (t/is (empty? (:spacy.domain/waiting-queue event)))
      (t/is (some   (fn [s] (= (get-in s [:spacy.domain/session :spacy.domain/id]) sid))
                    (:spacy.domain/schedule event)))
      (t/is (some   (fn [f] (= (:spacy.domain/fact f) :spacy.domain/session-scheduled))
                    facts)))))
