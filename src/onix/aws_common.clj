(ns onix.aws-common
  (:require
   [environ.core :refer [env]]
   [clojure.tools.logging :refer [debug info warn error]]
   [clojure.string :refer [blank?]])
  (:import
   (java.util UUID)
   (com.amazonaws ClientConfiguration)
   (com.amazonaws.auth BasicSessionCredentials)
   (com.amazonaws.services.securitytoken AWSSecurityTokenServiceClient)
   (com.amazonaws.services.securitytoken.model AssumeRoleRequest Credentials)))

;; **************************************************
;; *************** General AWS **********************
;; **************************************************

(def role-arn (env :service-prod-role-arn))

(defn uuid []  (.toString (UUID/randomUUID)))

(def amazon-client-config
  "Creates the client configuration, used to add a proxy if required (eg. when running in NOE.)"
  (delay
   (let [configuration (ClientConfiguration.)
         proxy-host (env :aws-http-proxy-host)
         proxy-port (env :aws-http-proxy-port)]
     (when (and (not (blank? proxy-host))
                (not (blank? proxy-port)))
        (info "Setting proxy: " proxy-host proxy-port)
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
      (info "Setting aws.accessKeyId: " (env :aws-access-key))
      (.put sysprops "aws.accessKeyId" access-key))
    (when-let [secret-key (env :aws-secret-key)]
      (info "Setting aws.secretKey: " (env :aws-secret-key))
      (.put sysprops "aws.secretKey" secret-key))))

(def sts-client
  (delay
   (doto (AWSSecurityTokenServiceClient. amazon-client-config)
     (.setEndpoint "https://sts.amazonaws.com"))))

(def assume-role-request
  "Creates a request wrapping the unique ID of the role we want to assume in 'prod'."
  (doto (AssumeRoleRequest.)
    (.setRoleArn (env :service-poke-role-arn))
    (.setRoleSessionName "onix")))

(defn get-assume-role-credentials
  "Get the temporary credentials required to assume the role of onix in the entdev environment.
   Note: time-limited, defaulting to 1 hour."
  []
  (info "Get temporary credentials for assuming role of onix in entdev")
  (let [result (.assumeRole @sts-client assume-role-request)
        ^Credentials credentials (.getCredentials result)]
    (info "Got credentials: " credentials)
    (BasicSessionCredentials. (.getAccessKeyId credentials)
                              (.getSecretAccessKey credentials)
                              (.getSessionToken credentials))))
