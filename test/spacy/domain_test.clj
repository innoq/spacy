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

(deftest test-waiting-queue
  (t/testing "Interleaving suggest and schedule actions"
    (let [sid (random-uuid)
          event (-> (test-event :next-up sid)
                    (sut/suggest-session "sponsor" {:title "one", :description ""})
                    :spacy.domain/event
                    (sut/schedule-session "sponsor" {:id sid :room "Berlin" :time "11:00 - 12:00"})
                    :spacy.domain/event
                    (sut/suggest-session "sponsor" {:title "two", :description ""})
                    :spacy.domain/event
                    (sut/suggest-session "sponsor" {:title "three", :description ""})
                    :spacy.domain/event)
          waiting-queued-titles (->> event
                                     :spacy.domain/waiting-queue
                                     (map (comp :spacy.domain/title
                                                :spacy.domain/session)))]
      (t/is (= ["one" "two" "three"]
               waiting-queued-titles)))))

(deftest test-delete-session

  (t/testing "Session you're not sponsoring"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/delete-session "jans" {:id sid}))]
      (t/is (:spacy.domain/error outcome))))

  (t/testing "Non-existent session"
    (let [sid (random-uuid)
          made-up-id (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/delete-session "joy" {:id made-up-id}))]
      (t/is (:spacy.domain/error outcome))))

  (t/testing "Suggested session"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/delete-session "joy" {:id sid}))
          {:spacy.domain/keys [event facts]} outcome]
      (t/is (empty? (:spacy.domain/waiting-queue event)))
      (t/is (some (fn [f] (= (:spacy.domain/fact f) :spacy.domain/session-deleted))
                  facts))))

  (t/testing "Scheduled session"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/schedule-session "joy" {:id sid :room "Berlin" :time "11:00 - 12:00"})
                      :spacy.domain/event
                      (sut/delete-session "joy" {:id sid}))
          {:spacy.domain/keys [event facts]} outcome]
      (t/is (empty? (:spacy.domain/waiting-queue event)))
      (t/is (empty? (->> (:spacy.domain/schedule event)
                         (filter (fn [s] (= (get-in s [:spacy.domain/session :spacy.domain/id]) sid))))))
      (t/is (some (fn [f] (= (:spacy.domain/fact f) :spacy.domain/session-deleted))
                  facts)))))

(deftest test-move-session

  (t/testing "Session in the waiting queue"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/move-session "joy" {:id sid :room "Monheim" :time "11:00 - 12:00"}))]
      (t/is (:spacy.domain/error outcome))))

  (t/testing "Session you're not sponsoring"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/schedule-session "joy" {:id sid :room "Berlin" :time "11:00 - 12:00"})
                      :spacy.domain/event
                      (sut/move-session "jans" {:id sid :room "Monheim" :time "11:00 - 12:00"}))]
      (t/is (:spacy.domain/error outcome))))

  (t/testing "Scheduled session to slot that's not open"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/schedule-session "joy" {:id sid :room "Berlin" :time "11:00 - 12:00"})
                      :spacy.domain/event
                      (sut/move-session "joy" {:id sid :room "Berlin" :time "10:00 - 11:00"}))]
      (t/is (:spacy.domain/error outcome))))

  (t/testing "Scheduled session to an open slot"
    (let [sid (random-uuid)
          outcome (-> (test-event :next-up sid)
                      (sut/schedule-session "joy" {:id sid :room "Berlin" :time "11:00 - 12:00"})
                      :spacy.domain/event
                      (sut/move-session "joy" {:id sid :room "Monheim" :time "11:00 - 12:00"}))
          {:spacy.domain/keys [event facts]} outcome
          moved-session (->> (:spacy.domain/schedule event)
                             (filter (fn [s] (= (get-in s [:spacy.domain/session :spacy.domain/id])
                                                sid)))
                             first)]
      (t/is (= "Monheim" (:spacy.domain/room moved-session)))
      (t/is (some (fn [f] (= (:spacy.domain/fact f) :spacy.domain/session-moved))
                  facts)))))
