(ns catchpocket.test.queries
  (:require [clojure.test :refer :all]
            [catchpocket.test-util :as tu]))

(use-fixtures :once tu/once)

(deftest run-all-queries
  (doseq [setup-name tu/all-setup-names]
    (testing (format "Running queries for %s: " setup-name)
      (let [setup (tu/stillsuit-setup setup-name)]
        (when (::tu/query-doc setup)
          (tu/verify-queries! setup))))))

