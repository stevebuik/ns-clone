(ns ^{:doc "Functions for cloning a namespaces functions"}
ns-clone.core
  (:require
    [clojure.spec.alpha :as s]
    [io.pedestal.interceptor.chain :as chain]))

;;;;;;;;; SPECS ;;;;;;;;

(s/def ::api keyword?)

; making it clear that using the direct conn/db should be a careful choice.
; this keyword will make finding direct usages easy in src/git/commits.
(s/def ::UNSAFE! any?)

(defmulti with-app-context ::app)
(s/def ::with-app-context (s/multi-spec with-app-context ::app))

(defmethod with-app-context :default [_]
  map?)

(s/def ::app keyword?)
(s/def ::context (s/keys :req [::api ::app ::UNSAFE!]))
(s/def ::wrapped-app-context (s/and ::context ::with-app-context))

(comment

  (s/explain-data ::context {::api     :mock
                             ::app     :dev
                             ::UNSAFE! "the peer/client conn"})

  (require '[clojure.test.check.generators :as gen])
  (gen/sample (s/gen ::context) 2))

;;;;;;;; UTIL FNS ;;;;;;;;

(defn exec
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
  (if-let [app-key (::app (context-from-args args))]
    app-key
    (throw (ex-info "no ::app key found in any arg" {:args args}))))