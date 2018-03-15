(ns catchpocket.lib.config
  (:require [stillsuit.lib.util :as su]))

(def default-config "catchpocket/defaults.edn")

(defn construct-config [config-file cmd-options]
  (let [defaults (su/load-edn-resource default-config)
        cmd-line (su/load-edn-file config-file)
        merged   (su/deep-map-merge defaults cmd-line)]
    merged))
