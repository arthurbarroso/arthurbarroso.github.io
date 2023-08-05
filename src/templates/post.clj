(ns templates.post
  (:require [templates.base :as base]
            [hiccup.core :as h]))

(defn build-post-header [translation date]
  (if (nil? translation)
    [:div {:class "col-lg-6"}
     (str date)]
    [:div
     [:div {:class "col-lg-6"}
      (str date)]
     [:span {:class "col-lg-6 right"}
      [:a {:class "col-lg-6"
           :href (str "/translations/" translation ".html")}
       "This post can be read in PT-BR"]]]))

(defn build-post-content [{:keys [translation date title content]}]
  [:div {:id "post"}
   [:div {:class "post-header"}
    [:div {:id "post-meta" :class "row"}
     (build-post-header translation date)]
    [:h2 title]
    [:div content]]])

(defn build-post-tags [{:keys [tags description title uri]}]
  [[:meta {:name "keywords"
           :content (reduce str tags)}]
   [:meta {:name "description"
           :content description}]
   [:meta {:property "og:description"
           :content description}]
   [:meta {:property "og:url"
           :content (str "https://arthurbrrs.me/" uri)}]
   [:meta {:property "og:title"
           :content title}]
   [:meta {:property "og:type"
           :content "article"}]
   [:meta {:property "og:image"
           :content "https://www.arthurbrrs.me/img/collage/publicint.png"}]])

(defn post-content [{:keys [date html tags description uri title translation] :as args}]
  {:tags (build-post-tags args)
   :content (build-post-content
             {:translation translation
              :date date
              :title title
              :content html})})

(defn build-post [post]
  (-> {:description (:description post)
       :uri (:slug post)
       :subtitle (:title post)}
      (base/base (post-content post))
      h/html))
