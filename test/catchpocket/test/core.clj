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
  (testing "skip attributes"
    (let [config {:catchpocket/skip #{:secret-agent/hashed-password}}
          schema (test-util/load-setup :capitalize config)]
      (is (= #{:name :country_of_origin :db_id}
             (some-> schema :objects :Secret_Agent :fields keys set))))))

(deftest test-enum-keyword
  (testing "enum generation for keywords"
    (let [schema           (test-util/load-setup :enums)
          expected-enum    #{:BIPED :BRACHIATOR :QUADROPED}
          expected-datomic #{:movement/biped :movement/brachiator :movement/quadroped}]
      (is (= #{:Animal_Ref :Animal_Keyword}
             (some-> schema :objects keys set)))
      (doseq [[obj-type enum-type] [[:Animal_Keyword :movement_type_kw]
                                    [:Animal_Ref :movement_type_ref]]]
        (testing (str obj-type)
          (let [movement (some-> schema :objects obj-type :fields :movement)]
            (is (some? movement))
            (is (= enum-type (:type movement)))
            (is (= :stillsuit/enum (some-> movement :resolve first))))
          (let [enum-def (get-in schema [:enums enum-type])]
            (is (= expected-enum
                   (->> enum-def :values (map :enum-value) set))))
          (let [lac-map (get-in schema [:stillsuit/enum-map :stillsuit/lacinia-to-datomic
                                        enum-type])]
            (is (= expected-enum (-> lac-map keys set))))
          (let [datomic-map (get-in schema [:stillsuit/enum-map :stillsuit/datomic-to-lacinia
                                            enum-type])]
            (is (= expected-datomic (-> datomic-map keys set)))))))))
