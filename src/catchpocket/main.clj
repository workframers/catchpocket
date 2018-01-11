(ns catchpocket.main
  (:require [catchpocket.lib.config :as cf]
            [catchpocket.generate.core :as g]
            [clojure.tools.cli :as cli]
            [clojure.tools.logging :as log]
            [clojure.string :as string]))

(def cli-options
  [["-d" "--debug" "Produce debug output" :default false]
   [nil "--color" "Produce color logs" :default true]
   ["-h" "--help" "Print usage information" :default false]
   ["-o" "--output-dir" "Directory to produce files in"
    :default "target/catchpocket"]])

(defn usage [options-summary]
  (->> ["Usage: lein catchpocket [options] CONFIG-FILE ACTION"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  generate  create new lacinia schema files from the datomic database"
        ""]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)                                       ; help => exit OK with usage summary
      {::exit-message (usage summary) ::ok? true}

      errors                                                ; errors => exit with description of errors
      {::exit-message (error-msg errors)}

      (and (= 2 (count arguments))
           (#{"generate"} (second arguments)))
      {::action      (-> arguments second keyword)
       ::options     options
       ::config-file (first arguments)}

      :else                                                 ; failed custom validation => exit with usage summary
      {::exit-message (usage summary)})))

(defn exit!
  ([status]
   (exit! status nil))
  ([status msg]
   (when msg
     (log/error msg))
   (System/exit status)))

(defn -main [& args]
  (let [{:keys [::action ::options ::exit-message ::ok? ::config-file]} (validate-args args)]
    (when exit-message
      (exit! (if ok? 0 1) exit-message))
    (try
      (let [config (cf/construct-config config-file options)]
        (case action
          :generate (g/generate config)
          (exit! 1 (format "Unknown action %s!" action))))
      (catch Exception e
        (when-not (-> e ex-data :die?)
          (log/errorf e "Exception caught during %s: %s" (name action) (.getMessage e)))
        (exit! 1 (.getMessage e))))
    (System/exit 0)))
