(ns catchpocket.generate.datomic
  (:require [datomic.api :as d]
            [clojure.tools.logging :as log]
            [cuerdas.core :as str]
            [clojure.set :as set]
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
              (not [(re-find #"fressian" ?ns)])
              (not [(re-find #"^db" ?ns)])]
            db)
       (map (partial d/entity db))))

;; TODO: make this configurable
(defn lacinia-type-name
  "Given a datomic attribute keyword such as `:my-thing/attribute-name` and a catchpocket config
  map, convert the keyword's namespace to a lacinia type name suchs as `:MyThing`."
  [attribute-kw config]
  (let [capitalize? true
        parts       (-> attribute-kw
                        namespace
                        str/kebab
                        (str/split #"[^A-Za-z0-9]+"))
        xform       (if capitalize? str/capital identity)
        sep         (if capitalize? "" "_")]
    (->> parts
         (remove str/blank?)
         (map xform)
         (str/join sep)
         keyword)))

;; TODO: this too
(defn- lacinia-field-name
  ([attr-ent config]
   (let [capitalize? false
         id          (:db/ident attr-ent)
         xform       (if capitalize? str/capital identity)
         join        (if capitalize? "" "_")]
     (->> (-> id
              str/kebab
              (str/split #"[^A-Za-z0-9]+"))
          (remove str/blank?)
          (map xform)
          (str/join join)
          keyword))))

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
  {:catchpocket.meta/backref-name :attribute/meta-backref-name
   :catchpocket.meta/lacinia-name :attribute/meta-lacinia-name
   :catchpocket.meta/lacinia-type :attribute/meta-lacinia-type
   :db/unique                     :attribute/unique
   :db/valueType                  :attribute/field-type
   :db/isComponent                :attribute/component?
   :db/fulltext                   :attribute/fulltext?
   :db/cardinality                :attribute/cardinality
   :db/doc                        :attribute/raw-doc
   :db/indexed                    :attribute/indexed})

(defn- attr-info
  "Get metadata about a datomic attribute. Note that this operates on the result of a
  (d/attribute) call. This function also merges in any part of the :catchpocket/references
  bit of the config that is named after this datomic attribute."
  [attr-ent lacinia-type {:keys [:catchpocket/references] :as config}]
  (let [ident   (:db/ident attr-ent)
        from-cf (get references ident)
        attrs   (->> (for [[ent-name attr-name] attr-name-to-attr-info-name
                           :let [value (get attr-ent ent-name)]
                           :when value]
                       [attr-name value])
                     (into {}))
        full    (merge {:attribute/lacinia-name (lacinia-field-name attr-ent config)
                        :attribute/lacinia-type lacinia-type
                        :attribute/ident        ident}
                       attrs
                       from-cf)
        docs    (annotate-docs full)]
    (assoc full :attribute/doc docs)))

(defn- add-attr [config accum attr-ent]
  (let [l-type  (lacinia-type-name (:db/ident attr-ent) config)
        set-add (fnil conj #{})
        ai      (attr-info attr-ent l-type config)]
    (update accum l-type #(set-add % ai))))

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
         {:catchpocket.enum/doc doc})))))
