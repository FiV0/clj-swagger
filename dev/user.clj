(ns user
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as repl]
            [lambdaisland.classpath.watch-deps :as watch-deps]
            [integrant.repl :as ir]
            [integrant.core :as ig]))

(defn watch-deps! []
  (watch-deps/start! {:aliases [:dev :test]}))

(comment
  (repl/set-refresh-dirs (io/file "src") (io/file "dev"))
  (repl/refresh)
  (repl/clear)
  (watch-deps!))

(def config {:clj-swagger.server/server {:port 8081 :dev-mode? true}
             :clj-swagger/postgres {:dbtype "postgresql"
                                    :dbname "clj-swagger"
                                    :user "postgres"
                                    :password "changethis"}})

(ir/set-prep! (fn [] config))

(defn init
  "Start the server"
  ([] (init config))
  ([config]
   (watch-deps!)
   (ig/load-namespaces config)
   (ir/prep)
   (ir/init)))

(defn stop
  "Stop the server"
  []
  (ir/halt))

(defn reset
  "Restart the server"
  []
  (stop)
  (ir/go))

(comment
  (init)
  (reset)
  (stop))
