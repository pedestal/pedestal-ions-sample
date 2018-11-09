;; Copyright 2018 Cognitect, Inc.
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
            [ion-sample.service :as service]))

(defn handler
  "Ion handler"
  [service-map]
  (-> service-map
      http/default-interceptors
      http/create-provider))

(def app (apig/ionize (handler service/service)))

(comment

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
