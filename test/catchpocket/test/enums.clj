(ns catchpocket.test.enums
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as tu]
            [clojure.tools.logging :as log]))

(use-fixtures :once tu/once)

(defn- run-enum-test
  [{:keys [::schema ::obj-type ::enum-type ::expected-enum ::expected-datomic]}]
  (testing (str obj-type)
    (let [movement (some-> schema :objects obj-type :fields :movement)]
      (is (some? movement))
      (is (= enum-type (:type movement)))
      (is (= :stillsuit/enum (some-> movement :resolve first))))
    (let [enum-def (get-in schema [:enums enum-type])]
      (is (= expected-enum
             (->> enum-def :values (map :enum-value) set))))))

(deftest test-enum-keyword
  (testing "enum generation for keywords"
    (let [schema           (tu/generate-schema :enums)
          expected-enum    #{:BIPED :BRACHIATOR :QUADROPED :WHEELS}
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
          expected-enum    #{:BIPED :BRACHIATOR :QUADROPED :WHEELS}
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
