(defproject onix "0.34-SNAPSHOT"
  :description "Onix service"

  :dependencies [[amazonica "0.2.28" :exclusions [com.fasterxml.jackson.core/jackson-annotations]]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-time "0.8.0"]
                 [com.taoensso/faraday "1.5.0"]
                 [compojure "1.2.1"]
                 [environ "1.0.0"]
                 [joda-time "2.5"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.7"]
                 [net.logstash.logback/logstash-logback-encoder "3.3"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.memoize "0.5.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ring-middleware-format "0.4.0"]]

  :exclusions [commons-logging
               joda-time
               log4j
               org.clojure/clojure]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[lein-environ "1.0.0"]
            [lein-release "1.0.5"]
            [lein-ring "0.8.13"]]

  :env {:auto-reload true
        :aws-access-key nil
        :aws-secret-key nil
        :aws-http-proxy-host "nokes.nokia.com"
        :aws-http-proxy-port 8080
        :dynamo-endpoint "http://dynamodb.eu-west-1.amazonaws.com"
        :environment-name "dev"
        :graphite-enabled false
        :graphite-host "carbon.brislabs.com"
        :graphite-port 2003
        :graphite-post-interval-seconds 60
        :logging-consolethreshold "off"
        :logging-filethreshold "info"
        :logging-level "info"
        :logging-path "/tmp"
        :logging-stashthreshold "off"
        :poke-role-arn "arn:aws:iam::513894612423:role/onix"
        :production false
        :requestlog-enabled false
        :requestlog-retainhours 24
        :service-name "onix"
        :service-port 8080
        :shutdown-timeout-millis 5000
        :start-timeout-seconds 120
        :threads 254}

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :ring {:handler onix.web/app
         :main onix.setup
         :port ~(Integer/valueOf (get (System/getenv) "SERVICE_PORT" "8080"))
         :init onix.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :uberjar-name "onix.jar"

  :rpm {:name "onix"
        :summary "RPM for Onix service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs"]
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
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/onix"
                                        :destination "onix"}]}}]}

  :main onix.setup)
