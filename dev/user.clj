(ns user
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.repl :as repl]
            [lambdaisland.classpath.watch-deps :as watch-deps]
            [integrant.repl :as ir]))

(defn watch-deps!
  []
  (watch-deps/start! {:aliases [:dev :test]}))

(defn go []
  (watch-deps!))

(comment
  (repl/set-refresh-dirs (io/file "src") (io/file "dev"))
  (repl/refresh)
  (repl/clear)

  (watch-deps!)

  )


(def config {:clj-swagger/server {:port 8080}})

(ir/set-prep! (fn [] config))

(def halt ir/halt)
(def reset ir/reset)

(comment
  (ir/prep)
  (ir/init)
  (do
    (ir/halt)
    (ir/go)
    )

  )
