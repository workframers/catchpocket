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

(defn lacinia-value-defs
  [enum-data]
  (for [{:catchpocket.enum/keys [value doc]} (::values enum-data)]
    {:enum-value (enum-lacinia-value value)
     :description (enum-value-description doc value)}))

(defn generate-enum
  [enum-name enum-data]
  (let [desc (:catchpocket.enum/description enum-data "")]
    {:description (enum-type-description enum-data)
     :values (lacinia-value-defs enum-data)}))

(defn lacinia-enum-def
  [enum-info]
  (reduce (fn [accum [enum-name enum-data]]
            (assoc accum enum-name (generate-enum enum-name enum-data)))
          {} enum-info))

(defn unpack-enums
  [db ent-map enums config]
  (->> (for [[enum-name enum-config] enums
             :let [{:catchpocket.enum/keys [attributes description values scan?]} enum-config
                   all-values (set/union (when scan? (datomic/enum-scan db attributes))
                                         values)]]
         [enum-name (assoc enum-config ::values all-values)])
       (into {})))

(defn generate-enums
  "Given an entity-map and config file, generate an enums list"
  [db ent-map {:catchpocket/keys [enums] :as config}]
  (let [enum-info (unpack-enums db ent-map enums config)]
    (log/spy enum-info)
    (log/spy (lacinia-enum-def enum-info))
    {:enums (lacinia-enum-def enum-info)}))

