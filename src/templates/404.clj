(ns templates.404
  (:require [templates.base :as base]
            [hiccup.core :as h]))

(def not-found-content
  [:div {:class "content"}
   [:section {:id "message"}
    [:h1 "404"]
    [:h2 "Error !"
     [:span
      "Looks like you're trying to access
       something that doesn't yet exist"]]]])

(def not-found
  {:tags '()
   :content not-found-content})

(def not-found-page
  (h/html
   (base/base
    {:description "Not found :("
     :subtitle "Page not found"
     :uri "404"}
    not-found)))
