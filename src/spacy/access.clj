(ns spacy.access
  (:require [buddy.sign.jwt :as jwt]
            [schema.core :as schema]
            [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [yada.yada :as yada]
            [yada.security]
            [spacy.config :as config]))

(def jwt-key (:jwt-key (:access (config/config :prod))))

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

(defn is-relative-uri? [s]
  (and s
       (re-find #"^/" s)
       (not (re-find #"://" s)))) ;; poor man's solution

(html/deftemplate login-template "templates/login.html"
  [redirect-url]
  [(html/attr= :name "redirect")] (when (is-relative-uri? redirect-url)
                                      (html/set-attr :value redirect-url)))

(defn login [system]
  (yada/handler
   (yada/resource
    {:access-control {:scheme ::cookie}
     :methods
     {:get
      {:produces {:media-type "text/html"}
       :parameters {:query {(schema/optional-key :redirect) String}}
       :response
       (fn [ctx]
         (let [{:keys [redirect]} (get-in ctx [:parameters :query])]
           (apply str (login-template redirect))))}
      :post
      {:consumes "application/x-www-form-urlencoded"
       :parameters {:form {(schema/optional-key :logout) String
                           (schema/optional-key :redirect) String
                           :name String}}
       :response
       (fn [{:keys [response] :as ctx}]
         (let [{:keys [name logout redirect]} (get-in ctx [:parameters :form])]
           (cond
             logout
             (-> response
                 (assoc :status 302)
                 (assoc :cookies {session {:value "", :max-age 0}})
                 (assoc-in [:headers "content-type"] "text/plain")
                 (assoc-in [:headers "Location"]
                           (if (is-relative-uri? redirect) redirect "/"))
                 (assoc :body "See you later, redirecting to root."))

             (valid? name)
             (-> response
                 (assoc :status 302)
                 (assoc :cookies (session-cookie name))
                 (assoc-in [:headers "content-type"] "text/plain")
                 (assoc-in [:headers "Location"]
                           (if (is-relative-uri? redirect) redirect "/"))
                 (assoc :body "OK, redirecting to root."))

             :otherwise
             (-> response
                 (assoc :status 400)
                 (assoc-in [:headers "content-type"] "text/plain")
                 (assoc :body "Invalid name.")))))}}})))
