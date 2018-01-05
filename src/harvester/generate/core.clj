(ns harvester.generate.core
  (:require [datomic.api :as d]
            [harvester.generate.datomic :as datomic]
            [puget.printer :as puget]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as string]))

(defn make-ent-map [db options]
  {:ok "then"})

(defn read-edn [name]
  (some-> name
          io/resource
          io/reader
          java.io.PushbackReader.
          edn/read))

(def datomic-to-lacinia
  {:db.type/keyword ::keyword
   :db.type/string  'String
   :db.type/boolean 'Boolean
   :db.type/long    'Int                                    ; ASSUMPTION
   :db.type/bigint  'String
   :db.type/float   'Float
   :db.type/double  'Float                                  ; ASSUMPTION
   :db.type/bigdec  'String
   :db.type/ref     ::ref
   :db.type/instant ::instant
   :db.type/uuid    'ID
   :db.type/uri     'String
   :db.type/bytes   'String})

(defn handle-field-type [x]
  x)

(defn get-field-type [field]
  (let [field-type (:attribute/field-type field)
        lacinia-type (get datomic-to-lacinia field-type)]
    (cond
      ;; ASSUMPTION
      (= (:attribute/unique field) :db.unique/identity)
      'ID

      (symbol? lacinia-type)
      lacinia-type

      (= lacinia-type ::ref)
      ':DatomicEntity

      :else
      (do
        (printf "Skipping unknown field %s with type %s.\n"
                (:attribute/ident field) field-type)
        nil))))

(defn make-single-field [field]
  (let [{:keys [:attribute/field-type :attribute/cardinality
                :attribute/doc :attribute/unique]} field
        lacinia-type (get-field-type field)
        full-type    (if (= cardinality :db.cardinality/many)
                       (list 'list (list 'non-null lacinia-type))
                       lacinia-type)]
    (when lacinia-type
      (merge
        {:type full-type}
        (when doc
          {:description doc})))))

(defn assoc-db-id [field-def]
  (assoc field-def :db_id {:type 'ID
                           :description "Unique :db/id value for a datomic entity"}))

(defn make-fields [field-defs]
  (->> (for [{:keys [:attribute/lacinia-name] :as field} field-defs
             :let [field-def (make-single-field field)]
             :when field-def]
         [lacinia-name field-def])
       (into {})))

(defn make-object [object field-defs]
  {:description (format "Entity containing fields with the namespace %s"
                        (-> object str string/lower-case))
   :implements  [:DatomicEntity]
   :fields      (-> field-defs
                    make-fields
                    assoc-db-id)})

(defn create-objects [ent-map]
  (->> (for [[object field-defs] ent-map]
         [object (make-object object field-defs)])
       (into {})))


(defn generate-edn [base ent-map]
  (-> base
      (assoc :objects (create-objects ent-map))))

(defn generate [datomic-uri options]
  (let [conn     (d/connect datomic-uri)
        db       (d/db conn)
        base-edn (read-edn "lacinia-base.edn")
        ent-map  (datomic/scan db options)
        result   (generate-edn base-edn ent-map)
        color?   (some? (System/console))]
    (puget/pprint result {:print-color color?})))
