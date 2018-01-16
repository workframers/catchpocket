(ns catchpocket.generate.core
  (:require [datomic.api :as d]
            [catchpocket.generate.datomic :as datomic]
            [catchpocket.generate.queries :as queries]
            [puget.printer :as puget]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [fipp.edn :as fipp]
            [clojure.edn :as edn]
            [cuerdas.core :as str]
            [clojure.string :as string]
            [catchpocket.lib.util :as util]))

(def datomic-to-lacinia
  {:db.type/keyword ::keyword
   :db.type/string  'String
   :db.type/boolean 'Boolean
   :db.type/long    'Int                                    ; ASSUMPTION
   :db.type/bigint  'String
   :db.type/float   'Float
   :db.type/double  'Float
   :db.type/bigdec  'String
   :db.type/ref     ::ref
   :db.type/instant ::instant
   :db.type/uuid    'ID
   :db.type/uri     'String
   :db.type/bytes   'String})

(defn get-ref-type [field {:keys [:stillsuit/datomic-entity-type] :as config}]
  (if-let [override-type (-> field :catchpocket/reference-to datomic/namespace-to-type)]
    (do
      (log/tracef "Using type %s as referent for field %s" override-type (:attribute/ident field))
      override-type)
    ;; Else no entity found
    (do
      (log/warnf "No reference type found for field %s, using %s" (:attribute/ident field) datomic-entity-type)
      datomic-entity-type)))

(defn get-field-type [field config]
  (let [field-type   (:attribute/field-type field)
        lacinia-type (get datomic-to-lacinia field-type)]
    (cond
      ;; ASSUMPTION
      (= (:attribute/unique field) :db.unique/identity)
      'ID

      (symbol? lacinia-type)
      lacinia-type

      (= lacinia-type ::ref)
      (get-ref-type field config)

      :else
      (log/warnf "Skipping unknown field %s with type %s."
                 (:attribute/ident field) field-type))))

(defn make-single-field [field config]
  (let [{:keys [:attribute/cardinality :attribute/doc]} field
        lacinia-type (get-field-type field config)
        full-type    (if (= cardinality :db.cardinality/many)
                       (list 'list (list 'non-null lacinia-type))
                       lacinia-type)]
    (when lacinia-type
      (merge
       {:type full-type}
       (when doc
         {:description doc})))))

(defn assoc-db-id [field-def {:keys [:stillsuit/db-id-name]}]
  (assoc field-def db-id-name {:type        'ID
                               :description "Unique :db/id value for a datomic entity"}))

(defn make-fields [field-defs config]
  (->> (for [{:keys [:attribute/lacinia-name] :as field} field-defs
             :let [field-def (make-single-field field config)]
             :when field-def]
         [lacinia-name field-def])
       (into {})))

(defn find-backrefs
  [objects config]
  (for [[lacinia-type {:keys [fields]}] objects
        [field-name field-info] fields
        :let [field-type (:type field-info)]
        :when (and (keyword? field-type)
                   (not= field-type (:stillsuit/db-id-name config))
                   (not= field-type :DatomicEntity))]
    [lacinia-type field-name field-type]))

(defn make-object [object field-defs config]
  (log/debugf "Found entity type %s" object)
  {:description (format "Entity containing fields with the namespace `%s`"
                        (-> object str string/lower-case))
   :implements  [:DatomicEntity]
   :fields      (-> field-defs
                    (make-fields config)
                    (assoc-db-id config))})
;::backrefs   (make-backrefs object field-defs config)})

;(defn- create-single-backref
;  [config objects reference]
;  objects)
;
;
;(defn create-backrefs [objects ent-map config]
;  (let [refs ()]
;    (reduce (partial create-single-backref config) objects refs)))
;
(defn create-objects
  [ent-map config]
  (->> (for [[object field-defs] ent-map]
         [object (make-object object field-defs config)])
       (into {})))
;backref (create-backrefs objects ent-map config))))

(defn find-backrefs
  "Given an entity map, scan it looking for datomic back-references. Return a seq of tuples
  [from-type field-name to-type datomic-attribute]."
  [ent-map config]
  (for [[to-type field-defs] ent-map
        field-def field-defs
        :let [backref (:catchpocket/backref-name field-def)]
        :when backref
        :let [from-type   (-> field-def :catchpocket/reference-to datomic/namespace-to-type)
              ident       (:attribute/ident field-def)
              datomic-ref (keyword (format "%s/_%s" (namespace ident) (name ident)))]]
    [from-type backref to-type datomic-ref]))

(defn- add-backref
  [config objects [from-type backref to-type datomic-ref]]
  (log/tracef "Generating back-reference %s from type %s to type %s for attribute %s"
              backref from-type to-type datomic-ref)
  (assoc-in objects [from-type :fields backref]
            {:type        (list 'list (list 'non-null to-type))
             :description (format "Back-reference for the `%s` datomic attribute" datomic-ref)}))

(defn generate-edn [base ent-map config]
  (log/infof "Generating lacinia schema for %d entity types..." (count ent-map))
  (let [objects   (create-objects ent-map config)
        backrefs  (find-backrefs ent-map config)
        decorated (reduce (partial add-backref config) objects backrefs)]
    ;(log/spy backrefs)
    ;(log/spy ent-map)
    (assoc base
      :objects decorated
      :catchpocket/generated-at (util/timestamp)
      :catchpocket/version (:catchpocket/version config))))

(defn write-file! [schema config]
  (let [filename (:catchpocket/schema-file config)]
    (io/make-parents filename)
    (spit filename (with-out-str (fipp/pprint schema)))
    (log/infof "Saved schema to %s" filename)))

(defn generate [{:keys [:catchpocket/datomic-uri] :as config}]
  (log/infof "Connecting to %s..." datomic-uri)
  (let [conn     (d/connect datomic-uri)
        db       (d/db conn)
        base-edn (util/load-edn (io/resource "catchpocket/lacinia-base.edn"))
        ent-map  (datomic/scan db config)
        objects  (generate-edn base-edn ent-map config)
        schema   (queries/attach-queries objects ent-map config)]
    (write-file! schema config)
    (when (:debug config)))
  ;(puget/pprint schema {:print-color (some? (System/console))})))
  (log/info "Finished generation."))
