(ns onix.dynamolib
  (:require [clojure.string :as str])
  (:use clojure.tools.logging)
  (:import com.amazonaws.auth.BasicAWSCredentials
           com.amazonaws.services.dynamodb.AmazonDynamoDBClient
           com.amazonaws.AmazonServiceException
           [com.amazonaws.services.dynamodb.model
            AttributeValue
            AttributeValueUpdate
            ComparisonOperator
            Condition
            ConditionalCheckFailedException
            CreateTableRequest
            DeleteTableRequest
            DeleteItemRequest
            DescribeTableRequest
            ExpectedAttributeValue
            GetItemRequest
            Key
            KeySchema
            KeySchemaElement
            ProvisionedThroughput
            PutItemRequest
            QueryRequest
            ScanRequest
            UpdateItemRequest
            ReturnValue]))

(def ^{:dynamic true} *ddb_client*)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;General stuff
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro doto-if
  "if cond then doto form to x,
  otherwise return x unchanged
  XXX: there has to be a better way to do this...."
  [x cond & form]
  `(if ~cond
     (doto ~x ~@form)
     ~x))

(defn create-ddb-client
  "Create the AmazonDynamoDBClient"
  [cred]
  (AmazonDynamoDBClient.
    (BasicAWSCredentials. (:access_key cred) (:secret_key cred))))

(defn with-client*
  [client func]
  (binding [*ddb_client* client]
    (func)))

(defmacro with-client
  [client & body]
  `(with-client* ~client (fn [] ~@body)))


;;Create an AttributeValue object from v
(defmulti create-attribute-value class)
(defmethod create-attribute-value String [v]
  {:pre [(not (empty? v))]}
  (doto (AttributeValue.) (.setS v)))

(defmethod create-attribute-value Number [v]
  {:pre [(not (empty? (str v)))]}
  (doto (AttributeValue.) (.setN (str v))))
(defmethod create-attribute-value java.util.Collection [v]
  {:pre [(not-empty v)]}
  (cond
    (every? #(and (string? %) (not (str/blank? %))) v)
    (doto (AttributeValue.) (.setSS (map str v)))

    (every? number? v)
    (doto (AttributeValue.) (.setNS (map str v)))

    :else
    (throw (Exception. "ddb sets must be all strings/numbers"))))


(defn- parse-number
  "Parse a number into the correct type
  ..there has to be a better way to do this"
  [n]
  (try
    (Long/parseLong n)
    (catch NumberFormatException e
      (Double/parseDouble n))))

(defn- get-value
  "Get the value of an AttributeValue object."
  [^AttributeValue v]
  (when v
    (cond
      (.getN v) (parse-number (.getN v))
      (.getS v) (.getS v)
      (.getNS v) (set (map #(parse-number %) (.getNS v)))
      (.getSS v) (set (.getSS v)))))



(defn create-key
  "Helper to create a (clojure map) key"
  [hash_key & [range_key]]
  (-> {:hash_key hash_key}
    (#(if range_key (assoc % :range_key range_key) %))))


(defn- create-KeyObject
  "Create a com.amazonaws.services.dynamodb.model.Key object from a value."
  [hash_key & [range_key]]
    (cond
      (and hash_key range_key)
      (Key. (create-attribute-value hash_key) (create-attribute-value range_key))

      hash_key
      (Key. (create-attribute-value hash_key))))

(defn KeyObject->key [k]
  (when k
    (create-key (get-value (.getHashKeyElement k)) (get-value (.getRangeKeyElement k)))))

;    (if hash_key
;      (Key. (create-attribute-value hash_key))))
;  ([hash_key range_key]
;    (Key. (create-attribute-value hash_key) (create-attribute-value range_key)))
;  )






;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;Table level functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-key-schema-element
  "Create a KeySchemaElement object."
  [{key_name :name key_type :type}]
  (doto (KeySchemaElement.)
    (.setAttributeName (str key_name))
    (.setAttributeType (str/upper-case (first (name key_type))))))

(defn- create-key-schema
  "hash_key - {:name \"some_name\" :type \"NUMBER\"}
  range_key - {:name \"some_name\" :type \"NUMBER\"}"
  ([hash_key]
    (doto (KeySchema. (create-key-schema-element hash_key))))

  ([hash_key range_key]
    (doto (KeySchema.)
      (.setHashKeyElement (create-key-schema-element hash_key))
      (.setRangeKeyElement (create-key-schema-element range_key))))
  )

(defn- create-provisioned-throughput
  "Created a ProvisionedThroughput object."
  [{read_units :read_units write_units :write_units}]
  (doto (ProvisionedThroughput.)
    (.setReadCapacityUnits (long read_units))
    (.setWriteCapacityUnits (long write_units))))


(defn create-table
  "Create a table in DynamoDB with the given name, throughput, and keys
  throughput - {:read_units 10 :write_units 5}"
  [name throughput & keys]
  (.createTable
    *ddb_client*
    (doto (CreateTableRequest.)
      (.setTableName (str name))
      (.setKeySchema (apply create-key-schema keys))
      (.setProvisionedThroughput (create-provisioned-throughput throughput)))))

(defn delete-table
  "Delete a table in DyanmoDB with the given name."
  [name]
  (.deleteTable
    *ddb_client*
    (DeleteTableRequest. name)))

(defn describe-table
  "Describe a table in DyanmoDB with the given name."
  [name]
  (.describeTable
    *ddb_client*
    (doto (DescribeTableRequest.)
      (.setTableName name))))

(defn list-tables
  "Return a list of tables in DynamoDB."
  []
  (-> *ddb_client*
    .listTables
    .getTableNames))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;write helper functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;Expected attribute functions
(defn create-expected-attribute-value
  "Create an ExpectedAttributeValue object
  param looks like: {:exists false :value 1234}"
  [{exists :exists value :value}]
  (-> (ExpectedAttributeValue.)
    (doto-if (not (nil? exists)) (.setExists exists) )
    (doto-if value (.setValue (create-attribute-value value)))))

(defn prepare-expected-attribute-values
  "turns clojure-ish expected attributes into ExpectedAttributeValues
  m looks like {:someattr {:exists false :value 1234}
                :otherattr {:exists true :value 5678}}"
  [m]
  (->> m
    (map #(hash-map (name (key %)) (create-expected-attribute-value (val %))))
    (reduce merge {})))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;put functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- prepare-map
  "Turn a clojure map into a Map<String,AttributeValue>"
  [m]
  (->> m
    (map #(hash-map (name (key %)) (create-attribute-value (val %))))
    (reduce merge {})))

(defn put-item
  "Add an item (a Clojure map) to a DynamoDB table.
  modifiers looks like {:expected {:someattr {:exists false :value 1234}
                                   :otherattr {:exists true :value 5678}}}"
  ([table item]
    (put-item table item {}))
  ([table item {expected :expected}]
     (debug "Dynamo put-item: table: [" table "] item: [" item "] expected: [" expected "]")
    (.putItem
      *ddb_client*
      (doto (PutItemRequest. table (prepare-map item))
        (.setExpected (prepare-expected-attribute-values expected))))))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;update functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;AttributeValueUpdate functions
(defn is-attribute-value-update-map? [m]
  (and (map? m)
    (or (and (contains? #{"PUT" "ADD"} (name (:action m))) (:value m))
        (and (= "DELETE" (name (get m :action)))))))


(defn create-attribute-value-update
  "create an AttributeValueUpdate object
  if v is a map, it will check :action to determine the action
  otherwise default to PUT
  "
  [v]
  (cond

    ;;If it's a DELETE with a specified value
    (and (is-attribute-value-update-map? v)
      (= "DELETE" (name (:action v)))
      (:value v))
    (doto (AttributeValueUpdate.)
      (.setValue (create-attribute-value (:value v)))
      (.setAction "DELETE"))

    ;;If it's a DELETE action with nil value
    (and (is-attribute-value-update-map? v)
         (= "DELETE" (name (:action v)))
         (nil? (:value v)))
    (doto (AttributeValueUpdate.)
      (.setAction "DELETE"))

    ;;If it's a PUT or ADD
    (is-attribute-value-update-map? v)
    (doto (AttributeValueUpdate.)
      (.setValue (create-attribute-value (:value v)))
      (.setAction (name (:action v))))

    :else
    (doto (AttributeValueUpdate.)
      (.setAction "PUT")
      (.setValue (create-attribute-value v)))))

(defn- prepare-update-map
  "Turn a clojure map into a Map<String,AttributeValue>"
  [m]
  (->> m
    (map #(hash-map (name (key %)) (create-attribute-value-update (val %))))
    (reduce merge {})))

(defn prepare-return-values
  [return-value]
  (cond
    (= return-value :updated-new)
    ReturnValue/UPDATED_NEW
    (= return-value :updated-old)
    ReturnValue/UPDATED_OLD
    (= return-value :all-old)
    ReturnValue/ALL_OLD
    (= return-value :all-new)
    ReturnValue/ALL_NEW
    :else
    ReturnValue/NONE))

(defn update-item
  "Updates an item in the DB by replacing the item's specific attributes with the one's passed in"
  ([table key item]
    (update-item table key item  {}))
  ([table {hash_key :hash_key range_key :range_key} item {expected :expected returned :returned}]
     (debug "Dynamo update-item: table: [" table "] hash: [" hash_key "] range: [" range_key "] item: [" item "]")
    (.updateItem
      *ddb_client*
      (doto (UpdateItemRequest.)
        (.setTableName table)
        (.setKey (create-KeyObject hash_key range_key))
        (.setAttributeUpdates (prepare-update-map item))
        (.setExpected (prepare-expected-attribute-values expected))
        (.setReturnValues (prepare-return-values returned))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;delete functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn delete-item
  "Delete an item from a DynamoDB table by its hash key."
  ([table {hash_key :hash_key range_key :range_key}]
    (debug "delete-item: " table " hash: " hash_key " range: " range_key)
    (.deleteItem
      *ddb_client*
      (DeleteItemRequest. table (create-KeyObject hash_key range_key)))))






;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;read functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn create-condition
  "Create a Condition object
  operators can be: EQ, NE, IN, LE, LT, GE, GT,
  BETWEEN, NOT_NULL, NULL, CONTAINS, NOT_CONTAINS, BEGINS_WITH"
  [operator & attribute_values]
  (doto (Condition.)
    (.setComparisonOperator (name operator))
    (.setAttributeValueList (map create-attribute-value attribute_values))))


(defn- to-map
  "Turns an dynamo item into a clojure map"
  [i]
  (if i
    (->> i
      (map #(hash-map (keyword (key %)) (get-value (val %))))
      (reduce merge {})
      (into (sorted-map)))))

(defn get-item
  "Retrieve an item from a DynamoDB table by its hash key."
  [ table {hash_key :hash_key range_key :range_key}]
  (to-map
    (.getItem
      (.getItem
        *ddb_client*
        (doto (GetItemRequest.)
          (.setTableName table)
          (.setKey (create-KeyObject hash_key range_key)))))))

(defn update-result->map
  [update-result]
  (to-map (.getAttributes update-result)))


(defn- create-query-request
  "Create the query request object"
  [table hash_key {range_condition :range_condition
                   range_start_key :range_start_key
                   limit :limit}]
  (-> (QueryRequest.)
    (doto
      (.setTableName table)
      (.setHashKeyValue (create-attribute-value hash_key)))
    (doto-if limit
      (.setLimit (Integer. limit)))
    (doto-if range_condition
      (.setRangeKeyCondition (apply create-condition range_condition)))
    (doto-if range_start_key
      (.setExclusiveStartKey (create-KeyObject hash_key range_start_key)))))


(def ^{:dynamic true} *query_paging_limit* 1000)
(defn query
  "Querying continues through the pages until:
  limit is hit or there are no more items
  this is to work around amazon's page limits
  query_params looks like: {:range_condition range_condition
                            :range_start_key range_start_key
                            :limit limit}"
  ([table hash_key query_params]
    (query table hash_key query_params []))
  ([table hash_key query_params items]
    (let [query_request (create-query-request table hash_key (assoc query_params :limit *query_paging_limit*))
          result (.query *ddb_client* query_request)
          limit (or (:limit query_params) (Integer/MAX_VALUE))
          last_evaluated_key (.getLastEvaluatedKey result)
          new_range_start_key (if last_evaluated_key (get-value (.getRangeKeyElement last_evaluated_key)))
          new_items (map to-map (.getItems result))
          items (into items new_items)
          ]
    ;;recurse until it either runs out of items or limit is reached
      (if (or (<= limit (count items))
            (nil? new_range_start_key))
        (vec (take limit items))
        (query table hash_key
          (assoc query_params :range_start_key new_range_start_key)
          items)))))

(defn create-scan-filter [m]
  (if m
    (throw (Exception. "Scan filter not implemented yet"))))

(defn- prepare-attribute-list
  "Converts keywords to strings, handles nils"
  [attributes]
  (->> (or attributes [])
    (map name)
    (vec)))

(def ^{:dynamic true} *scan_paging_limit* 1000)

(defn- create-scan-request
  "Create the scan request object"
  [table {:keys [attributes_to_get last-key conditions]}]
  (let [request (-> (ScanRequest.)
                    (.withTableName table)
                    (.withAttributesToGet (prepare-attribute-list attributes_to_get))
                    (.withExclusiveStartKey (create-KeyObject (:hash_key last-key) (:range_key last-key)))
                    (.withLimit (int *scan_paging_limit*)))]
    (if (nil? conditions) request (.withScanFilter request conditions))))

(defn scan
  "Do a scan request 1 or more scan requests to return full set for given params"
  [table {:keys [limit]
          :or {limit Integer/MAX_VALUE}
          :as options}
   dynamo-client]
    (loop [retval {:items []}
           options (merge {:pages -1} options)]
;      (debug items)
      (let [scan_request (create-scan-request table options)
            result (.scan dynamo-client scan_request)
            retval (assoc retval :items (into (:items retval) (map to-map (.getItems result)))
                          :last-key (KeyObject->key (.getLastEvaluatedKey result)))]
        (if
            (or (<= limit (count (:items retval)))
                (nil? (:last-key retval))
                (<= 0 (:pages options) 1))
          (if (nil? (:last-key retval))
            (dissoc retval :last-key)
            retval)
          (recur retval
                 (assoc options :last-key (:last-key retval)
                        :pages (dec (:pages options))))))))

(defn lazy-scan
  "Returns a lazy sequence of all the entries in the dynamo table after applying 'conditions'"
  [table options dynamo-client]
  (let [{:keys [conditions last-key attributes_to_get]} options
        result (scan table {:attributes_to_get attributes_to_get :conditions conditions :pages 1 :last-key last-key} dynamo-client)]
    (if-let [last-key (:last-key result)]
      (concat (:items result) (lazy-seq (lazy-scan table (assoc options :last-key last-key) dynamo-client)))
      (:items result))))


(defn count-items
  "Do a scan request 1 or more scan requests to return full set for given params"
  ([table]
     (count-items table nil))
  ([table last-key]
     (let [scan_request (-> (ScanRequest.)
                            (.withTableName table)
                            (.withCount true))
           scan_request (if (nil? last-key) scan_request (.withExclusiveStartKey scan_request (create-KeyObject (:hash_key last-key) (:range_key last-key))))
           result (.scan *ddb_client* scan_request)
           result-count (.getCount result)]
       (if (nil? (.getLastEvaluatedKey result))
         result-count
         (+ result-count (count-items table (KeyObject->key (.getLastEvaluatedKey result))))))))
