#!/usr/bin/env bb
(ns generate
  (:require [babashka.pods :as pods]
            [clojure.java.io :as io]
            [clojure.string :refer [replace]]))

(pods/load-pod "bootleg")
(require '[pod.retrogradeorbit.bootleg.markdown :as markdown])

(let [posts-dir "./posts"
      dir (io/file posts-dir)
      quickstart (slurp "quickstart.html")
      index-quickstart (slurp "index-quickstart.html")
      files (.listFiles dir)]
  (doseq [file files]
    (let [title (-> (.getName file)
                    (replace ".md" "")
                    (replace "-" " "))]
      (spit (replace (str "./docs/" (.getName file)) ".md" ".html")
            (->
              (replace quickstart "{{& body }}"
                       (markdown/markdown (.getPath file) :html))
              (replace "{{& title }}" title))))
    (spit "./index.html"
          (replace index-quickstart "{{& posts }}"
                   (->> files
                        (map (fn [file]
                               (str "<li>"
                                    "<a href="
                                    (-> (.getPath file)
                                        (replace ".md" ".html")
                                        (replace "posts" "docs"))
                                    ">"
                                    (-> (.getName file)
                                        (replace ".md" "")
                                        (replace "-" " "))
                                    "</a>"
                                    "</li>")))
                        (clojure.string/join))))))
