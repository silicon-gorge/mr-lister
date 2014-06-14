(ns onix.web-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [onix
             [persistence :as persistence]
             [web :refer :all]]
            [ring.util.io :refer [string-input-stream]]))

(defn- to-json
  [raw-body]
  {:body (string-input-stream (json/encode raw-body))
   :headers {"content-type" "application/json"}})

(defn request
  "Creates a compojure request map and applies it to our application.
   Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [body headers params]
                       :or {:body nil
                            :headers {}
                            :params {}}}]]
  (let [{:keys [body] :as res} (app {:request-method method
                                     :body body
                                     :uri resource
                                     :params params
                                     :headers headers})]
    (cond-> res
            (instance? java.io.InputStream body)
            (assoc :body (json/parse-string (slurp body) true)))))

(fact "that ping pongs"
      (:body (request :get "/ping")) => "pong")

(fact "that our healthcheck gives a 200 status if things are healthy"
      (:status (request :get "/healthcheck")) => 200
      (provided
       (persistence/dynamo-health-check) => true))

(fact "that our healthcheck gives a 500 status if things aren't healthy"
      (:status (request :get "/healthcheck")) => 500
      (provided
       (persistence/dynamo-health-check) => false))

(fact "that our pokémon resource is awesome"
      (:status (request :get "/1.x/pokemon")) => 200)

(fact "that creating an application successfully does the right thing"
      (:status (request :post "/1.x/applications" (to-json {:name "something"}))) => 201
      (provided
       (persistence/create-application {:name "something"}) => true))

(fact "that failing to create an application does the right thing"
      (:status (request :post "/1.x/applications" (to-json {:name "something"}))) => 409
      (provided
       (persistence/create-application {:name "something"}) => false))

(fact "that listing applications does the right thing"
      (:body (request :get "/1.x/applications")) => {:applications ["app1" "app2" "app3"]}
      (provided
       (persistence/list-applications) => ["app1" "app2" "app3"]))

(fact "that getting an application which doesn't exist is a 404"
      (:status (request :get "/1.x/applications/onix")) => 404
      (provided
       (persistence/get-application "onix") => nil))

(fact "that getting an application which exists gives back the data"
      (:body (request :get "/1.x/applications/onix")) => {:name "onix"
                                                          :metadata {:something "interesting"}}
      (provided
       (persistence/get-application "onix") => {:name "onix"
                                                :metadata {:something "interesting"}}))

(fact "that getting an individual metadata item for an application which doesn't exist is a 404"
      (:status (request :get "/1.x/applications/onix/property")) => 404
      (provided
       (persistence/get-application "onix") => nil))

(fact "that getting an individual metadata item for an application which exists but doesn't have the property is a 404"
      (:status (request :get "/1.x/applications/onix/property")) => 404
      (provided
       (persistence/get-application "onix") => {:name "onix"
                                                :metadata {:something "else"}}))

(fact "that getting an individual metadata item which exists gives back the data"
      (:body (request :get "/1.x/applications/onix/property")) => {:property "hello"}
      (provided
       (persistence/get-application "onix") => {:name "onix"
                                                :metadata {:property "hello"}}))

(fact "that putting an individual metadata item for an application which didn't exist is 404"
      (:status (request :put "/1.x/applications/onix/property" (to-json {:value "something"}))) => 404
      (provided
       (persistence/update-application-metadata "onix" "property" "something") => nil))

(fact "that putting an individual metadata item for an application which exists is a 201"
      (:status (request :put "/1.x/applications/onix/property" (to-json {:value "something"}))) => 201
      (provided
       (persistence/update-application-metadata "onix" "property" "something") => {:property "something"}))

(fact "that deleting an individual metadata item is a 204"
      (:status (request :delete "/1.x/applications/onix/property")) => 204
      (provided
       (persistence/delete-application-metadata-item "onix" "property") => anything))
