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
(ns ion-sample.datomic
  (:require [datomic.client.api :as d]))

(def petstore-schema
  [{:db/ident       :pet-store.pet/id
    :db/doc         "The id of a pet"
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident       :pet-store.pet/name
    :db/doc         "The name of a pet"
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :pet-store.pet/tag
    :db/doc         "The tag of a pet"
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}])

(def seed-data
  [{:pet-store.pet/id   1
    :pet-store.pet/name "Yogi"
    :pet-store.pet/tag  "dog"}
   {:pet-store.pet/id   2
    :pet-store.pet/name "Dante"
    :pet-store.pet/tag  "cat"}])

(defn- has-ident?
  [db ident]
  (contains? (d/pull db {:eid ident :selector [:db/ident]})
             :db/ident))

(defn- data-loaded?
  [db]
  (has-ident? db :pet-store.pet/id))

(defn load-dataset
  [conn]
  (let [db (d/db conn)]
    (if (data-loaded? db)
      :already-loaded
      (let [xact #(d/transact conn {:tx-data %})]
        (xact petstore-schema)
        (xact seed-data)
        :loaded))))
