(ns server
  "Utilies for development mode"
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [hawk.core :as hawk])
  (:import java.io.File))

(defn page->route [page-name]
  [(clojure.string/replace (str "/" page-name) ".html" "")
   {:get {:handler (fn [_req]
                     {:body
                      (slurp (str "./docs/" page-name))})}}])

(defn filter-page [page]
  (not (.isDirectory page)))

(defn get-pages []
  (let [dir (File. "./docs/")
        files (.listFiles dir)]
    (->> files
         (filter filter-page)
         (map #(.getName %)))))

(defn create-page-routes [page-names]
  [""
   (->> page-names
        (map page->route))])

(defn serve []
  (ring/ring-handler
   (ring/router
    [""
     ["/css/*" (ring/create-resource-handler {:root "/css"})]
     (create-page-routes
      (get-pages))])
   (ring/create-default-handler)))

(def server (atom nil))

(defn start! []
    (reset! server
        (jetty/run-jetty (serve) {:port 4000 :join? false})))

(defn stop! []
  (reset! server
         (.stop @server)))

(defn- auto-reset-handler [ctx _event]
  (binding [*ns* *ns*]
    (stop!)
    (start!)
    ctx))

(defn watch! []
  (hawk/watch! [{:paths ["posts"]
                 :handler auto-reset-handler}]))

(comment
  (start!)
  (stop!))
