(defproject onix "0.26-SNAPSHOT"
  :description "Onix service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Onix"

;;; USEFUL COMMANDS

;;; lein start                       Starts up ring in auto-reload mode on port SERVICE_PORT (default 3000). Alias for: lein ring server-headless.
;;; lein autotest                    Uses midje autotest across all classes and tests. Alias for: lein midje :autotest src/ingestion_store test/ingestion_store.
;;; lein autounit                    Uses midje autotest across all classes and unit tests. Alias for: lein midje :autotest src/ingestion_store test/ingestion_store/unit.
;;; lein start-integration           Starts in integration mode (pointing at real external services)** Alias for: lein with-profile integration ring server-headless.
;;; lein midje                       Runs all tests
;;; lein acceptance                  Runs the acceptance tests (useful for CI server). Alias for: lein midje :filter acceptance.
;;; lein midje :filter unit          Run unit tests

  :dependencies [[ch.qos.logback/logback-classic "1.1.1"]
                 [cheshire "5.3.1"]
                 [clj-http "0.7.9"]
                 [clj-time "0.6.0"]
                 [com.amazonaws/aws-java-sdk "1.7.2"]
                 [com.ovi.common.logging/logback-appender "0.0.45"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.23"]
                 [com.yammer.metrics/metrics-logback "2.2.0"]
                 [compojure "1.1.6" :exclusions [javax.servlet/servlet-api]]
                 [environ "0.4.0"]
                 [metrics-clojure "1.0.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [nokia/instrumented-ring-jetty-adapter "0.1.8"]
                 [nokia/ring-utils "1.2.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.eclipse.jetty/jetty-server "8.1.14.v20131031"]
                 [org.slf4j/jcl-over-slf4j "1.7.6"]
                 [org.slf4j/jul-to-slf4j "1.7.6"]
                 [org.slf4j/log4j-over-slf4j "1.7.6"]
                 [org.slf4j/slf4j-api "1.7.6"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.3.2"]]

  :exclusions [commons-logging
               log4j]

  :profiles {:dev {:dependencies [[midje "1.6.2"]
                                  [rest-cljer "0.1.11" :exclusions [javax.servlet/servlet-api
                                                                    org.eclipse.jetty.orbit/javax.servlet]]]
                   :plugins [[lein-rpm "0.0.5"]
                             [lein-midje "3.1.3"]
                             [jonase/kibit "0.0.8"]]}}

  :plugins [[lein-ring "0.8.10"]
            [lein-environ "0.4.0"]
            [lein-release "1.0.73"]]

  ;; development token values (settings correct for integrating with our AWS entdev environment).
  :env {:aws-access-key nil ; for local dev, you need to have your own AWS credentials set up.
        :aws-secret-key nil
        :aws-http-proxy-host "nokes.nokia.com"
        :aws-http-proxy-port "8080"
        :dynamo-endpoint "http://dynamodb.eu-west-1.amazonaws.com"

        :environment-name "Dev"
        :service-name "onix"
        :service-port "8080"
        :service-url "http://localhost:%s/1.x"
        :restdriver-port "8081"
        :environment-entertainment-graphite-host "graphite.brislabs.com"
        :environment-entertainment-graphite-port "8080"
        :service-graphite-post-interval "1"
        :service-graphite-post-unit "MINUTES"
        :service-graphite-enabled "ENABLED"
        :service-production "false"
        :service-poke-role-arn "arn:aws:iam::513894612423:role/onix"}

  :aliases { "autotest" ["midje" ":autotest" "src/onix" "test/onix" ":filter" "-slow"]
             "autounit" ["midje" ":autotest" "src/onix" "test/onix/unit"]
             "acceptance" ["midje" ":filter" "acceptance"]
             "start" ["ring" "server-headless"]
             "start-integration" ["with-profile" "integration" "ring" "server-headless"]}

  :lein-release {:release-tasks [:clean :uberjar :pom :rpm]
                 :clojars-url "clojars@clojars.brislabs.com:"}

  :ring {:handler onix.web/app
         :main onix.setup
         :port ~(Integer.  (get (System/getenv) "SERVICE_PORT" "8080"))
         :init onix.setup/setup
         :browser-uri "/1.x/status"}

  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}

  :uberjar-name "onix.jar"

  :rpm {:name "onix"
        :summary "RPM for Onix service"
        :copyright "Nokia 2013"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.6.0_31-fcs"]
        :mappings [{:directory "/usr/local/onix"
                    :filemode "444"
                    :username "onix"
                    :groupname "onix"
                    :sources {:source [{:location "target/onix.jar"}]}}
                   {:directory "/usr/local/onix/bin"
                    :filemode "744"
                    :username "onix"
                    :groupname "onix"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/usr/local/deployment/onix/bin"
                    :filemode "744"
                    :sources {:source [{:location "scripts/dmt"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "744"
                    :username "onix"
                    :groupname "onix"
                    :sources {:source [{:location "scripts/service/onix"}]}}]}

  :main onix.setup)
