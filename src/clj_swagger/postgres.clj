(ns clj-swagger.postgres
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]
            [next.jdbc.types :as types])
  (:import [java.sql Connection PreparedStatement]
           [org.postgresql.util PGobject]))

(def mapper (json/object-mapper {:decode-key-fn keyword}))
(def ->json json/write-value-as-string)
(def <-json #(json/read-value % mapper))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (cond (#{"jsonb" "json"} type) (some-> value <-json (with-meta {:pgtype type}))
          :else value)))

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))

(defmethod ig/init-key :clj-swagger/postgres [_ opts]
  (log/info "Initializing Postgres connection")
  (let [conn (-> (jdbc/get-connection opts)
                 (jdbc/with-options jdbc/unqualified-snake-kebab-opts))]
    conn))

(defmethod ig/halt-key! :clj-swagger/postgres [_ {:keys [connectable]}]
  (.close ^Connection connectable))


(comment
  (require '[integrant.repl.state :as state])

  (def pg-conn (:clj-swagger/postgres state/system))

  (jdbc/execute! pg-conn ["SELECT 1"]))
