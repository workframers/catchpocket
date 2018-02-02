(ns catchpocket.generate.enums
  (:require [clojure.tools.logging :as log]
            [catchpocket.generate.datomic :as datomic]
            [clojure.set :as set]
            [catchpocket.lib.util :as util]
            [cuerdas.core :as str]))

;; http://lacinia.readthedocs.io/en/latest/enums.html

(defn enum-type-description
  "Generate the description for a lacinia enum type."
  [enum-data]
  (let [attr-list (->> enum-data
                       :catchpocket.enum/attributes
                       sort
                       (map #(format "`%s`" %)))
        raw-desc  (if-let [docs (:catchpocket.enum/description enum-data)]
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

(defn generate-lacinia-names [value-set]
  (->> value-set
       (map (fn [{:catchpocket.enum/keys [value] :as value-map}]
              (assoc value-map :catchpocket.enum/lacinia-name (enum-lacinia-value value))))
       (into #{})))

(defn lacinia-value-defs
  [enum-data]
  (for [{:catchpocket.enum/keys [value doc lacinia-name]} (::values enum-data)]
    {:enum-value  lacinia-name
     :description (enum-value-description doc value)}))

(defn generate-enum
  [enum-name enum-data]
  {:description (enum-type-description enum-data)
   :values      (lacinia-value-defs enum-data)})

(defn lacinia-enum-def
  [enum-info]
  (reduce-kv (fn [accum enum-name enum-data]
               (assoc accum enum-name (generate-enum enum-name enum-data)))
             {} enum-info))

(defn unpack-enums
  [db ent-map enums config]
  (->> (for [[enum-name enum-config] enums
             :let [{:catchpocket.enum/keys [attributes values scan?]} enum-config
                   all-values (set/union (when scan? (datomic/enum-scan db attributes))
                                         values)
                   named      (generate-lacinia-names all-values)]]
         [enum-name (assoc enum-config ::values named)])
       (into {})))

(defn- datomic-lacinia-map
  "Extract a nested map from the enum-info map that can be used to translate from
  datomic enum names to lacinia enum names and vice versa."
  [enum-info leaf-type]
  (reduce (fn [acc [enum-type value lacinia-name]]
            (if (= leaf-type ::lacinia)
              (assoc-in acc [enum-type lacinia-name] value)
              (assoc-in acc [enum-type value] lacinia-name)))
          {}
          (for [[enum-type {:keys [::values]}] enum-info
                {:catchpocket.enum/keys [value lacinia-name]} values]
            [enum-type value lacinia-name])))

(defn attribute-to-enum-type
  [enum-info]
  (->> (for [[enum-type {:catchpocket.enum/keys [attributes]}] enum-info
             attribute attributes]
         [attribute enum-type])
       (into {})))

(defn generate-enums
  "Given an entity-map and config file, generate an enums list"
  [db ent-map {:catchpocket/keys [enums] :as config}]
  (let [enum-info   (unpack-enums db ent-map enums config)
        enum-defs   (lacinia-enum-def enum-info)
        lacinia-map (datomic-lacinia-map enum-info ::lacinia)
        datomic-map (datomic-lacinia-map enum-info ::datomic)]
    {:catchpocket.enums/lacinia-defs  enum-defs
     :catchpocket.enums/attribute-map (attribute-to-enum-type enum-info)
     :stillsuit/enum-map              {:stillsuit/lacinia-to-datomic lacinia-map
                                       :stillsuit/datomic-to-lacinia datomic-map}}))

