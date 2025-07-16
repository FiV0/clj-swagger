(ns clj-swagger.server-test
  (:require [cheshire.core :as json]
            [clj-swagger.server :as server]
            [clj-swagger.test-system :as ts]
            [clojure.java.io :as io]
            [clojure.test :as t :refer [deftest]]
            [hato.client :as http]
            [ring.mock.request :as mock]))

(t/use-fixtures :once ts/system-fixture)

(defn ->url [s]
  (str "http://localhost:" (-> ts/test-config :clj-swagger.server/server :port) s))

(deftest test-get-endpoint
  (t/testing "GET /get"
    (let [response (http/get (->url "/get") {:accept :json :throw-exceptions false :as :json})]

      (t/is (= 200 (:status response)))
      (t/is (= {:message "Hello, world!"} (:body response))))))

(deftest test-get-with-mock-request-and-handler
  (t/testing "GET /get with mock request"
    (let [request (mock/request :get "/get")
          response ((server/handler {}) request)]
      (t/is (= 200 (:status response)))

      (t/is (= {:message "Hello, world!"} (json/parse-stream (io/reader (:body response)) true))))))

(deftest test-swagger-validation
  (t/testing "Invalid request data returns 400"
    (let [response (http/post (->url "/post")
                              {:content-type :json
                               :body (json/generate-string {:invalid "data"})
                               :throw-exceptions false
                               :as :json})]
      (t/is (= 400 (:status response)))
      ;; TODO make this more readable
      #_(t/is (= nil (:body response))))))
