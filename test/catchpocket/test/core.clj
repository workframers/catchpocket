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
    (is (= #{:Artist :Album :Track}
           (some-> schema :objects keys set)))
    (is (= :stillsuit/ref
           (some-> schema :objects :Artist :fields :albums :resolve first)))))

(deftest test-default-case-conversion
  (testing "default case conversion"
    (let [schema (test-util/load-setup :capitalize)]
      (is (= #{:Secret_Agent :Country}
             (some-> schema :objects keys set)))
      (is (= #{:name :country_of_origin :db_id :hashed_password}
             (some-> schema :objects :Secret_Agent :fields keys set)))
      (is (= #{:name :land_mass :db_id :secret_agents}
             (some-> schema :objects :Country :fields keys set))))))

(deftest test-snake-conversion
  (testing "sanke_case conversion"
    (let [config {:catchpocket/names {:fields  :snake_case
                                      :objects :Snake_Case}}
          schema (test-util/load-setup :capitalize config)]
      (is (= #{:Secret_Agent :Country}
             (some-> schema :objects keys set)))
      (is (= #{:name :country_of_origin :db_id :hashed_password}
             (some-> schema :objects :Secret_Agent :fields keys set)))
      (is (= #{:name :land_mass :db_id :secret_agents}
             (some-> schema :objects :Country :fields keys set))))))

(deftest test-camel-conversion
  (testing "camelCase conversion"
    (let [config {:catchpocket/names {:fields  :camelCase
                                      :objects :CamelCase}}
          schema (test-util/load-setup :capitalize config)]
      (is (= #{:SecretAgent :Country}
             (some-> schema :objects keys set)))

      (is (= #{:name :countryOfOrigin :dbId :hashedPassword}
             (some-> schema :objects :SecretAgent :fields keys set)))
      (is (= #{:name :landMass :dbId :secret_agents}
             ;; Note: :secret_agents is not capitalized as it comes from the catchpocket config
             (some-> schema :objects :Country :fields keys set))))))

(deftest test-skip
  (testing "skip attributes")
  (let [config {:catchpocket/skip #{:secret-agent/hashed-password}}
        schema (test-util/load-setup :capitalize config)]
    (is (= #{:name :country_of_origin :db_id}
           (some-> schema :objects :Secret_Agent :fields keys set)))))

