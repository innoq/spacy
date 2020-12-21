(defproject spacy "0.1.0-SNAPSHOT"
  :description "An accessible web application for moderating open space events"
  :url "http://example.com/FIXME"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/core.async "1.3.610"]
   [org.clojure/tools.logging "0.3.1"]
   [ch.qos.logback/logback-classic "1.1.3"]
   [com.stuartsierra/component "1.0.0"]
   [yada "1.2.15"]
   [bidi "2.1.6"]
   [juxt.modular/bidi "0.9.5"]
   [juxt.modular/aleph "0.1.4"]
   [selmer "1.12.31"]
   [enlive "1.1.6"]
   [juxt/crux-core "20.09-1.12.1-beta"]
   [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]]
  :repl-options {:init-ns user}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]]}})
