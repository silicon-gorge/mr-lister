(ns lister.web-test
  (:require [cheshire.core :as json]
            [lister
             [persistence :as persistence]
             [web :refer :all]]
            [midje.sweet :refer :all]
            [ring.util.io :refer [string-input-stream]]))

(defn- to-json
  [raw-body]
  {:body (string-input-stream (json/encode raw-body))
   :headers {"content-type" "application/json"}})

(defn- streamed-body?
  [{:keys [body]}]
  (instance? java.io.InputStream body))

(defn- json-response?
  [{:keys [headers]}]
  (when-let [content-type (get headers "Content-Type")]
    (re-find #"^application/(vnd.+)?json" content-type)))

(defn request
  "Creates a compojure request map and applies it to our application.
  Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [body headers params]
                       :or {:body nil
                            :headers {}
                            :params {}}}]]
  (let [response (app {:request-method method
                       :body body
                       :uri resource
                       :params params
                       :headers headers})]
    (cond-> response
            (streamed-body? response) (update-in [:body] slurp)
            (json-response? response) (update-in [:body] (fn [b] (json/parse-string b true))))))

(fact "that ping pongs"
      (request :get "/ping") => (contains {:body "pong"}))

(fact "that our healthcheck gives a 200 status if things are healthy"
      (request :get "/healthcheck") => (contains {:status 200})
      (provided
       (persistence/applications-table-healthcheck) => true
       (persistence/environments-table-healthcheck) => true))

(fact "that our healthcheck gives a 500 status if the applications table dependency isn't healthy"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (persistence/applications-table-healthcheck) => false
       (persistence/environments-table-healthcheck) => true))

(fact "that our healthcheck gives a 500 status if the environments table dependency isn't healthy"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (persistence/applications-table-healthcheck) => true
       (persistence/environments-table-healthcheck) => false))

(fact "that creating an application successfully does the right thing"
      (request :put "/applications/something") => (contains {:status 201})
      (provided
       (persistence/create-application {:name "something"}) => true))

(fact "that listing applications does the right thing"
      (request :get "/applications") => (contains {:body {:applications ["app1" "app2" "app3"]}})
      (provided
       (persistence/list-applications) => ["app1" "app2" "app3"]))

(fact "that getting an application which doesn't exist is a 404"
      (request :get "/applications/application") => (contains {:status 404})
      (provided
       (persistence/get-application "application") => nil))

(fact "that getting an application which exists gives back the data"
      (request :get "/applications/application") => (contains {:body {:name "application" :metadata {:something "interesting"}}})
      (provided
       (persistence/get-application "application") => {:name "application"
                                                       :something "interesting"}))

(fact "that getting an individual metadata item for an application which doesn't exist is a 404"
      (request :get "/applications/application/property") => (contains {:status 404})
      (provided
       (persistence/get-application-metadata-item "application" "property") => nil))

(fact "that getting an individual metadata item for an application which exists but doesn't have the property is a 404"
      (request :get "/applications/application/property") => (contains {:status 404})
      (provided
       (persistence/get-application-metadata-item "application" "property") => nil))

(fact "that getting an individual metadata item which exists gives back the data"
      (request :get "/applications/application/property") => (contains {:body {:value "hello"}})
      (provided
       (persistence/get-application-metadata-item "application" "property") => {:value "hello"}))

(fact "that putting an individual metadata item for an application which didn't exist is 404"
      (request :put "/applications/application/property" (to-json {:value "something"})) => (contains {:status 404})
      (provided
       (persistence/update-application-metadata "application" "property" "something") => nil))

(fact "that putting an individual metadata item for an application which exists is a 201"
      (request :put "/applications/application/property" (to-json {:value "something"})) => (contains {:status 201})
      (provided
       (persistence/update-application-metadata "application" "property" "something") => {:value "something"}))

(fact "that deleting an individual metadata item is a 204"
      (request :delete "/applications/application/property") => (contains {:status 204})
      (provided
       (persistence/delete-application-metadata-item "application" "property") => anything))

(fact "that listing environments does the right thing"
      (request :get "/environments") => (contains {:body {:environments ["env1" "env2" "env3"]}})
      (provided
       (persistence/list-environments) => ["env1" "env2" "env3"]))

(fact "that getting an environment which doesn't exist is a 404"
      (request :get "/environments/environment") => (contains {:status 404})
      (provided
       (persistence/get-environment "environment") => nil))

(fact "that getting an environment which exists is a 200"
      (request :get "/environments/environment") => (contains {:body {:name "environment"
                                                                      :metadata {:anything "else"}}
                                                               :status 200})
      (provided
       (persistence/get-environment "environment") => {:name "environment"
                                                       :anything "else"}))

(fact "that deleting an application works"
      (request :delete "/applications/blah") => (contains {:status 204})
      (provided
       (persistence/delete-application "blah") => nil))

(fact "that deleting an environment works"
      (request :delete "/environments/blah") => (contains {:status 204})
      (provided
       (persistence/delete-environment "blah") => nil))

(fact "that creating an environment works"
      (request :put "/environments/blah" {:params {"account" "dev-id"}}) => (contains {:status 201})
      (provided
       (persistence/create-environment "blah" "dev-id") => {}))

(fact "that creating an environment but forgetting account-id gives a 400"
      (request :put "/environments/blah" {}) => (contains {:status 400}))
