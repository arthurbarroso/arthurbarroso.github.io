(ns core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [markdown.core :as markdown]
            [selmer.parser :as selmer]
            [slugger.core :as slug]
            [tick.core :as t]))

(defn now []
  (->> (t/zoned-date-time)
       (t/format (t/formatter "yyyy-MM-dd"))))

(def config {:archives-uri "archives"
             :author "Arthur Barroso"
             :title "((arthur barroso))"
             :last-mod (now)})

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

(defn post->selmer [post]
  (let [metadata (-> post :metadata)]
    (merge post
           {:title (-> metadata :title first)
            :author (-> metadata :author first)
            :description (-> metadata :description first)
            :tags (-> metadata :tags)
            :content (:parsed-content post)
            :date (-> metadata :date first t/date)
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
       (map post->selmer)
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
       (map post->selmer)
       (map post->slug)
       (map post->preview)
       (map post->sitemap-url)))

(defn render-post [post]
  (selmer/cache-off!)
  (selmer/render-file "post.html"
                      (merge config
                             {:post post})))

(defn save-translation [post html-content]
  (spit
   (str
    "./docs/translations/"
    (:slug post)
    ".html")
   html-content))

(defn save-post [post html-content]
  (spit
   (str
    "./docs/"
    (:slug post)
    ".html")
   html-content))

(defn render-archives [posts]
  (let [post-list (reverse (sort-by :date posts))]
    (spit "./docs/archives.html"
          (selmer/render-file "archives.html"
                              (merge config {:posts post-list})))))

(defn render-home [posts]
  (let [post-list (take 3 (reverse (sort-by :date posts)))]
    (spit "./docs/index.html"
          (selmer/render-file "home.html"
                              (merge config
                                     {:posts post-list})))))

(defn render-about []
  (let [raw-content (slurp "./pages/about.md")
        post (post->html {:raw-content
                          raw-content})]
    (spit "./docs/about.html"
          (selmer/render-file "page.html"
                              (merge config
                                     {:page {:title "((arthur barroso))"
                                             :description "about"
                                             :content (:parsed-content
                                                       post)}})))))

(defn render-collage []
  (let [raw-content (slurp "./pages/collage.md")
        post (post->html {:raw-content
                          raw-content})]
    (spit "./docs/collage.html"
          (selmer/render-file "page.html"
                              (merge config
                                     {:title "((arthur barroso))"
                                      :archives-uri "archives.html"
                                      :page {:title "((arthur barroso))"
                                             :description "collage"
                                             :content (:parsed-content
                                                       post)}})))))

(defn render-404 []
  (let [posts (get-posts)]
    (spit "./docs/404.html"
          (selmer/render-file "404.html"
                              (merge config {:posts posts})))))

(defn create-sitemap []
  (let [posts (get-posts)]
    (spit "./docs/sitemap.xml"
          (selmer/render-file "sitemap.xml"
                              {:posts posts
                               :last-mod (:last-mod config)}))))

(defn render-all [_]
  (let [posts (get-posts)]
    (doseq [p posts]
      (save-post p (render-post p)))
    (render-archives posts)
    (render-home posts)
    (render-404)
    (render-about)
    (render-collage)
    (create-sitemap)
    (let [translations (get-translations)]
      (doseq [t translations]
        (save-translation t (render-post t))))))

(comment
  (render-all {}))
