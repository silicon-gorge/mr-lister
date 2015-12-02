(ns lister.persistence-test
  (:require [amazonica.aws.securitytoken :as sts]
            [environ.core :refer [env]]
            [lister.persistence :refer :all]
            [midje.sweet :refer :all]
            [ninjakoala.instance-metadata :as im]
            [taoensso.faraday :as far]))

(background
 (im/instance-identity) => {:account-id "master-account-id"})

(fact "that creating configuration removes empty values"
      (create-configuration {:something nil :something-else :value}) =not=> (contains {:something nil}))

(fact "that creating assumed credentials does what we want"
      (create-assumed-credentials) => (contains {:creds anything})
      (provided
       (sts/assume-role {:role-arn "arn:aws:iam::master-account-id:role/lister" :role-session-name "lister"}) => {:credentials {:access-key "access-key"
                                                                                                                                :secret-key "secret-key"
                                                                                                                                :session-token "session-token"}}))

(fact "that creating standard credentials does what we want"
      (create-standard-credentials) => anything
      (provided
       (sts/assume-role anything) => anything :times 0))

(fact "that creating credentials uses assumed credentials when we're not running in the master account"
      (create-creds) => ..assumed-credentials..
      (provided
       (im/instance-identity) => {:account-id "not-master-account-id"}
       (create-assumed-credentials) => ..assumed-credentials..))

(fact "that creating an application which already exists returns a falsey value"
      (create-application {:name "dummy"}) => falsey
      (provided
       (get-application "dummy") => {:name "dummy"}))

(fact "that creating an application which doesn't exist creates it"
      (create-application {:name "application"}) => {:name "application"}
      (provided
       (im/instance-identity) => {:account-id "master-account-id"}
       (get-application "application") => nil
       (far/put-item anything applications-table {:name "application"}) => ..put-result..))

(fact "that listing applications does what we expect"
      (list-applications) => ["app1" "app2" "app3"]
      (provided
       (far/scan anything applications-table {:return [:name]}) => [{:name "app1"} {:name "app2"} {:name "app3"}]))

(fact "that fetching an application which exists gets the application all properties works"
      (get-application "dummy") => {:name "dummy"
                                    :metadata {:size "big"
                                               :colour "red"}}
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => {:name "dummy"
                                                                      :size "big"
                                                                      :colour "red"}))

(fact "that fetching an application which exists but has extra no properties works"
      (get-application "dummy") => {:name "dummy"}
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => {:name "dummy"}))

(fact "that fetching an application which does not exist returns nil"
      (get-application "dummy") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => nil))

(fact "that adding a metadata item to an existing app adds the item to the stored application."
      (update-application-metadata "dummy" "key" "value") => {:value "value"}
      (provided
       (get-application "dummy") => {:name "dummy"
                                     :size "big"
                                     :colour "red"}
       (upsert-application {:name "dummy"
                            :size "big"
                            :colour "red"
                            :key "value"}) => anything))

(fact "that updating metadata for an existing app overwrites the previous value"
      (update-application-metadata "dummy" "colour" "blue") => {:value "blue"}
      (provided
       (get-application "dummy") => {:name "dummy"
                                     :size "big"
                                     :colour "red"}
       (upsert-application {:name "dummy"
                            :size "big"
                            :colour "blue"}) => anything))

(fact "that updating metadata item for an app which doesn't exist returns nil"
      (update-application-metadata "dummy" "key" "value") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"}) => nil))

(fact "that requesting a metadata item which exists on an existing application succeeds."
      (get-application-metadata-item "dummy" "colour") => {:value "red"}
      (provided
       (far/get-item anything applications-table {:name "dummy"} {:return [:colour]}) => {:colour "red"}))

(fact "that requesting a metadata item which does not exist on an existing application returns nil."
      (get-application-metadata-item "dummy" "key") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"} {:return [:key]}) => nil))

(fact "that requesting a metadata item on an application which doesn't exist returns nil."
      (get-application-metadata-item "dummy" "key") => nil
      (provided
       (far/get-item anything applications-table {:name "dummy"} {:return [:key]}) => nil))

(fact "that deleting a metadata item that doesn't exist returns nil"
      (delete-application-metadata-item "app" "key") => nil
      (provided
       (get-application "app") => {:name "app"
                                   :anotherkey "val"
                                   :anotherkey2 "val2"}))

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
                                   :key "value"}
       (upsert-application {:name "app"}) => "something"))

(fact "that our trusty applications table healthcheck is true when things are good"
      (applications-table-healthcheck) => true
      (provided
       (far/describe-table anything applications-table) => {}))

(fact "that our shiny applications table healthcheck is false when nothing comes back from the describe-table call"
      (applications-table-healthcheck) => false
      (provided
       (far/describe-table anything applications-table) => nil))

(fact "that our delightful applications table healthcheck is false when things are bad"
      (applications-table-healthcheck) => false
      (provided
       (far/describe-table anything applications-table) =throws=> (ex-info "Boom" {})))

(fact "that listing environments does what we expect"
      (list-environments) => ["env1" "env2" "env3"]
      (provided
       (far/scan anything environments-table {:return [:name]}) => [{:name "env1"} {:name "env2"} {:name "env3"}]))

(fact "that fetching an environment which exists gets the environment including its metadata"
      (get-environment "dummy") => {:name "dummy"
                                    :metadata {:size "big"
                                               :colour "red"}}
      (provided
       (far/get-item anything environments-table {:name "dummy"}) => {:name "dummy"
                                                                      :size "big"
                                                                      :colour "red"}))

(fact "that fetching an environment which exists but has no metadata works"
      (get-environment "dummy") => {:name "dummy"
                                    :metadata {}}
      (provided
       (far/get-item anything environments-table {:name "dummy"}) => {:name "dummy"}))

(fact "that fetching an environment which does not exist returns nil"
      (get-environment "dummy") => nil
      (provided
       (far/get-item anything environments-table {:name "dummy"}) => nil))

(fact "that our eminent environments table healthcheck is true when things are good"
      (environments-table-healthcheck) => true
      (provided
       (far/describe-table anything environments-table) => {}))

(fact "that our brilliant environments table healthcheck is false when nothing comes back from the describe-table call"
      (environments-table-healthcheck) => false
      (provided
       (far/describe-table anything environments-table) => nil))

(fact "that our splendid environments table healthcheck is false when things are bad"
      (environments-table-healthcheck) => false
      (provided
       (far/describe-table anything environments-table) =throws=> (ex-info "Boom" {})))

(fact "that delete applcation calls delete-item. worst. test. evar."
      (delete-application ..application..) => nil
      (provided
       (far/delete-item anything applications-table {:name ..application..}) => nil))

(fact "that delete environment calls delete-item. also. worst. test. evar."
      (delete-environment ..environment..) => nil
      (provided
       (far/delete-item anything environments-table {:name ..environment..}) => nil))

(fact "that create environment creates a new environment with the associated account-id, returns the new environment"
      (create-environment ..environment.. "dev-id") => {:name ..environment..
                                                        :account-id "dev-id"}
      (provided
       (far/put-item anything environments-table (contains {:name ..environment..
                                                            :account-id "dev-id"})) => nil))
