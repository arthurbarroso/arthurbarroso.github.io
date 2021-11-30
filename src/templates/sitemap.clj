(ns templates.sitemap
  (:require [hiccup.core :as h]))

(defn sitemap [posts current-date]
  [:xml {:version "1.0" :encoding "UTF-8"}
   [:urlset {:xmlns "http://www.sitemaps.org/schemas/sitemap/0.9"
             :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
             :xsi:schemaLocation "http://www.sitemaps.org/schemas/sitemap/0.9
            http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd"}
    [:url
     [:loc "https://www.arthurbrrs.me/"]
     [:lastmod (str current-date "T0:24:30+00:00")]
     [:priority 1.00]]
    [:url
     [:loc "https://www.arthurbrrs.me/archives"]
     [:lastmod (str current-date "T0:24:30+00:00")]
     [:priority 0.80]]
    [:url
     [:loc "https://www.arthurbrrs.me/collage"]
     [:lastmod (str current-date "T0:24:30+00:00")]
     [:priority 0.80]]
    (for [post posts]
      [:url
       [:loc (:sitemap-url post)]
       [:lastmod (str (:date post) "T0:24:30+00:00")]
       [:priority 0.80]])]])

(defn build-sitemap [posts current-date]
  (h/html {:mode :xml}
          (sitemap posts current-date)))
