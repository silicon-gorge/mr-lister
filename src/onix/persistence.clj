(ns onix.persistence
  (:require [amazonica.aws.securitytoken :as sts]
            [cheshire.core :as cheshire]
            [clojure.core.memoize :as mem]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [taoensso.faraday :as far]))

(def applications-table
  :onix-applications)

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
  [{:keys [access-key secret-key]}]
  (let [proxy-port (raw-proxy-port)]
    (remove-empty-entries
     {:access-key access-key
      :secret-key secret-key
      :proxy-host (env :aws-http-proxy-host)
      :proxy-port (if (seq proxy-port) (Integer/parseInt proxy-port) nil)
      :endpoint (env :dynamo-endpoint)})))

(defn create-assumed-credentials
  []
  (let [assumed-role (sts/assume-role {:role-arn (env :service-poke-role-arn) :role-session-name "onix"})]
    (create-credentials (:credentials assumed-role))))

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
    (if-let [metadata (cheshire/parse-string (:metadata application) true)]
      (assoc application :metadata metadata)
      application)))

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
      (when (not (nil? (kw metadata)))
        (let [new-metadata (dissoc metadata kw)
              new-app (assoc app :metadata (cheshire/generate-string new-metadata))]
          (upsert-application new-app))))))

(defn dynamo-health-check
  "Checks that we can talk to Dynamo and get a description of one of our tables."
  []
  (try
    (far/describe-table (create-creds) applications-table)
    true
    (catch Exception e
      (warn e "Exception while describing table for healthcheck")
      false)))
