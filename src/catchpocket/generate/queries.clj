(ns catchpocket.generate.queries
  (:require [clojure.tools.logging :as log]
            [cuerdas.core :as str]))


;(defn attr-info [attr doc]
;  {:attribute/lacinia-name (attr-name (:ident attr))
;   :attribute/ident        (:ident attr)
;   :attribute/field-type   (:value-type attr)
;   :attribute/cardinality  (:cardinality attr)
;   :attribute/doc          (annotate-docs attr doc)
;   :attribute/unique       (:unique attr)
;   :attribute/component?   (:is-component attr)})

(defn- make-query-name
  [lacinia-type lacinia-name config]
  (-> (str lacinia-type "_by_" lacinia-name)
      (str/snake)))

(defn- make-query-for
  [lacinia-type {:keys [:attribute/lacinia-name] :as attr-info} config]
  (let [qname (make-query-name lacinia-type lacinia-name config)]
    [qname qname]))

(defn- accumulate-query
  [config accum [lacinia-type {:keys [:attribute/unique] :as attr-info}]]
  (if-not unique
    accum
    (let [[query-name query-details] (make-query-for lacinia-type attr-info config)]
      (assoc accum query-name query-details))))

(defn attach-queries
  [schema entity-map config]
  (let [queries (reduce (partial accumulate-query config) {} entity-map)]
    (log/spy queries)
    schema))
