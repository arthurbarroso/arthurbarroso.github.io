(ns templates.page
  (:require [templates.base :as base]
            [hiccup.core :as h]))

(defn build-page-tags [{:keys [description slug title]}]
  [[:meta {:name "description"
           :content description}
    [:meta {:property "og:description"
            :content description}]
    [:meta {:property "og:url"
            :content (str "https://arthurbrrs.me/" slug)}]
    [:meta {:property "og:title"
            :content title}]
    [:meta {:property "og:type"
            :content "article"}]]])

(defn build-page-content [{:keys [title content]}]
  [:div {:id "custom-page"}
   [:div {:id "page-header"}
    [:h2 title]]
   content])

(defn page-template [page]
  {:tags (build-page-tags page)
   :content (build-page-content page)})

(defn build-page [page-item]
  (h/html
   (base/base {:description (:description page-item)
               :uri (:slug page-item)
               :subtitle (:title page-item)}
              (page-template page-item))))
