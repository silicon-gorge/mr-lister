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

(defmethod read-body "text/plain" [http-response]
  (:body http-response))

(defn dynamo-request-build [body-matcher action]
  (let [req {:method :POST :url "/" :body [body-matcher "application/x-amz-json-1.0"]}]
    (if (nil? action)
      req
      (assoc req :and #(.withHeader % "X-Amz-Target" (re-pattern (format ".*%s" action)))))))

(defn dynamo-request
  [& {:keys [table key action] :or {key ""}}]
  (dynamo-request-build (re-pattern (format ".*%s.*%s.*" table key)) action))

(dynamo-request-build (re-pattern (format ".*%s.*%s.*" "onix-applications" ".*")) "PutItem")

(defn dynamo-describe-response
  [table-name]
  {:content-type "application/x-amz-json- 1.0"
   :status 200
   :body (json/generate-string {:Table {:TableName table-name}})})

(defn dynamo-get-request
  [& {:keys [table key]}]
  (dynamo-request-build (json/generate-string {:TableName table :Key {:HashKeyElement {:S key}}}) "GetItem"))

(defn dynamo-get-response
  [& {:keys [item]}]
  (let [body-map (if item {:ConsumedCapacityUnits 1 :Item item} {:ConsumedCapacityUnits 1})]
    {:type "application/x-amz-json-1.0" :status 200 :body
     (json/generate-string body-map)}))

(defn dynamo-scan-request
  [& {:keys [table]}]
  (dynamo-request-build (re-pattern (format ".*%s.*" table)) "Scan"))

(defn dynamo-scan-response
  [items]
  {:type "application/x-amz-json-1.0"
   :status 200
   :body (json/generate-string {:ConsumedCapacityUnits 1
                           :Count (count items)
                           :Items items
                           :ScannedCount (count items)})})

(defn dynamo-put-response
  []
  {:type "application/x-amz-json-1.0" :status 200 :body "{\"ConsumedCapacityUnits\":0.5}"})

(defn dynamo-error-response
  [& {:keys [status ex message]}]
  {:status status :type "application/x-amz-json-1.0" :body (json/generate-string {:__type (str "com.amazonaws.dynamodb.v20111205#" ex) :message message })})

(fact-group :acceptance
   (fact "Ping resource returns 200 HTTP response"
         (let [response (client/get (url+ "/ping")  {:throw-exceptions false})]
           response => (contains {:status 200})))

   (fact "Status returns successfully when valid response can be obtained from dynamo."
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "DescribeTable")
           (dynamo-describe-response "onix-applications")]
          (let [response (client/get (url+ "/status") {:throw-exceptions false})
                body (read-body response)
                success (:success body)]
            response => (contains {:status 200})
            success => true)))

   (fact "Status returns false when dynamo gives 400 response, i.e. bad request, table doesn't exist."
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "DescribeTable")
           (dynamo-error-response :status 400 :ex "ConditionalCheckFailedException" :message "The conditional request failed")]
          (let [response (client/get (url+ "/status") {:throw-exceptions false})
                body (read-body response)
                success (:success body)]
            response => (contains {:status 200})
            success => false)))

   ; Note that the dynamo client does several retries, hence :times 11 for the rest driver response.
   (fact "Status returns false when dynamo gives 500 response, I'm not working."
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "DescribeTable")
           (assoc (dynamo-error-response :status 500 :ex "InternalServerError" :message "The service is currently unavailable or busy.") :times 11)]
          (let [response (client/get (url+ "/status") {:throw-exceptions false})
                body (read-body response)
                success (:success body)]
            response => (contains {:status 200})
            success => false)))

   (fact "Create application succeeds with correct input."
         (rest-driven
          [(dynamo-request :table "onix-applications" :action "PutItem")
           (dynamo-put-response)]
          (let [response (client/post (url+ "/applications") {:body "{\"name\":\"myapp\"}" :throw-exceptions false})
                body (read-body response)]
            response => (contains {:status 201}))))

   (fact "Create application fails with invalid json."
         (let [response (client/post (url+ "/applications") {:body "{\"key\":\"value\"" :throw-exceptions false})]
           response => (contains {:status 400})))

   (fact "List applications succeeds."
         (rest-driven
          [(dynamo-scan-request :table "onix-applications")
           (dynamo-scan-response [{"name" {"S" "myapplication"}}])]
          (let [response (client/get (url+ "/applications") {:throw-exceptions false})
                body (read-body response)]
            response => (contains {:status 200}))))

   (fact "Create metadata for application fails when application does not exist."
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response)]
          (let [response (client/put (url+ "/applications/myapp/newkey") {:body "{\"value\":\"myvalue\"}" :throw-exceptions false})]
            response => (contains {:status 404}))))

   (fact "Create metadata for application fails when no data is supplied."
         (let [response (client/put (url+ "/applications/myapp/newkey") {:throw-exceptions false})]
           response => (contains {:status 400})))

   (fact "Create metadata for application succeeds."
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}})
           (dynamo-request :table "onix-applications" :action "PutItem")
           (dynamo-put-response)]
          (let [response (client/put (url+ "/applications/myapp/newkey") {:body "{\"value\":\"newvalue\"}"
                                                                          :throw-exceptions false})
                body (read-body response)]
            response => (contains {:status 201})
            body => {:newkey "newvalue"})))

   (fact "Create metadata for application fails if the json submitted is invalid"
         (let [response (client/put (url+ "/applications/my/newkey") {:body "{\"value:\"newvalue\"}"
                                                                      :throw-exceptions false})]
           response => (contains {:status 400})))

   (fact "Getting application is successful"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}})]
          (let [response (client/get (url+ "/applications/myapp"))]
            response => (contains {:status 200}))))

   (fact "Getting application returns not found when application does not exist"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response)]
          (let [response (client/get (url+ "/applications/myapp") {:throw-exceptions false})]
            response => (contains {:status 404}))))

   (fact "Getting metadata returns not found when application does not exist"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response)]
          (let [response (client/get (url+ "/applications/myapp/mykey") {:throw-exceptions false})]
            response => (contains {:status 404}))))

   (fact "Getting metadata is successful when application and metadata both exist"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}
                                       "metadata" {"S" "{\"mykey\":\"myvalue\"}"}})]
          (let [response (client/get (url+ "/applications/myapp/mykey"))
                body (read-body response)]
            response => (contains {:status 200})
            body => {:mykey "myvalue"})))

   (fact "Deleting a metadata item where application doesn't exist returns 204 No Content"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response)]
          (let [response (client/delete (url+ "/applications/myapp/mykey") {:throw-exceptions false})]
            response => (contains {:status 204}))))

   (fact "Deleting a metadata item that doesn't exist returns 204 No Content"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}
                                       "metadata" {"S" "{\"anotherkey\":\"value\"}"}})]
          (let [response (client/delete (url+ "/applications/myapp/mykey") {:throw-exceptions false})]
            response => (contains {:status 204}))))

   (fact "Deleting a metadata item where app has no metadata returns 204 No Content"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}})]
          (let [response (client/delete (url+ "/applications/myapp/mykey") {:throw-exceptions false})]
            response => (contains {:status 204}))))

   (fact "Deleting a metadata item where app has empty metadata returns 204 No Content"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}
                                       "metadata" {"S" "{}"}})]
          (let [response (client/delete (url+ "/applications/myapp/mykey") {:throw-exceptions false})]
            response => (contains {:status 204}))))

   (fact "Deleting a metadata item is successful and returns 204 No Content"
         (rest-driven
          [(dynamo-get-request :table "onix-applications" :key "myapp")
           (dynamo-get-response :item {"name" {"S" "myapp"}
                                       "metadata" {"S" "{\"mykey\":\"value\",\"key2\":\"value2\"}"}})
           (dynamo-request :table "onix-applications" :action "PutItem")
           (dynamo-put-response)]
          (let [response (client/delete (url+ "/applications/myapp/mykey") {:throw-exceptions false})]
            response => (contains {:status 204})))))
