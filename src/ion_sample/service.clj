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
(ns ion-sample.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.log :as log]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.ions :as provider]
            [ion-sample.retry :as retry]
            [ring.util.response :as ring-resp]
            [datomic.client.api :as d]))

;;
;; Datomic utils
;;
(def get-client
  "Given `app-name` and `region`, the AWS Region, returns a shared Datom client."
  (memoize (fn [app-name region]
             (d/client {:server-type :ion
                        :region      region
                        :system      app-name
                        :query-group app-name
                        :endpoint    (format "http://entry.%s.%s.datomic.net:8182/" app-name region)
                        :proxy-port  8182}))))

(def get-connection
  "Given `client`, a Datomic client,  and `db-name`, returns a datomic connection.
  Retries on failure."
  (fn [client db-name]
    (retry/with-retry #(d/connect client {:db-name db-name}))))

;;
;; Interceptors
;;
(def datomic-param-validation-interceptor
  (interceptor/interceptor
   {:name ::datomic-param-validation-interceptor
    :enter (fn [ctx]
             (and (get-in ctx [::provider/app-info :app-name])
                  (get-in ctx [::provider/params :db-name])
                  ctx))}))

(def datomic-client-interceptor
  (interceptor/interceptor
   {:name  ::datomic-client-interceptor
    :enter (fn [ctx]
             (let [app-name (get-in ctx [::provider/app-info :app-name])
                   region   (System/getenv "AWS_REGION")
                   client   (get-client app-name region)]
               (assoc ctx ::client client)))}))

(def datomic-conn-db-interceptor
  (interceptor/interceptor
   {:name  ::datomic-conn-db-interceptor
    :enter (fn [ctx]
             (let [db-name  (get-in ctx [::provider/params :db-name])
                   conn     (get-connection (::client ctx) db-name)
                   m        {::conn conn
                             ::db   (d/db conn)}]
               (-> ctx
                   (merge m)
                   (update-in [:request] merge m))))}))

(def pet-interceptor
  (interceptor/interceptor
   {:name  ::pet-interceptor
    :enter (fn [ctx]
             (let [db (::db ctx)
                   id (long (Integer/valueOf (or (get-in ctx [:request :path-params :id])
                                                 (get-in ctx [:request :json-params :id]))))
                   e  (d/pull db '[*] [:pet-store.pet/id id])]
               (assoc-in ctx [:request ::pet] (dissoc e :db/id))))}))

;; Handlers
;;
(defn about
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about))))

(defn home
  [request]
  (ring-resp/response "Hello World!"))

(defn pets
  [request]
  (let [db (::db request)]
    (ring-resp/response
     (map (comp #(dissoc % :db/id) first)
          (d/q '[:find (pull ?e [*])
                 :where [?e :pet-store.pet/id]]
               db)))))

(defn get-pet
  [request]
  (let [pet (::pet request)]
    (when (seq pet)
      (ring-resp/response pet))))

(defn add-pet
  [request]
  (let [conn                  (::conn request)
        pet                   (::pet request)
        {:keys [id name tag]} (:json-params request)]
    (if (seq pet)
      (ring-resp/status (ring-resp/response (format "Pet with id %d exists." id)) 500)
      (do
        (d/transact conn {:tx-data [{:db/id              "new-pet"
                                     :pet-store.pet/id   (long id)
                                     :pet-store.pet/name name
                                     :pet-store.pet/tag  tag}]})
        (ring-resp/status (ring-resp/response "Created") 201)))))

(defn update-pet
  [request]
  (let [conn               (::conn request)
        pet                (::pet request)
        id                 (Long/valueOf (get-in request [:path-params :id]))
        {:keys [name tag]} (:json-params request)]
    (when (seq pet)
      (let [{:keys [db-after]} (d/transact conn {:tx-data [{:db/id              [:pet-store.pet/id id]
                                                            :pet-store.pet/id   id
                                                            :pet-store.pet/name name
                                                            :pet-store.pet/tag  tag}]})]
        (ring-resp/response (dissoc (d/pull db-after '[*] [:pet-store.pet/id id]) :db/id))))))

(defn remove-pet
  [request]
  (let [conn (::conn request)
        pet  (::pet request)]
    (when (seq pet)
      (d/transact conn {:tx-data [[:db/retractEntity [:pet-store.pet/id (:pet-store.pet/id pet)]]]})
      (ring-resp/status (ring-resp/response "No Content.") 204))))

;;
;; Routing
;;
(def common-interceptors [(body-params/body-params) http/json-body])

(def app-interceptors
  (into [(io.pedestal.ions/datomic-params-interceptor)
         datomic-param-validation-interceptor
         datomic-client-interceptor
         datomic-conn-db-interceptor]
        common-interceptors))

(def routes #{["/" :get (conj common-interceptors `home)]
              ["/about" :get (conj common-interceptors `about)]
              ["/pets" :get (conj app-interceptors `pets)]
              ["/pets" :post (into app-interceptors [pet-interceptor `add-pet])]
              ["/pet/:id" :get (into app-interceptors [pet-interceptor `get-pet])]
              ["/pet/:id" :put (into app-interceptors [pet-interceptor `update-pet])]
              ["/pet/:id" :delete (into app-interceptors [pet-interceptor `remove-pet])]})

;;
;; Service
;;
;; See http/default-interceptors for additional options you can configure
(def service {;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/chain-provider provider/ion-provider})
