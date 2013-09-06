(ns onix.web
  (:require [onix.persistence :as persistence])
  (:require   [cheshire.core :as cheshire]
              [compojure.core :refer [defroutes context GET PUT POST DELETE]]
              [compojure.route :as route]
              [compojure.handler :as handler]
              [ring.middleware.format-response :refer [wrap-json-response]]
              [ring.middleware.params :refer [wrap-params]]
              [ring.middleware.keyword-params :refer [wrap-keyword-params]]
              [clojure.data.xml :refer [element emit-str]]
              [clojure.string :refer [split]]
              [clojure.tools.logging :refer [info warn error]]
              [environ.core :refer [env]]
              [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
              [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                                replace-guid replace-mongoid replace-number]]
              [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
              [metrics.ring.expose :refer [expose-metrics-as-json]]
              [metrics.ring.instrument :refer [instrument]]))


(def pokemon
"       =,                 +,++++++
       +~              =+++++++++,+  7+
       +~            +++++++++++,++++++++7
       :~,           +,+++++,+++++++++++++:  ++++++7
       ,~~     ++++++++=++++=+++++++++:+++~+++++++++++
       :~~   ++++++++++++++~~~,+++++++++++~,++++++++++++
      +~~~ +++++++++++++++,+~~~~,+++~+++++~~++++++++++++
      ,~~~I+,++++++++++++,+++~~~~++~~~~++~~~++++++,+++++,
      :~~~+++,+++++++++++++++,~~~~~~~,~~~~~~+++++++++++++
      +~~~:++++++++++++++++++~~~~~~~~~~~~:~~~++++++=+++++
      +~~~~+++~+++++++:+++++,~~~~~~~~~~~,~~~~,+++++++++~~
    +++~,~~~~~~~~+++++++++++~~~~,      ~:++++~~~~~~+++~~
   ++++~~~~~~~,~~+++++++++~~~~          ++++~~~~~~~:~~~,~
  +++++~~~~~~,~~,++:+++++~~~,            ,+~~~~~~~,~~~++++
 +,++++:~=++++~~+++++~~~~~~                  =~~~~~++++++++
 +++++++++,+++~~++++++~,~~                  I++++++++++++++
++++++++++++++~~++++++~,                     ++++++++++++~
 ,+++++++++~~~~~+++++,                        +++~~~~~~~~:
,+ ,,++~~~~  ~~~~~~~~,                        ,~~~~~~~~~~
=++ +++~,   ~~~~~~:~,~                         ?++,,~,~~,
 ++=+++~:~++++,~~~~~7                         =+++++~~~~~~
 ++++++~~++++~~,:~                           I+,++++++~~~~,
,+++++~~++++,,,~~                          ++=+++:++++:~~~
 +++++~++++~~,,~                          ++++++++++++~~~~
  ++++~,~~~,,~~:                       I+,++++++,~~~~~~~~
    ~~,,,,,,~~,                      +=+,=++++++~~~~~~~~
     7~~~~~~                        ++++++,+++=~~~~,,
                                 ++++,++++~~~~~~~~~
                                ++++++++++,~~~~~,
                               ++++++~,,~~~~~~
                                ++++,~::~~~~~
                             7+++++~~,~~~
                             +++,~~~~~~~,
                            ++++++,:~~~
                            +++,~,~~~
                             ~~~~~,~~
                               ~~~,~+
                                 ,~~,++,+++, ++~
                                    ~~,~~~~~~~~~
                                       ,~~,~~ ,
                                                            ")

(def json-content-type "application/json;charset=UTF-8")

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
     (cheshire/generate-string)
     (response json-content-type))))

(defn- create-application
  [req]
  (let [body (cheshire/parse-string (slurp (:body req)))
        result (persistence/create-application body)]
    (response (cheshire/generate-string body) json-content-type 201)))

(defn- list-applications
  []
  (->
   (persistence/list-applications)
   (cheshire/generate-string)
   (response json-content-type)))

(defroutes applications-routes

  (POST "/" req
        (create-application req))

  (GET "/" []
       (list-applications)))

(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        [] "pong")

   (GET "/status"
        [] (status))

   (GET "/pokemon"
        [] (response pokemon "text/plain;charset=UTF-8"))

   (context "/applications"
            [] applications-routes))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
