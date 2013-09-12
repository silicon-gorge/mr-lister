(ns onix.persistence
  (:require [onix.aws-common :as aws]
            [onix.dynamolib :as dynamo]
            [environ.core :refer [env]]
            [clojure.tools.logging :refer [info warn error]]
            [cheshire.core :as cheshire])
  (:import (com.amazonaws AmazonServiceException)
           (com.amazonaws AmazonClientException)
           (com.amazonaws.services.dynamodb AmazonDynamoDBClient)))

(def applications-table "onix-applications")

(def dynamo-client
  "Creates a DynamoDB client from the AWS Java SDK"
  (delay
   (do
;     (prn "DYNAMO ENDPOINT" (env :dynamo-endpoint))
     (aws/add-credentials)
     (doto (AmazonDynamoDBClient. @aws/amazon-client-config)
       (.setEndpoint (env :dynamo-endpoint))))))

(defn create-or-update-application
  "Creates the given application in store."
  [application]
  (dynamo/with-client
    @dynamo-client
    (dynamo/put-item applications-table application)))

(defn list-applications
  []
  {:applications
   (map :name (dynamo/lazy-scan applications-table {:conditions {} :attributes_to_get ["name"]} @dynamo-client))})

(defn get-application
  "Fetches the data for the application with the given name."
  [application-name]
  (when-let [application (dynamo/with-client
                           @dynamo-client
                           (dynamo/get-item applications-table {:hash_key application-name}))]
    (if-let [metadata (cheshire/parse-string (:metadata application) true)]
      (assoc application :metadata metadata)
      application)))

(defn create-application
  "Creates the given application in store, unless it already exists."
  [application]
  (if (nil? (get-application (:name application)))
    (create-or-update-application application)))

(defn get-application-metadata-item
  [application-name key]
  (when-let [app (get-application application-name)]
    (when-let [metadata (:metadata app)]
      (when-let [value ((keyword key) metadata)]
        {(keyword key) value}))))

(defn update-application-metadata
  [application-name key value]
  (when-let [app (get-application application-name)]
    (let [new-metadata (assoc (:metadata app) (keyword key) value)
          new-app (assoc app :metadata (cheshire/generate-string new-metadata))]
      (create-or-update-application new-app)
      {(keyword key) value})))

(defn delete-application-metadata-item
  [application-name key]
  (when-let [app (get-application application-name)]
    (let [kw (keyword key)
          metadata (:metadata app)]
        (when (not (nil? (kw metadata)))
          (let [new-metadata (dissoc metadata kw)
                new-app (assoc app :metadata (cheshire/generate-string new-metadata))]
            (create-or-update-application new-app))))))

(defn dynamo-health-check
  "Checks that we can talk to Dynamo and get a description of one of our tables."
  []
  (try
    (dynamo/with-client
      @dynamo-client
      (dynamo/describe-table applications-table))
    true
    (catch AmazonServiceException e (warn e "AWS Dynamo service error") false)
    (catch AmazonClientException e (warn e "AWS Dynamo client error") false)))
