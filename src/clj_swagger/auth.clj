(ns clj-swagger.auth
  (:require [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]
            [buddy.sign.util :as sign-util]
            [next.jdbc :as jdbc]))

(defn encrypt-pw [pw]
  (hashers/derive pw {:alg :argon2id}))

(def ^:private password-query "SELECT email, is_superuser, hashed_password AS encrypted
                               FROM users WHERE email = ?")

(defn verify-pw [conn user password]
  (when password
    (when-let [{:keys [encrypted is-superuser] :as _res} (jdbc/execute-one! conn [password-query user])]
      (when (:valid (hashers/verify password encrypted))
        {:email user
         :is-superuser is-superuser}))))

(comment
  (require '[integrant.repl.state :as state])
  (def pg-conn (:clj-swagger/postgres state/system))

  (verify-pw pg-conn "admin@example.com" "changethis")
  (verify-pw pg-conn "admin@example.com" "garbage"))

;; TODO move secret to config
(def ^:private secret "please-change-this")
(def ^:dynamic *exp-time* (* 60 60 3)) ;; 3 hours

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
