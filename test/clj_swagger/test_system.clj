(ns clj-swagger.test-system
  (:require [integrant.core :as ig]))

(def test-config {:clj-swagger.server/server {:port 8989 :dev-mode? true}
                  :clj-swagger/postgres {:dbtype "postgresql"
                                         :dbname "clj-swagger"
                                         :user "postgres"
                                         :password "changethis"}})

(def test-system (atom nil))

(defn system-fixture [f]
  (ig/load-namespaces test-config)
  (reset! test-system (ig/init (ig/expand test-config)))
  (try
    (f)
    (finally
      (ig/halt! @test-system))))

(defn db-fixture [f]
  ;;TODO
  (f))
