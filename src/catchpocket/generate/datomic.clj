(ns catchpocket.generate.datomic
  (:require [datomic.api :as d]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cuerdas.core :as str]))

(defn tdb []
  (-> "datomic:dev://localhost:4334/workframe"
      d/connect
      d/db))

;; TODO: move this into stillsuit, integrate
(defn namespace-to-type [kw]
  (-> kw str/camel str/capitalize keyword))

(defn attr-list [db]
  (d/q '[;:find ?ident ?cardinality
         :find ?ns ?attr ?doc
         :where
         [_ :db.install/attribute ?a]
         [?a :db/ident ?ident]
         [?a :db/doc ?doc]
         [(namespace ?ident) ?ns]
         [(datomic.api/attribute $ ?a) ?attr]
         (not [(re-find #"deprecated" ?ns)])
         (not [(re-find #"fressian" ?ns)])
         (not [(re-find #"^db" ?ns)])]
       db))

(defn attr-name
  ([id]
   (attr-name id false))
  ([id capitalize?]
   (let [xform (if capitalize? string/capitalize identity)
         join  (if capitalize? "" "_")]
     (->> (-> id
              str/kebab
              (string/split #"[^A-Za-z0-9]+"))
          (remove string/blank?)
          (map xform)
          (string/join join)
          keyword))))

(defn annotate-docs [attr-map]
  (let [info (keep identity [(when (:attribute/unique attr-map) "unique")
                             (when (:attribute/component? attr-map) "is-component")])
        note (format "> datomic attribute: `%s`. Type `%s`%s."
                     (:attribute/ident attr-map)
                     (:attribute/field-type attr-map)
                     (if (empty? info)
                       ""
                       (str ", " (string/join ", " info))))]
    (if (string/blank? (:attribute/raw-doc attr-map))
      note
      (str (:attribute/raw-doc attr-map) "\n\n" note))))

(defn attr-info
  "Get metadata about a datomic attribute. Note that this operates on the result of a
  (d/attribute) call."
  [attr doc]
  (let [base {:attribute/lacinia-name (attr-name (:ident attr))
              :attribute/ident        (:ident attr)
              :attribute/field-type   (:value-type attr)
              :attribute/cardinality  (:cardinality attr)
              :attribute/unique       (:unique attr)
              :attribute/raw-doc      doc
              :attribute/indexed      (:indexed attr)
              :attribute/fulltext?    (:fulltext attr)
              :attribute/component?   (:is-component attr)}]
    (assoc base :attribute/doc (annotate-docs base))))


(defn add-attr [accum [ns attrs doc]]
  (update accum (attr-name ns true)
          #((fnil conj #{}) % (attr-info attrs doc))))

(defn scan
  "Produce an entity map - a map of lacinia type names to a set of attribute maps,
  where each attribute map corresponds to one datomic attribute as returned by (attr-info)."
  [db options]
  (log/info "Scanning datomic attributes...")
  (let [attrs (attr-list db)]
    (reduce add-attr {} attrs)))
