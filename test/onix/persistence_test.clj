(ns onix.persistence-test
  (:require [amazonica.aws.securitytoken :as sts]
            [midje.sweet :refer :all]
            [onix.persistence :refer :all]
            [taoensso.faraday :as far]))

(fact "that creating credentials removes empty values"
      (create-credentials {:access-key nil :something-else :value}) =not=> (contains {:access-key nil}))

(fact "that creating credentials treats a nil port string as nil"
      (create-credentials {}) =not=> (contains {:proxy-port anything})
      (provided
       (raw-proxy-port) => nil))

(fact "that creating credentials treats an empty port string as nil"
      (create-credentials {}) =not=> (contains {:proxy-port anything})
      (provided
       (raw-proxy-port) => ""))

(fact "that creating credentials handles an actual port string correctly"
      (create-credentials {}) => (contains {:proxy-port 123})
      (provided
       (raw-proxy-port) => "123"))

(fact "that creating assumed credentials does what we want"
      (create-assumed-credentials) => (contains {:access-key ..access-key..
                                                 :secret-key ..secret-key..})
      (provided
       (sts/assume-role {:role-arn "arn:aws:iam::513894612423:role/onix" :role-session-name "onix"}) => {:credentials {:access-key ..access-key..
                                                                                                                       :secret-key ..secret-key..}}))

(fact "that creating standard credentials does what we want"
      (create-standard-credentials) => anything
      (provided
       (sts/assume-role anything) => anything :times 0))

(fact "that creating an application which already exists returns a falsey value"
      (create-application {:name "dummy"}) => falsey
      (provided
       (get-application "dummy") => {:name "dummy"}))

(fact "that creating an application which doesn't exist creates it"
      (create-application {:name "application"}) => {:name "application"}
      (provided
       (get-application "application") => nil
       (far/put-item anything applications-table {:name "application"}) => ..put-result..))

(fact "that listing applications does what we expect"
      (list-applications) => ["app1" "app2" "app3"]
      (provided
       (far/scan anything applications-table {:return [:name]}) => [{:name "app1"} {:name "app2"} {:name "app3"}]))

(fact "that fetching an app which exists gets the application including its metadata"
      (get-application "dummy") => {:name "dummy"
                                    :metadata {:size "big" :colour "red"}}
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => {:name "dummy"
                                                                      :metadata "{\"size\":\"big\",\"colour\":\"red\"}"}))
(fact "that fetching a app which does not exist returns nil"
      (get-application "dummy") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => nil))

(fact "that adding a metadata item to an existing app adds the item to the stored application."
      (update-application-metadata "dummy" "key" "value") => {:key "value"}
      (provided
       (get-application "dummy") => {:name "dummy"
                                     :metadata {:size "big" :colour "red"}}
       (upsert-application {:name "dummy"
                            :metadata "{\"key\":\"value\",\"size\":\"big\",\"colour\":\"red\"}"}) => anything))

(fact "that updating a metadata item in an existing app overwrites the previous value"
      (update-application-metadata "dummy" "colour" "blue") => {:colour "blue"}
      (provided
       (get-application "dummy") => {:name "dummy"
                                     :metadata {:size "big" :colour "red"}}
       (upsert-application {:name "dummy"
                            :metadata "{\"size\":\"big\",\"colour\":\"blue\"}"}) => anything))

(fact "that updating a metadata item in an app which doesn't exist returns nil"
      (update-application-metadata "dummy" "key" "value") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => nil))

(fact "that requesting a metadata item which exists on an existing application succeeds."
      (get-application-metadata-item "dummy" "colour") => {:colour "red"}
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => {:name "dummy"
                                                                      :metadata "{\"size\":\"big\",\"colour\":\"red\"}"}))

(fact "that requesting a metadata item which does not exist on an existing application returns nil."
      (get-application-metadata-item "dummy" "key") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => {:name "dummy"
                                                                      :metadata "{\"size\":\"big\",\"colour\":\"red\"}"}))

(fact "that requesting a metadata item on an application which doesn't exist returns nil."
      (get-application-metadata-item "dummy" "key") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => nil))

(fact "that deleting a metadata item that doesn't exist returns nil"
      (delete-application-metadata-item "app" "key") => nil
      (provided
       (get-application "app") => {:name "app"
                                   :metadata "{\"anotherkey\":\"val\",\"anotherkey2\":\"val2\"}"}))

(fact "that deleting a metadata item where the application doesn't exist returns nil"
      (delete-application-metadata-item "app" "key") => nil
      (provided
       (get-application "app") => nil))

(fact "that deleting a metadata item where the application doesn't have any metadata at all returns nil"
      (delete-application-metadata-item "app" "key") => nil
      (provided
       (get-application "app") => {:name "app"}))

(fact "that deleting a metadata item is successful"
      (delete-application-metadata-item "app" "key") =not=> nil
      (provided
       (get-application "app") => {:name "app"
                                   :metadata {:key "value"}}
       (upsert-application {:name "app"
                            :metadata "{}"}) => "something"))

(fact "that our trusty healthcheck is truthy when things are good"
      (dynamo-health-check) => truthy
      (provided
       (far/describe-table anything applications-table) => {}))

(fact "that our delightful healthcheck is falsey when things are bad"
      (dynamo-health-check) => falsey
      (provided
       (far/describe-table anything applications-table) =throws=> (ex-info "Boom" {})))
