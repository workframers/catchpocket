(ns catchpocket.preview
  (:require [io.pedestal.http :as http]
            [clojure.tools.logging :as log]
            [datomic.api :as d]
            [com.walmartlabs.lacinia.pedestal :as lacinia]
            [catchpocket.lib.config :as cf]
            [io.pedestal.http :as http]
            [catchpocket.generate.core :as g]
            [stillsuit.core :as stillsuit]
            [zprint.core :as zp]))

(defn- usage! [& args]
  (when args
    (println (apply format args)))
  (println "Usage: lein preview path/to/CONFIG-FILE.edn")
  (System/exit 1))

(defn verbose-generate
  [conn config]
  (let [schema (g/generate conn config)]
    (println "Generated schema:\n")
    (zp/czprint schema 120 {:map {:comma? false
                                  :sort?  true
                                  :indent 0
                                  :key-depth-color [:red :yellow :cyan :green :blue :magenta]}})
    schema))

(defn -main
  [& args]
  (if-let [filename (first args)]
    (try
      (let [config      (cf/construct-config filename)
            datomic-uri (:catchpocket/datomic-uri config)
            connection  (d/connect datomic-uri)
            schema      (verbose-generate connection config)
            decorated   (stillsuit/decorate #:stillsuit{:schema     schema
                                                        :connection connection
                                                        :config     {}})
            smap        (lacinia/service-map (:stillsuit/schema decorated)
                                             {:graphiql    true
                                              :app-context (:stillsuit/app-context decorated)})]
        (-> smap
            http/create-server
            http/start)
        (log/infof "Ready. Serving graphiql at http://localhost:8888/"))
      (catch Exception e
        (log/errorf e "Error running preview: %s" (.getMessage e))
        (System/exit 1)))
    (usage!)))

