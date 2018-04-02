(ns datomic-peer.test-utils
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [datomic.api :as d]))

(defn setup
  [uri schema data]
  (d/create-database uri)
  (let [conn (d/connect uri)]
    ; deref to block till complete
    @(d/transact conn schema)
    {:conn         conn
     :data-results @(d/transact conn data)}))

(defn teardown
  [uri]
  (d/delete-database uri))
