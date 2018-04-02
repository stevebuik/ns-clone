(defproject datomic-peer-clone "0.1.0-SNAPSHOT"
  :description "The Datomic peer API, cloned using ns-clone. Exposing enough fns to be compatible with the Cloud/Client API"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ns-clone "0.1.0-SNAPSHOT"]
                 [com.datomic/datomic-free "0.9.5561.50"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
