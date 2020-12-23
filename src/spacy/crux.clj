(ns spacy.crux
  (:require [crux.api :as crux]
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

(defn- add-event-id [event-id doc]
  (assoc doc ::belongs-to-event event-id))

(defn- persist! [node {::domain/keys [facts event]}]
  (assert (s/valid? ::domain/event event)
          (s/explain-str ::domain/event event))
  (assert (s/valid? ::domain/facts facts)
          (s/explain-str ::domain/facts facts))
  (let [event-id (:crux.db/id event)
        new-facts (->> facts
                       (map (partial add-event-id event-id))
                       (map maybe-add-crux-id))]
    (crux/await-tx
     node
     (crux/submit-tx node (for [doc (cons event new-facts)]
                            [:crux.tx/put doc])))))

(defn- seed! [node]
  (let [event (-> (slurp "session.edn")
                  edn/read-string
                  maybe-add-crux-id)]
    (assert (s/valid? ::domain/event event)
            (s/explain-str ::domain/event event))
    (crux/submit-tx node [[:crux.tx/put event]])))

(defn- save-seeds! [db]
  (let [slug (ffirst (crux/q db
                             {:find '[slug]
                              :where '[[e ::domain/slug slug]]}))
        entity (fetch db slug)]
    (spit "session.edn" (with-out-str (pprint entity)))))

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

(defrecord Crux [config node]
  component/Lifecycle
  (start [component]
    (log/debug "Starting" component)
    (let [node (crux/start-node (opts config))]
      (seed! node)
      (subscribe! node (:fact-channel component))
      (assoc component :node node)))

  (stop [component]
    (log/debug "Stopping" node)
    (when node
      (save-seeds! (crux/db node))
      (.close node))
    (dissoc component :node))

  data/Events
  (fetch [component slug]
    (fetch (crux/db node) slug))

  (persist! [component outcome]
    (persist! node outcome)))

(defmethod clojure.core/print-method Crux
  [system ^java.io.Writer writer]
  (.write writer "#<Crux>"))
