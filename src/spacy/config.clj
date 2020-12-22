(ns spacy.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn config [profile]
  {:pre [(contains? #{:dev :prod} profile)]}
  (aero/read-config (io/resource "config.edn") {:profile profile}))

(defn webserver [config]
  (:webserver config))

(defn crux [config]
  (:crux config))
