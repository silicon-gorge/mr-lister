(ns onix.persistence
  (:require [onix.aws-common :as aws]
            [onix.dynamolib :as dynamo]
            [environ.core :refer [env]]
            [clojure.tools.logging :refer [info warn error]])
  (:import (com.amazonaws AmazonServiceException)
           (com.amazonaws AmazonClientException)
           (com.amazonaws.services.dynamodb AmazonDynamoDBClient)))

(def applications-table "onix-applications")

(def dynamo-client
  "Creates a DynamoDB client from the AWS Java SDK"
  (delay
   (do
     (aws/add-credentials)
     (doto (AmazonDynamoDBClient. @aws/amazon-client-config)
       (.setEndpoint (env :dynamo-endpoint))))))

(defn create-application
  [application]
  (dynamo/with-client
    @dynamo-client
    (dynamo/put-item applications-table application))
  )

(defn list-applications
  []
  (dynamo/lazy-scan applications-table {:conditions {} :attributes_to_get ["name"]} @dynamo-client)
  ;; (dynamo/with-client
  ;;   @dynamo-client
  ;;   (dynamo/query applications-table {}))
  )

(defn dynamo-health-check
  "Checks that we can talk to Dynamo and get a description of one of our tables."
  []
  (try
    (dynamo/with-client
      @dynamo-client
      (dynamo/describe-table applications-table))
    true
    (catch AmazonServiceException e (warn e "AWS Dynamo service error") nil)
    (catch AmazonClientException e (warn e "AWS Dynamo client error") nil)))

(dynamo/lazy-scan applications-table {:conditions {} :attributes_to_get ["name"]} @dynamo-client)


;; (defn scan-dynamo
;;   [entity-type table attribute]
;;   (dynamo/lazy-scan table {:conditions {"entityType" (dynamo/create-condition :EQ entity-type)} :attributes_to_get [attribute]} @dynamo-client))


;; (defn scan-entities
;;   [entity-type]
;;   (scan-dynamo entity-type (dynamo-entity-table) "entityId"))
