(ns core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [markdown.core :as markdown]
            [slugger.core :as slug]
            [tick.core :as t]
            [templates.post :as post]
            [templates.archives :as archives]
            [templates.home :as home]
            [templates.404 :as page-404]
            [templates.page :as page-template]
            [templates.sitemap :as sitemap-template]))

(defn now []
  (->> (t/zoned-date-time)
       (t/format (t/formatter "yyyy-MM-dd"))))

(defn filter-post [post]
  (string/includes? (.getName post) ".md"))

(defn post->html [post]
  (let [{:keys [metadata html]}
        (markdown/md-to-html-string-with-meta
         (:raw-content post))]
    (merge post
           {:metadata metadata
            :parsed-content html})))

(defn md->map [file-name]
  (let [content (slurp file-name)]
    {:file-name (.getName file-name)
     :raw-content content}))

(defn tag->taglist [post]
  (let [tags (-> post :metadata :tags first)]
    (if (nil? tags)
      post
      (assoc-in post [:metadata :tags] (string/split tags #",")))))

(defn get-translation [metadata]
  (if (:translation metadata)
    (slug/->slug (-> metadata :translation first))
    nil))

(defn get-date [date]
  (if date
    (-> date first t/date)
    nil))

(defn post->data [post]
  (let [metadata (-> post :metadata)]
    (merge post
           {:title (-> metadata :title first)
            :author (-> metadata :author first)
            :description (-> metadata :description first)
            :tags (-> metadata :tags)
            :content (:parsed-content post)
            :date (-> metadata :date get-date)
            :url (-> metadata :link first)
            :translation (get-translation metadata)})))

(defn post->slug [post]
  (assoc post
         :slug (slug/->slug (:url post))
         :uri (str (slug/->slug (:url post)) ".html")))

(defn re-seq-pos [pattern string]
  (let [m (re-matcher pattern string)]
    ((fn step []
       (when (. m find)
         (cons {:start (. m start) :end (. m end) :group (. m group)}
               (lazy-seq (step))))))))

(defn post->preview [post]
  (let [parsed-content (:parsed-content post)
        matches (re-seq-pos #"\<p>+" parsed-content)
        third-match (nth matches 2)]
    (assoc post
           :preview (subs parsed-content 0 (:end third-match)))))

(defn post->sitemap-url [post]
  (assoc post :sitemap-url (str "https://arthurbrrs.me/" (:slug post) ".html")))

(defn get-posts []
  (->> "posts/"
       (io/file)
       (file-seq)
       (filter filter-post)
       (map md->map)
       (map post->html)
       (map tag->taglist)
       (map post->data)
       (map post->slug)
       (map post->preview)
       (map post->sitemap-url)))

(defn get-translations []
  (->> "translations/"
       (io/file)
       (file-seq)
       (filter filter-post)
       (map md->map)
       (map post->html)
       (map tag->taglist)
       (map post->data)
       (map post->slug)
       (map post->preview)
       (map post->sitemap-url)))

(defn spit-post [post post-content]
  (spit (str "./docs/" (:slug post) ".html")
        post-content))

(defn render-post [post]
  (post/build-post post))

(defn spit-translation [post post-content]
  (spit
   (str
    "./docs/translations/"
    (:slug post)
    ".html")
   post-content))

(defn render-archives [posts]
  (let [post-list (reverse (sort-by :date posts))]
    (spit "./docs/archives.html"
          (archives/archives-page post-list))))

(defn render-home [posts]
  (let [post-list (take 3 (reverse (sort-by :date posts)))]
    (spit "./docs/index.html"
          (home/home-page post-list))))

(defn render-about []
  (let [raw-content (slurp "./pages/about.md")
        page (post->html {:raw-content
                          raw-content})]
    (spit "./docs/about.html"
          (page-template/build-page
           (merge page {:title "((arthur barroso))"
                        :description "about"
                        :content (:parsed-content page)})))))

(defn render-collage []
  (let [raw-content (slurp "./pages/collage.md")
        page (post->html {:raw-content
                          raw-content})]
    (spit "./docs/collage.html"
          (page-template/build-page
           (merge page
                  {:title "((arthur barroso))"
                   :description "collage"
                   :content (:parsed-content page)})))))

(defn render-404 []
  (spit "./docs/404.html"
        page-404/not-found-page))

(defn create-sitemap []
  (let [posts (get-posts)]
    (spit "./docs/sitemap.xml"
          (sitemap-template/build-sitemap posts (now)))))

(defn render-all [& _args]
  (let [posts (get-posts)]
    (doseq [p posts]
      (spit-post p (render-post p))
      (render-archives posts)
      (render-home posts)
      (render-404)
      (render-about)
      (render-collage)
      (create-sitemap)))
  (let [translations (get-translations)]
    (doseq [t translations]
      (spit-translation t (render-post t)))))

(comment
  (render-all))
