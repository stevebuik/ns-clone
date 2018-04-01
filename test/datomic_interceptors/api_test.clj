(ns datomic-interceptors.api-test
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [datomic-interceptors.api :as d]
    [io.pedestal.interceptor.chain :as chain]
    [clojure.spec.alpha :as s]))

;;;;;;;;; SETUP ;;;;;;;;

(def fake-conn "datomic conn")
(def fake-db "datomic db value")

(s/def ::username string?)
(s/def ::user (s/keys :req [::username]))

; in tests, pretend to require a username for all datomic api calls
(defmethod d/with-app-context :test
  [_]
  (s/keys :req [::user]))

; construct the interceptor chain for d/db
(defmethod d/db-context :test
  [conn]
  {::chain/queue [{:name  "mock datomic db delegate"
                   :enter (fn [context]
                            ; a real delegate would invoke d/db here
                            (let [db-result fake-db]
                              (->> (assoc conn ::d/UNSAFE! db-result)
                                   (assoc context ::d/result))))}]})

; construct the interceptor chain for d/pull
(defmethod d/pull-context :test
  [db pattern eid]
  {::chain/queue [{:name  "mock datomic pull delegate"
                   :enter (fn [context]
                            ; a real delegate would invoke d/pull here
                            (let [pull-result {:db/id        100
                                               :identity/key 42}]
                              (assoc context ::d/result pull-result)))}]})

(defmethod d/query-context :test
  [query db & inputs]
  {::chain/queue [{:name  "mock datomic query delegate"
                   :enter (fn [context]
                            ; a real delegate would invoke d/q here
                            (let [query-result #{[100 42]}]
                              (assoc context ::d/result query-result)))}]})

(defmethod d/attribute-context :test
  [db attrid]
  {::chain/queue [{:name  "mock datomic attribute delegate"
                   :enter (fn [context]
                            ; a real delegate would invoke d/attribute here
                            (let [attr-result 42]
                              (assoc context ::d/result attr-result)))}]})

;;;;;;;;; TESTS ;;;;;;;;

(deftest read-using-api

  (let [app-context {::username "Dave"}
        conn {::d/api     :mock
              ::d/app     :test
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
