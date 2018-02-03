(ns catchpocket.test.core
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as test-util]
            [clojure.tools.logging :as log]
            [datomic.api :as d]))

(use-fixtures :once test-util/once)

(deftest test-music-generation
  (let [schema (test-util/load-setup :music)]
    (is (some? (:catchpocket/generated-at schema)))
    (is (some? (:catchpocket/version schema)))
    (is (= (some-> schema :objects keys set)
           #{:Artist :Album :Track}))
    (is (= (some-> schema :objects :Artist :fields :albums :resolve first)
           :stillsuit/ref))))

(deftest test-snake
  (let [schema (test-util/load-setup :capitalize {:catchpocket/names {:fields :snake_case
                                                                      :objects :Snake_Case}})]
    (is (= (some-> schema :objects keys set)
           #{:Secret_Agent :Country}))
    (is (= (some-> schema :objects :Secret_Agent :fields keys set)
           #{:name :country_of_origin :db_id}))
    (is (= (some-> schema :objects :Country :fields keys set)
           #{:name :land_mass :db_id :secret_agents}))))

(deftest test-camel
  (let [schema (test-util/load-setup :capitalize {:catchpocket/names {:fields :camelCase
                                                                      :objects :CamelCase}})]
    (is (= (some-> schema :objects keys set)
           #{:SecretAgent :Country}))
    (is (= (some-> schema :objects :SecretAgent :fields keys set)
           #{:name :countryOfOrigin :dbId}))
    (is (= (some-> schema :objects :Country :fields keys set)
           ;; Note: :secret_agents is not capitalized as it comes from the catchpocket config
           #{:name :landMass :dbId :secret_agents}))))
