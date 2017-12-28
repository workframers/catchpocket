(ns harvester.generate.core
  (:require [datomic.api :as d]
            [harvester.generate.datomic :as datomic]
            [puget.printer :as puget]))

(defn make-ent-map [db options]
  {:ok "then"})

(defn generate [datomic-uri options]
  (let [conn    (d/connect datomic-uri)
        db      (d/db conn)
        ent-map (datomic/form db options)]
    (puget/with-color
      (puget/pprint ent-map))))
