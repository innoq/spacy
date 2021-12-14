(defproject spacy "0.1.0-SNAPSHOT"
  :description "An accessible web application for moderating open space events"
  :url "http://example.com/FIXME"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies
  [[org.clojure/clojure "1.10.3"]
   [org.clojure/core.async "1.5.644"]
   [org.clojure/tools.logging "1.2.1"]
   [ch.qos.logback/logback-classic "1.2.8"]
   [com.stuartsierra/component "1.0.0"]
   [yada "1.2.16"]
   [bidi "2.1.6"]
   [juxt.modular/bidi "0.9.5"]
   [juxt.modular/aleph "0.1.4"]
   [enlive "1.1.6"]
   [aero "1.1.6"]
   [com.xtdb/xtdb-core "1.20.0"]
   [com.xtdb/xtdb-jdbc "1.20.0"]
   [org.postgresql/postgresql "42.3.1"]
   [buddy/buddy-sign "3.4.1"]
   [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
   [jarohen/chime "0.3.3"]
   [org.tobereplaced/http-accept-headers "0.1.0"]]
  :repl-options {:init-ns user}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]
                                  [org.xerial/sqlite-jdbc "3.34.0"]]}})
