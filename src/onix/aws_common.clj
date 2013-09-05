(ns onix.aws-common
  (:require
   [environ.core :refer [env]])
  (:import
   (java.util UUID)
   (com.amazonaws ClientConfiguration)))

;; **************************************************
;; *************** General AWS **********************
;; **************************************************

(defn uuid []  (.toString (UUID/randomUUID)))

(def amazon-client-config
  "Creates the client configuration, used to add a proxy if required (eg. when running in NOE.)"
  (delay
    (let [configuration (ClientConfiguration.)]
      (when-let [proxy-host (env :aws-http-proxy-host)]
        (.setProxyHost configuration proxy-host)
        (.setProxyPort configuration (Integer/valueOf (env :aws-http-proxy-port))))
      configuration)))

(defn add-credentials
  "Adds the credentials from environment properties to the system property names that the AWS SDK clients will use.
   Note that when running in EC2, we will not need credentials as we will be using IAM roles, so this is for
   development purposes only."
  []
  (let [sysprops (System/getProperties)]
    (when-let [access-key (env :aws-access-key)]
      (.put sysprops "aws.accessKeyId" access-key))
    (when-let [secret-key (env :aws-secret-key)]
      (.put sysprops "aws.secretKey" secret-key))))
