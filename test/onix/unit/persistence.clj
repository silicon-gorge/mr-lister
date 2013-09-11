(ns onix.unit.persistence
  (:require [onix.persistence :as persistence])
  
  (:use midje.sweet)
  
  )

(fact-group :unit
            
            (facts "About getting an application from store"
                   
                   (fact "Fetching a app which does not exist returns nil")
                   
                   (future-fact "Fetching an app which does exist gets the app including its metadata")
                   
                   )
            
            (facts "About updating an existing application"
                   
                   (future-fact "Adding a metadata item to an existing app completes correctly")
                   
                   (future-fact "Updating a metadata item in an existing app overwrites the previous value")
                   
                   (future-fact "Updating a metadata item in an app which doesn't exist returns nil")
                   
                   )
            
            )

