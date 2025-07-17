(ns clj-swagger.auth
  (:require [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]
            [buddy.sign.util :as sign-util]
            [next.jdbc :as jdbc]))

(defn encrypt-pw [pw]
  (hashers/derive pw {:alg :argon2id}))

(def ^:private pw-query "SELECT password_hash AS encrypted, admin, customer_id
                         FROM users WHERE email = ?")

(defn verify-pw [conn user password]
  (when password
    (when-let [{:keys [encrypted admin customer-id] :as res} (first (jdbc/execute! conn [pw-query user]))]
      (when (:valid (hashers/verify password encrypted))
        {:email user
         :admin admin
         :customer-id customer-id}))))
#_
(comment
  (jdbc/execute! test-data/conn ["SELECT * FROM users;"])
  (verify-pw test-data/conn "finn@example.com" "123456"))

;; TODO move secret key to config
(def ^:private secret "your-secret-key")
(def ^:dynamic *exp-time* (* 60 60 3))

(defn generate-token
  ([claims] (generate-token claims *exp-time*))
  ([claims ^long exp-time]
   (-> claims
       (assoc :exp (+ (sign-util/now) exp-time))
       (jwt/sign secret))))

(defn validate-token [token]
  (-> (jwt/unsign token secret)
      (dissoc :exp)))

(comment
  (def token (generate-token {:sub "user"}))
  (validate-token token))
