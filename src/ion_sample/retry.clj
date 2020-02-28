(ns ion-sample.retry
  "The following implementation is taken from the Datomic Ions Starter project.
  See https://github.com/Datomic/ion-starter/blob/master/src/datomic/ion/starter/utils.clj"
  (:require [cognitect.anomalies :as anomalies]))

(def retryable-anomaly?
  "Set of retryable anomalies."
  #{::anomalies/busy
    ::anomalies/unavailable
    ::anomalies/interrupted})

(defn with-retry
  "Try op, return result if successful, if op throws, check exception against retry? pred,
  retrying if the predicate returns true. Optional backoff function controls retry wait. Backoff
  function should return msec backoff desired or nil to stop retry.
  Defaults to trying 10 times with linear backoff. "
  [op & {:keys [retry? backoff]
         :or {retry? (fn [e]
                       (-> e ex-data ::anomalies/category retryable-anomaly?))
              backoff (fn [epoch]
                        (when (<= epoch 10)
                          (* 200 epoch)))}}]
  (loop [epoch 1]
    (let [[success val] (try [true (op)]
                             (catch Exception e
                               [false e]))]
      (if success
        val
        (if-let [ms (and (retry? val) (backoff epoch))]
          (do
            (Thread/sleep ms)
            (recur (inc epoch)))
          (throw val))))))
