(ns catchpocket.test.queries
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as tu]
            [clojure.tools.logging :as log]))

(use-fixtures :once tu/once)

(deftest ^:watch test-music-generation
  (let [schema (tu/generate-schema :music {})]
    (log/spy (some-> schema :queries))
    ;(log/spy (some-> schema :objects :Artist))
    (is (= #{:Artist :Album :Track}
           (some-> schema :queries keys set)))
    (is (= :stillsuit/ref
           (some-> schema :objects :Artist :fields :albums :resolve first)))))

