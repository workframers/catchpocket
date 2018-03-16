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

(defn- run-enum-test
  [{:keys [::schema ::obj-type ::enum-type ::expected-enum ::expected-datomic]}]
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
      (is (= expected-datomic (-> datomic-map keys set))))))


(deftest test-enum-keyword
  (testing "enum generation for keywords"
    (let [schema           (tu/generate-schema :enums)
          expected-enum    #{:BIPED :BRACHIATOR :QUADROPED}
          expected-datomic #{:movement/biped :movement/brachiator :movement/quadroped}
          obj-type         :Animal_Keyword
          enum-type        :movement_type_kw]
      (is (= #{:Animal_Ref :Animal_Keyword}
             (some-> schema :objects keys set)))
      (run-enum-test {::schema schema
                      ::expected-enum expected-enum
                      ::expected-datomic expected-datomic
                      ::obj-type obj-type
                      ::enum-type enum-type}))))

(deftest test-enum-ref
  (testing "enum generation for keywords"
    (let [schema           (tu/generate-schema :enums)
          expected-enum    #{:BIPED :BRACHIATOR :QUADROPED}
          expected-datomic #{:movement/biped :movement/brachiator :movement/quadroped}
          obj-type         :Animal_Keyword
          enum-type        :movement_type_kw]
      (is (= #{:Animal_Ref :Animal_Keyword}
             (some-> schema :objects keys set)))
      (run-enum-test {::schema schema
                      ::expected-enum expected-enum
                      ::expected-datomic expected-datomic
                      ::obj-type obj-type
                      ::enum-type enum-type}))))

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
