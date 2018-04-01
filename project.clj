(defproject datomic-interceptors "0.1.0-SNAPSHOT"
  :description "A Datomic shim that supports middleware using interceptors"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [io.pedestal/pedestal.interceptor "0.5.3"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
