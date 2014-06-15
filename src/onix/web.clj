(ns onix.web
  (:require [cheshire.core :as cheshire]
            [clojure.data.xml :refer [element emit-str]]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info warn error]]
            [compojure
             [core :refer [defroutes context GET PUT POST DELETE]]
             [handler :as handler]
             [route :as route]]
            [environ.core :refer [env]]
            [metrics.ring
             [expose :refer [expose-metrics-as-json]]
             [instrument :refer [instrument]]]
            [nokia.ring-utils
             [error :refer [wrap-error-handling error-response]]
             [ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]]
            [onix
             [persistence :as persistence]
             [pokemon :as pokemon]]
            [ring.middleware
             [format-params :refer [wrap-json-kw-params]]
             [format-response :refer [wrap-json-response]]
             [params :refer [wrap-params]]]))

(def json-content-type "application/json;charset=UTF-8")
(def text-plain-type "text/plain;charset=UTF-8")

(def healthcheck-response
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "I am healthy. Thank you for asking."})

(def healthcheck-error-response
  {:status 500
   :headers {"Content-Type" "text/plain"}
   :body "I am unwell. Check my logs."})

(def ^:dynamic *version* "none")
(defn set-version! [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn response [data content-type & [status]]
  {:status (or status 200)
   :headers {"Content-Type" content-type}
   :body data})

(defn status
  []
  (let [dynamo-ok (persistence/dynamo-health-check)]
    (->
     {:name "onix"
      :version *version*
      :success dynamo-ok
      :dependencies [{:name "dynamodb" :success dynamo-ok}]}
     (response json-content-type))))

(defn- create-application
  "Create a new application from the contents of the given request."
  [application]
  (if-let [result (persistence/create-application application)]
    (response application json-content-type 201)
    (error-response (str "application named '" (:name application) "' already exists") 409)))

(defn- list-applications
  "Get a list of all the stored applications."
  []
  (->
   (persistence/list-applications)
   sort
   ((fn [a] {:applications a}))
   (response json-content-type)))

(defn- get-application
  "Returns the application with the given name, or '404' if it doesn't exist."
  [application-name]
  (if-let [application (persistence/get-application application-name)]
    (response application json-content-type)
    (error-response (str "Application named: '" application-name "' does not exist.") 404)))

(defn- put-application-metadata-item
  "Updates the given application with the given key and value (from the request body)."
  [application-name key value]
  (if-let [result (persistence/update-application-metadata application-name key value)]
    (response result json-content-type 201)
    (error-response (str "Application named: '" application-name "' does not exist.") 404)))

(defn- get-application-metadata-item
  "Get a piece of metadata for an application. Returns 404 if either the application or the metadata is not found"
  [application-name key]
  (if-let [item (persistence/get-application-metadata-item application-name key)]
    (response item json-content-type)
    (error-response (str "Can't find metadata '" key "' for application '" application-name "'.") 404)))

(defn- delete-application-metadata-item
  "Delete an application metadata-item. Always returns 204 NoContent. Idempotent."
  [application-name key]
  (persistence/delete-application-metadata-item application-name key)
  {:status 204})

(defroutes applications-routes

  (GET "/" []
       (list-applications))

  (POST "/" [:as {application :body-params}]
        (create-application application))

  (GET "/:application" [application]
       (get-application application))

  (GET "/:application/:key" [application key]
       (get-application-metadata-item application key))

  (PUT "/:application/:key" [application key value]
       (put-application-metadata-item application key value))

  (DELETE "/:application/:key" [application key]
          (delete-application-metadata-item application key)))

(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        [] "pong")

   (GET "/status"
        [] (status))

   (GET "/pokemon"
        [] (response pokemon/image text-plain-type))

   (GET "/icon" []
        {:status 200
         :headers {"Content-Type" "image/jpeg"}
         :body (-> (clojure.java.io/resource "onix.jpg")
                   (clojure.java.io/input-stream))})

   (context "/applications"
            [] applications-routes))

  (GET "/ping" []
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body "pong"})

  (GET "/healthcheck" []
    (let [dynamo-health (future (persistence/dynamo-health-check))]
      (if @dynamo-health
        healthcheck-response
        healthcheck-error-response)))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-response)
      (wrap-json-kw-params)
      (wrap-params)
      (expose-metrics-as-json)))
