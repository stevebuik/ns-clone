(ns datomic-peer.datomic-peer-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [ns-clone.core :as clone]
    [ns-clone.middleware :as middleware]
    [datomic-clone.api :as d]                               ; << the clone ns instead of the cloned datomic ns
    [io.pedestal.interceptor.chain :as chain]
    [datomic-peer.middleware :as peer-middleware]
    [clojure.spec.alpha :as s]
    [datomic-peer.test-utils :as utils])
  (:import (java.util UUID)))

(s/def ::username string?)
(s/def ::user (s/keys :req [::username]))
(s/def ::test-app-context (s/keys :req [::user]))

(defmethod clone/with-app-context :middleware-tests [_] ::test-app-context)

(defmethod d/db-context :middleware-tests [conn]
  {::chain/queue [(middleware/logger (::middleware/logger-data conn) 'd/db)
                  (peer-middleware/db-delegate conn)]})

(defmethod d/pull-context :middleware-tests [db pattern eid]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/pull pattern eid)
                  (peer-middleware/pull-delegate db pattern eid)]})

(defmethod d/query-context :middleware-tests
  [& args]
  (let [db (clone/context-from-args args)]
    {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/q (first args))
                    (apply peer-middleware/query-delegate args)]}))

(defmethod d/attribute-context :middleware-tests
  [db attrid]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/attribute key attrid)
                  (peer-middleware/attribute-delegate db attrid)]})

(defmethod d/transact-context :middleware-tests [conn data]
  {::chain/queue [(middleware/logger (::middleware/logger-data conn) 'd/transact data)
                  ; TODO add a txn annotation middleware
                  (peer-middleware/transact-delegate conn data)]})

(deftest peer-middleware
  (let [uri (str "datomic:mem://test-" (UUID/randomUUID))
        app-context {::username "Dave"}
        log-atom (atom {:invocations []})                   ; must use a vector so that conj (in middleware/logger) appends at end
        test-schema [{:db/ident       :identity/key
                      :db/valueType   :db.type/keyword
                      :db/cardinality :db.cardinality/one}]
        test-data [{:identity/key :foo}]
        {:keys [conn data-results]} (utils/setup uri test-schema test-data)
        conn {::clone/api              :mock
              ::clone/app              :middleware-tests
              ::clone/UNSAFE!          conn
              ::user                   app-context
              ::middleware/logger-data log-atom}]

    ; notice how datomic invocations below here look identical to datomic.api calls
    (try
      (let [db (d/db conn)
            test-entity-id (->> data-results :tempids vals first)]
        (is (= #{[test-entity-id :foo]}
               (d/q '[:find ?e ?k
                      :in $ ?e
                      :where
                      [?e :identity/key ?k]]
                    db
                    test-entity-id))
            "query returns expected result")
        (is (= {:db/id test-entity-id :identity/key :foo}
               (d/pull db '[*] test-entity-id))
            "pull returns expected result")
        (is (= :db.type/keyword (:value-type (d/attribute db [:db/ident :identity/key])))
            "attribute returns expected result")
        (let [{:keys [db-after tempids] :as result} @(d/transact conn [{:identity/key :bar}])
              new-entity-id (-> tempids vals first)]
          (is (= {:db/id new-entity-id :identity/key :bar}
                 (d/pull db-after '[*] new-entity-id))
              "new entity read using db-after returns expected result")))
      (catch Throwable t
        (utils/teardown uri)
        (throw t)))

    ; now check the logged data
    (is (= '[d/db d/q d/pull d/attribute d/transact d/pull]
           (->> @log-atom
                :invocations
                (mapv :fn)))
        "logger interceptor recorded all api calls, including the db call")))




