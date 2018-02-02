(ns catchpocket.test-util
  (:require [clojure.test :as test]
            [catchpocket.lib.util :as util]                 ; TODO: move to stillsuit
            [datomic.api :as d]
            [clojure.tools.logging :as log]
            [catchpocket.generate.core :as g]
            [clojure.java.io :as io]
            [catchpocket.lib.config :as cf]))

;; There's a bunch of redundancy between this and the stillsuit tests; we should probably break
;; them out into a third library

(def ^:private test-db-prefix "datomic:mem://stillsuit-test-")
(def ^:private all-db-names [:music])
(def ^:private db-store (atom {}))

(defn- db-uri [db-name]
  (str test-db-prefix (name db-name)))

(defn- provision-db
  [db-name]
  (let [uri  (db-uri db-name)
        path (format "resources/test-schemas/%s/datomic.edn" (name db-name))
        txes (util/load-edn (io/resource path))]
    (if-not (d/create-database uri)
      (log/errorf "Couldn't create database %s!" uri)
      (let [conn (d/connect uri)]
        (doseq [tx txes]
          @(d/transact conn tx))
        (log/debugf "Loaded %d transactions from %s to %s" (count txes) path uri)
        conn))))

(defn- setup-datomic []
  (doseq [db-name all-db-names
          :let [conn (provision-db db-name)]]
    (swap! db-store assoc db-name conn)))

(defn- teardown-datomic []
  (doseq [db-name all-db-names]
    ;(some-> @db-store (get db-name) (d/release))
    (d/delete-database (db-uri db-name))
    (log/debugf "Deleted database %s" db-name)
    (swap! db-store dissoc db-name)))

(defn datomic-fixture [test-fn]
  (setup-datomic)
  (test-fn)
  (teardown-datomic))

(defn- get-config
  [setup-name]
  (->> setup-name
       name
       (format "resources/test-schemas/%s/catchpocket.edn")
       io/resource
       util/load-edn))

(defn- get-connection [db-name]
  (get @db-store db-name))

(defn load-setup
  "Given a db-name which maps to a directory under test/resources/test-schemas, load up a
  bunch of sample edn data and queries and compile a schema. Return a map of the data which
  can be passed to (execute-query) for further testing."
  ([setup-name]
   (load-setup setup-name nil))
  ([setup-name override-options]
   (let [config (-> (get-config setup-name)
                    (assoc :catchpocket/datomic-uri (db-uri setup-name))
                    (util/deep-map-merge override-options))
         conn   (get-connection setup-name)]
     (g/generate conn config))))

(def once (test/join-fixtures [datomic-fixture]))

