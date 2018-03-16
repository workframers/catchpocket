(ns catchpocket.generate.queries
  (:require [clojure.tools.logging :as log]
            [cuerdas.core :as str]
            [catchpocket.generate.names :as names]))

(defn- make-query-name
  [parent-type lacinia-name config]
  (-> (names/query-name parent-type config)
      keyword))

(defn- query-definition
  [field-type parent-type {:attribute/keys [lacinia-name ident] :as attr-info} config]
  (let []
    ;(log/spy field-type)
    {:type        parent-type
     :args        {lacinia-name {:type        (list 'non-null field-type)
                                 :description (format "The `%s` value of the entity to find" ident)}}
     :resolve     [:stillsuit/query-by-unique-id
                   #:stillsuit{:attribute    ident
                               :datomic-type field-type
                               :lacinia-type parent-type}]
     :description (format "Find a single %s entity given its `%s` attribute." parent-type ident)}))

(defn- make-query-for
  [field-type parent-type {:attribute/keys [lacinia-name] :as attr-info} config]
  ;(log/spy [field-type parent-type lacinia-name config])
  (let [qname (names/query-name parent-type config)]
    ;(log/spy [parent-type qname])
    [qname (query-definition field-type parent-type attr-info config)]))

(defn attach-queries
  [schema entity-map config]
  (log/spy (some-> entity-map :Artist))
  ;(log/spy (some-> schema :hmm/objects))
  (let [queries (->> (for [[parent-type attr-set] entity-map
                           attr-info              attr-set
                           :when (:attribute/unique attr-info)
                           :let [id         (:attribute/lacinia-name attr-info)
                                 field-type (get-in schema [:objects parent-type :fields id :type])]]
                       (make-query-for field-type parent-type attr-info config))
                     (into {}))]
    (update schema :queries merge queries)))
