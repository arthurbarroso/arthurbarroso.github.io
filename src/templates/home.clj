(ns templates.home
  (:require [templates.base :as base]
            [hiccup.core :as h]))

(defn build-home-posts [posts]
  [:div {:id "post"}]
  (for [post posts]
    [:div {:id "post"}
     [:div {:class "post-header"}
      [:a {:class "post-header-link"
           :href (:uri post)}
       (:title post)]
      [:div {:id "post-meta"}
       [:div {:class "date"}
        (:date post)]]]
     (:preview post)
     [:a {:class "continue-reading"
          :href (:uri post)}
      "Continue reading &#8594;"]]))

(defn home [posts]
  {:tags '()
   :content (build-home-posts posts)})

(defn home-page [posts]
  (h/html
   (base/base {:description "oi"
               :site-url "x"
               :uri "xa"}
              (home posts))))
