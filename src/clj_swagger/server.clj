(ns clj-swagger.server
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clj-swagger.auth :as auth]
            [clojure.tools.logging :as log]
            [cognitect.transit :as transit]
            [integrant.core :as ig]
            [jsonista.core :as json]
            [muuntaja.core :as m]
            [muuntaja.format.core :as mf]
            [reitit.coercion :as r.coercion]
            [reitit.coercion.spec :as rc.spec]
            [reitit.core :as r]
            [reitit.dev.pretty :as pretty]
            [reitit.http :as http]
            [reitit.http.coercion :as rh.coercion]
            [reitit.http.interceptors.exception :as ri.exception]
            [reitit.http.interceptors.muuntaja :as ri.muuntaja]
            [reitit.http.interceptors.parameters :as ri.parameters]
            [reitit.interceptor.sieppari :as r.sieppari]
            [reitit.openapi :as openapi]
            [reitit.ring :as r.ring]
            [reitit.spec :as rs]
            [reitit.swagger :as r.swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.adapter.jetty9 :as j]
            [ring.util.response :as ring-response]
            [expound.alpha :as expound]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql])
  (:import org.eclipse.jetty.server.Server
           [clojure.lang ExceptionInfo]))

(def ^:private muuntaja-opts m/default-options)

(def http-routes
  [["/v1"
    ["/get" {:name :get
             :summary "Get request"
             :description "A simple GET request"}]

    ["/post" {:name :post
              :summary "Post request"
              :description "A simple POST request"}]

    ["/login/acces-token" {:name :login-access-token
                           :summary "Get an access token"}]

    ["/login/test-token" {:name :login-test-token
                          :summary "Test an access token"}]

    ["/users" {:name :users
               :swagger {:security [{"auth" []}]}}]

    ["/users/me" {:name :users-me
                  :swagger {:security [{"auth" []}]}}]

    ["/items" {:name :items
               :swagger {:security [{"auth" []}]}}]

    ["/items/:id" {:name :items-by-id
                   :swagger {:security [{"auth" []}]}}]

    #_["/get-with-param/:id" {:name :get-with-param
                              :summary "Get request with parameter"
                              :description "A GET request with path and query parameters"}]]

   ["/swagger.json" {:name :swagger-json
                     :no-doc true
                     :swagger {:securityDefinitions {"auth" {:type :apiKey
                                                             :in :header
                                                             :name "Authorization"}}}}]

   ["/openapi.json" {:name :openapi-json
                     :no-doc true}]

   ["/api-docs/*" {:name :swagger-ui
                   :no-doc true}]])

(defmulti ^:private route-handler :name, :default ::default)

(defmethod route-handler :get [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:handler (fn [{:as _req}]
                    {:status 200, :body {:message "Hello, world!"}})}})

(s/def ::message string?)

(defmethod route-handler :post [_]
  {:muuntaja (m/create muuntaja-opts)

   :post {:handler (fn [{:keys [parameters] :as _req}]
                     {:status 200, :body {:original-message (:body parameters)}})
          :parameters {:body (s/keys :req-un [::message])}}})

(s/def ::username string?)
(s/def ::password string?)

(defmethod route-handler :login-access-token [_]
  {:muuntaja (m/create muuntaja-opts)

   :post {:handler (fn [{:keys [parameters conn] :as _req}]
                     (let [{:keys [username password]} (:body parameters)]
                       (if-let [claims (auth/verify-pw conn username password)]
                         {:status 200, :body {:token (auth/generate-token claims)}}
                         {:status 401, :body {:message "Authentication failed!"}})))
          :parameters {:body (s/keys :req-un [::username ::password])}}})

(defmethod route-handler :login-test-token [_]
  {:muuntaja (m/create muuntaja-opts)

   :post {:handler (fn [{:keys [headers] :as _req}]
                     (try
                       (if-let [claims (some-> (get headers "authorization") auth/validate-token)]
                         {:status 200 :body claims}
                         {:status 401, :body {:message "Authentication failed!"}})
                       (catch ExceptionInfo e
                         (let [data (ex-data e)]
                           (if (= :validation (:type data))
                             (throw (ex-info (ex-message e) (assoc data ::status 419)))
                             (throw e))))))}})

(defn authenticate-interceptor [only-superuser?]
  {:name ::authenticate
   :enter (fn [{:keys [request] :as ctx}]
            (let [{:keys [headers]} request]
              (try
                (if-let [{:keys [is-superuser] :as claims} (some-> (get headers "authorization")
                                                                   auth/validate-token)]
                  (do (log/debug "User authentication" claims)
                      (if (or (not only-superuser?) is-superuser)
                        (-> ctx
                            (update-in [:request :headers] dissoc "authorization")
                            (assoc-in [:request :claims] claims))
                        (assoc ctx :error (ex-info "Unauthorized" {:type ::unauthorized
                                                                   ::status 403}))))

                  (assoc ctx :error (ex-info "Authentication failed" {:type ::unauthenticated
                                                                      ::status 401})))
                (catch ExceptionInfo e
                  (let [data (ex-data e)]
                    (if (= :validation (:type data))
                      (assoc ctx :error (ex-info (ex-message e) (assoc data ::status 419)))
                      (throw e)))))))})

(s/def ::email string?)
(s/def ::is-superuser boolean?)
(s/def ::is-active boolean?)
(s/def ::password string?)
(s/def ::full-name string?)

(s/def ::id int?)
(s/def ::user (s/keys :req-un [::id ::email ::is-superuser ::is-active]
                      :opt-un [::full-name]))
(s/def ::user-update (s/keys :opt-un [::email ::full-name]))

(s/def ::title string?)
(s/def ::description string?)
(s/def ::item (s/keys :req-un [::id ::title]
                      :opt-un [::description]))
(s/def ::count int?)
(s/def ::items-response (s/keys :req-un [::data ::count]))
(s/def ::data (s/coll-of ::item))
(s/def ::item-input (s/keys :req-un [::title]
                            :opt-un [::description]))

(defmethod route-handler :users [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:interceptors [(authenticate-interceptor true)]
         :summary "Read users"
         :handler (fn [{:keys [conn] :as _req}]
                    (let [data (jdbc/execute! conn ["SELECT id, email, is_superuser, is_active, full_name FROM users"])]
                      {:status 200, :body {:data data :count (count data)}}))}

   :post {:interceptors [(authenticate-interceptor true)]
          :summary "Create user"
          :parameters {:body (s/keys :req-un [::email ::password]
                                     :opt-un [::is-superuser ::is-active ::full-name])}
          :handler (fn [{:keys [parameters conn] :as _req}]
                     (let [{:keys [password] :as body} (:body parameters)]
                       {:status 200, :body (-> (sql/insert! conn :users (-> body
                                                                            (assoc :hashed-password (auth/encrypt-pw password))
                                                                            (dissoc :password)))
                                               (dissoc :hashed-password))}))}})

(defmethod route-handler :users-me [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:interceptors [(authenticate-interceptor false)]
         :summary "Get current user"
         :description "Returns the current user information based on JWT token"
         :responses {200 {:body ::user}
                     401 {:description "Authentication failed"}
                     404 {:description "User not found"}}
         :handler (fn [{:keys [claims conn] :as _req}]
                    (let [{:keys [id]} claims
                          user (jdbc/execute-one! conn ["SELECT id, email, is_superuser, is_active, full_name FROM users WHERE id = ?" id])]
                      (if user
                        {:status 200, :body user}
                        {:status 404, :body {:message "User not found"}})))}

   :patch {:interceptors [(authenticate-interceptor false)]
           :summary "Update current user"
           :description "Updates the current user's email and full name based on JWT token"
           :parameters {:body ::user-update}
           :responses {200 {:body ::user}
                       400 {:description "Invalid input"}
                       401 {:description "Authentication failed"}
                       404 {:description "User not found"}}
           :handler (fn [{:keys [claims parameters conn] :as _req}]
                      (let [{:keys [id]} claims
                            update-data (:body parameters)]
                        (if (empty? update-data)
                          {:status 400, :body {:message "No fields to update"}}
                          (if-let [_updated-user (sql/update! conn :users (dissoc update-data :id) {:id id})]
                            (let [user (jdbc/execute-one! conn ["SELECT id, email, is_superuser, is_active, full_name FROM users WHERE id = ?" id])]
                              {:status 200, :body user})
                            {:status 404, :body {:message "User not found"}}))))}})

(defmethod route-handler :items [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:interceptors [(authenticate-interceptor false)]
         :summary "Get all items"
         :description "Returns all items"
         :responses {200 {:body ::items-response}
                     401 {:description "Authentication failed"}}
         :handler (fn [{:keys [conn] :as _req}]
                    (let [data (jdbc/execute! conn ["SELECT id, title, description FROM items"])]
                      {:status 200, :body {:data data :count (count data)}}))}

   :post {:interceptors [(authenticate-interceptor false)]
          :summary "Create item"
          :description "Creates a new item with auto-generated ID"
          :parameters {:body ::item-input}
          :responses {201 {:body ::item}
                      400 {:description "Invalid input"}
                      401 {:description "Authentication failed"}}
          :handler (fn [{:keys [parameters conn] :as _req}]
                     (let [item-data (:body parameters)
                           created-item (sql/insert! conn :items item-data)]
                       {:status 201, :body (first created-item)}))}})

(defmethod route-handler :items-by-id [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:interceptors [(authenticate-interceptor false)]
         :summary "Get item by ID"
         :description "Returns a single item by ID"
         :parameters {:path (s/keys :req-un [::id])}
         :responses {200 {:body ::item}
                     401 {:description "Authentication failed"}
                     404 {:description "Item not found"}}
         :handler (fn [{:keys [parameters conn] :as _req}]
                    (let [id (-> parameters :path :id)
                          item (jdbc/execute-one! conn ["SELECT id, title, description FROM items WHERE id = ?" id])]
                      (if item
                        {:status 200, :body item}
                        {:status 404, :body {:message "Item not found"}})))}

   :delete {:interceptors [(authenticate-interceptor false)]
            :summary "Delete item by ID"
            :description "Deletes a single item by ID"
            :parameters {:path (s/keys :req-un [::id])}
            :responses {204 {:description "Item deleted successfully"}
                        401 {:description "Authentication failed"}
                        404 {:description "Item not found"}}
            :handler (fn [{:keys [parameters conn] :as _req}]
                       (let [id (-> parameters :path :id)
                             result (sql/delete! conn :items {:id id})]
                         (if result
                           {:status 204}
                           {:status 404, :body {:message "Item not found"}})))}})

(defmethod route-handler :swagger-json [_]
  {:muuntaja (m/create muuntaja-opts)
   :get {:handler (r.swagger/create-swagger-handler)}})

(defmethod route-handler :swagger-ui [_]
  {:muuntaja (m/create muuntaja-opts)
   :get {:handler (swagger-ui/create-swagger-ui-handler
                   {:config {:validatorUrl nil
                             :persistAuthorization true}})}})

(defmethod route-handler :openapi-json [_]
  {:muuntaja (m/create muuntaja-opts)
   :get {:handler (openapi/create-openapi-handler)}})

(defn- default-handler [^Throwable t _]
  {:status 500, :body {:class (.getName (.getClass t))
                       :message (.getMessage t)
                       :stringified (.toString t)}})

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:theme :figwheel-theme, :print-specs? false})
        handler (ri.exception/create-coercion-handler status)]
    (fn [exception request]
      (printer (-> exception ex-data :problems))
      (handler exception request))))

(def router
  (let [m (m/create muuntaja-opts)]
    (http/router http-routes
                 {:expand (fn [{route-name :name, :as route} opts]
                            (r/expand (cond-> route
                                        route-name (merge (route-handler route)))
                                      opts))

                  :data {:muuntaja m
                         :coercion rc.spec/coercion
                         :interceptors [r.swagger/swagger-feature
                                        openapi/openapi-feature
                                        (ri.parameters/parameters-interceptor)
                                        (ri.muuntaja/format-negotiate-interceptor)
                                        (ri.muuntaja/format-response-interceptor)
                                        (ri.muuntaja/format-request-interceptor)

                                        (ri.exception/exception-interceptor
                                         (merge ri.exception/default-handlers
                                                {:reitit.coercion/request-coercion (coercion-error-handler 422)
                                                 :reitit.coercion/response-coercion (coercion-error-handler 500)
                                                 ::ri.exception/default default-handler
                                                 ::ri.exception/wrap
                                                 (fn [handler e req]
                                                   (prn (:type (ex-data e))
                                                        (handler e req))
                                                   (log/error (format "response error (%s): '%s'" (class e) (ex-message e)))
                                                   (m/format-response m req (handler e req)))}))

                                        (rh.coercion/coerce-request-interceptor)]}
                  :validate rs/validate})))

(defn- with-opts [opts]
  {:enter (fn [ctx]
            (update ctx :request into opts))})

(defn handler [{:keys [conn] :as extra-opts}]
  ;; (assert conn "db-conn is missing!!")
  (http/ring-handler router
                     (r.ring/create-default-handler)
                     {:executor r.sieppari/executor
                      :interceptors [[with-opts {:conn conn
                                                 :extra-opts extra-opts}]]}))

(defmethod ig/expand-key :clj-swagger.server/server [k opts]
  {k (assoc opts :conn (ig/ref :clj-swagger/postgres))})

(defmethod ig/init-key :clj-swagger.server/server [_ {:keys [port dev-mode?] :as opts}]
  (let [f (fn [] (handler opts))
        ^Server server (j/run-jetty (if dev-mode?
                                      (r.ring/reloading-ring-handler f)
                                      (f))
                                    {:port port, :h2c? true, :h2? true
                                     :async? true :join? false})]
    (log/info "HTTP server started on port:" port)
    server))

(defmethod ig/halt-key! :clj-swagger.server/server [_ ^Server server]
  (.stop server)
  (log/info "HTTP server stopped"))
