(ns spacy.crux
  (:require [crux.api :as crux]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [spacy.data :as data]
            [spacy.domain :as domain]))

(defn- random-uuid []
  (java.util.UUID/randomUUID))

(defn with-crux-id [doc]
  (assoc doc :crux.db/id (random-uuid)))

(defn- maybe-add-crux-id [doc]
  (if (:crux.db/id doc)
    doc
    (with-crux-id doc)))

(defn- fetch [db slug]
  (let [eid (ffirst (crux/q db
                            {:find '[e]
                             :where '[[e ::domain/slug slug]]
                             :args [{'slug slug}]}))
        event (crux/entity db eid)]
    (assert (s/valid? ::domain/event event)
            (s/explain-str ::domain/event event))
    event))


(defn- all-slugs [db]
  (let [found (crux/q db
                      {:find '[name slug]
                       :where '[[e ::domain/slug slug]
                                [e ::domain/name name]]})]
    (seq found)))

(defn- add-event-id [event-id doc]
  (assoc doc ::belongs-to-event event-id))

(defn- replace!
  "Stores the given event and facts iff the current event state in the database
  is equal to old-state. This allows us to make sure there were no concurrent
  writes."
  [node old-state {::domain/keys [facts event]}]
  (assert (s/valid? ::domain/event event)
          (s/explain-str ::domain/event event))
  (assert (s/valid? ::domain/facts facts)
          (s/explain-str ::domain/facts facts))
  (let [event-id (:crux.db/id event)
        new-facts (->> facts
                       (map (partial add-event-id event-id))
                       (map maybe-add-crux-id))
        tx (crux/submit-tx node (cons
                                 [:crux.tx/match event-id old-state]
                                 (for [doc (cons event new-facts)]
                                   [:crux.tx/put doc])))]
    (crux/await-tx node tx)
    (if-not (crux/tx-committed? node tx)
      {::error ::concurrent-writes})))

(def ^:private retries 10)

(defn- wait-after-attempt [attempt]
  {:pre [(<= 0 attempt retries)]}
  (Thread/sleep (rand (* 100 (+ 2 attempt)))))

(defn- retry-with-breaks
  "Invokes f with increasing numbers as long as f returns false.
  Waits for a random period between calls to f."
  [f]
  (some (fn [attempt]
          (or (f attempt)
              (wait-after-attempt attempt)))
        (range retries)))

(defn- update!
  "Applies f to the event with a given slug and tries to store it.
  Retries a limited number of times in case of races with other writes."
  [node slug f]
  (or (retry-with-breaks (fn [attempt]
                           (let [db (crux.api/db node)
                                 event (fetch db slug)]
                             (if (nil? (::error (replace! node event (f event))))
                               :ok))))
      (throw (ex-info "Concurrent writes" {::reason ::mismatch}))))

(def ^:private put-if-slug-absent
  ::put-if-slug-absent)

(defn- put-seeding-function! [node]
  (let [fun '(fn [ctx doc]
               (let [db (crux.api/db ctx)
                     our-slug (::domain/slug doc)
                     query {:find '[e]
                            :where '[[e ::domain/slug slug]]
                            :args [{'slug our-slug}]}
                     found (crux.api/q db query)]
                 (when (empty? found)
                   [[:crux.tx/put doc]])))]
    (crux/submit-tx node
                    [[:crux.tx/put
                      {:crux.db/id put-if-slug-absent
                       :crux.db/fn fun}]])))

(defn- maybe-seed! [node]
  (when-let [events (some->> (io/resource "seeds.edn")
                             slurp
                             edn/read-string
                             (map maybe-add-crux-id))]
    (put-seeding-function! node)
    (doseq [event events]
      (assert (s/valid? ::domain/event event)
              (s/explain-str ::domain/event event))
      (crux/submit-tx node
                      [[:crux.tx/fn put-if-slug-absent event]]))))


(defn- interpret [crux-event]
  (let [ops (:crux/tx-ops crux-event)
        facts (for [[op doc] ops
                    :when (and (= op :crux.tx/put)
                               (:spacy.domain/fact doc))]
                (dissoc doc :crux.db/id))]
    facts))

(defn- subscribe! [node fact-channel]
  {:pre [(:channel fact-channel)]}
  (crux/listen node
               {:crux/event-type :crux/indexed-tx, :with-tx-ops? true}
               (fn [ev]
                 (let [facts (interpret ev)
                       ch (:channel fact-channel)]
                   (async/go (doseq [fact facts]
                               (async/>! ch fact)))))))

(defn- dialect [db-spec]
  (case (:dbtype db-spec)
    "sqlite" {:crux/module 'crux.jdbc.sqlite/->dialect}
    "postgres" {:crux/module 'crux.jdbc.psql/->dialect}))

(defn- opts [{:keys [db-spec]}]
  {:pre [(map? db-spec)]}
  {:crux.jdbc/connection-pool {:dialect (dialect db-spec)
                               :pool-opts {}
                               :db-spec db-spec}
   :crux/tx-log {:crux/module 'crux.jdbc/->tx-log
                 :connection-pool :crux.jdbc/connection-pool}
   :crux/document-store {:crux/module 'crux.jdbc/->document-store
                         :connection-pool :crux.jdbc/connection-pool}})

(defrecord Crux [config node listener]
  component/Lifecycle
  (start [component]
    (log/debug "Starting" component)
    (let [node (crux/start-node (opts config))]
      (maybe-seed! node)
      (assoc component
             :listener (subscribe! node (:fact-channel component))
             :node node)))

  (stop [component]
    (log/debug "Stopping" node)
    (when listener
      (.close listener))
    (when node
      (.close node))
    (assoc component :node nil :listener nil))

  data/Events
  (fetch [component slug]
    (fetch (crux/db node) slug))

  (all-slugs [component]
    (all-slugs (crux/db node)))

  (update! [component slug f]
    (update! node slug f)))

(defmethod clojure.core/print-method Crux
  [system ^java.io.Writer writer]
  (.write writer "#<Crux>"))
