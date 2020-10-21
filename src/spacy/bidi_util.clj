(ns spacy.bidi-util
  (:require
   [bidi.bidi :as bidi]))

(defn handler-creator
  "Create a function which will retrieve the correct handler from the handler-map
  and call the initialization function with the system that is passed in as an argument."
  [system handler-map]
  (fn [handler]
    (let [creator (get handler-map handler)]
      (bidi/handler handler (creator system)))))

(defn route-reducer
  "Creates a reducing function to transform a single entry in the handler-map so that
  it points to the correct bidi handler."
  [system handler-map]
  (let [update-handler (handler-creator system handler-map)]
    (fn [handler-map {:keys [path]}]
      (update-in handler-map path update-handler))))

(defn route-generator
  "Generates the bidi routes with a designated handler based on the handlers which are
  specified in the handler-map."
  [system routes handler-map]
  (let [all-routes (bidi/route-seq routes)
        route-map (into {} [routes])]
    (first (reduce (route-reducer system handler-map) route-map all-routes))))
