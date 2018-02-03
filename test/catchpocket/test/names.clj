(ns catchpocket.test.names
  (:require [clojure.test :refer :all]
            [catchpocket.generate.names :as names]))

(defn- lacinia-field [kw style]
  (names/lacinia-field-name kw {:catchpocket/names {:fields style}}))

(defn- lacinia-type [kw style]
  (names/lacinia-type-name kw {:catchpocket/names {:objects style}}))

(deftest names
  (let [kw :this-keyword/has-some-words]
    (testing "Field conversion"
      (is (= (lacinia-field kw :snake_case) :has_some_words))
      (is (= (lacinia-field kw :Snake_Case) :Has_Some_Words))
      (is (= (lacinia-field kw :camelCase) :hasSomeWords))
      (is (= (lacinia-field kw :CamelCase) :HasSomeWords)))
    (testing "Type conversion"
      (is (= (lacinia-type kw :snake_case) :this_keyword))
      (is (= (lacinia-type kw :Snake_Case) :This_Keyword))
      (is (= (lacinia-type kw :camelCase) :thisKeyword))
      (is (= (lacinia-type kw :CamelCase) :ThisKeyword)))))


