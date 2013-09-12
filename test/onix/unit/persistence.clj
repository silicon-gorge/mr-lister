(ns onix.unit.persistence
  (:require [onix.persistence :as persistence]
            [onix.dynamolib :as dynamo])
  (:use midje.sweet))

(fact-group :unit

            (facts "About getting an application from store"

                   (fact "Fetching an app which does exist gets the app including its metadata"
                         (persistence/get-application "dummy") => {:name "dummy"
                                                                   :metadata {:size "big" :colour "red"}}
                         (provided
                           (dynamo/get-item persistence/applications-table {:hash_key "dummy"}) => {:name "dummy"
                                                                                                    :metadata "{\"size\":\"big\",\"colour\":\"red\"}"}))

                   (fact "Fetching a app which does not exist returns nil"
                         (persistence/get-application "dummy") => nil
                         (provided
                           (dynamo/get-item persistence/applications-table {:hash_key "dummy"}) => nil)))

            (facts "About updating an existing application"

                   (fact "Adding a metadata item to an existing app adds the item to the stored application."
                         (persistence/update-application-metadata "dummy" "key" "value") => {:key "value"}
                         (provided
                           (persistence/get-application "dummy") => {:name "dummy"
                                                                     :metadata {:size "big" :colour "red"}}
                           (persistence/create-or-update-application {:name "dummy"
                                                            :metadata "{\"key\":\"value\",\"size\":\"big\",\"colour\":\"red\"}"})
                           => anything))

                   (fact "Updating a metadata item in an existing app overwrites the previous value"
                         (persistence/update-application-metadata "dummy" "colour" "blue") => {:colour "blue"}
                         (provided
                           (persistence/get-application "dummy") => {:name "dummy"
                                                                     :metadata {:size "big" :colour "red"}}
                           (persistence/create-or-update-application {:name "dummy"
                                                            :metadata "{\"size\":\"big\",\"colour\":\"blue\"}"})
                           => anything))

                   (fact "Updating a metadata item in an app which doesn't exist returns nil"
                         (persistence/update-application-metadata "dummy" "key" "value") => nil
                         (provided
                           (dynamo/get-item persistence/applications-table {:hash_key "dummy"}) => nil)))

            (facts "About getting an individual metadata item from an application."

                   (fact "Requesting a metadata item which exists on an existing application succeeds."
                         (persistence/get-application-metadata-item "dummy" "colour") => {:colour "red"}
                         (provided
                           (dynamo/get-item persistence/applications-table {:hash_key "dummy"}) => {:name "dummy"
                                                                                                    :metadata "{\"size\":\"big\",\"colour\":\"red\"}"}))

                   (fact "Requesting a metadata item which does not exist on an existing application returns nil."
                         (persistence/get-application-metadata-item "dummy" "key") => nil
                         (provided
                           (dynamo/get-item persistence/applications-table {:hash_key "dummy"}) => {:name "dummy"
                                                                                                    :metadata "{\"size\":\"big\",\"colour\":\"red\"}"}))

                   (fact "Requesting a metadata item on an application which doesn't exist returns nil."
                         (persistence/get-application-metadata-item "dummy" "key") => nil
                         (provided
                          (dynamo/get-item persistence/applications-table {:hash_key "dummy"}) => nil)))

            (facts "About delete an individual metadatya item for an application"

                   (fact "Deleting a metadata item that doesn't exist returns nil"
                         (persistence/delete-application-metadata-item "app" "key") => nil
                         (provided
                          (persistence/get-application "app") => {:name "app"
                                                                  :metadata "{\"anotherkey\":\"val\",\"anotherkey2\":\"val2\"}"}))

                   (fact "Deleting a metadata item where the application doesn't exist returns nil"
                         (persistence/delete-application-metadata-item "app" "key") => nil
                         (provided
                          (persistence/get-application "app") => nil))

                   (fact "Deleting a metadata item where the application doesn't have any metadata at all returns nil"
                         (persistence/delete-application-metadata-item "app" "key") => nil
                         (provided
                          (persistence/get-application "app") => {:name "app"}))

                   (fact "Deleting a metadata item is successful"
                         (persistence/delete-application-metadata-item "app" "key") =not=> nil
                         (provided
                          (persistence/get-application "app") => {:name "app"
                                                                  :metadata {:key "value"}}
                          (persistence/create-application {:name "app"
                                                           :metadata "{}"}) => "something"))))