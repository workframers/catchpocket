(ns catchpocket.test-util
  (:require [clojure.test :as test]
            [datomic.api :as d]
            [stillsuit.core :as stillsuit]
            [clojure.tools.logging :as log]
            [catchpocket.generate.core :as g]
            [com.walmartlabs.lacinia :as lacinia]
            [stillsuit.lib.util :as su]
            [clojure.java.io :as io]
            [clojure.walk :as walk]))

;; There's a bunch of redundancy between this and the stillsuit tests; we should probably break
;; them out into a third library

(def ^:private test-db-prefix "datomic:mem://catchpocket-test-")
(def ^:private all-db-names [:music :capitalize :enums :annotation :rainbow])
(def ^:private db-store (atom {}))

(defn- db-uri [db-name]
  (str test-db-prefix (name db-name)))

(defn- provision-db
  [db-name]
  (let [uri  (db-uri db-name)
        path (format "resources/test-schemas/%s/datomic.edn" (name db-name))
        txes (su/load-edn-resource path)]
    (if-not (d/create-database uri)
      (log/errorf "Couldn't create database %s!" uri)
      (let [conn (d/connect uri)]
        (doseq [tx txes]
          @(d/transact conn tx))
        (log/debugf "Loaded %d transactions from %s to %s"
                    (count txes) (format "%s/datomic.edn" (name db-name)) uri)
        conn))))

(defn- setup-datomic []
  (doseq [db-name all-db-names
          :let [conn (provision-db db-name)]]
    (swap! db-store assoc db-name conn)))

(defn- teardown-datomic []
  (doseq [db-name all-db-names]
    (d/delete-database (db-uri db-name))
    (log/debugf "Deleted database %s" db-name)
    (swap! db-store dissoc db-name)))

(defn datomic-fixture [test-fn]
  (setup-datomic)
  (test-fn)
  (teardown-datomic))

(defn- catchpocket-config
  [setup-name]
  (->> setup-name
       name
       (format "resources/test-schemas/%s/catchpocket.edn")
       su/load-edn-resource))

(defn- get-query-doc
  [db-name]
  (->> db-name
       name
       (format "resources/test-schemas/%s/queries.edn")
       su/load-edn-resource))

(defn- get-connection [db-name]
  (get @db-store db-name))

(defn generate-schema
  "Given a db-name which maps to a directory under test/resources/test-schemas, load up a
  test database and its associated config from . Return a map of the data which
  can be passed to (execute-query) for further testing."
  ([setup-name]
   (generate-schema setup-name nil))
  ([setup-name override-options]
   (let [config (-> (catchpocket-config setup-name)
                    (assoc :catchpocket/datomic-uri (db-uri setup-name))
                    (su/deep-map-merge override-options))
         conn   (get-connection setup-name)]
     (g/generate conn config))))

(defn stillsuit
  "Return a big map with a bunch of stillsuit stuff"
  ([setup-name schema]
   (stillsuit setup-name schema nil))
  ([setup-name schema resolver-map]
   (stillsuit setup-name schema resolver-map nil))
  ([setup-name schema resolver-map overrides]
   (let [cp-config (catchpocket-config setup-name)
         config    {}
         queries   (get-query-doc setup-name)
         decorated (stillsuit/decorate #:stillsuit{:schema     schema
                                                   :config     config
                                                   :connection (get-connection setup-name)
                                                   :resolvers  resolver-map})]
     (su/deep-map-merge {::context   (:stillsuit/app-context decorated)
                         ::cp-config cp-config
                         ::schema    (:stillsuit/schema decorated)
                         ::query-doc queries}
                        overrides))))

(defn stillsuit-query
  "Given a setup map as returned by (stillsuit), execute the query defined in the associated YAML"
  [{::keys [context schema query-doc]} query-name]
  (let [query (get-in query-doc [query-name :query])
        vars  (get-in query-doc [query-name :variables])]
    (test/is (some? query))
    (lacinia/execute schema query vars context)))

(defn approx-floats
  "This function is here to aid testing floating-point numbers. It walks the data structure provided
  and converts any floating-point numbers provided into BigDecimals with the given scale (default 3)."
  ([data]
   (approx-floats data 3))
  ([data ^Integer scale]
   (walk/postwalk (fn [item]
                    (if (float? item)
                      (-> item bigdec (.setScale scale java.math.RoundingMode/HALF_UP))
                      item))
                  data)))

(defn verify-queries!
  "Given a setup map returned by load-setup, run through every query in the queries.yaml file,
  executing each one and asserting that its output is identical to the expected output, if any."
  [setup]
  (test/testing "Verifying query response"
    (doseq [qname (-> setup ::query-doc keys sort)
            :let [expected (get-in setup [::query-doc qname :response])]
            :when [expected]]
      (test/testing (str qname)
        (let [result     (stillsuit-query setup qname)
              simplified (su/simplify result)]
          (test/is (= (approx-floats expected)
                      (approx-floats simplified))))))))

(def once (test/join-fixtures [datomic-fixture]))

