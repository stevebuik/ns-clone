(ns ^{:doc "Functions that match the Datomic API, but execute via interceptors."}
datomic-interceptors.api
  (:require
    [clojure.spec.alpha :as s]
    [io.pedestal.interceptor.chain :as chain]))

(declare db-context pull-context query-context attribute-context)

;;;;;;;;; SPECS ;;;;;;;;

(s/def ::api #{:client :peer :mock})

; making it clear that using the direct conn/db should be a careful choice.
; this keyword will make finding direct usages easy in src/git/commits.
(s/def ::UNSAFE! any?)

(defmulti with-app-context ::app)
(s/def ::with-app-context (s/multi-spec with-app-context ::app))

(defmethod with-app-context :default [_]
  map?)

(s/def ::app keyword?)
(s/def ::connection (s/and (s/keys :req [::api ::app ::UNSAFE!])
                           ::with-app-context))

;;;;;;;;; API WRAPPER FNS ;;;;;;;;

(defn- exec
  [context]
  (::result (chain/execute context)))

(defn db
  [connection]
  (exec (db-context connection)))

(defn pull
  [db pattern eid]
  (exec (pull-context db pattern eid)))

(defn q
  [query db & inputs]                                       ; db must be the 2nd arg. not quite as flexible as the datomic api
  (exec (query-context query db inputs)))

(defn attribute
  [db attrid]
  (exec (attribute-context db attrid)))

;;;;;;;;; INTERCEPTORS ;;;;;;;;



;;;;;;;;; INTERCEPTORS API  ;;;;;;;;

; only providing fns that are available in call Datomic apis. e.g. not including d/entity since the client api doesn't support it

(defmulti db-context ::app)
(defmulti pull-context (fn [db _ _] (::app db)))
(defmulti query-context (fn [query db inputs] (::app db)))
(defmulti attribute-context (fn [db attrid] (::app db)))

(comment

  (s/explain-data ::connection {::api     :mock
                                ::app     :dev
                                ::UNSAFE! "the peer/client conn"})

  (require '[clojure.test.check.generators :as gen])
  (gen/sample (s/gen ::connection) 2))

