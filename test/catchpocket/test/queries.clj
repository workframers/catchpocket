(ns catchpocket.test.queries
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as tu]
            [clojure.tools.logging :as log]))

(use-fixtures :once tu/once)

(deftest ^:watch test-music-generation
  (testing "Vanilla query generation"
    (let [schema (tu/generate-schema :music)]
      (is (= #{:Artist :Album :Track}
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
