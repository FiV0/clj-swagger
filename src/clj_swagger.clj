(ns clj-swagger
  (:require [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [jsonista.core :as json]
            [muuntaja.core :as m]
            [muuntaja.format.core :as mf]
            [reitit.coercion :as r.coercion]
            [reitit.coercion.spec :as rc.spec]
            [reitit.core :as r]
            [reitit.http :as http]
            [reitit.http.coercion :as rh.coercion]
            [reitit.http.interceptors.exception :as ri.exception]
            [reitit.http.interceptors.muuntaja :as ri.muuntaja]
            [reitit.http.interceptors.parameters :as ri.parameters]
            [reitit.interceptor.sieppari :as r.sieppari]
            [reitit.ring :as r.ring]
            [reitit.swagger :as r.swagger]
            [reitit.openapi :as openapi]
            [ring.adapter.jetty9 :as j]
            [ring.util.response :as ring-response]
            [reitit.swagger-ui :as swagger-ui])
  (:import org.eclipse.jetty.server.Server))


(def ^:private muuntaja-opts m/default-options)

(def http-routes
  [["/get" {:name :get
            :summary "Get request"
            :description "A simple GET request"}]

   ["/post" {:name :post
             :summary "Post request"
             :description "A simple POST request"}]

   ["/get-with-param/:id" {:name :get-with-param
                           :summary "Get request with parameter"
                           :description "A GET request with path and query parameters"}]

   ["/swagger.json" {:name :swagger-json
                     :no-doc true}]

   ["/openapi.json" {:name :openapi-json
                     :no-doc true}]

   ["/api-docs/*" {:name :swagger-ui
                   :no-doc true}]])

(defmulti ^:private route-handler :name, :default ::default)

(defmethod route-handler :get [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:handler (fn [{:as _req}]
                    {:status 200, :body {:message "Hello, world!"}})}})

(s/def ::a string?)
(s/def ::b int?)

(defmethod route-handler :post [_]
  {:muuntaja (m/create muuntaja-opts)

   :post {:handler (fn [{:keys [parameters] :as _req}]
                     {:status 200, :body {:original-message (:body parameters)}})
          :parameters {:body (s/keys :req-un [::a]
                                     :opt-un [::b])}}})

(s/def ::id int?)

(defmethod route-handler :get-with-param [_]
  {:muuntaja (m/create muuntaja-opts)

   :get {:parameters {:query {:q string?}
                      :path {:id ::id}}
         :handler (fn [{:keys [parameters] :as _req}]
                    (log/info :req parameters)
                    {:status 200, :body {:query-params (:query parameters)
                                         :path-params (:path parameters)}})}})

(defmethod route-handler :swagger-json [_]
  {:muuntaja (m/create muuntaja-opts)
   :get {:handler (r.swagger/create-swagger-handler)}})

(defmethod route-handler :swagger-ui [_]
  {:muuntaja (m/create muuntaja-opts)
   :get {:handler (swagger-ui/create-swagger-ui-handler)}})

(defmethod route-handler :openapi-json [_]
  {:muuntaja (m/create muuntaja-opts)
   :get {:handler (openapi/create-openapi-handler)}})


(defn- default-handler [^Throwable t _]
  {:status 500, :body {:class (.getName (.getClass t))
                       :message (.getMessage t)
                       :stringified (.toString t)} })

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
                                        [ri.parameters/parameters-interceptor]
                                        [ri.muuntaja/format-negotiate-interceptor]

                                        [ri.exception/exception-interceptor
                                         (merge ri.exception/default-handlers
                                                {::ri.exception/default default-handler
                                                 ::ri.exception/wrap
                                                 (fn [handler e req]
                                                   (log/debug e (format "response error (%s): '%s'" (class e) (ex-message e)))
                                                   (m/format-response m req (handler e req)))})]

                                        [ri.muuntaja/format-response-interceptor]
                                        [ri.muuntaja/format-request-interceptor]
                                        [rh.coercion/coerce-request-interceptor]]}})))

(defn- with-opts [opts]
  {:enter (fn [ctx]
            (update ctx :request into opts))})


(defn handler [extra-opts]
  (http/ring-handler router
                     (r.ring/create-default-handler)
                     {:executor r.sieppari/executor
                      :interceptors [[with-opts {:extra-opts extra-opts}]]}))

(defmethod ig/init-key :clj-swagger/server [_ {:keys [port dev-mode?] :as opts}]
  (let [f (fn [] (handler opts))
        ^Server server (j/run-jetty (if dev-mode?
                                      (r.ring/reloading-ring-handler f)
                                      (f))
                                    {:port port, :h2c? true, :h2? true
                                     :async? true :join? false})]
    (log/info "HTTP server started on port:" port)
    server))

(defmethod ig/halt-key! :clj-swagger/server [_ ^Server server]
  (.stop server)
  (log/info "HTTP server stopped"))
