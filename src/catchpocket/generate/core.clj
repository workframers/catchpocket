(ns catchpocket.generate.core
  (:require [datomic.api :as d]
            [catchpocket.generate.datomic :as datomic]
            [catchpocket.generate.queries :as queries]
            [catchpocket.generate.enums :as enums]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [fipp.edn :as fipp]
            [catchpocket.lib.util :as util]
            [clojure.string :as str]
            [catchpocket.generate.names :as names]))

(def ^:private default-config "catchpocket/defaults.edn")

(def ^:private datomic-to-lacinia
  {:db.type/string  'String
   :db.type/boolean 'Boolean
   :db.type/long    :JavaLong
   :db.type/keyword :ClojureKeyword
   :db.type/bigint  :JavaBigInt
   :db.type/float   'Float
   :db.type/double  'Float
   :db.type/bigdec  :JavaBigDec
   :db.type/ref     ::ref
   :db.type/instant ::instant
   :db.type/uuid    :JavaUUID
   :db.type/uri     'String
   :db.type/bytes   'String
   ;; These types are usable as :catchpocket/lacinia-field-type values
   :Int             'Int})

(defn- get-ref-type [field {:keys [:stillsuit/datomic-entity-type] :as config}]
  (if-let [override-type (-> field :catchpocket/reference-to datomic/namespace-to-type)]
    (do
      (log/tracef "Using type %s as return type for field %s" override-type (:attribute/ident field))
      override-type)
    ;; Else no entity found
    (do
      (log/warnf "No reference type found for field %s, using %s" (:attribute/ident field)
                 datomic-entity-type)
      datomic-entity-type)))

(defn- get-field-type [field config]
  (let [base-type        (:attribute/field-type field)
        datomic-override (:attribute/meta-lacinia-type field)
        field-type       (if datomic-override
                           (do
                             (log/infof "Overriding type '%s' with type '%s' for attribute '%s'"
                                        base-type datomic-override (:attribute/ident field))
                             datomic-override)
                           base-type)
        primitive        (get datomic-to-lacinia field-type)]
    (cond
      ;; ASSUMPTION
      (= (:attribute/unique field) :db.unique/identity)
      'ID

      (= primitive ::ref)
      (get-ref-type field config)

      (or (keyword? primitive) (symbol? primitive))
      primitive

      datomic-override
      datomic-override

      :else
      (do
        (log/warnf "Skipping unknown field %s with type %s."
                   (:attribute/ident field) field-type)))))


(defn- make-single-field [field config]
  (let [{:attribute/keys [cardinality doc]} field
        lacinia-type (get-field-type field config)
        full-type    (if (= cardinality :db.cardinality/many)
                       (list 'list (list 'non-null lacinia-type))
                       lacinia-type)]
    (when lacinia-type
      (merge
        {:type    full-type
         :resolve [:stillsuit/ref
                   #:stillsuit{:attribute    (:attribute/ident field)
                               :lacinia-type lacinia-type}]}
        (when doc
          {:description doc})))))

(defn- make-enum-field [field enum-type enums config]
  (let [{:attribute/keys [cardinality doc ident]} field
        full-type (if (= cardinality :db.cardinality/many)
                    (list 'list (list 'non-null enum-type))
                    enum-type)]
    (log/tracef "Using enum type %s for field %s" full-type ident)
    (merge
      {:type    full-type
       :resolve [:stillsuit/enum
                 #:stillsuit{:attribute    (:attribute/ident field)
                             :lacinia-type enum-type}]}
      (when doc
        {:description doc}))))

(defn- assoc-db-id [field-def config]
  (assoc field-def
    (names/db-id-name config)
    {:type        :JavaLong
     :description "Unique :db/id value for a datomic entity"}))

(defn- make-fields [field-defs enums config]
  (->> (for [{:keys [:attribute/lacinia-name :attribute/meta-lacinia-name :attribute/ident] :as field}
             field-defs
             :let [enum-type  (get-in enums [:catchpocket.enums/attribute-map ident])
                   field-def  (if enum-type
                                (make-enum-field field enum-type enums config)
                                (make-single-field field config))
                   final-name (if meta-lacinia-name
                                (do
                                  (log/infof "Overriding lacinia name '%s' as '%s' for attribute '%s'"
                                             lacinia-name meta-lacinia-name ident)
                                  meta-lacinia-name)
                                lacinia-name)]
             :when field-def]
         [final-name field-def])
       (into {})))

(defn- make-object [object enums field-defs config]
  (log/debugf "Found entity type %s" object)
  {:description (format "Entity containing fields with the namespace `%s`"
                        (-> object str str/lower-case))
   :implements  [:DatomicEntity]
   :fields      (-> field-defs
                    (make-fields enums config)
                    (assoc-db-id config))})

(defn- create-objects
  [ent-map enums config]
  (->> (for [[object field-defs] ent-map]
         [object (make-object object enums field-defs config)])
       (into {})))

(defn find-backrefs
  "Given an entity map, scan it looking for datomic back-references. Return a seq of tuples
  [from-type field-name to-type datomic-attribute is-component?]."
  [ent-map config]
  (for [[to-type field-defs] ent-map
        field-def field-defs
        :let [datomic-override (:attribute/meta-backref-name field-def)
              backref          (if datomic-override
                                 (do
                                   (log/infof "Using backref name '%s' from datomic metadata on '%s'"
                                              datomic-override (:attribute/ident field-def))
                                   datomic-override)
                                 (:catchpocket/backref-name field-def))]
        :when backref
        :let [from-type   (or (-> field-def :catchpocket/reference-to datomic/namespace-to-type)
                              (-> field-def :attribute/meta-lacinia-type))
              ident       (:attribute/ident field-def)
              datomic-ref (keyword (format "%s/_%s" (namespace ident) (name ident)))]]
    [from-type backref to-type datomic-ref (:attribute/component? field-def)]))

(defn- add-backref
  [config objects [from-type backref to-type datomic-ref is-component?]]
  (let [plural-type (if is-component?
                      to-type
                      (list 'list (list 'non-null to-type)))]
    (log/tracef "Generating back-reference %s from type %s to type %s for attribute %s"
                backref from-type plural-type datomic-ref)
    (assoc-in objects [from-type :fields backref]
              {:type        plural-type
               :resolve     [:stillsuit/ref
                             #:stillsuit{:attribute    datomic-ref
                                         :lacinia-type plural-type}]
               :description (format "Back-reference for the `%s` datomic attribute" datomic-ref)})))

(defn generate-edn [base-schema ent-map enums config]
  (log/infof "Generating lacinia schema for %d entity types..." (count ent-map))
  (let [objects   (create-objects ent-map enums config)
        backrefs  (find-backrefs ent-map config)
        decorated (reduce (partial add-backref config) objects backrefs)]
    (util/deep-map-merge
      base-schema
      {:objects                  decorated
       :enums                    (:catchpocket.enums/lacinia-defs enums)
       :stillsuit/enum-map       (:stillsuit/enum-map enums)
       :catchpocket/generated-at (util/timestamp)
       :catchpocket/version      (:catchpocket/version config)})))

(defn construct-config
  ([config]
   (construct-config config nil))
  ([config override]
   (let [defaults (util/load-edn (io/resource default-config))
         merged   (util/deep-map-merge config defaults override)]
     merged)))

(defn generate [conn base-config]
  (let [db       (d/db conn)
        config   (construct-config base-config)
        base-edn (util/load-edn (io/resource "catchpocket/lacinia-base.edn"))
        ent-map  (datomic/scan db config)
        enums    (enums/generate-enums db ent-map config)
        objects  (generate-edn base-edn ent-map enums config)
        schema   (queries/attach-queries objects ent-map config)]
    schema))

(defn- write-file! [schema config]
  (let [filename (:catchpocket/schema-file config)]
    (io/make-parents filename)
    (spit filename (with-out-str (fipp/pprint schema)))
    (log/infof "Saved schema to %s" filename)))

(defn generate-and-write [{:keys [:catchpocket/datomic-uri] :as config}]
  (log/infof "Connecting to %s..." datomic-uri)
  (let [conn      (d/connect datomic-uri)
        generated (generate conn config)]
    (write-file! generated config))
  (log/info "Finished generation."))
