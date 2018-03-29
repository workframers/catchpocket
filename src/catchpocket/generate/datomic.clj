(ns catchpocket.generate.datomic
  (:require [datomic.api :as d]
            [clojure.tools.logging :as log]
            [cuerdas.core :as str]
            [catchpocket.generate.names :as names]
            [catchpocket.lib.util :as util]))

;; TODO: move this into stillsuit, integrate
(defn namespace-to-type [kw]
  (-> kw str/camel str/capital keyword))

(defn- attr-entities
  "Scan a database for metadata about its attributes. Return a seq of entities for the attributes.
  Skip attributes with `deprecated` or `fressian` in their namespaces."
  [db]
  (->> (d/q '[:find [?a ...]
              :where
              [?a :db/valueType]
              [?a :db/ident ?ident]
              [(namespace ?ident) ?ns]
              (not [(re-find #"deprecated" ?ns)])
              (not [(re-find #"^fressian" ?ns)])
              (not [(re-find #"^db" ?ns)])
              (not [(re-find #"^catchpocket" ?ns)])]
            db)
       (map (partial d/entity db))))

(defn- annotate-docs [attr-map]
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

(def ^:private attr-name-to-attr-info-name
  "This map translates from attributes that are attached to the entity for an
  attribute to the `:attribute/*` names that are used when we generate the schema."
  {:catchpocket/lacinia-backref-name        :attribute/meta-backref-name
   :catchpocket/lacinia-field-name          :attribute/meta-lacinia-name
   :catchpocket/lacinia-field-type          :attribute/meta-lacinia-type
   :catchpocket/lacinia-field-cardinality   :attribute/meta-lacinia-type
   :catchpocket/lacinia-backref-cardinality :attribute/meta-backref-cardinality
   :db/unique                               :attribute/unique
   :db/valueType                            :attribute/field-type
   :db/isComponent                          :attribute/component?
   :db/fulltext                             :attribute/fulltext?
   :db/cardinality                          :attribute/cardinality
   :db/doc                                  :attribute/raw-doc
   :db/indexed                              :attribute/indexed})

(defn- attr-info
  "Get metadata about a datomic attribute. Note that this operates on the result of a
  (d/attribute) call. This function also merges in any part of the :catchpocket/references
  bit of the config that is named after this datomic attribute."
  [attr-ent lacinia-type {:keys [:catchpocket/references] :as config}]
  (let [ident        (:db/ident attr-ent)
        from-cf      (get references ident)
        attrs        (->> (for [[datomic-attr-name attr-name] attr-name-to-attr-info-name
                                :let [value (get attr-ent datomic-attr-name)]
                                :when value]
                            [attr-name value])
                          (into {}))
        lacinia-name (names/lacinia-field-name (:db/ident attr-ent) config)
        full         (merge {:attribute/lacinia-name lacinia-name
                             :attribute/lacinia-type lacinia-type
                             :attribute/ident        ident}
                            attrs
                            from-cf)
        docs         (annotate-docs full)]
    (assoc full :attribute/doc docs)))

(defn- add-attr [config accum attr-ent]
  (let [ident   (:db/ident attr-ent)
        l-type  (names/lacinia-type-name ident config)
        set-add (fnil conj #{})
        ai      (attr-info attr-ent l-type config)]
    (if (contains? (:catchpocket/skip config) ident)
      (do
        (log/infof "Skipping datomic attribute %s" ident)
        accum)
      (update accum l-type #(set-add % ai)))))

(defn scan
  "Produce an entity map - a map of lacinia type names to a set of attribute maps,
  where each attribute map corresponds to one datomic attribute as returned by (attr-info)."
  [db config]
  (log/info "Scanning datomic attributes...")
  (let [attr-ents (attr-entities db)]
    (reduce (partial add-attr config) {} attr-ents)))

(defn enum-scan
  "Given a set of attributes, scan the database to produce the set of all of their values.
  This function works for either :db/ident enum references or keywords.
  Return a seq of {::datomic-value :foo/bar ::attribute :x/foo ::description \"docstring\"}
  maps, where :description is the docstring for enum references."
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
    (for [[value doc] vals
          attribute   attributes]
      (merge
       {::datomic-value value
        ::attribute     attribute}
       (when (not= doc :none)
         {::description doc})))))
