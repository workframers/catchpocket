(ns harvester.main
  (:require [harvester.generate.core :as g]
            [clojure.tools.cli :as cli]
            [clojure.string :as string]))

(def cli-options
  [["-d" "--debug" "Produce debug output"
    :default false]
   ["-o" "--output-dir" "Directory to produce files in"
    :default "target/harvester"]])

(defn usage [options-summary]
  (->> ["Usage: lein harvester ACTION DATOMIC-URI"
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
      (:help options) ; help => exit OK with usage summary
      {::exit-message (usage summary) ::ok? true}

      errors ; errors => exit with description of errors
      {::exit-message (error-msg errors)}

      (and (= 2 (count arguments))
           (#{"generate"} (first arguments)))
      {::action (-> arguments first keyword)
       ::options options
       ::datomic-uri (second arguments)}

      :else ; failed custom validation => exit with usage summary
      {::exit-message (usage summary)})))

(defn exit! [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [::action ::options ::exit-message ::ok? ::datomic-uri]} (validate-args args)]
    (when exit-message
      (exit! (if ok? 0 1) exit-message))
    (try
      (case action
        :generate (g/generate datomic-uri options)
        (exit! 1 (format "Unknown action %s!" action)))
      (catch Exception e
        (.printStackTrace e)
        (exit! 1 (.getMessage e))))
    (System/exit 0)))
