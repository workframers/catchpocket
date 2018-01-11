(ns catchpocket.lib.util
  (:require [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (java.io IOException PushbackReader)))

(defn die!
  "Die with an error message (the top-level -main function is looking for this)"
  [err-msg & args]
  (let [msg (apply format (into [err-msg] args))]
    (throw (ex-info msg {:die? true}))))

(defn load-edn
  "Load edn from an io/reader input (filename or io/resource). If not found, die."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read {:readers *data-readers*} (PushbackReader. r)))
    (catch IOException e
      (die! "Couldn't open file '%s': %s" source (.getMessage e)))
    ;; This is the undocumented exception clojure.edn throws if it gets an error parsing an edn file
    (catch RuntimeException e
      (die! "Error parsing edn file '%s': %s" source (.getMessage e)))))

;; Adapted from deep-merge-with to handle nil values:
;; https://github.com/clojure/clojure-contrib/commit/19613025d233b5f445b1dd3460c4128f39218741
(defn deep-merge-with
  "Like merge-with, but merges maps recursively, appling the given fn
  only when there's a non-map at a particular level.
  (deep-merge-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                     {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  -> {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
    (fn m [& maps]
      (if (every? #(or (map? %) (nil? %)) maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))

(defn deep-map-merge
  "Recursively merge one or more maps, using the values of later maps to replace the
  values of earlier ones."
  [& maps]
  (apply deep-merge-with
         (fn [& values]
           (last values))
         maps))

