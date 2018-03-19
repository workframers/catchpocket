(ns catchpocket.test.queries
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as tu]
            [clojure.tools.logging :as log]))

(use-fixtures :once tu/once)

(deftest ^:watch test-query-generation
  (testing "Vanilla query generation"
    (let [schema (tu/generate-schema :capitalize)]
      (is (= #{:Country :Secret_Agent}
             (some-> schema :queries keys set)))))
  (testing "camelCase query names"
    (let [config {:catchpocket/names {:queries :camelCase}}
          schema (tu/generate-schema :music config)]
      (is (= #{:artist :album :track}
             (some-> schema :queries keys set)))))
  (testing "Vanilla query names"
    (let [schema (tu/generate-schema :capitalize)]
      (is (= #{:Country :Secret_Agent}
             (some-> schema :queries keys set)))))
  (testing "Fallback to type name"
    (let [config {:catchpocket/names {:objects :CamelCase}}
          schema (tu/generate-schema :capitalize config)]
      (is (= #{:Country :SecretAgent}
             (some-> schema :queries keys set)))))
  (testing "CamelCase query names"
    (let [config {:catchpocket/names {:queries :CamelCase}}
          schema (tu/generate-schema :capitalize config)]
      (is (= #{:Country :SecretAgent}
             (some-> schema :queries keys set)))))
  (testing "camelCase query names"
    (let [config {:catchpocket/names {:queries :camelCase}}
          schema (tu/generate-schema :capitalize config)]
      (is (= #{:country :secretAgent}
             (some-> schema :queries keys set))))))

(deftest test-query-whitelist-blacklist
  (testing "Default is to to generate everything"
    (let [schema (tu/generate-schema :music)]
      (is (= #{:Album :Artist :Track}
             (some-> schema :queries keys set)))))
  (testing "Query whitelist"
    (let [config {:catchpocket/queries {:whitelist #{:Artist}}}
          schema (tu/generate-schema :music config)]
      (is (= #{:Artist}
             (->> schema :queries keys (into #{}))))))
  (testing "Query whitelist (empty)"
    (let [config {:catchpocket/queries {:whitelist #{}}}
          schema (tu/generate-schema :music config)]
      (is (= #{}
             (->> schema :queries keys (into #{}))))))
  (testing "Query blacklist"
    (let [config {:catchpocket/queries {:blacklist #{:Artist}}}
          schema (tu/generate-schema :music config)]
      (is (= #{:Album :Track}
             (->> schema :queries keys (into #{}))))))
  (testing "Query blacklist (empty)"
    (let [config {:catchpocket/queries {:blacklist #{}}}
          schema (tu/generate-schema :music config)]
      (is (= #{:Album :Artist :Track}
             (->> schema :queries keys (into #{})))))))

(deftest test-query-explicit-names
  (testing "Default is to to generate everything"
    (let [schema (tu/generate-schema :music)]
      (is (= #{:Album :Artist :Track}
             (some-> schema :queries keys set)))))
  (testing "Rename query by attribute"
    (let [config {:catchpocket/queries {:names {:artist/id :OogaBooga}}}
          schema (tu/generate-schema :music config)]
      (is (= #{:Album :OogaBooga :Track}
             (->> schema :queries keys (into #{})))))))
