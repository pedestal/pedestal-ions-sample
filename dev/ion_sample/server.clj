(ns ion-sample.server
  "Houses the implementation for running the sample locally as a pedestal service."
  (:require [ion-sample.service :as service]
            [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]))

(defn run-dev
  "Starts a service."
  [port]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with the ion configuration
      ;; Remove the ion chain provider
      (dissoc ::server/chain-provider)
      (merge {:env                     :dev
              ;; do not block thread that starts web server
              ::server/join?           false
              ::server/port            port
              ::server/type            :jetty
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::server/routes          #(route/expand-routes (deref #'service/routes))
              ;; all origins are allowed in dev mode
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}
              ;; Content Security Policy (CSP) is mostly turned off in dev mode
              ::server/secure-headers  {:content-security-policy-settings {:object-src "'none'"}}})
      ;; Wire up interceptor chains
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))

(defn stop
  [s]
  (server/stop s))

(comment


 (def s (run-dev 9091))

 (server/stop s)


 )
