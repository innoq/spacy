(ns spacy.access
  (:require [buddy.sign.jwt :as jwt]
            [schema.core :as schema]
            [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [yada.yada :as yada]
            [yada.security]
            [spacy.config :as config]
            [environ.core :as env]))

(def jwt-key (or (env/env :jwt-key)
                 "UAWX9WK366IRDF5AQTRPX2I457AFTTU93PBB1QAJME107IKJM4A5HPW2I6PJRHB0"))

(def session "session")

(defn- sign [claims]
  (jwt/sign claims jwt-key))

(defn- read-cookie [cookie]
  (jwt/unsign cookie jwt-key))

(defmethod yada.security/verify ::cookie
  [ctx access-control]
  (if-let [cookie (get-in ctx [:cookies session])]
    (try
      (read-cookie cookie)
      (catch clojure.lang.ExceptionInfo e
        (log/warn e "reading cookie")))))

(def control
  {:scheme ::cookie})

(defn- valid? [name]
  ;; TODO: maybe check for non-printable ASCII
  ;; or be even more restrictive
  (and (seq name)
       (not-any? #{\< \> \; \& \#} name)))

(defn current-user [ctx]
  (get-in ctx [:authentication "default" :name]))

(defn- session-cookie [name]
  {session {:value (sign {:id (java.util.UUID/randomUUID)
                          :name name})}})

(defn login [system]
  (yada/handler
   (yada/resource
    {:access-control {:scheme ::cookie}
     :methods
     {:get
      {:produces {:media-type "text/html"}
       :response
       (fn [ctx]
         (str
          "<html><body>"
          (if-let [name (current-user ctx)]
            (format "<p>Hi there, <em>%s</em></p>" name)
            "<p>Who are you?</p>")
          "<form method=post >"
          "<input type=text name=name placeholder=name />"
          "<input type=submit />"
          "<br />"
          "<br />"
          "<input type=submit name=logout value=Logout />"
          "</form>"
          "</body></html>"))}
      :post
      {:consumes "application/x-www-form-urlencoded"
       :parameters {:form {(schema/optional-key :logout) String
                           :name String}}
       :response
       (fn [{:keys [response] :as ctx}]
         (let [{:keys [name logout]} (get-in ctx [:parameters :form])]
           (cond
             logout
             (-> response
                 (assoc :status 302)
                 (assoc :cookies {session {:value "", :max-age 0}})
                 (assoc-in [:headers "content-type"] "text/plain")
                 (assoc-in [:headers "Location"] "/")
                 (assoc :body "See you later, redirecting to root."))

             (valid? name)
             (-> response
                 (assoc :status 302)
                 (assoc :cookies (session-cookie name))
                 (assoc-in [:headers "content-type"] "text/plain")
                 (assoc-in [:headers "Location"] "/")
                 (assoc :body "OK, redirecting to root."))

             :otherwise
             (-> response
                 (assoc :status 400)
                 (assoc-in [:headers "content-type"] "text/plain")
                 (assoc :body "Invalid name.")))))}}})))
