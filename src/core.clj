(ns core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [markdown.core :as markdown]
            [selmer.parser :as selmer]
            [slugger.core :as slug]))

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

(defn post->url [post]
  (assoc post
         :url-html (string/replace
                    (:file-name post)
                    ".md" ".html")
         :url-clean (string/replace
                     (:file-name post)
                     ".md" "")))

(defn post->selmer [post]
  (let [metadata (-> post :metadata)]
    (merge post
     {:title (-> metadata :title first)
      :author (-> metadata :author first)
      :description (-> metadata :description first)
      :tags (-> metadata :tags)
      :content (:parsed-content post)
      :date (-> metadata :date first)
      :url (-> metadata :link first)})))

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
       (map post->preview)))

(defn render-post [post]
  (selmer/cache-off!)
  (selmer/render-file "post.html"
                      {:title "((arthur barroso))"
                       :archives-uri "archives.html"
                       :post post}))

(defn save-post [post html-content]
  (spit
   (str
    "./docs/"
    (:slug post)
    ".html")
   html-content))

(defn render-archives []
  (let [posts (get-posts)]
    (spit "./docs/archives.html"
        (selmer/render-file "archives.html"
               {:title "((arthur barroso))"
                :posts posts}))))

(defn render-home []
  (let [posts (get-posts)]
    (spit "./docs/index.html"
        (selmer/render-file "home.html"
               {:title "((arthur barroso))"
                :archives-uri "archives.html"
                :posts posts}))))

(defn render-about []
  (let [raw-content (slurp "./pages/about.md")
        post (post->html {:raw-content
                          raw-content})]
     (spit "./docs/about.html"
         (selmer/render-file "page.html"
                             {:title "((arthur barroso))"
                              :archives-uri "archives.html"
                              :page {:title "((arthur barroso))"
                                     :description "about"
                                     :content (:parsed-content
                                               post)}}))))

(defn render-collage []
  (let [raw-content (slurp "./pages/collage.md")
        post (post->html {:raw-content
                          raw-content})]
     (spit "./docs/collage.html"
         (selmer/render-file "page.html"
                             {:title "((arthur barroso))"
                              :archives-uri "archives.html"
                              :page {:title "((arthur barroso))"
                                     :description "collage"
                                     :content (:parsed-content
                                               post)}}))))

(defn render-404 []
  (let [posts (get-posts)]
    (spit "./docs/404.html"
        (selmer/render-file "404.html"
               {:title "((arthur barroso))"
                :archives-uri "archives.html"
                :posts posts}))))

(comment
  (let [post (first (get-posts))]
    (->> post
         render-post
         (save-post post))))
