(ns datomic-interceptors.api-test
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [datomic-interceptors.api :as d]
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
                         ; a real delegate would invoke d/db here
                         (let [db-result fake-db]
                           (->> (assoc conn ::d/UNSAFE! db-result) ; <<< db value goes here, the rest of the conn map is untouched
                                (assoc context ::d/result))))))

(defn pull-delegate
  "return an interceptor that mocks a datomic d/pull call"
  [_]
  (before :pull-delegate (fn [context]
                           ; a real delegate would invoke d/pull here, using the db arg
                           (let [pull-result {:db/id        100
                                              :identity/key 42}]
                             (assoc context ::d/result pull-result)))))

(defn query-delegate
  [_]
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

;;;; basic test multi-methods ;;;;

(defmethod d/with-app-context :basic [_] ::test-app-context)

; construct the interceptor chain for d/db
(defmethod d/db-context :basic [conn]
  {::chain/queue [(db-delegate conn)]})

; construct the interceptor chain for d/pull
(defmethod d/pull-context :basic [db pattern eid]
  {::chain/queue [(pull-delegate db)]})

; construct the interceptor chain for d/query
(defmethod d/query-context :basic
  [query db & inputs]
  {::chain/queue [(query-delegate db)]})

(defmethod d/attribute-context :basic
  [db attrid]
  {::chain/queue [(attribute-delegate db)]})

;;;; middleware test multi-methods ;;;;


;;;;;;;;; TESTS ;;;;;;;;

(deftest basic-reads

  ; all tests here use the :basic config above

  (let [app-context {::username "Dave"}
        conn {::d/api     :mock
              ::d/app     :basic
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
