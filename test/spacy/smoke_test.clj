(ns spacy.smoke-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [aleph.http :as http]
            [spacy.system :as sys]))

(def port (+ 39215 (rand-int 1000)))

(defn url [path]
  (format "http://localhost:%s%s" port path))

(defn get-body [path]
  (-> path url http/get deref :body slurp))

(def dbname
  (format "%s/spacy-test-%s.db"
          (System/getProperty "java.io.tmpdir")
          (rand-int 1000)))

(deftest integration-test
  (let [system (-> {:webserver {:port port}
                    :xtdb {:db-spec {:dbtype "sqlite"
                                     :dbname dbname}}}
                   (sys/system)
                   (component/start))]
    (try

      (Thread/sleep 100) ;; wait for DB seeding

      @(http/post (url "/februar-2021-event/submit-session")
                  {:headers {:content-type "application/x-www-form-urlencoded"}
                   :body "title=Integration&description=Test"})

      (Thread/sleep 100) ;; wait for our request to be indexed

      (let [body (get-body "/februar-2021-event/")]
        (is (re-find #"title.*Integration" body))
        (is (re-find #"description.*Test" body)))

      (finally
        (component/stop system)
        (io/delete-file dbname)))))
