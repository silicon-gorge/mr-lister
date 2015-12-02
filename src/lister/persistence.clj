(ns lister.persistence
  (:require [amazonica.aws.securitytoken :as sts]
            [clojure.core.memoize :as mem]
            [clojure.tools.logging :refer [info warn error]]
            [clojure.walk :refer [postwalk]]
            [environ.core :refer [env]]
            [ninjakoala.instance-metadata :as im]
            [taoensso.faraday :as far])
  (:import [com.amazonaws.auth BasicSessionCredentials]))

(def applications-table
  (keyword (env :dynamo-table-applications "lister-applications")))

(def environments-table
  (keyword (env :dynamo-table-environments "lister-environments")))

(def dynamo-endpoint
  (env :dynamo-endpoint))

(def master-account-id
  (env :aws-master-account-id))

(def role-name
  (env :aws-role "lister"))

(defn- remove-empty-entries
  [m]
  (into {} (filter (comp not empty? str val) m)))

(defn create-configuration
  [credentials]
  (remove-empty-entries
   (merge credentials {:endpoint dynamo-endpoint})))

(defn- role-arn
  [account-id]
  (str "arn:aws:iam::" account-id ":role/" role-name))

(defn create-assumed-credentials
  []
  (info "Assuming role")
  (let [assumed-role (sts/assume-role {:role-arn (role-arn master-account-id) :role-session-name "lister"})
        assumed-role-credentials (:credentials assumed-role)]
    (create-configuration {:creds (BasicSessionCredentials. (:access-key assumed-role-credentials)
                                                            (:secret-key assumed-role-credentials)
                                                            (:session-token assumed-role-credentials))})))

(defn create-standard-credentials
  []
  (create-configuration {}))

(defn create-creds
  []
  (if-let [current-account-id (:account-id (im/instance-identity))]
    (if (= master-account-id current-account-id)
      (create-standard-credentials)
      ((mem/ttl create-assumed-credentials :ttl/threshold (* 30 60 1000))))
    (create-standard-credentials)))

(defn upsert-application
  "Upserts the given application in store."
  [application]
  (far/put-item (create-creds) applications-table application)
  application)

(defn list-applications
  []
  (map :name (far/scan (create-creds) applications-table {:return [:name]})))

(defn list-applications-full
  []
  (far/scan (create-creds) applications-table))

(defn get-application
  "Fetches the data for the application with the given name."
  [application-name]
  (far/get-item (create-creds) applications-table {:name application-name}))

(defn create-application
  "Creates the given application in store, unless it already exists."
  [application]
  (when-not (get-application (:name application))
    (upsert-application application)))

(defn get-application-metadata-item
  "Gets the metadata value corresponding to the given key for the given service. If either does not exist, 'nil' is returned."
  [application-name key]
  (let [target (keyword key)]
    (when-let [data (far/get-item (create-creds) applications-table {:name application-name} {:return [target]})]
      {:value (target data)})))

(defn update-application-metadata
  "Updates or creates a metadata property with the given key and value, for the given application. If the application doesn't exist 'nil' is returned."
  [application-name key value]
  (when-let [app (get-application application-name)]
    (let [new-app (assoc app (keyword key) value)]
      (upsert-application new-app)
      {:value value})))

(defn delete-application-metadata-item
  "Removes the metadata item with the given key from the given application, if it exists."
  [application-name key]
  (when-let [app (get-application application-name)]
    (let [new-app (dissoc app (keyword key))]
      (when (not= app new-app)
        (upsert-application new-app)))))

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

(defn- order-keys
  [m]
  (postwalk (fn [x] (if (map? x) (into (sorted-map) x) x)) m))

(defn get-environment
  [environment-name]
  (far/get-item (create-creds) environments-table {:name environment-name}))

(defn delete-environment
  "Remove the supplied environment"
  [environment-name]
  (far/delete-item (create-creds) environments-table {:name environment-name}))

(defn create-environment
  "Creates a new environment associated with the new account-id, doesn't set create-repo to true, assumes the new environment will be a limpet environment"
  [environment-name account-id]
  (let [environment {:name environment-name
                     :account-id account-id}]
    (far/put-item (create-creds) environments-table environment)
    environment))

(defn environments-table-healthcheck
  "Checks that we can talk to Dynamo and get a description of our environments tables."
  []
  (try
    (some? (far/describe-table (create-creds) environments-table))
    (catch Exception e
      (warn e "Exception while describing table for healthcheck")
      false)))

(defn copy-applicaions
  []
  (doseq [{:keys [metadata name]} (far/scan (create-creds) :onix-applications)]
    (far/put-item (create-creds) :lister-applications (merge (cheshire.core/parse-string metadata true) {:name name}))))

(defn copy-environments
  []
  (doseq [{:keys [metadata name]} (far/scan (create-creds) :onix-environments)]
    (far/put-item (create-creds) :lister-environments (merge (cheshire.core/parse-string metadata true) {:name name}))))
