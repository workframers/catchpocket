(defproject com.workframe/harvester "0.1.0-SNAPSHOT"
  :description "datomic-to-lacinia tool"
  :url "https://github.com/workframers/harvester"
  :pedantic? :warn
  :license {:name "MIT"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [fipp "0.6.12"]
                 [io.aviso/pretty "0.1.34"]
                 [com.datomic/datomic-pro "0.9.5656"]]

  ; :plugins [[lein-ancient "1.1.4"]]

  :min-lein-version "2.8.1"

  :source-paths ["src"]

  :test-paths ["test/clj" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js"]

  :uberjar-name "choam.jar"

  :main harvester.main

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (go) and
  ;; (browser-repl) live.
  ;:repl-options {:init-ns user}

  :profiles {:dev {:plugins [[lein-ancient "0.6.15"]]}})
