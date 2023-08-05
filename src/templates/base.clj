(ns templates.base)

(defn build-meta-tags [{:keys [description extra-tags]}]
  [[:meta {:charset "utf-8"}]
   [:meta {:name "description" :content description}]
   [:meta {:name "google-site-verification"
           :content "KLXtbzGXgE_BcqGEwJv7vCVH6-XMLskVDgd-rRVoZHg"}]
   [:meta {:property "og:image"
           :content "https://www.arthurbrrs.me/img/collage/publicint.png"}]
   [:meta {:name "keywords" :content "clojure"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   (for [tag extra-tags]
     tag)])

(defn build-title-subtitle [subtitle]
  (if (nil? subtitle)
    "((arthur barroso))"
    (str "((arthur barroso)): " subtitle)))

(defn build-head [{:keys [description content uri subtitle]}]
  [:head
   (for [tag (build-meta-tags {:description description
                               :extra-tags (:tags content)})]
     tag)
   [:link {:rel "canonical" :href (str "https://arthurbrrs.me/" uri)}]
   [:link {:rel "shortcut icon" :href "css/favicon.ico"}]
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect"
           :href "https://fonts.gstatic.com" :crossorigin true}]
   [:link {:href "https://fonts.googleapis.com/css2?family=Roboto:ital,wght@0,300;0,400;0,500;0,700;0,900;1,700&display=swap"
           :rel "stylesheet"}]
   [:link {:rel "stylesheet"
           :href "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.2.0/styles/github.min.css"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.2.0/highlight.min.js"}]
   [:link {:rel "stylesheet" :href "/css/screen.css"}]
   [:title (build-title-subtitle subtitle)]])

(def navbar
  [:nav {:class "navbar"}
   [:div {:class "container"}
    [:div {:class "navbar-header"}
     [:a {:class "navbar-title" :href "/"}
      "((arthur barroso))"]
     [:ul {:class "navbar-list"}
      [:li
       [:a {:class "navbar-link" :href "/archives"}
        "archives"]
       [:a {:class "navbar-link" :href "/collage"}
        "collage"]
       [:a {:class "navbar-link"
            :href "/about"}
        "about"]]]]]])

(defn build-body [{:keys [content]}]
  [:body
   navbar
   [:div {:class "container"}
    [:div {:class "row"}
     [:div
      [:div {:id "content"}
       content]]]
    [:footer "Copyright &copy; 2023 Arthur Barroso"]]])

(defn base [{:keys [description subtitle uri]} content]
  [:html {:xmlns "http://www.w3.org/1999/xhtml" :lang "en" :xml.lang "en"}
   (build-head {:description description
                :uri uri
                :content content
                :subtitle subtitle})
   (build-body content)
   [:script {:src "/js/clojure_highlighter.js"}]
   [:script
    "document.addEventListener('DOMContentLoaded', (event) => {
      document.querySelectorAll('pre code:not(.clj)').forEach((el) => {
          hljs.highlightElement(el);
      });
     });"]])
