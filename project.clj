(defproject ns-clone "0.1.0"
  :description "Clones a namespace by replicating some/all functions and invoking them using Pedestal Interceptor chains."
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.1.143"]
                 [io.pedestal/pedestal.interceptor "0.5.3"]]
  :sub ["datomic-peer-clone"]
  :plugins [[lein-sub "0.3.0"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
