(ns datomic-clone.api
  (:require
    [ns-clone.core :as clone]
    [clojure.spec.alpha :as s]))

;;;;;;;;; DATOMIC INTERCEPTORS FACTORIES  ;;;;;;;;

; only providing fns that are available in call Datomic apis. e.g. not including d/entity since the client api doesn't support it

; TODO generate these multi-methods and the clone fns below using a macro

(defmulti db-context clone/context-dispatch)
(defmulti pull-context clone/context-dispatch)
(defmulti query-context clone/context-dispatch)
(defmulti attribute-context clone/context-dispatch)
(defmulti transact!-context clone/context-dispatch)

;;;;;;;;; NS/API CLONE FNS ;;;;;;;;

(defn db
  [& args]
  (clone/exec (apply db-context args)))

(defn pull
  [& args]
  (clone/exec (apply pull-context args)))

(defn q
  [& args]
  (clone/exec (apply query-context args)))

(defn attribute
  [& args]
  (clone/exec (apply attribute-context args)))

(defn transact!
  [& args]
  (clone/exec (apply transact!-context args)))

;;;;;;;; FSPECS (optional) ;;;;;;;

; only fns where the position of the wrapped arg is known can be spec'd
; e.g. d/q allows the db arg to be anywhere except the first arg

; NOTE : if fspecs are added, then api clients must implement clone/with-app-context to use app-specific specs

(s/fdef db :args (s/cat :conn ::clone/wrapped-app-context))
(s/fdef pull :args (s/cat :db ::clone/wrapped-app-context
                          :expr vector?
                          :eid any?))
(s/fdef attribute :args (s/cat :attribute keyword?
                               :db ::clone/wrapped-app-context
                               :aid any?))
(s/fdef transact! :args (s/cat :conn ::clone/wrapped-app-context
                               :data vector?))
