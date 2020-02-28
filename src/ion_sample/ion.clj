;; Copyright 2020 Cognitect, Inc.
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
;; which can be found in the file epl-v10.html at the root of this distribution.
;;
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;;
;; You must not remove this notice, or any other, from this software.
(ns ion-sample.ion
  (:require [datomic.ion.lambda.api-gateway :as apig]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.ions :as provider]
            [ion-sample.datomic]
            [ion-sample.retry :as retry]
            [ion-sample.service :as service]))

(defn handler
  "Ion handler"
  [service-map]
  (-> service-map
      http/default-interceptors
      http/create-provider))

(def app
  "Application ion."
  (apig/ionize (handler service/service)))

(def ensure-db-interceptor
  "Interceptor for initializing the application's Datomic database."
  (interceptor/interceptor
   {:name ::ensure-db-interceptor
    :enter (fn [ctx]
             (let [client  (::service/client ctx)
                   db-name (get-in ctx [::provider/params :db-name])]
               (assoc ctx
                      ::ensure-db-result
                      (retry/with-retry #(ion-sample.datomic/ensure-db client db-name 'ion-sample.datomic/load-dataset)))))}))

(defn ensure-db
  "Datomic database initialization ion."
  [_]
  (let [ctx (chain/execute-only {} :enter [(provider/datomic-params-interceptor)
                                           service/datomic-param-validation-interceptor
                                           service/datomic-client-interceptor
                                           ensure-db-interceptor])]
    (pr-str (::ensure-db-result ctx))))

(comment

 ;; ensure db
 (ensure-db nil)

 (def h (handler service/service))

 (h {:server-port    0
     :server-name    "localhost"
     :remote-addr    "127.0.0.1"
     :uri            "/"
     :scheme         "http"
     :request-method :get
     :headers        {}})

 (slurp (:body (h {:server-port    0
                   :server-name    "localhost"
                   :remote-addr    "127.0.0.1"
                   :uri            "/pets"
                   :scheme         "http"
                   :request-method :get})))

 (h {:server-port    0
     :server-name    "localhost"
     :remote-addr    "127.0.0.1"
     :uri            "/pets"
     :scheme         "http"
     :request-method :post
     :headers        {"content-type" "application/json"}
     :body           (clojure.java.io/input-stream (.getBytes "{\"id\": 302, \"name\": \"Foob\", \"tag\": \"bird\"}"))})

 (slurp (:body (h {:server-port    0
                   :server-name    "localhost"
                   :remote-addr    "127.0.0.1"
                   :uri            "/pet/302"
                   :scheme         "http"
                   :request-method :get
                   :headers        {}})))

 (slurp (:body (h {:server-port    0
                   :server-name    "localhost"
                   :remote-addr    "127.0.0.1"
                   :uri            "/pet/302"
                   :scheme         "http"
                   :request-method :put
                   :headers        {"content-type" "application/json"}
                   :body           (clojure.java.io/input-stream (.getBytes "{\"id\": 302, \"name\": \"Foox\", \"tag\": \"bird\"}"))})))

 (h {:server-port    0
     :server-name    "localhost"
     :remote-addr    "127.0.0.1"
     :uri            "/pet/302"
     :scheme         "http"
     :request-method :delete
     :headers        {}})

 )
