(defproject com.workframe/catchpocket "0.5.0-SNAPSHOT"
  :description "datomic-to-lacinia schema extractor"
  :url "https://github.com/workframers/catchpocket"
  :pedantic? :warn
  :license {:name "EPL"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.cli "0.3.5"]
                 [com.workframe/stillsuit "0.7.0"]
                 [fipp "0.6.12"]
                 [zprint "0.4.7"]
                 [funcool/cuerdas "2.0.5"]
                 [com.datomic/datomic-pro "0.9.5656" :scope "provided"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]

  :min-lein-version "2.8.1"

  :source-paths ["src"]

  :main catchpocket.main

  :aot [catchpocket.main]

  :plugins [[s3-wagon-private "1.3.1" :exclusions [commons-logging]]]

  :repositories [["workframe-private"
                  {:url           "s3p://deployment.workframe.com/maven/releases/"
                   :no-auth       true
                   :sign-releases false}]]

  :test-selectors {:watch :watch}

  :codox {:metadata   {:doc/format :markdown}
          :themes     [:rdash]
          :source-uri "https://github.com/workframers/catchpocket/blob/master/{filepath}#L{line}"}

  :asciidoctor {:sources "doc/*.adoc"
                :format  :html5
                :to-dir  "target/manual"}

  :aliases {"refresh" ["with-profile" "+ultra" "test-refresh"]
            "preview" ["with-profile" "+test" "run" "-m" "catchpocket.preview"]}

  :profiles {:dev   {:plugins      [[s3-wagon-private "1.3.1" :exclusions [commons-logging]]
                                    [jonase/eastwood "0.2.5"]
                                    [com.jakemccrary/lein-test-refresh "0.22.0"]
                                    [lein-cloverage "1.0.10"]
                                    [lein-codox "0.10.3"]
                                    [lein-shell "0.5.0"]
                                    [lein-asciidoctor "0.1.14" :exclusions [org.slf4j/slf4j-api]]
                                    [lein-ancient "0.6.15"
                                     :exclusions [com.fasterxml.jackson.core/jackson-annotations
                                                  com.fasterxml.jackson.core/jackson-databind
                                                  com.fasterxml.jackson.core/jackson-core]]]
                     :dependencies [[codox-theme-rdash "0.1.2"]
                                    [com.walmartlabs/lacinia-pedestal "0.7.0"]
                                    [io.forward/yaml "1.0.7" :exclusions [org.flatland/ordered]]]}
                    :ultra {:plugins [[venantius/ultra "0.5.2" :exclusions [org.clojure/clojure]]]}
             :test  {:resource-paths ["test/resources"]}}

  :release-tasks [;; Make sure we're up to date
                  ["vcs" "assert-committed"]
                  ["shell" "git" "checkout" "develop"]
                  ["shell" "git" "pull"]
                  ["shell" "git" "checkout" "master"]
                  ["shell" "git" "pull"]
                  ;; Merge develop into master
                  ["shell" "git" "merge" "develop"]
                  ;; Update version to non-snapshot version, commit change to master, tag
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "catchpocket-" "--no-sign"]
                  ;; Merge master back into develop (we'll now have the non-SNAPSHOT version)
                  ["shell" "git" "checkout" "develop"]
                  ["shell" "git" "merge" "master"]
                  ;; Bump up SNAPSHOT version in develop and commit
                  ["change" "version" "leiningen.release/bump-version" "minor"]
                  ["vcs" "commit"]
                  ;; All done
                  ["shell" "echo"]
                  ["shell" "echo" "Release tagged in master; develop bumped to ${:version}."]
                  ["shell" "echo" "To push it, run 'git push origin develop master --tags'"]])
