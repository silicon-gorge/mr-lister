(ns onix.aws-common
  (:require
   [environ.core :refer [env]]
   [clojure.string :refer [blank?]])
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
   (let [configuration (ClientConfiguration.)
         proxy-host (env :aws-http-proxy-host)
         proxy-port (env :aws-http-proxy-port)]
     (when (and (not (blank? proxy-host))
                (not (blank? proxy-port)))
       (prn "SETTING PROXY" proxy-host proxy-port)
        (.setProxyHost configuration proxy-host)
        (.setProxyPort configuration (Integer/valueOf proxy-port)))
      configuration)))

(defn add-credentials
  "Adds the credentials from environment properties to the system property names that the AWS SDK clients will use.
   Note that when running in EC2, we will not need credentials as we will be using IAM roles, so this is for
   development purposes only."
  []
  (let [sysprops (System/getProperties)]
    (when-let [access-key (env :aws-access-key)]
      (prn "AWS SECRET KEY" (env :aws-access-key))
      (.put sysprops "aws.accessKeyId" access-key))
    (when-let [secret-key (env :aws-secret-key)]
      (prn "AWS SECRET VALUE" (env :aws-secret-key))
      (.put sysprops "aws.secretKey" secret-key))))
