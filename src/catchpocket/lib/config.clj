(ns catchpocket.lib.config
  (:require [catchpocket.lib.util :as util]
            [clojure.java.io :as io]))

(def default-config "catchpocket/defaults.edn")

(defn construct-config [config-file cmd-options]
  (let [defaults (util/load-edn (io/resource default-config))
        cmd-line (util/load-edn config-file)
        merged   (util/deep-map-merge defaults cmd-line)]
    merged))
