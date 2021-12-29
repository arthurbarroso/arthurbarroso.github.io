(ns templates.archives
  (:require [templates.base :as base]
            [hiccup.core :as h]))

(defn archives [posts]
  {:tags '()
   :content
   [:div {:id "posts"}
    [:div {:id "page-header"}
     [:h2 "archives"]]
    [:ul
     (for [post posts]
       [:li (str (:date post) " ")
        [:a {:href (:uri post)}
         (:title post)]])]]})

(defn archives-page [posts]
  (h/html
   (base/base {}
              (archives posts))))
