(ns catchpocket.test.names
  (:require [clojure.test :refer :all]
            [catchpocket.generate.names :as names]))

(defn- lacinia-field [kw style]
  (names/lacinia-field-name kw {:catchpocket/names {:fields style}}))

(defn- lacinia-type [kw style]
  (names/lacinia-type-name kw {:catchpocket/names {:objects style}}))

(deftest keyword-conversion
  (let [mult :this-keyword/has-some-words
        sing :hello/world]
    (testing "Type conversion, single-word"
      (is (= (lacinia-type sing :snake_case) :hello))
      (is (= (lacinia-type sing :Snake_Case) :Hello))
      (is (= (lacinia-type sing :camelCase) :hello))
      (is (= (lacinia-type sing :CamelCase) :Hello))
      (is (= (lacinia-type sing :SNAKE_CASE) :HELLO)))
    (testing "Type conversion, multi-word"
      (is (= (lacinia-type mult :snake_case) :this_keyword))
      (is (= (lacinia-type mult :Snake_Case) :This_Keyword))
      (is (= (lacinia-type mult :camelCase) :thisKeyword))
      (is (= (lacinia-type mult :CamelCase) :ThisKeyword))
      (is (= (lacinia-type mult :SNAKE_CASE) :THIS_KEYWORD)))
    (testing "Field conversion, single-word"
      (is (= (lacinia-field sing :snake_case) :world))
      (is (= (lacinia-field sing :Snake_Case) :World))
      (is (= (lacinia-field sing :camelCase) :world))
      (is (= (lacinia-field sing :CamelCase) :World))
      (is (= (lacinia-field sing :SNAKE_CASE) :WORLD)))
    (testing "Field conversion, multi-word"
      (is (= (lacinia-field mult :snake_case) :has_some_words))
      (is (= (lacinia-field mult :Snake_Case) :Has_Some_Words))
      (is (= (lacinia-field mult :camelCase) :hasSomeWords))
      (is (= (lacinia-field mult :CamelCase) :HasSomeWords))
      (is (= (lacinia-field mult :SNAKE_CASE) :HAS_SOME_WORDS)))))
