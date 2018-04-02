(ns ns-clone.middleware
  (:require
    [clojure.test :refer :all]
    [io.pedestal.interceptor.helpers :as helpers :refer [around]]))

(defn logger
  "return an interceptor that record the time taken for a the current chain and records it using the fn-sym arg.
  Warning: do not include the conn or db map in the args or a circular reference will occur"
  [log-atom fn-sym & args]
  (around
    (fn [context]
      (assoc-in context [::logger fn-sym] (System/nanoTime)))
    (fn [context]
      (let [log-entry (cond-> {:fn       fn-sym
                               :duration (- (System/nanoTime)
                                            (get-in context [::logger fn-sym]))}
                              (seq args) (assoc :args args))]
        (swap! log-atom update :invocations conj log-entry))
      context)))




