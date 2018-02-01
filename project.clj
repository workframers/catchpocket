(defproject com.workframe/catchpocket "0.1.0-SNAPSHOT"
  :description "datomic-to-lacinia schema extractor"
  :url "https://github.com/workframers/catchpocket"
  :pedantic? :warn
  :license {:name "EPL"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.cli "0.3.5"]
                 [fipp "0.6.12"]
                 [funcool/cuerdas "2.0.5"]
                 [com.datomic/datomic-pro "0.9.5656" :scope "provided"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.apache.logging.log4j/log4j-core "2.10.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.10.0"]]

  :min-lein-version "2.8.1"

  :source-paths ["src"]

  :main catchpocket.main

  :aot [catchpocket.main]

  :plugins [[s3-wagon-private "1.3.1" :exclusions [commons-logging]]]

  :repositories [["workframe-private" {:url     "s3p://deployment.workframe.com/maven/releases/"
                                       :no-auth true}]]

  :test-selectors {:watch :watch}

  :codox {:metadata   {:doc/format :markdown}
          :themes     [:rdash]
          :source-uri "https://github.com/workframers/stillsuit/blob/develop/{filepath}#L{line}"}

  :asciidoctor {:sources "doc/*.adoc"
                :format  :html5
                :to-dir  "target/manual"}

  :profiles {:dev  {:plugins      [[s3-wagon-private "1.3.1" :exclusions [commons-logging]]
                                   [jonase/eastwood "0.2.5"]
                                   [com.jakemccrary/lein-test-refresh "0.22.0"]
                                   [lein-cloverage "1.0.10"]
                                   [lein-codox "0.10.3"]
                                   [lein-asciidoctor "0.1.14" :exclusions [org.slf4j/slf4j-api]]
                                   [lein-ancient "0.6.15"
                                    :exclusions [com.fasterxml.jackson.core/jackson-annotations
                                                 com.fasterxml.jackson.core/jackson-databind
                                                 com.fasterxml.jackson.core/jackson-core]]]
                    :dependencies [[codox-theme-rdash "0.1.2"]]}

             :test {:resource-paths ["test/resources"]}})
