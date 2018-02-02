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

(deftest test-music-caps
  (let [schema (test-util/load-setup :capitalize)]
    (is (= (some-> schema :objects keys set)
           #{:SecretAgent :Country}))
    (is (= (some-> schema :objects :SecretAgent :fields keys set)
           #{:name :country_of_origin}))
    (is (= (some-> schema :objects :Country :fields keys set)
           #{:name :land_mass}))))
