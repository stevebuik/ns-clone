(ns ^{:doc "Functions that match the Datomic API, but execute via interceptors."}
datomic-interceptors.api
  (:require
    [clojure.spec.alpha :as s]
    [io.pedestal.interceptor.chain :as chain]))

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

(comment

  (s/explain-data ::connection {::api     :mock
                                ::app     :dev
                                ::UNSAFE! "the peer/client conn"})

  (require '[clojure.test.check.generators :as gen])
  (gen/sample (s/gen ::connection) 2))

;;;;;;;; UTIL FNS ;;;;;;;;

(defn- exec
  [context]
  (::result (chain/execute context)))

(defn context-from-args
  "return the first instance of a seq of values that contains an ::app key"
  [args]
  (->> args
       (filter map?)
       (filter ::app)
       first))

(defn context-dispatch
  [& args]
  (::app (context-from-args args)))

;;;;;;;;; DATOMIC INTERCEPTORS FACTORIES  ;;;;;;;;

; only providing fns that are available in call Datomic apis. e.g. not including d/entity since the client api doesn't support it

; TODO generate these multi-methods and the clone fns below using a macro

(defmulti db-context context-dispatch)
(defmulti pull-context context-dispatch)
(defmulti query-context context-dispatch)
(defmulti attribute-context context-dispatch)

;;;;;;;;; NS/API CLONE FNS ;;;;;;;;

(defn db
  [& args]
  (exec (apply db-context args)))

(defn pull
  [& args]
  (exec (apply pull-context args)))

(defn q
  [& args]
  (exec (apply query-context args)))

(defn attribute
  [db attrid]
  (exec (attribute-context db attrid)))


