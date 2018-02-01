(ns catchpocket.generate.datomic
  (:require [datomic.api :as d]
            [clojure.tools.logging :as log]
            [cuerdas.core :as str]
            [clojure.set :as set]
            [catchpocket.lib.util :as util]))

;; TODO: move this into stillsuit, integrate
(defn namespace-to-type [kw]
  (-> kw str/camel str/capital keyword))

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
   (let [xform (if capitalize? str/capital identity)
         join  (if capitalize? "" "_")]
     (->> (-> id
              str/kebab
              (str/split #"[^A-Za-z0-9]+"))
          (remove str/blank?)
          (map xform)
          (str/join join)
          keyword))))

(defn annotate-docs [attr-map]
  (let [info (keep identity [(when (:attribute/unique attr-map) "unique")
                             (when (:attribute/component? attr-map) "is-component")])
        note (format "> datomic attribute: `%s`. Type `%s`%s."
                     (:attribute/ident attr-map)
                     (:attribute/field-type attr-map)
                     (if (empty? info)
                       ""
                       (str ", " (str/join ", " info))))]
    (if (str/blank? (:attribute/raw-doc attr-map))
      note
      (str (:attribute/raw-doc attr-map) "\n\n" note))))

(defn attr-info
  "Get metadata about a datomic attribute. Note that this operates on the result of a
  (d/attribute) call. This function also merges in any part of the :catchpocket/references
  bit of the config that is named after this datomic attribute."
  [attr doc {:keys [:catchpocket/references]}]
  (let [ident   (:ident attr)
        from-cf (get references ident)
        base    {:attribute/lacinia-name (attr-name ident)
                 :attribute/ident        ident
                 :attribute/field-type   (:value-type attr)
                 :attribute/cardinality  (:cardinality attr)
                 :attribute/unique       (:unique attr)
                 :attribute/raw-doc      doc
                 :attribute/indexed      (:indexed attr)
                 :attribute/fulltext?    (:fulltext attr)
                 :attribute/component?   (:is-component attr)}]
    (merge base
           from-cf
           {:attribute/doc (annotate-docs base)})))

;; TODO: make-sensical
(defn add-attr [config accum [ns attrs doc]]
  (update accum (attr-name ns true)
          #((fnil conj #{}) % (attr-info attrs doc config))))

(defn scan
  "Produce an entity map - a map of lacinia type names to a set of attribute maps,
  where each attribute map corresponds to one datomic attribute as returned by (attr-info)."
  [db config]
  (log/info "Scanning datomic attributes...")
  (let [attrs (attr-list db)]
    (reduce (partial add-attr config) {} attrs)))

(defn enum-scan
  "Given a set of attributes, scan the database to produce the set of all of their values.
  This function works for either :db/ident enum references or keywords.
  Return a seq of {::value :foo/bar ::doc \"docstring\"} maps, where ::doc is the docstring
  for enum references."
  [db attributes]
  (let [vals (d/q '[:find ?value ?doc
                    :in $ [?attribute ...]
                    :where
                    [_ ?attribute ?v]
                    (or-join [?v ?doc ?value]
                             ;; Ident enum
                             (and
                              [(datomic.api/entid $ ?v) ?v-id]
                              [?v-id :db/ident ?value]
                              [(get-else $ ?v-id :db/doc :none) ?doc])
                             (and
                              [(keyword? ?v)]
                              [(identity ?v) ?value]
                              [(ground :none) ?doc]))]
                  db attributes)]
    (log/infof "Found %d possible enum values for attribute%s %s."
               (count vals)
               (if (> (count attributes) 1) "s" "")
               (util/oxford attributes))
    (for [[value doc] vals]
      (merge
        {:catchpocket.enum/value value}
        (when (not= doc :none)
          {:catchpocket.enum/doc   doc})))))
