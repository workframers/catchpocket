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
      (str/snake)
      keyword))

(defn- query-definition
  [lacinia-type {:keys [:attribute/lacinia-name] :as attr-info} config]
  (let []
    {:type        lacinia-type
     :args        {lacinia-name {:type '(non-null ID)
                                 :description "The unique ID"}}}))
     ;:resolve     (resolvers/resolve-entity-by-unique-attribute )
     ;:description "Return the current time."}))

(defn- make-query-for
  [lacinia-type {:keys [:attribute/lacinia-name] :as attr-info} config]
  (let [qname (make-query-name lacinia-type lacinia-name config)]
    [qname (query-definition lacinia-type attr-info config)]))

(defn attach-queries
  [schema entity-map config]
  (let [queries (->> (for [[lacinia-type attr-set] entity-map
                           attr-info               attr-set
                           :when (:attribute/unique attr-info)]
                       (make-query-for lacinia-type attr-info config))
                     (into {}))]
    (log/spy queries)
    schema))
