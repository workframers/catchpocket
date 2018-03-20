(ns catchpocket.generate.queries
  (:require [clojure.tools.logging :as log]
            [cuerdas.core :as str]
            [catchpocket.generate.names :as names]))

(defn- query-definition
  [field-type parent-type {:attribute/keys [lacinia-name ident] :as attr-info} config]
  (let []
    {:type        parent-type
     :args        {lacinia-name {:type        (list 'non-null field-type)
                                 :description (format "The `%s` value of the entity to find" ident)}}
     :resolve     [:stillsuit/query-by-unique-id
                   #:stillsuit{:attribute    ident
                               :datomic-type field-type
                               :lacinia-type parent-type}]
     :description (format "Find a single %s entity given its `%s` attribute." parent-type ident)}))

(defn attach-queries
  [schema entity-map config]
  (let [blacklist  (get-in config [:catchpocket/queries :blacklist])
        whitelist  (get-in config [:catchpocket/queries :whitelist])
        renames    (get-in config [:catchpocket/queries :names])
        query-defs (for [[parent-type attr-set] entity-map
                         attr-info attr-set
                         :when (:attribute/unique attr-info)
                         :when (not= (:attribute/field-type attr-info) :db.type/ref)
                         :let [id         (:attribute/lacinia-name attr-info)
                               attr-ident (:attribute/ident attr-info)
                               field-type (get-in schema [:objects parent-type :fields id :type])
                               qname      (or (get renames attr-ident)
                                              (names/query-name parent-type config))]
                         :when (or (nil? whitelist) (contains? whitelist qname))
                         :when (or (nil? blacklist) (not (contains? blacklist qname)))
                         :let [qdef (query-definition field-type parent-type attr-info config)]]
                     (do
                       (log/debugf "Generating top-level query %s from unique attribute %s"
                                   qname attr-ident)
                       [qname qdef]))
        queries    (into {} query-defs)]
    (update schema :queries merge queries)))
