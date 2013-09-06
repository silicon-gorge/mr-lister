(ns onix.acceptance
  (:require [onix.web :as web]
            [clj-http.client :as client]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clojure.data.zip.xml :as xml]
            [environ.core :refer [env]]
            [rest-cljer.core :refer [rest-driven]])
  (:import [java.util UUID]))

(defn url+ [& suffix] (apply str
                             (format (env :service-url) (env :service-port))
                             suffix))

(defn content-type
  [response]
  (if-let [ct ((:headers response) "content-type")]
    (first (clojure.string/split ct #";"))
    :none))

(defmulti read-body content-type)

(defmethod read-body "application/xml" [http-response]
  (-> http-response
      :body
      .getBytes
      java.io.ByteArrayInputStream.
      clojure.xml/parse clojure.zip/xml-zip))

(defmethod read-body "application/json" [http-response]
  (json/parse-string (:body http-response) true))

(defn dynamo-request-build [body-matcher action]
  (let [req {:method :POST :url "/" :body [body-matcher "application/x-amz-json-1.0"]}]
    (if (nil? action)
      req
      (assoc req :and #(.withHeader % "X-Amz-Target" (re-pattern (format ".*%s" action)))))))

(defn dynamo-request
  [& {:keys [table key action] :or {key ""}}]
  (dynamo-request-build (re-pattern (format ".*%s.*%s.*" table key)) action))

(defn dynamo-describe-response
  [table-name]
  {:content-type "application/x-amz-json-1.0"
   :status 200
   :body (json/generate-string {:Table {:TableName table-name}})})

(defn dynamo-error-response
  [status]
  {:status status
   :content-type "application/x-amz-json-1.0"})

(fact-group :acceptance
   (fact "Ping resource returns 200 HTTP response"
         (let [response (client/get (url+ "/ping")  {:throw-exceptions false})]
           response => (contains {:status 200})))

   (fact "Status returns successfully when valid response can be obtained from dynamo"
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "DescribeTable")
           (dynamo-describe-response "onix-applications")]
          (let [response (client/get (url+ "/status") {:throw-exceptions false})
                body (read-body response)
                success (:success body)]
            response => (contains {:status 200})
            success => true)))

   (fact "Status returns false when dynamo gives 400 response, i.e. bad request, table doesn't exist"
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "DescribeTable")
           (dynamo-error-response 400)]
          (let [response (client/get (url+ "/status") {:throw-exceptions false})
                body (read-body response)
                success (:success body)]
            response => (contains {:status 200})
            success => false)))

   (fact "Status returns false when dynamo gives 500 response, I'm not working"
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "DescribeTable")
           (dynamo-error-response 500)]
          (let [response (client/get (url+ "/status") {:throw-exceptions false})
                body (read-body response)
                success (:success body)]
            response => (contains {:status 200})
            success => false))))
