(ns datomic-interceptors.api-test
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [datomic-interceptors.api :as d]
    [datomic-interceptors.middleware :as middleware]
    [io.pedestal.interceptor.chain :as chain]
    [io.pedestal.interceptor.helpers :as helpers :refer [before]]
    [clojure.spec.alpha :as s]))

;;;;;;;;; SETUP ;;;;;;;;

(def fake-conn "datomic conn")
(def fake-db "datomic db value")

(s/def ::username string?)
(s/def ::user (s/keys :req [::username]))

;;;; shared utils ;;;;

(defn db-delegate
  "return an interceptor that mocks a datomic d/db call"
  [conn]
  (before :db-delegate (fn [context]
                         (let [db-result fake-db            ; << a real delegate would invoke d/db here
                               wrapped-db-value (assoc conn ::d/UNSAFE! db-result)]
                           (assoc context ::d/result wrapped-db-value)))))

(defn pull-delegate
  "return an interceptor that mocks a datomic d/pull call"
  [_]
  (before :pull-delegate (fn [context]
                           ; a real delegate would invoke d/pull here, using the db arg
                           (let [pull-result {:db/id        100
                                              :identity/key 42}]
                             (assoc context ::d/result pull-result)))))

(defn query-delegate
  [_ _]
  (before :query-delegate (fn [context]
                            ; a real delegate would invoke d/q here, using the db arg
                            (let [query-result #{[100 42]}]
                              (assoc context ::d/result query-result)))))

(defn attribute-delegate
  [_]
  (before :attribute-delegate (fn [context]
                                ; a real delegate would invoke d/attribute here
                                (let [attr-result 42]
                                  (assoc context ::d/result attr-result)))))

; in tests, pretend to require a username for all datomic api calls
(s/def ::test-app-context (s/keys :req [::user]))

;;;;;;;;; TESTS ;;;;;;;;

;;;; :basic-test multi-methods ;;;;

; use the ::test-app-context for :basic-tests
(defmethod d/with-app-context :basic-tests [_] ::test-app-context)

; construct the interceptor chain for d/db
(defmethod d/db-context :basic-tests [conn]
  {::chain/queue [(db-delegate conn)]})

; construct the interceptor chain for d/pull
(defmethod d/pull-context :basic-tests [db pattern eid]
  {::chain/queue [(pull-delegate db)]})

; construct the interceptor chain for d/query
(defmethod d/query-context :basic-tests
  [& args]
  {::chain/queue [(query-delegate (first args) (rest args))]})

(defmethod d/attribute-context :basic-tests
  [db attrid]
  {::chain/queue [(attribute-delegate db)]})


(deftest basic-reads

  ; all tests here use the :basic-tests config above

  (let [app-context {::username "Dave"}
        conn {::d/api     :mock
              ::d/app     :basic-tests
              ::d/UNSAFE! fake-conn
              ::user      app-context}
        ; below looks just like the datomic api
        db (d/db conn)]

    (testing "database value"
      (is (= fake-db (::d/UNSAFE! db)) "conn was transformed into a db ")
      (is (= app-context (get-in db [::user])) "app context is present in db"))

    (testing "pull"
      (is (= {:db/id        100
              :identity/key 42}
             ; below looks just like the datomic api
             (d/pull db '[:db/id :identity/key] [:identity/key 42]))
          "pull returned data from delegate interceptor"))

    (testing "query"
      (is (= #{[100 42]}
             ; below looks just like the datomic api
             (d/q '[:find ?e ?k
                    :in $ ?id ?e
                    :where
                    [?e :identity/key ?k]]
                  db
                  100))))

    (testing "attribute"
      ; below looks just like the datomic api
      (is (= 42 (d/attribute db 100))))))

(deftest bad-data
  ; TODO add specs to api fns
  :TODO)

;;;; :middleware-test multi-methods ;;;;

; use the ::test-app-context for :middleware-tests as well
(defmethod d/with-app-context :middleware-tests [_] ::test-app-context)

(defmethod d/db-context :middleware-tests [conn]
  {::chain/queue [(middleware/logger (::middleware/logger-data conn) 'd/db)
                  (db-delegate conn)]})

(defmethod d/pull-context :middleware-tests [db pattern eid]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/pull pattern eid)
                  (pull-delegate db)]})

(defmethod d/query-context :middleware-tests
  [& args]
  (let [db (d/context-from-args args)]
    {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/q (first args))
                    (query-delegate (first args) (rest args))]}))

(defmethod d/attribute-context :middleware-tests
  [db attrid]
  {::chain/queue [(middleware/logger (::middleware/logger-data db) 'd/attribute attrid)
                  (attribute-delegate db)]})

(deftest stateful-middleware
  (let [app-context {::username "Dave"}
        log-atom (atom {:invocations []})                   ; must use a vector so that conj (in middleware/logger) appends at end
        conn {::d/api                  :mock
              ::d/app                  :middleware-tests
              ::d/UNSAFE!              fake-conn
              ::user                   app-context
              ::middleware/logger-data log-atom}
        db (d/db conn)]

    ; notice how datomic invocations below here look identical to datomic.api calls

    (d/pull db '[*] 1)
    (d/pull db '[*] 1)
    (d/pull db '[*] 1)
    (d/q '[:find ?e ?k
           :in $ ?id ?e
           :where
           [?e :identity/key ?k]]
         db
         100)
    (d/attribute db 100)

    ; now check the logged data
    (is (= '[d/db d/pull d/pull d/pull d/q d/attribute]
           (->> @log-atom
                :invocations
                (mapv :fn)))
        "logger interceptor recorded all api calls, including the db call")))

(deftest basic-writes
  :TODO)
