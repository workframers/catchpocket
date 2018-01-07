(ns harvester.logging
  (:require [taoensso.timbre :as timbre]
            [puget.color.ansi :as ansi]
            [cuerdas.core :as str])
  (:import (java.util TimeZone)))

(def ^:private color-log-map
  {:info   :green
   :warn   :yellow
   :error  :red
   :fatal  :magenta
   :report :blue})

;(defn default-output-fn
;  "Default (fn [data]) -> string output fn.
;  Use`(partial default-output-fn <opts-map>)` to modify default opts."
;  ([data] (default-output-fn nil data))
;  ([opts data]                                              ; For partials
;   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
;         {:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
;                 timestamp_ ?line]} data]
;     (str
;       #+clj (force timestamp_) #+clj " "
;       #+clj (force hostname_) #+clj " "
;       (string/upper-case (name level)) " "
;       "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
;       (force msg_)
;       (when-not no-stacktrace?
;         (when-let [err ?err]
;           (str "\n" (stacktrace err opts))))))))

(defn color-output-fn
  "Default (fn [data]) -> string output fn.
  Use`(partial default-output-fn <opts-map>)` to modify default opts."
  [options {:keys [level ?err msg_ ?ns-str ?file timestamp_ ?line] :as data}]
  (let [level-color (get color-log-map level)
        level-pad   ()
        colorize    (if (and (:color options) level-color)
                      #(ansi/sgr % level-color)
                      identity)]
    (format "%-8s %s [%s:%s] - %s%s"
            (force timestamp_)
            (-> level name str/upper (str/pad {:length 5 :type :right}) colorize)
            (or ?ns-str ?file "?")
            (or ?line "?")
            (force msg_)
            (if ?err
              (str "\n" (timbre/stacktrace ?err))
              ""))))

(defn- color-logger [options {:keys [level instant ?ns-str ?file ?line output-fn output_] :as data}]
  (let [lcolor (get color-log-map level)
        xform  (if (and (:color options) lcolor)
                 (partial timbre/color-str lcolor)
                 identity)
        output (force output_)]
    (println (ansi/sgr (str data) :underline))
    (println (ansi/sgr (str output) :bold))
    (-> data output-fn xform println)))

(defn config-logging! [options]
  (timbre/set-config!
    {:level          :debug
     :output-fn      (partial color-output-fn options)
     :timestamp-opts {:pattern  "HH:mm:ss"
                      :timezone (TimeZone/getDefault)}
     :appenders      {:console (timbre/println-appender)}})
  (timbre/set-level! (if (:debug options) :debug :info)))

(defn go []
  (let [options {:color true :debug true}]
    (config-logging! options)
    (timbre/spy options)
    (timbre/trace "trace log")
    (timbre/debug "debug log")
    (timbre/info "info log")
    (timbre/warn "warn log")
    (timbre/error "error log")
    (timbre/fatal "fatal log")
    (timbre/report "report log")
    (try
      (throw (ex-info "whoops" {:hi "there"}))
      (catch Exception e
        (timbre/error e "Uh oh")))))
