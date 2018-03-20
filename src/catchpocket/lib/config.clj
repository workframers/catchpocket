(ns catchpocket.lib.config
  (:require [stillsuit.lib.util :as su]))

(def default-config "catchpocket/defaults.edn")

(defn construct-config
  ([config-file]
   (construct-config config-file nil))
  ([config-file overrides]
   (let [defaults (su/load-edn-resource default-config)
         cmd-line (su/load-edn-file config-file)
         merged   (su/deep-map-merge defaults cmd-line overrides)]
     merged)))
