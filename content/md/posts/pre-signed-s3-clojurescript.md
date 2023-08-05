{:title "Pre-signed uploads to AWS S3 using Clojure(script)"
 :layout :post
 :date "2022-01-24"
 :tags ["shadow-cljs" "reagent" "re-frame" "react" "clojurescript" "clojure" "upload" "s3" "pre-signed-url" "aws"]}

I've recently stumbled upon a task: uploading images from an input and saving those. This can be done in several ways, such as storing files directly to your server's storage, saving these files inside a database, or uploading them to some cloud provider's service, such as AWS's S3. There are various reasons one may choose each of these strategies, but I decided to stick with the latter.

From my previous JavaScript/React experience I knew this could be done using S3's `pre-signed URLs`, but didn't know how to do this in Clojure, even though the overall process/technique isn't much different.

### How a pre-signed upload works using S3:
- your client-side application sends your server some metadata about the file.
- the server validates the metadata (this is kind of optional though).
- the server then responds with a unique URL that can be used to perform a request to S3 to upload this file.
- the client uploads the image/file to S3 and sends the server the URL so it can be stored in some kind of database.

This approach is nice because the file never gets sent to the server, which means we have less bandwidth consumption and there is something less for our server to deal with.

### Basic AWS setup:
We'll want to go to AWS's console, create ourselves a user, give it S3 permissions and create a new S3 bucket. 

I won't cover much of this in detail, but I'd like to add that we *need* to configure S3's CORS to get our uploads working. To do so, navigate to your bucket's `Permissions` tab, scroll down and change the CORS config so it matches this one:
```json
[
    {
        "AllowedHeaders": [
            "*"
        ],
        "AllowedMethods": [
            "HEAD",
            "GET",
            "PUT",
            "POST",
            "DELETE"
        ],
        "AllowedOrigins": [
            "*"
        ],
        "ExposeHeaders": [
            "ETag"
        ]
    }
]
```
Please beware this is a development configuration and you'll need to tweak it for your purposes and production deployments.

### The Clojure backend
To start it off, we'll need a server. I'll use Reitit and Ring to do this and won't show much of the application's boilerplate in here, but all of the code for this post can be found in this [repository](https://github.com/arthurbarroso/s3-upload-blog-demo). With a very basic server setup, we'll want to add [`amazonica`](https://github.com/mcohen01/amazonica) to our dependencies -I first wanted to use cognitec's [aws-api](https://github.com/cognitect-labs/aws-api), but it offers no support for generating S3 pre-signed URLs. I am using `deps.edn`, so this can be done by adding `amazonica/amazonica {:mvn/version "0.3.157"}` to my dependencies. With the library added, it is time to go create our handlers, routes, and controllers. Please beware I have also created files called `contracts` and `schemas`, but its only purposes are to allow Reitit to coerce data and to help me annotate functions using malli.
```clj
(ns blog.s3.contracts)

(def S3PresignedUrlData
  [:map
   [:file-size number?]
   [:file-key string?]
   [:file-type string?]])

(def S3UploadedData
  [:map
   [:file-url string?]
   [:username string?]
   [:title string?]])
```

The `S3PresignedUrlData` contract/structure defines what we'll receive when trying to generate a pre-signed URL: we'll want the file's size, its key (which is the name it'll be given in the bucket) and its type. The `S3UploadedData` structure defines what we'll expect to receive when a file is uploaded -this is the step where we'd save this URL to some database with other info such as the user's name and a post's title.

```clj
(ns blog.s3.schemas)

(def S3SignedURL
  [:or
   [:map
    [:s3/url string?]]
   [:map
    [:s3/error string?]]])
```
The `generate-upload-url!` handler will be able to return two types of maps: one containing a `s3/url` keyword (which would contain the pre-signed-url as its value) or a map containing an `s3/error` keyword with an error reason.

```clj
(ns blog.s3.handlers
  (:require [amazonica.aws.s3 :as client]
            [blog.s3.contracts :as c]
            [blog.s3.schemas :as s]))

(def max-file-size 10000)
(def allowed-file-types ["image/png" "image/jpg" "image/jpeg"])

(defn generate-signed-url [environment file-key]
  (let [{:keys [creds bucket]} (:s3 environment)]
    {:s3/url (client/generate-presigned-url creds {:bucket-name bucket
                                                   :method "PUT"
                                                   :key file-key})}))

(defn generate-upload-url!
  {:malli/schema [:=> [:cat c/S3PresignedUrlData :any] s/S3SignedURL]}
  [s3-data environment]
  (let [{:keys [file-type file-size file-key]} s3-data]
    (if (and (< file-size max-file-size) (some #(= file-type %) allowed-file-types))
      (generate-signed-url environment file-key)
      {:s3/error "Invalid file"})))
```
We set the maximum file size to 10000 (this is in KBs, since we will send the file size in KBs from the client), set the allowed file types to accept a few image types, and then check if the file the client is trying to upload matches these constraints. If the file conforms to our specifications, the application responds with a pre-signed URL.

```clj
(ns blog.s3.controllers
  (:require [blog.s3.handlers :as h]
            [ring.util.response :as rr]))

(defn generate-upload-url-controller! [environment]
  (fn [request]
    (let [file-input (-> request :parameters :body)
          upload-url (h/generate-upload-url!
                      file-input
                      environment)]
      (if (:s3/url upload-url)
        (rr/response upload-url)
        (rr/bad-request {:error "Something went wrong"})))))

(defn respond-uploaded-data-controller! [_environment]
  (fn [request]
    (let [req-body (-> request :parameters :body)]
      (rr/response req-body))))
```
The `generate-upload-url-controller!` function returns a function, that then takes a request. The request is then processed and we respond with 200 if a pre-signed url gets generated or 400 if it fails our verification system.

```clj
(ns blog.s3.routes
  (:require [blog.s3.contracts :as c]
            [blog.s3.controllers :as co]))

(defn routes [environment]
  ["/s3"
   ["/generate"
    {:post {:handler (co/generate-upload-url-controller! environment)
            :parameters {:body c/S3PresignedUrlData}}}]
   ["/store"
    {:post {:handler (co/respond-uploaded-data-controller! environment)
            :parameters {:body c/S3UploadedData}}}]])
```

The above code should be enough for our server. Please note the environment should contain your AWS credentials and failing to provide those will raise no error, so you'll probably want to check those. For this post, I decided to hardcode my env vars, but you'll probably want to use something like [aero](https://github.com/juxt/aero) in your apps.

### The Clojurescript client
It is now time to create the client: I decided to use re-frame and shadow-cljs to build it. We'll dispatch the file uploads from within re-frame.

We'll be creating a form that accepts a few fields:
- A `username` field that accepts a string (text)
- A `title` field that accepts a string (text)
- An input that accepts images.

Since we're using re-frame, it may be nice to handle these input's states from within re-frame itself. Let's create subscriptions and events to handle this:
```clj
(ns blog.client.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::file-form-values
 (fn [db]
   (get-in db [:forms :file-form])))
```

```clj
(ns blog.client.events
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax] ;; the following dependencies 
            [day8.re-frame.http-fx] ;;will be needed to handle 
            [clojure.string :as string])) ;; the upload and such :)

(re-frame/reg-event-db
 ::initialize-db
 (fn [_]
   {:forms {:file-form {:title nil
                        :username nil
                        :file-url nil}}}))

(re-frame/reg-event-db
 ::set-file-form-field-value
 (fn [db [_ field-path new-value]]
   (assoc-in db [:forms :file-form field-path] new-value)))
```

With the base events and subscriptions we'll move to our form and our image-picker component:
```clj
(ns blog.client.form
  (:require [reagent.core :as r]
            [re-frame.core :as re-frame]
            [blog.client.subs :as subs]
            [blog.client.events :as events]))

(defn handle-image-data [file file-key]
  {:file-type (.-type file)
   :file-size (-> file
                  (.-size)
                  (/ 1024)
                  .toFixed)
   :file-key (str file-key (.-name file))
   :file-name (.-name file)})

(defn get-image-data [input-id]
  (let [el (.getElementById js/document input-id)
        file (aget (.-files el) 0)
        form-data (js/FormData.)
        _ (.append form-data "file" file)]
    {:form-data form-data
     :file file}))

(defn generate-file-key [{:keys [title username]}]
  (str title "-" username "-"))

(defn submit-image
  [data input-id]
  (let [{:keys [form-data file]} (get-image-data input-id)
        file-key (generate-file-key data)]
    (assoc
     (handle-image-data file file-key)
     :file form-data)))

(defn image-selector
  [input-id]
  (let [UPLOADED-IMAGE (r/atom nil)]
    (fn []
      [:div
       [:input {:type "file"
                :id input-id
                :value @UPLOADED-IMAGE
                :on-change #(reset! UPLOADED-IMAGE
				     (-> % .-target .-value))
                :accept "image/*"}]
       [:label {:htmlFor input-id}
        (if @UPLOADED-IMAGE
          "File added 🥳"
          "Upload image")]])))

(defn file-creation-handler [data input-id]
  (let [image-data (submit-image data input-id)
        submit-data (merge data image-data)]
    (re-frame/dispatch
     [::events/create-file submit-data])))

(defn file-form []
  (let [form-values (re-frame/subscribe [::subs/file-form-values])
        image-picker-input-id "woooo"]
    (fn []
      [:form {:onSubmit (fn [e]
                          (do (.preventDefault e)
                              (file-creation-handler 
	@form-values image-picker-input-id)))}
       [:label {:htmlFor "title-input"}
        "Title"]
       [:input {:type "text"
                :id "title-input"
                :value (:title @form-values)
                :on-change #(re-frame/dispatch 
					 [::events/set-file-form-field-value
                      :title (-> % .-target .-value)])}]
       [:label {:htmlFor "username-input"}
        "Username"]
       [:input {:type "text"
                :id "username-input"
                :value (:username @form-values)
                :on-change #(re-frame/dispatch 
					[::events/set-file-form-field-value
					 :username (-> % .-target .-value)])}]
       [image-selector image-picker-input-id]
       [:button {:type "submit"
                 :on-click #()}
        "Submit"]])))
```
Okay, that's a big step, so lets walk through it:
- `handle-image-data` is a function that takes in a file and it's key (which is the name the fill will receive at S3's bucket). It basically creates a map from the file's properties.
- `get-image-data` takes in an input-id, queries the dom for this input, gets the first file from that input and creates a `FormData` object with this file. It then returns a map with the raw file and the form-data object.
- `generate-file-key` takes in the form's data to create a string containing the form data and the file name. This is an utility function to create somewhat unique file names. UUIDs could also be used to do this.
- `submit-iamge` takes in the form's data and the input id. It then uses `get-image-data` to get the raw file and the `FormData` object. It then generates a file-key and creates a map with the file's metadata and the form-data.
- the `image-selector` is a Reagent component that handles it's state using a reagent atom. I decided to use an atom instead of re-frame's state because I don't really think the file is part of the application's state.
- `file-creation-handler` is a function that takes the form's data and the image-picker's input-id and dispatches the data to the re-frame we'll create soon.

####  The `create-file` re-frame event
We'll start with a pretty straightforward re-frame-http-fx event:
```clj
(re-frame/reg-event-fx
 ::create-file
 (fn [{:keys [db]} [_ form-data]]
   (let [{:keys [file-type file-key file-size]} form-data]
     {:db (assoc db :loading true)
      :http-xhrio {:method :post
                   :uri "http://localhost:4000/v1/s3/generate"
                   :format (ajax/json-request-format)
                   :timeout 8000
                   :params {:file-type file-type
                            :file-key file-key
                            :file-size (js/parseFloat file-size)}
                   :with-credentials true
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [::s3-url-success form-data]
                   :on-failure [::s3-url-failure]}})))

(re-frame/reg-event-fx
 ::s3-url-failure
 (fn [{:keys [db]} [_ _response]]
   {:db (assoc db :loading false)}))

(re-frame/reg-event-fx
 ::s3-url-success
 (fn [{:keys [db]} [_ form-data response]]
   (let [s3-url (:s3/url response)
         params-index (string/index-of s3-url "?")
         file-url (subs s3-url 0 params-index)]
     (cljs.pprint/pprint file-url)
     {:db (assoc db :loading false)}
```
This event will then create a POST request to our server and try to create a pre-signed URL. In case it succeeds, we should be able to check the pre-signed URL being printed to the browser's console. If it prints, then you're probably doing it right (assuming you didn't mix up your credentials on the server), so it is now time to upload the file to the generated URL.

```clj
(re-frame/reg-event-fx
 ::s3-url-success
 (fn [{:keys [db]} [_ form-data response]]
   (let [s3-url (:s3/url response)
         params-index (string/index-of s3-url "?")
         file-url (subs s3-url 0 params-index)]
     (cljs.pprint/pprint file-url)
     {:db (assoc db :loading false)
      :dispatch [::upload-file (merge response form-data {:file-url file-url})]}
;; we need to first tell our `s3-url-success` to dispatch the event we're creating
      
(re-frame/reg-event-fx
 ::upload-file
 (fn [_ [_ data]]
   {:http-xhrio {:method :put
                 :uri (:s3/url data)
                 :timeout 8000
                 :body (.get (:file data) "file")
                 :headers {"Content-Type" "image/*"}
                 :response-format (ajax/raw-response-format)
                 :on-success [::upload-image-success data]
                 :on-failure [::s3-url-failure]}}))

(re-frame/reg-event-fx
 ::upload-image-success
 (fn [_ [_ data _response]]
   (cljs.pprint/pprint data)
   {}))
```
Now, hitting the submit button on the form should simply print our initial data to the console. You should check the `Requests` tab of your browser's devtools and find out whether the PUT request succeeded. In case it did, it is time to move forward.

```clj
(re-frame/reg-event-fx
 ::upload-image-success
 (fn [_ [_ data _response]]
   {:dispatch [::send-server-uploaded-file data]}))

(re-frame/reg-event-fx
 ::send-server-uploaded-file
 (fn [{:keys [db]} [_ {:keys [file-url title username]}]]
   {:db (assoc db :loading true)
    :http-xhrio {:method :post
                 :uri "http://localhost:4000/v1/s3/store"
                 :format (ajax/json-request-format)
                 :timeout 8000
                 :params {:file-url file-url
                          :title title
                          :username username}
                 :with-credentials true
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::store-success]
                 :on-failure [::s3-url-failure]}}))

(re-frame/reg-event-fx
 ::store-success
 (fn [{:keys [db]} [_ response]]
   (cljs.pprint/pprint response)
   {:db (assoc db :loading false)}))
```
We make sure `uploaded-image-success` dispatches `send-server-uploaded-file` passing it our data map. We then make a POST request to our server and sent it the file url. The server response should then be printed to the console.

#### Related posts:
There are two blog posts that helped me build my solution and served as the base for this post. These can be found in the links below.
- [Uploading Files and Handling Upload Requests in Clojurescript](https://tech.toryanderson.com/2021/11/06/uploading-files-and-handling-upload-requests-in-clojurescript/) by Tory Anderson
- [Pre-signed S3 URLs with Clojure and ClojureScript](https://pablofernandez.tech/2017/05/11/pre-signed-s3-urls-with-clojure-and-clojurescript/) by Pablo Fernandez

A big thank you to both authors 💖
