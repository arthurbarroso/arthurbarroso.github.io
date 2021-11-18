Author: Arthur Barroso
Title: React prerendering experiments in Clojure(script) land
Link: Prerendering React Clojurescript Land
Description: A blog post about different React pre-rendering techniques and approaches in Clojure(script)
Date: 2021-09-21
Tags: shadow-cljs, react, clojure, clojurescript, pre-render, ssr

I've been kinda fascinated about pre-rendering my react applications for about three months. It all started when I started to develop [brundij](https://github.com/arthurbarroso/brundij) and realized I wouldn't be able to get good performance results without some prerendering techniques, which ultimately led me into reading lots of stuff about it.

I have developed React applications for some time already, but never really had to get prerendering to work without using frameworks such as Gatsby or Next, so this was a new challenge for me. I ended up deciding to write this post talking about all the stuff I tried and what I think about each of those. This should not be seen as a tutorial, all the stuff here is highly experimental.

While writing, I realized this post would end up getting quite lengthy, so I also pushed a demo repository with all the code you'd need to follow everything I've written. You can check it [here](https://github.com/arthurbarroso/blog-prerendering-demo)

### React prerendering

Prerendering in React is commonly achieved by using frameworks such as Next.js and Gatsby. These tools/frameworks share a common feature: they make it possible to generate your application's static HTML on build time. (Next.js also makes it possible to generate the static content/page on each request, using server-side-rendering).

What happens for both of these strategies (static generation and server-side rendering) is that the React components/views get rendered to a string that later gets event handlers attached to it. This means the client downloads a prerendered static HTML version of the React application and React attaches event handlers to it afterward. This is done  by using [ReactDomServer.renderToString()](https://reactjs.org/docs/react-dom-server.html) and [React.hydrate()](https://reactjs.org/docs/react-dom.html#hydrate).

### Clojurescript and prerendering
I wanted to be able to write my applications using Clojurescript and still prerender them. The problem for Clojurescript is that these React prerendering frameworks often don't play well with it, as pointed out by Thomas Heller in [this post](https://clojureverse.org/t/creating-websites-with-shadow-cljs-gatsby-or-next-js/2912).

This ultimately got me into thinking about how could I achieve prerendering in my Reagent/re-frame applications without having to spin up a node server. I then started looking for stuff on that topic, which led me to a few findings:

- [React Server Side Rendering with GraalVM for Clojure](https://nextjournal.com/kommen/react-server-side-rendering-with-graalvm-for-clojure)
- [Prerendering a re-frame app with Chrome headless](https://medium.com/@joelsanchezclj/prerendering-a-re-frame-app-with-chrome-headless-bb875de31fd0)
- [pupeno/Prerenderer](https://github.com/pupeno/prerenderer) - which I haven't yet tried and won't talk about in this post
- [borkdude/nbb](https://github.com/borkdude/nbb)

These resources helped me grasp things and try a few different setups, which I'll describe here.

### First setup: using GraalVM and Polyglot
This setup is heavily based on the post I linked above, [React Server Side Rendering with GraalVM for Clojure](https://nextjournal.com/kommen/react-server-side-rendering-with-graalvm-for-clojure), but I decided to tweak things a little bit to better suit my development workflow:
- Instead of using Nextjournal's custom Clojurescript version, I decided to go with `shadow-cljs` and use its related Clojurescript version.
- I wanted to use re-frame to control my application's state.

To acheive this, I needed to do some digging and do some hackish things that aren't really recommended. Let's take a look at the steps needed to get shadow, graal and re-frame working:

We will start by first defining our `deps.edn` file:
```clj
;;deps.edn
{:deps {org.clojure/clojure {:mvn/version "1.10.3"}
        reagent/reagent {:mvn/version "1.1.0"}
        thheller/shadow-cljs {:mvn/version "2.15.10"}
        re-frame/re-frame {:mvn/version "1.2.0"}
        org.clojure/core.async {:mvn/version "1.3.618"}}

 :paths ["src" "dev"]

 :aliases {:cljs {:main-opts ["-m" "shadow.cljs.devtools.cli"]
                  :paths ["src" "dev"]}

           :repl {:extra-deps {nrepl/nrepl {:mvn/version "0.8.3"}
                               cider/cider-nrepl {:mvn/version "0.26.0"}}
                  :extra-paths ["public/assets"]
                  :main-opts ["-m" "nrepl.cmdline"
                              "--interactive"
                              "--middleware"
                              "[cider.nrepl/cider-middleware]"]}}}
```
Now it is time to create our `shadow-cljs.edn` config file.
```clj
;;shadow-cljs.edn
{:nrepl {:port 8777}

 :deps true

 :dev-http
 {8280 "public"}

 :builds
   {:app {:target :graaljs
          :output-to "public/assets/graal.js"
          :entries [app.component]
          :jvm-opts ["-Xmx4G"]
          :modules
          {:app {:init-fn [app.component/countinghtml]}}}}}
```
If we compile our code using the above configuration and try to use any of javascript's async functions such as `setTimeout` or `setInterval` we'll be greeted with an error message saying async is not yet supported in shadow's graaljs target.  As seen on [this issue](https://github.com/thheller/shadow-cljs/issues/685) on shadow-cljs's repo, Clojurescript removed the shims needed for async to work in its graaljs compile target, which means we'll have to add those shims ourselves.  We will want shadow to prepend our built code with these shims and also remove the definitions of the functions shadow creates for these. This can be done by adding the key `:prepend-js` pointing at [this file](https://github.com/clojure/clojurescript/blob/e4300da64c4781735146cafc0ca029046b83944c/src/main/cljs/cljs/bootstrap_graaljs.js) to our `:app` module and by creating a build hook:
```clj
;;shadow-cljs.edn
{...
 :builds
   {:app {...
          :modules
            {:app {:init-fn [app.component/countinghtml]}
           :prepend-js "./graal-bootstrap.js"}
          :build-hooks [(util.clean/hook)]}}}
```
Our hook:
```clj
;;src/util/clean.clj
(ns util.clean
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn hook
  {:shadow.build/stage :flush}
  [build-state & args]
  (let [original (slurp (io/file "public/assets/graal.js"))
        start (- (string/index-of original "function graaljs_async_not_supported()") 0)
        to-replace (subs original start (+ start 664))]
    (spit "public/assets/clean.js"
          (-> original
              (string/replace to-replace " "))))
  build-state)
```
The above build hook creates a copy of our shadow-built code and removes all of shadow's `graaljs async_not supported` functions, so our code ends up calling our shims instead of throwing async not supported errors. It is now time to set up Graal's Polyglot and our very first component:

```clj
(ns app.render
  (:require [clojure.java.io :as io])
  (:import (org.graalvm.polyglot Context Source Engine)
           (org.graalvm.polyglot.proxy ProxyArray ProxyObject)))

(defn serialize-arg [arg]
  (cond
    (keyword? arg)
      (name arg)

    (symbol? arg)
      (name arg)

    (map? arg)
      (ProxyObject/fromMap (into {} (map (fn [[k v]]
                                           [(serialize-arg k) (serialize-arg v)])
                                         arg)))

    (coll? arg)
      (ProxyArray/fromArray (into-array Object (map serialize-arg arg)))

    :else
      arg))

(defn execute-fn [context fn & args]
  (let [fn-ref (.eval context "js" fn)
        argsv (into-array Object (map serialize-arg args))]
    (assert (.canExecute fn-ref) (str "cannot execute " fn))
    (.execute fn-ref argsv)))

(defn template-html-2 [react-data]
  (str "<html>
        <head>
       <meta charset=\"utf-8\">
         <script src=\"/assets/clean.js\">
         </script>
        </head>
        <body>
        <div id=\"root\">"
       react-data
       "</div>
       </body>
       <script>
        app.component.countinghydrate();
       </script>
       </html>"))

(defn context-build []
  (let [engine (Engine/create)]
    (doto (Context/newBuilder (into-array String ["js"]))
      (.engine engine)
      (.allowExperimentalOptions true)
      (.option "js.experimental-foreign-object-prototype" "true")
      (.option "js.timer-resolution" "1")
      (.option "js.java-package-globals" "false")
      (.out System/out)
      (.err System/err)
      (.allowAllAccess true)
      (.allowNativeAccess true))))

(def build-page
  (memoize
    (fn [ctx js-file fun arg]
      (let [context (.build ctx)
            app-s (-> js-file
                      (io/file)
                      (#(.build (Source/newBuilder "js" %))))]
        (.eval context app-s)
        (.asString (execute-fn context fun arg))))))
```
ps.: the `serialize-arg` function was found [here](https://github.com/wavejumper/clj-polyglot/blob/e56783822e85d0b75d048c3e6a8b597f0e26724a/src/clj_polyglot/core.clj)

The component we'll prerender throughout this post:
```clj
(ns app.component
  (:require [app.events :as events]
            [app.subs :as subs]
            [re-frame.core :as re-frame]
            ["react-dom" :as react-dom]
            [reagent.core :as reagent]
            [reagent.dom.server :as dom-server]))

(defn counting-component []
  (let [click-2 (reagent/atom 0)
        name (re-frame/subscribe [::subs/name])
        users (re-frame/subscribe [::subs/users])]
    (fn []
      [:div
       "The atom " [:code "click-count"] " has value: "
       @click-2 ". "
       [:p "Name has value " @name]
       [:p "Users:"
        (for [user @users]
          ^{:key (:id user)}
          [:p (:first_name user)])]
       [:p "Test 2"]
       [:input {:type "button" :value "Click me!"
                :on-click #(swap! click-2 inc)}]
       [:button {:on-click #(re-frame/dispatch [::events/change-name "teste"])}
        "Change name"]
       [:button {:on-click #(re-frame/dispatch [::events/fetch])}
        "Fetch users"]])))

(defn wrapped-counter []
  (re-frame/dispatch-sync [::events/init-db])
  (fn []
    [counting-component]))

(defn countinghtml []
  (dom-server/render-to-string [wrapped-counter]))

(defn ^:export countinghydrate []
  (let [cb (.getElementById js/document "root")]
    (react-dom/hydrate (reagent/as-element [wrapped-counter])
                       cb)))
```

Let's just add dummy event handlers for now.

There are two ways we can use this setup:
- Server-side rendering (as seen on NextJournal's post).
- Generating static HTML pages to be hydrated on the build.

#### Server-side rendering
We'll want to run `app.render` on each of the requests our app receives. I'll use reitit, ring, and jetty for this. To do so, we'll add the needed dependencies to our `deps.edn` file:
```clj
metosin/reitit {:mvn/version "0.5.5"}
ring/ring {:mvn/version "1.8.1"}
```
It is now time to create our very basic handler/router:
```clj
(ns app.server
  (:require [app.render :as renderer]
            [reitit.dev.pretty :as pretty]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [ring.adapter.jetty :as jetty]))

(def router-options {:exception pretty/exception
                     :middleware [exception/exception-middleware]})

(defn server []
  (ring/ring-handler
    (ring/router
      [""
       ["/assets/*" (ring/create-resource-handler {:root "."})]
       ["/" {:get (fn [_]
                    {:body
                       (-> (renderer/build-page
                             (renderer/context-build)
                             "public/assets/clean.js"
                             "app.component.countinghtml"
                             {})
                           (renderer/template-html-2))})}]])
    router-options))

(defn run-server []
  (jetty/run-jetty (server) {:port 4000 :join? false}))

(comment
  (run-server))
```
We'll now want to run `clj -M:cljs watch app` and navigate to `http://localhost:4000` in our browser, which will show us our prerendered and hydrated component/view. This sum's up how you would use Graal with shadow to server-side render your application.

#### Generating static HTMLs
It is also possible to use Graal to create static HTML files for our application at build time. These HTML files can then be hydrated by the browser. To do so, we'll simply modify our build-hook so it generates the HTML using graal and then spits it to a file inside our public dir:

```clj
(ns util.clean
  (:require [app.render :as renderer]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn hook
  {:shadow.build/stage :flush}
  [build-state & args]
  (let [original (slurp (io/file "public/assets/graal.js"))
        start (- (string/index-of original "function graaljs_async_not_supported()") 0)
        to-replace (subs original start (+ start 664))]
    (spit "public/assets/clean.js"
          (-> original
              (string/replace to-replace " "))))
  (let [html-to-output (-> (renderer/build-page
                             (renderer/context-build)
                             "public/assets/clean.js"
                             "app.component.countinghtml"
                             {})
                           (renderer/template-html-2))]
    (spit "public/prerendered.html" html-to-output))
  build-state)
```

Running `clj -M:cljs watch app` and navigating to `http://localhost:8280/prerendered.html` will show us our prerendered and hydrated component/view. 

#### HTTP requests from within the client
I didn't show the code of the applications event handlers on purpose. You may have noticed I have an event called `fetch`. This event is supposed to fire a HTTP request, which would most probably be done by using `re-frame-http-fx` in your regular SPA.

The thing is: while using the `graaljs` target, you won't be able to even require `cljs-ajax`, which is used by `re-frame-http-fx`, since it uses `XMLHTTPRequest`, which isn't available. To solve this, I came up with a library that wraps `cljs-http` into a re-frame effect handler. Let's add it and create our `fetch` event.

We'll start by adding the following dependencies to our `deps.edn` file:
```clj
cljs-http/cljs-http {:mvn/version "0.1.46"}
org.clojars.arthurbarroso/re-frame-cljs-http {:mvn/version "0.1.0"}
```
With the dependencies added, we will add/change our `fetch` event handler:
```clj
(ns app.events
  (:require [re-frame-cljs-http.http-fx]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
  ::init-db
  (fn [_ _]
    {:db
       {:name "app"
        :loading false
        :error nil
        :users []}}))

(re-frame/reg-event-db
  ::change-name
  (fn [db [_ v]]
    (assoc db :name v)))

(re-frame/reg-event-db
  ::success
  (fn [db [_ result]]
    (assoc db :users (-> result :body :data))))

(re-frame/reg-event-db
  ::failure
  (fn [db [_ result]]
    (assoc db :users [] :http-failure true :http-error result)))

(re-frame/reg-event-fx
  ::fetch
  (fn [cofx [_ _]]
    {:db (assoc (:db cofx) :b true)
     :http-cljs {:method :get
                 :url "https://reqres.in/api/users?page=2"
                 :params {:testing true}
                 :timeout 8000
                 :on-success [::success]
                 :on-failure [::failure]}}))
```
Now, accessing your application and clicking the `Fetch users` button should add the http results to your users list and show it at your component/view.

### Second setup: Headless Chrome using Etaoin
I won't describe much of this setup in here. [Joel Sánchez's post](https://medium.com/@joelsanchezclj/prerendering-a-re-frame-app-with-chrome-headless-bb875de31fd0) covers it pretty well. This setup has been by far one of the easiest to get up and running.  You can either set it up as he did in his post (which serves as a server-side rendered version of your application) or use it to generate static HTML's like I did in [this file](https://github.com/arthurbarroso/brundij/blob/main/src/clj/brundij/pre_render.clj).

It is important to add that my setup for generating these static htmls helps me achieve good performance results, but isn't the best and does something wrong: it calls `react-dom.render` instead of `react-dom.hydrate`. This happens because I am using a library that injects css at the dom asynchronously, which means the server-rendered html would never match the client's html.

### Third setup: building prerender scripts using shadow-cljs
Another possible approach is to create a shadow project that runs two separate builds: one for the regular browser build and another one that creates a node script for generating the prerendered html.

I'll use the same codebase we've used for the previous setups for simplicity's sake. To set up this method, I'll add two new build's to our `shadow-cljs.edn` and a new file to our source code:
```clj
{:nrepl {:port 8777}

 :deps true

 :dev-http
 {8280 "public"}

 :builds
   {:app {:target :graaljs
          :output-to "public/assets/graal.js"
          :entries [app.component]
          :jvm-opts ["-Xmx4G"]
          :modules
          {:app {:init-fn [app.component/countinghtml]
                 :prepend-js "./graal-bootstrap.js"}}
          :build-hooks [(util.clean-static/static-hook)]}
     :browser {:target     :browser
               :output-dir "public/assets/js"
               :asset-path "/js"

               :jvm-opts ["-Xmx6G"]
               :module-loader true

               :modules
               {:shared {}
                :counting {:entries [app.component
                                     app.events
                                     app.subs
                                     app.render-server
                                     app.render-client]
                           :depends-on #{:shared}}}}

     :pre-render {:target :node-script
                  :main app.render-server/main-to-html
                  :output-to "public/prerenderscript.js"}}}
```
```clj
(ns app.render-server
  (:require [app.component :refer [counting-component]]
            [app.events :as events]
            [clojure.string :as string]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [reagent.dom.server :as dom-server]
            ["react-dom" :as react-dom]
            ["fs" :as fs]))

(defn ^:export main-hydrate []
  (re-frame/dispatch-sync [::events/init-db])
  (let [cb (.getElementById js/document "root")]
    (react-dom/hydrate (r/as-element [counting-component])
                       cb)))

(defn main-to-html []
  (re-frame/dispatch-sync [::events/init-db])
  (let [html-base "
<html>
  <head>
    <meta charset=\"utf-8\">
    <script src=\"/assets/js/shared.js\"></script>
    <script src=\"/assets/js/counting.js\"></script>
  </head>
  <body>
    <div id=\"root\">${{html-string}}</div>
  </body>
  <script>app.render_server.main_hydrate();</script>
</html>"
        pre-rendered-view (dom-server/render-to-string [counting-component])
        final (clojure.string/replace html-base "${{html-string}}" pre-rendered-view)]
    (fs/writeFileSync "public/counting-view.html" final)))
```

The `browser` build target creates our typical browser build. This build is important because it makes it possible for us to import the needed javascript to our page (there are other ways of doing this, but you can probably figure it out). The `pre-render` build uses our new namespace's function to output a node script that will use `fs` to write a prerendered html to our public path.

Having the above set up, it is time to run `clj -M:cljs compile pre-render`, then `clj -M:cljs watch app` and, finally, navigate to `http://localhost:8280/counting-view.html`

### Fourth setup: using `nbb`
[nbb](https://github.com/borkdude/nbb) is a tool for `ad-hoc cljs scripting in node.js`. It allows us to run `Clojurescript` code as scripts. Let's prerender our application using `nbb`:

First, we'll want to modify our `counting-component` so it accepts an initial-data state. We'll talk about this initial state soon.
```clj
(defn counting-component [initial-data]
  (let [click-2 (reagent/atom 0)
        name (re-frame/subscribe [::subs/name])
        users (re-frame/subscribe [::subs/users])]
    (fn []
      [:div
       "The atom " [:code "click-count"] " has value: "
       @click-2 ". "
       [:p "Name has value " (if @name @name (:name initial-data))]
       [:p "Users:"
        (for [user (if @users @users (:users initial-data))]
          ^{:key (:id user)}
          [:p (:first_name user)])]
       [:p "Test 2"]
       [:input {:type "button" :value "Click me!"
                :on-click #(swap! click-2 inc)}]
       [:button {:on-click #(re-frame/dispatch [::events/change-name "teste"])}
        "Change name"]
       [:button {:on-click #(re-frame/dispatch [::events/fetch])}
        "Fetch users"]])))
```
We will then want to modify our `app.render-server/main-to-html` function so it accepts initial data:
```clj
(defn ^:export main-hydrate []
  (re-frame/dispatch-sync [::events/init-db])
  (let [cb (.getElementById js/document "root")]
    (react-dom/hydrate (r/as-element [counting-component])
                       cb)))

(defn main-to-html [initial-data]
  (re-frame/dispatch-sync [::events/init-db])
  (let [html-base "
<html>
  <head>
    <meta charset=\"utf-8\">
    <script src=\"/assets/js/shared.js\"></script>
    <script src=\"/assets/js/counting.js\"></script>
  </head>
  <body>
    <div id=\"root\">${{html-string}}</div>
  </body>
  <script>app.render_server.main_hydrate();</script>
</html>"
        pre-rendered-view (dom-server/render-to-string [counting-component initial-data])
        final (clojure.string/replace html-base "${{html-string}}" pre-rendered-view)]
    (fs/writeFileSync "public/counting-view.html" final)))
```

The nbb script file at the project's root:
```clj
(ns re-frame.core)

(defn reg-event-db [& _args])
(defn reg-event-fx [& _args])
(defn reg-sub [& _args])
(defn subscribe [& _args] (atom false))
(defn dispatch [& _args])
(defn dispatch-sync [& _args])

(ns re-frame-cljs-http.http-fx)

(ns cljs-http)

(ns render
  (:require [app.render-server :refer [main-to-html]]))

(defn render-server []
  (main-to-html {:name "app" :users []}))

(println (render-server))
```
You may have noticed something weird here. We're re-defining `re-frame.core`'s namespace. This is needed because [`nbb` doesn't support re-frame as of now](https://github.com/borkdude/nbb/issues/79), so we simply "mock" those to pre-render and this is also the reason we need initial data. Our initial data must match the data that will be inserted at our re-frame's `db` so React's prerendered DOM matches the DOM React's hydrate expects. We also re-define `cljs-http` and `re-frame-cljs-http.http-fx` namespaces to avoid having to use replace-deps

`nbb` supports adding clojure dependencies, but re-frame depends on a few `goog` classes that aren't defined on nbb right now.

Since our component/view is outside of our script, we'll need to pass our classpath to `nbb` and run it:
```bash
classpath="$(clojure -A:nbb -Spath -Sdeps '{}')"
nbb --classpath "$classpath" script.cljs
```
You should now be able to run `clj -M:cljs watch browser` and visit `http://localhost:8280/counting-view.html` to check the nbb-preredered page.

### Wrapping up

It was pretty fun to try all of this stuff. I ended up learning more about React, Clojurescript and the web. I am probably going to try using the `static html generation` graal setup for my next project since it gets hooked to shadow's build without having to spin up a node server.

I am also pretty sure there are much better setups out there, but I wanted to try and make things on my own. If you're looking for production-ready stuff you're better off asking people at Clojurians' slack.

If you're planning to adopt any of the prerendering setups above, you'd probably take a few things into account:
- Every of these setups is **highly experimental**
- You most probably wont be able to use `css-in-js` solutions.
- The third step will require some more boilerplate code/tooling if you need to use "esm only" libraries.
- The decision to use static htmls vs server-side rendered html should be based on your application's performance and needs.
- The etaoin setup is still probably the easiest one.
- [You can make `nbb` pull requests to add stuff that isn't yet available](https://github.com/borkdude/nbb/pull/87).
- Hacking shadow's `graaljs` target to output async code isn't the best approach.
