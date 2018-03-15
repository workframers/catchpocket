(ns catchpocket.lib.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cuerdas.core :as str])
  (:import (java.io IOException PushbackReader)))

(defn timestamp []
  (str (java.util.Date.)))

(defn die!
  "Die with an error message (the top-level -main function is looking for this)"
  [err-msg & args]
  (let [msg (apply format (into [err-msg] args))]
    (throw (ex-info msg {:die? true}))))

(defn oxford
  "Given a seq of strings, join them to insert into an English string as 'a, b, and c'."
  [strs]
  (let [[h t] (split-at (-> strs count dec) strs)
        end (first t)
        len (count h)]
    (cond
      (zero? len) (str end)
      (= 1 len)   (str (first h) " and " end)
      :else       (str (str/join ", " h) ", and " end))))

(defn plural
  "Return '' if the supplied seq has a single element, else 's'."
  [strs]
  (if (= (count strs) 1)
    ""
    "s"))

