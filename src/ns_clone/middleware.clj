(ns ns-clone.middleware
  (:require
    [clojure.test :refer :all]
    [io.pedestal.interceptor.helpers :as helpers :refer [around]]
    [ns-clone.middleware :as middleware]))

(defn logger
  "return an interceptor that record the time taken for a the current chain and records it using the fn-sym arg.
  Warning: do not include the conn or db map in the args or a circular reference will occur"
  [log-atom fn-sym & args]
  (around
    (fn [context]
      (assoc-in context [::logger-start fn-sym] (System/nanoTime)))
    (fn [context]
      (let [log-entry (cond-> {:fn       fn-sym
                               :duration (- (System/nanoTime)
                                            (get-in context [::logger-start fn-sym]))
                               :id       (:io.pedestal.interceptor.chain/execution-id context)}
                              (seq args) (assoc :args args))]
        (swap! log-atom (fn [log]
                          ;(sc.api/spy :f)
                          (let [group-present? (seq (:groups log))
                                last-group-position (when group-present? (dec (count (:groups log))))]
                            (cond-> log
                                    group-present? (update-in [:groups last-group-position :invocations] conj log-entry)
                                    true (update :invocations conj log-entry))))))
      context)))

(defn start-logger-group
  "mark the start of a new log group. this causes all subsequent api calls to be recorded in this new group as well as in the :invocations top level vector."
  [wrapped-arg group-key]
  (let [log-atom (::middleware/logger-data wrapped-arg)]
    (swap! log-atom
           (fn [log]
             (update log :groups
                     (fn [groups]
                       (let [new-group {:group       group-key
                                        :invocations []}]
                         (if (nil? groups)
                           [new-group]
                           (conj groups new-group)))))))))

(defn summarise-log
  "returns the data in a log-atom in a summary."
  [wrapped-arg summary-type]
  (let [log @(::middleware/logger-data wrapped-arg)]
    (case summary-type
      :groups-totals (->> (:groups log)
                          (mapv (fn [group]
                                  (update group :invocations
                                          (fn [calls]
                                            (->> calls
                                                 (group-by :fn)
                                                 (mapv (fn [[k v]]
                                                         (let [total (/ (reduce + (map :duration v)) 1000)]
                                                           {:fn      k
                                                            :count   (count v)
                                                            :sum     (int total)
                                                            :average (int (/ total (count v)))})))))))))
      )))



