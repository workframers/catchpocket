(defproject com.workframe/catchpocket "0.8.0-SNAPSHOT"
  :description "datomic-to-lacinia schema extractor"
  :url "https://github.com/workframers/catchpocket"
  :pedantic? :warn
  :license {:name "Apache 2.0"
            :url  "https://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.3.7"]
                 [com.workframe/stillsuit "0.12.0"]
                 [zprint "0.4.9"]
                 [funcool/cuerdas "2.0.5"]
                 [com.datomic/datomic-pro "0.9.5656" :scope "provided"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]

  :min-lein-version "2.8.1"

  :source-paths ["src"]

  :main catchpocket.main

  :aot [catchpocket.main]

  :plugins [[s3-wagon-private "1.3.2" :exclusions [commons-logging]]]

  :repositories [["workframe-private"
                  {:url           "s3p://deployment.workframe.com/maven/releases/"
                   :no-auth       true
                   :sign-releases false}]]

  :test-selectors {:watch :watch}

  :codox {:metadata   {:doc/format :markdown}
          :themes     [:rdash]
          :source-uri "https://github.com/workframers/catchpocket/blob/master/{filepath}#L{line}"}

  :asciidoctor [{:sources "doc/manual/*.adoc"
                 :format  :html5
                 :to-dir  "target/manual"}]

  :aliases {"refresh" ["with-profile" "+ultra" "test-refresh"]
            "preview" ["with-profile" "+test" "run" "-m" "catchpocket.preview"]}

  :profiles {:dev     {:plugins      [[jonase/eastwood "0.2.6"]
                                      [com.jakemccrary/lein-test-refresh "0.22.0"]
                                      [lein-cloverage "1.0.10"]
                                      [lein-codox "0.10.3"]
                                      [lein-shell "0.5.0"]]
                       :dependencies [[codox-theme-rdash "0.1.2"]
                                      [com.walmartlabs/lacinia-pedestal "0.8.0"
                                       :exclusions [com.walmartlabs/lacinia]]
                                      [io.forward/yaml "1.0.8" :exclusions [org.flatland/ordered]]]}
             :docs    {:plugins      [[lein-codox "0.10.3"]
                                      [lein-asciidoctor "0.1.16" :exclusions [org.slf4j/slf4j-api]]]
                       :dependencies [[codox-theme-rdash "0.1.2"]]}
             :ancient {:plugins [[lein-ancient "0.6.15"
                                  :exclusions [commons-logging
                                               org.apache.httpcomponents/httpclient
                                               com.fasterxml.jackson.core/jackson-annotations
                                               com.fasterxml.jackson.core/jackson-core
                                               com.fasterxml.jackson.core/jackson-databind]]]}
             :ultra   {:plugins [[venantius/ultra "0.5.2" :exclusions [org.clojure/clojure]]]}
             :test    {:resource-paths ["test/resources"]}}

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
