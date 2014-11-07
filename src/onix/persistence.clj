(ns onix.persistence
  (:require [amazonica.aws.securitytoken :as sts]
            [cheshire.core :as cheshire]
            [clojure.core.memoize :as mem]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [taoensso.faraday :as far])
  (:import [com.amazonaws.auth BasicSessionCredentials]))

(def applications-table
  :onix-applications)

(def environments-table
  :onix-environments)

(defn raw-proxy-port
  []
  (env :aws-http-proxy-port))

(defn environment
  []
  (keyword (env :environment-name)))

(defn- remove-empty-entries
  [m]
  (into {} (filter (comp not empty? str val) m)))

(defn create-credentials
  [credentials]
  (let [proxy-host (env :aws-http-proxy-host)
        proxy-port (raw-proxy-port)]
    (remove-empty-entries
     (merge credentials
            {:endpoint (env :dynamo-endpoint)}
            (if (and proxy-host proxy-port)
              {:proxy-host proxy-host
               :proxy-port (Integer/valueOf proxy-port)})))))

(defn create-assumed-credentials
  []
  (info "Assuming role")
  (let [assumed-role (sts/assume-role {:role-arn (env :poke-role-arn) :role-session-name "onix"})
        assumed-role-credentials (:credentials assumed-role)]
    (create-credentials {:creds (BasicSessionCredentials. (:access-key assumed-role-credentials)
                                                          (:secret-key assumed-role-credentials)
                                                          (:session-token assumed-role-credentials))})))

(defn create-standard-credentials
  []
  (create-credentials {:access-key (env :aws-access-key) :secret-key (env :aws-secret-key)}))

(def create-creds
  (if (= (environment) :prod)
    (mem/ttl create-assumed-credentials :ttl/threshold (* 30 60 1000))
    create-standard-credentials))

(defn upsert-application
  "Upserts the given application in store."
  [application]
  (far/put-item (create-creds) applications-table application)
  application)

(defn list-applications
  []
  (map :name (far/scan (create-creds) applications-table {:return [:name]})))

(defn get-application
  "Fetches the data for the application with the given name."
  [application-name]
  (when-let [application (far/get-item (create-creds) applications-table {:name application-name})]
    (update-in application [:metadata] #(into (sorted-map) (cheshire/parse-string % true)))))

(defn create-application
  "Creates the given application in store, unless it already exists."
  [application]
  (when-not (get-application (:name application))
    (upsert-application application)))

(defn get-application-metadata-item
  "Gets the metadata value corresponding to the given key for the given service. If either does not exist, 'nil' is returned."
  [application-name key]
  (when-let [app (get-application application-name)]
    (when-let [metadata (:metadata app)]
      (when-let [value ((keyword key) metadata)]
        {(keyword key) value}))))

(defn update-application-metadata
  "Updates or creates a metadata property with the given key and value, for the given application. If the application doesn't exist 'nil' is returned."
  [application-name key value]
  (when-let [app (get-application application-name)]
    (let [new-metadata (assoc (:metadata app) (keyword key) value)
          new-app (assoc app :metadata (cheshire/generate-string new-metadata))]
      (upsert-application new-app)
      {(keyword key) value})))

(defn delete-application-metadata-item
  "Removes the metadata item with the given key from the given application, if it exists."
  [application-name key]
  (when-let [app (get-application application-name)]
    (let [kw (keyword key)
          metadata (:metadata app)]
      (when-not (nil? (kw metadata))
        (let [new-metadata (dissoc metadata kw)
              new-app (assoc app :metadata (cheshire/generate-string new-metadata))]
          (upsert-application new-app))))))

(defn delete-application
  "Removes the application"
  [application]
  (far/delete-item (create-creds) applications-table {:name application}))

(defn applications-table-healthcheck
  "Checks that we can talk to Dynamo and get a description of our applications tables."
  []
  (try
    (some? (far/describe-table (create-creds) applications-table))
    (catch Exception e
      (warn e "Exception while describing table for healthcheck")
      false)))

(defn list-environments
  []
  (map :name (far/scan (create-creds) environments-table {:return [:name]})))

(defn get-environment
  [environment-name]
  (when-let [environment (far/get-item (create-creds) environments-table {:name environment-name})]
    (if-let [metadata (cheshire/parse-string (:metadata environment) true)]
      (assoc environment :metadata metadata)
      environment)))

(defn delete-environment
  "Remove the supplied environment"
  [environment-name]
  (far/delete-item (create-creds) environments-table {:name environment-name}))

(defn create-environment
  "Creates a new environment associated with the new account, doesn't set createRepo to true, assumes
   the new environment will be a limpet environment"
  [environment account]
  (far/put-item (create-creds) environments-table {:name environment
                                                   :metadata (cheshire/generate-string
                                                              {:account account})})
  (get-environment environment))

(defn environments-table-healthcheck
  "Checks that we can talk to Dynamo and get a description of our environments tables."
  []
  (try
    (some? (far/describe-table (create-creds) environments-table))
    (catch Exception e
      (warn e "Exception while describing table for healthcheck")
      false)))
