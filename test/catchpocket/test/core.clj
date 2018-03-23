(ns catchpocket.test.core
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as tu]
            [clojure.tools.logging :as log]
            [datomic.api :as d]))

(use-fixtures :once tu/once)

(deftest test-music-generation
  (let [schema (tu/generate-schema :music)]
    (is (some? (:catchpocket/generated-at schema)))
    (is (some? (:catchpocket/version schema)))
    (is (= #{:Artist :Album :Track}
           (some-> schema :objects keys set)))
    (is (= :stillsuit/ref
           (some-> schema :objects :Artist :fields :albums :resolve first)))))

(deftest test-skip
  (testing "skip attributes"
    (let [config {:catchpocket/skip #{:secret-agent/hashed-password}}
          schema (tu/generate-schema :capitalize config)]
      (is (= #{:name :country_of_origin :db_id}
             (some-> schema :objects :Secret_Agent :fields keys set))))))

(deftest test-annotation
    (let [schema (tu/generate-schema :annotation)]
      (testing "sanity check"
        (is (= #{:Artist :Album :Track}
               (some-> schema :objects keys set))))
      (testing "Datomic name override for graphql type"
        (is (some? (get-in schema [:objects :Album :fields :override_year])))
        (is (nil? (get-in schema [:objects :Album :fields :year]))))
      (testing "Datomic type override for graphql type"
        (is (= '(list (non-null :Album))
               (get-in schema [:objects :Artist :fields :albums :type]))))
      (testing "Datomic backref works correctly"
        (is (= :Album
               (get-in schema [:objects :Track :fields :album :type]))))
      (testing "Datomic backref missing implies no backref"
        (is (nil? (get-in schema [:objects :Album :fields :artists :type]))))
      (testing "Datomic type override for graphql type"
        (is (= 'Int
               (get-in schema [:objects :Track :fields :position :type]))))))

(deftest test-field-types
    (let [schema (tu/generate-schema :rainbow)
          obj    (some-> schema :objects :Rainbow :fields)]
      (testing "sanity check"
        (is (= #{:Rainbow} (some-> schema :objects keys set))))
      (testing "Generated field types are kosher"
        (is (= :ClojureKeyword (-> obj :one_keyword :type)))
        (is (= :JavaLong (-> obj :one_long :type)))
        (is (= :JavaUUID (-> obj :one_uuid :type)))
        (is (= 'String (-> obj :one_string :type)))
        (is (= 'String (-> obj :one_uri :type)))
        (is (= 'String (-> obj :one_bytes :type)))
        (is (= 'Boolean (-> obj :one_boolean :type)))
        (is (= 'Float (-> obj :one_float :type)))
        (is (= 'Float (-> obj :one_double :type)))
        (is (= :JavaBigDec (-> obj :one_bigdec :type)))
        (is (= :Rainbow (-> obj :one_ref :type))))))
