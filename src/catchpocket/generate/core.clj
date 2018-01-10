(ns catchpocket.generate.core
  (:require [datomic.api :as d]
            [catchpocket.generate.datomic :as datomic]
            [puget.printer :as puget]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [fipp.edn :as fipp]
            [clojure.edn :as edn]
            [clojure.string :as string]))

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

(defn get-field-type [field]
  (let [field-type   (:attribute/field-type field)
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
      (log/infof "Skipping unknown field %s with type %s."
                 (:attribute/ident field) field-type))))

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
  (assoc field-def :db_id {:type        'ID
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
  (log/infof "Generating lacinia schema for %d entity types..." (count ent-map))
  (-> base
      (assoc :objects (create-objects ent-map))))

(defn write-file! [schema options]
  (let [filename (str (:output-dir options) "/catchpocket.edn")]
    (io/make-parents filename)
    (spit filename (with-out-str (fipp/pprint schema)))
    (log/infof "Saved schema to %s" filename)))

(defn generate [datomic-uri options]
  (log/infof "Connecting to %s..." datomic-uri)
  (let [conn     (d/connect datomic-uri)
        db       (d/db conn)
        base-edn (read-edn "lacinia-base.edn")
        ent-map  (datomic/scan db options)
        schema   (generate-edn base-edn ent-map)]
    (write-file! schema options)
    (when (:debug options)
      (puget/pprint schema {:print-color (some? (System/console))})))
  (log/info "Finished generation."))
