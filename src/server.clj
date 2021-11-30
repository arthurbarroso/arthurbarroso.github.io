(ns server
  "Utilies for development mode"
  (:require [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [core :as core]))

(defn asset-route []
  [["/css/screen.css"
    {:get {:handler (fn [_req]
                      {:body
                       (slurp "./docs/css/screen.css")})}}]

   ["/js/clojure_highlighter.js"
    {:get {:handler (fn [_req]
                      {:body
                       (slurp "./docs/js/clojure_highlighter.js")})}}]])

(defn post->page [post]
  [[(str "/" (:slug post))
    {:get {:handler (fn [_req]
                      {:body (slurp (str "./docs/"
                                         (:slug post)
                                         ".html"))})}}]
   [(str "/" (:slug post) ".html")
    {:get {:handler (fn [_req]
                      {:body (slurp (str "./docs/"
                                         (:slug post)
                                         ".html"))})}}]])

(defn get-posts-pages []
  (->> (core/get-posts)
       (map #(dissoc % :raw-content))
       (map #(dissoc % :preview))
       (map #(dissoc % :description))
       (map post->page)))

(defn get-index-page []
  ["/"
   {:get
    {:handler
     (fn [_req]
       {:body (slurp "./docs/index.html")})}}])

(defn get-archives-page []
  ["/archives"
   {:get
    {:handler
     (fn [_req]
       {:body (slurp "./docs/archives.html")})}}])

(defn get-about-page []
  ["/about"
   {:get
    {:handler
     (fn [_req]
       {:body (slurp "./docs/about.html")})}}])

(defn get-collage-page []
  ["/collage"
   {:get
    {:handler
     (fn [_req]
       {:body (slurp "./docs/collage.html")})}}])

(defn serve []
  (ring/ring-handler
   (ring/router
    [""
     (get-posts-pages)
     (get-index-page)
     (get-archives-page)
     (get-about-page)
     (get-collage-page)
     (asset-route)])
   (ring/create-default-handler)))

(defonce server (atom nil))

(defn start! []
  (reset! server
          (jetty/run-jetty (serve) {:port 4000 :join? false})))

(defn stop! []
  (reset! server
          (.stop @server)))

(comment
  (start!)
  (stop!))
