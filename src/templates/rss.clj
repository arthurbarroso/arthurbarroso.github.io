(ns templates.rss
  (:require [hiccup.core :as h]
            [tick.core :as t]
            [tick.locale-en-us]))

(defn format-date [{:keys [date] :as post}]
  (when date
    (let [z-d (.atStartOfDay date (java.time.ZoneId/of "EST"
                                                       java.time.ZoneId/SHORT_IDS))]
      (t/format (t/formatter "EEE, dd MMM yyyy HH:mm:ss Z")
                z-d))))

(def base-string
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
       "<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">"
       "<channel>"
       "<link>"
       "https://arthurbrrs.me"
       "</link>"
       "<atom:link href=\"https://arthurbrrs.me/feed.xml\" rel=\"self\" type=\"application/rss+xml\" />" 
       "<title>"
       "arthur barroso"
       "</title>"
       "<description>"
       "A blog about stuff I find fun"
       "</description>"))

(def ending-string
  (str "</channel>"
       "</rss>"))

(defn post->rss [post]
  (str "<item>"
       "<link>"
       (:sitemap-url post)
       "</link>"
       "<guid>"
       (:sitemap-url post)
       "</guid>"
       "<title>"
       (:title post)
       "</title>"
       "<pubDate>"
       (format-date post)
       "</pubDate>"
       "</item>"))
       

(defn rss [posts]
  (let [rss-posts (doall (map post->rss posts))]
    (str base-string
         (apply str rss-posts)
         ending-string)))

(defn build-rss [posts current-date]
  (h/html {:mode :xml}
          (rss posts)))
