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

(defn get-ref-type [field {:keys [:catchpocket/references :stillsuit/datomic-entity-type] :as config}]
  (if-let [override (get references field)]
    (let [override-type (-> override :catchpocket/reference-to datomic/namespace-to-type)]
      (log/tracef "Using type %s as referent for field %s" override-type field)
      override-type)
    ;; Else no entity found
    (do
      (log/warnf "No reference type found for field %s, using %s" field datomic-entity-type)
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
      (get-ref-type (:attribute/ident field) config)

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

(defn make-object [object field-defs config]
  (log/debugf "Found entity type %s" object)
  {:description (format "Entity containing fields with the namespace `%s`"
                        (-> object str string/lower-case))
   :implements  [:DatomicEntity]
   :fields      (-> field-defs
                    (make-fields config)
                    (assoc-db-id config))})

(defn create-objects [ent-map config]
  (->> (for [[object field-defs] ent-map]
         [object (make-object object field-defs config)])
       (into {})))


(defn generate-edn [base ent-map config]
  (log/infof "Generating lacinia schema for %d entity types..." (count ent-map))
  (-> base
      (assoc :objects (create-objects ent-map config))
      (assoc :catchpocket/generated-at (util/timestamp))))

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
    (when (:debug config)
      (puget/pprint schema {:print-color (some? (System/console))})))
  (log/info "Finished generation."))
