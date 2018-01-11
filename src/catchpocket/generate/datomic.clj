(ns catchpocket.generate.datomic
  (:require [datomic.api :as d]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn tdb []
  (-> "datomic:dev://localhost:4334/workframe"
      d/connect
      d/db))

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
         (not [(re-find #"^db" ?ns)])]
       db))

(defn attr-name
  ([id]
   (attr-name id false))
  ([id capitalize?]
   (let [xform (if capitalize? string/capitalize identity)
         join  (if capitalize? "" "_")]
     (->> (-> id
              name
              (string/split #"[^A-Za-z0-9]+"))
          (remove string/blank?)
          (map xform)
          (string/join join)
          keyword))))

(defn annotate-docs [attr docstring]
  (let [note (format "> *datomic attribute: `%s`*" (:ident attr))]
    (if (string/blank? docstring)
      note
      (str docstring "\n\n" note))))


(defn attr-info [attr doc]
  {:attribute/lacinia-name (attr-name (:ident attr))
   :attribute/ident        (:ident attr)
   :attribute/field-type   (:value-type attr)
   :attribute/cardinality  (:cardinality attr)
   :attribute/doc          (annotate-docs attr doc)
   :attribute/unique       (:unique attr)
   :attribute/component?   (:is-component attr)})


(defn add-attr [accum [ns attrs doc]]
  (if (= ns "fressian")
    accum
    (update accum (attr-name ns true)
            #((fnil conj #{}) % (attr-info attrs doc)))))

(defn scan [db options]
  (log/info "Scanning datomic attributes...")
  (let [attrs (attr-list db)]
    (reduce add-attr {} attrs)))
