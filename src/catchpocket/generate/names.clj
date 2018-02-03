(ns catchpocket.generate.names
  (:require [cuerdas.core :as str]
            [clojure.tools.logging :as log]))

(defn keyword-part->type
  [kw style]
  (let [capitalize? (#{:Snake_Case :CamelCase :camelCase} style)
        underjoin?  (#{:Snake_Case :snake_case} style)
        up-first?   (#{:Snake_Case :CamelCase} style)
        parts       (-> kw
                        str/kebab
                        (str/split #"[^A-Za-z0-9]+"))
        xform       (if capitalize? str/capital identity)
        xform-head  (if up-first? str/capital str/lower)
        sep         (if underjoin? "_" "")
        camels      (->> parts
                         (remove str/blank?)
                         (map xform))
        complete    (concat [(-> camels first xform-head)]
                            (rest camels))]
    (log/spy [style (some? capitalize?) (some? up-first?) sep])
    (log/spy [complete])
    (->> complete
         (str/join sep)
         keyword)))

(defn lacinia-type-name
  "Given a datomic attribute keyword such as `:my-thing/attribute-name` and a catchpocket config
  map, convert the keyword's namespace to a lacinia type name suchs as `:MyThing`."
  [attribute-kw {:keys [:catchpocket/names] :as config}]
  (keyword-part->type
    (namespace attribute-kw)
    (get names :catchpocket.lacinia-type :Snake_Case)))

(defn lacinia-field-name
  [attribute-kw {:keys [:catchpocket/names] :as config}]
  (keyword-part->type
    (name attribute-kw)
    (get names :catchpocket.lacinia-field :snake_case)))
