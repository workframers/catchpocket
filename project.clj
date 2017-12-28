(defproject com.workframe/harvester "0.1.0-SNAPSHOT"
  :description "datomic-to-lacinia tool"
  :url "https://github.com/workframers/harvester"
  :pedantic? :warn
  :license {:name "MIT"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.3.465"]
                 [org.clojure/tools.cli "0.3.5"]
                 [mvxcvi/puget "1.0.2"]
                 [fipp "0.6.12"]
                 [io.aviso/pretty "0.1.34"]
                 [com.datomic/datomic-pro "0.9.5656"]
                 [com.datomic/clj-client "0.8.606"]]

  ; :plugins [[lein-ancient "1.1.4"]]

  :min-lein-version "2.8.1"

  :source-paths ["src"]

  :main harvester.main

  :profiles {:dev {:plugins [[lein-ancient "0.6.15"]]}})
