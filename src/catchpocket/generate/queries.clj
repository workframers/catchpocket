(ns catchpocket.generate.queries
  (:require [clojure.tools.logging :as log]
            [cuerdas.core :as str]))

(defn- make-query-name
  [lacinia-type lacinia-name config]
  (-> (str lacinia-type "_by_" lacinia-name)
      (str/snake)
      keyword))

(defn- query-definition
  [lacinia-type {:attribute/keys [lacinia-name ident field-type] :as attr-info} config]
  (let []
    {:type        lacinia-type
     :args        {lacinia-name {:type        '(non-null ID)
                                 :description (format "The `%s` value of the entity to find" ident)}}
     :resolve     [:stillsuit/query-by-unique-id
                   #:stillsuit{:attribute    ident
                               :datomic-type field-type
                               :lacinia-type lacinia-type}]
     :description (format "Find a single %s entity given its `%s` attribute." lacinia-type ident)}))

(defn- make-query-for
  [lacinia-type {:attribute/keys [lacinia-name] :as attr-info} config]
  (let [qname (make-query-name lacinia-type lacinia-name config)]
    [qname (query-definition lacinia-type attr-info config)]))

(defn attach-queries
  [schema entity-map config]
  (let [queries (->> (for [[lacinia-type attr-set] entity-map
                           attr-info               attr-set
                           :when (:attribute/unique attr-info)]
                       (make-query-for lacinia-type attr-info config))
                     (into {}))]
    (update schema :queries merge queries)))
