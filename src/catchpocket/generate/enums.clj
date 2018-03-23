(ns catchpocket.generate.enums
  (:require [clojure.tools.logging :as log]
            [catchpocket.generate.datomic :as dt]
            [clojure.set :as set]
            [catchpocket.lib.util :as util]
            [cuerdas.core :as str]
            [catchpocket.generate.names :as names]))

;; http://lacinia.readthedocs.io/en/latest/enums.html

(defn enum-type-description
  "Generate the description for a lacinia enum type."
  [enum-data]
  (let [attr-list (->> enum-data
                       (map ::attributes)
                       (reduce set/union)
                       sort
                       (map #(format "`%s`" %)))
        raw-desc  (if-let [docs (::description enum-data)]
                    (str docs "\n\n")
                    "")]
    (format "%s> Enum generated from datomic attribute%s %s."
            raw-desc
            (util/plural attr-list)
            (util/oxford attr-list))))

(defn enum-value-description
  [doc value]
  (format "%s> Datomic value: `%s`"
          (if doc (str doc "\n\n") "")
          value))

(defn enum-lacinia-value
  "Given a keyword representing a datomic value, return a GraphQL keyword."
  [enum-value]
  (->> enum-value
       name
       str/snake
       str/upper
       keyword))

(defn lacinia-value-defs
  [enum-data]
  (for [{::keys [datomic-value enum-value description]} (sort-by ::enum-value enum-data)]
    {:enum-value              enum-value
     :stillsuit/datomic-value datomic-value
     :description             (enum-value-description description datomic-value)}))

(defn generate-enum
  [enum-name enum-data]
  {:description (enum-type-description enum-data)
   :values      (lacinia-value-defs enum-data)})

(defn lacinia-enum-def
  [enum-info]
  (reduce-kv (fn [accum enum-name enum-data]
               (assoc accum enum-name (generate-enum enum-name enum-data)))
             {} enum-info))

(defn- decompose-manual-values
  "Normalize a single {:catchpocket/enum {:type_name {...}}} value into a
  common structure that we can compose with scanned datomic values."
  [enum-name attributes manual-values]
  (map (fn [{:keys [:stillsuit/datomic-value enum-value description]}]
         (merge
          {::datomic-value datomic-value
           ::attributes    attributes
           ::enum-value    enum-value
           ::enum-name     enum-name}
          (when description
            {::description description})))
       manual-values))

(defn- decompose-scanned-values
  "Normalize the results of scanning datomic for all possible enum values."
  [enum-name attributes scanned-values config]
  (map (fn [{::dt/keys [datomic-value description] :as value}]
         (merge
          {::datomic-value datomic-value
           ::attributes    attributes
           ::enum-value    (names/enum-value-name datomic-value config)
           ::enum-name     enum-name}
          (when description
            {::description description})))
       scanned-values))

(defn- combine-manual-and-scanned
  "Given a seq of enum-values maps parsed from the catchpocket config file plus
  a seq of maps scanned from datomic, combine them, ignoring any scanned values
  which were already specified in the the manual config. Return a map from the
  lacinia enum type-names to a seq of enum-value maps."
  [manual scanned]
  (let [by-enum     (group-by ::enum-name manual)
        manual-vals (->> (for [[enum-name values] by-enum
                               {::keys [enum-value]} values]
                           {enum-name #{enum-value}})
                         (merge-with set/union))]
    (reduce (fn [by-enum {::keys [enum-name datomic-value] :as scanned-value}]
              (if (contains? (get manual-vals enum-name) datomic-value)
                by-enum
                (update by-enum enum-name conj scanned-value)))
            by-enum
            scanned)))

(defn unpack-enums
  "Given a db value and the :catchpocket/enum entry of the config file, scan the
  db for any enums that are marked as `:catchpocket.enum/scan?`. Return a structure
  can be turned into a lacinia `:enum` structure via the `(generate-enum)` function."
  [db ent-map enums config]
  (->> (for [[enum-name enum-config] enums
             :let [{:catchpocket.enum/keys [attributes values scan?]} enum-config
                   manual  (decompose-manual-values enum-name attributes values)
                   scanned (when scan?
                             (decompose-scanned-values enum-name
                                                       attributes
                                                       (dt/enum-scan db attributes)
                                                       config))]]
         (combine-manual-and-scanned manual scanned))
       (apply merge)))

(defn attribute-to-enum-type
  "Given a data structure returned from unpack-enums, produce a simple map from
  datomic attribute names to the corresponding names of lacinia types."
  [enum-info]
  (->> (for [[enum-type enum-values] enum-info
             {::keys [attributes]} enum-values
             attribute attributes]
         [attribute enum-type])
       (into {})))

(defn generate-enums
  "Given an entity-map and config file, generate an enums list"
  [db ent-map {:catchpocket/keys [enums] :as config}]
  (let [enum-info   (unpack-enums db ent-map enums config)
        enum-defs   (lacinia-enum-def enum-info)
        attr-map    (attribute-to-enum-type enum-info)]
    {:catchpocket.enums/lacinia-defs       enum-defs
     :catchpocket.enums/attribute-map      attr-map}))
