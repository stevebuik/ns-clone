(ns datomic-peer.datomic-peer-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [io.pedestal.interceptor.chain :as chain]
    [clojure.set :as set]

    [ns-clone.core :as clone]
    [ns-clone.middleware :as middleware]
    [datomic-clone.api :as d]                               ; << the clone ns instead of the cloned datomic ns
    [datomic-peer.middleware :as peer-middleware]

    [datomic-peer.test-utils :as utils])
  (:import (java.util UUID)
           (java.util.concurrent ExecutionException)))

(st/instrument)
(s/check-asserts true)

(s/def ::username string?)
(s/def ::user (s/keys :req [::username]))
(s/def ::test-app-context (s/keys :req [::user]))

(defmethod clone/with-app-context :peer-tests [_] ::test-app-context)

(defmethod d/tempid-context :all [partition]
  {::chain/queue [(peer-middleware/tempid-delegate partition)]})

(defmethod d/squuid-context :all []
  {::chain/queue [peer-middleware/squuid-delegate]})

(defmethod d/db-context :peer-tests [conn]
  {::chain/queue [(middleware/logger (::middleware/logger-data conn) 'd/db)
                  (peer-middleware/db-delegate conn)]})

(defmethod d/pull-context :peer-tests [db pattern eid]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/pull pattern eid)
                  (peer-middleware/pull-delegate db pattern eid)]})

(defmethod d/query-context :peer-tests
  [& args]
  (let [db (clone/context-from-args args)]
    {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/q (first args))
                    (apply peer-middleware/query-delegate args)]}))

(defmethod d/attribute-context :peer-tests
  [db attrid]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/attribute key attrid)
                  (peer-middleware/attribute-delegate db attrid)]})

(defmethod d/transact-context :peer-tests [conn data]
  {::chain/queue [(middleware/logger (::middleware/logger-data conn) 'd/transact data)
                  (let [tx-annotion (-> (select-keys conn [::clone/app ::user])
                                        (update ::user ::username)
                                        (set/rename-keys {::user      :user/username
                                                          ::clone/app :app/key}))]
                    (peer-middleware/transaction-annotator tx-annotion))
                  (peer-middleware/transact-delegate conn data)]})

(defmethod d/resolve-tempid-context :peer-tests
  [db tempids id]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/resolve-tempid tempids id)
                  (peer-middleware/resolve-tempid-delegate db tempids id)]})

(def test-schema [{:db/ident       :identity/key
                   :db/valueType   :db.type/keyword
                   :db/cardinality :db.cardinality/one
                   :db/unique      :db.unique/identity
                   :db/doc         "Application data for storing unique keywords"}
                  {:db/ident       :identity/id
                   :db/valueType   :db.type/uuid
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Application data for storing user facing ids"}
                  {:db/ident       :app/key
                   :db/valueType   :db.type/keyword
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Transaction annotation recording which 'app' chain was being used when a transaction occurred"}
                  {:db/ident       :user/username
                   :db/valueType   :db.type/string
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Transaction annotation record which user invoked a transaction"}])

(defn- test-conn
  []
  (let [uri (str "datomic:mem://test-" (UUID/randomUUID))
        app-context {::username "Dave"}
        log-atom (atom {:invocations []})                   ; must use a vector so that conj (in middleware/logger) appends at end
        test-data [{:identity/key :foo}]
        {:keys [conn data-results]} (utils/setup uri test-schema test-data)]
    {:uri          uri
     :wrapped-conn (s/assert ::clone/wrapped-app-context
                             {::clone/app              :peer-tests
                              ::clone/UNSAFE!          conn
                              ::user                   app-context
                              ::middleware/logger-data log-atom})
     :data-results data-results
     :log-atom     log-atom}))

(deftest peer-middleware
  (let [{:keys [uri wrapped-conn data-results log-atom]} (test-conn)]

    (try

      ; notice how datomic invocations below are identical to datomic.api calls

      (let [db (d/db wrapped-conn)
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
        (let [new-public-id (d/squuid)
              new-entity-id (d/tempid :db.part/user)
              {:keys [db-after tempids] :as result} @(d/transact wrapped-conn [{:db/id        new-entity-id
                                                                                :identity/id  new-public-id
                                                                                :identity/key :bar}])
              new-entity-id (d/resolve-tempid db-after tempids new-entity-id)
              pulled-entity {:db/id        new-entity-id
                             :identity/key :bar
                             :identity/id  new-public-id}]
          (is (= pulled-entity (d/pull db-after '[*] new-entity-id))
              "new entity read using db-after and id correct")
          (is (= pulled-entity (d/pull db-after '[*] [:identity/key :bar]))
              "new entity read using db-after and ref correct")
          (is (= {:app/key :peer-tests}
                 (-> '[:find (pull ?tx-id [:db/txInstant :app/key]) .
                       :in $ ?user
                       :where
                       [?tx-id :user/username ?user]]
                     (d/q db-after "Dave")
                     (select-keys [:app/key])))
              "the new entity transaction was annotated")))

      (is (= '[d/db d/q d/pull d/attribute d/transact d/resolve-tempid d/pull d/pull d/q]
             (->> @log-atom
                  :invocations
                  (mapv :fn)))
          "logger interceptor recorded all api calls, including the db call")

      (finally
        (utils/teardown uri)))))

(deftest peer-tx-errors
  (let [{:keys [uri wrapped-conn data-results log-atom]} (test-conn)
        db (d/db wrapped-conn)]
    (try
      (is (thrown? ExecutionException
                   @(d/transact wrapped-conn [{:identity/id  1 ; << incorrect type
                                               :identity/key :bar}]))
          "exception thrown by the nested future is seen by app code")
      (is (= '[d/db d/transact]
             (->> @log-atom
                  :invocations
                  (mapv :fn)))
          "logger interceptor recorded all api calls, including the failed transact call")
      (finally
        (utils/teardown uri)))))




