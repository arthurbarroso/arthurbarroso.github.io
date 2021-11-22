Author: Arthur Barroso
Title: Testing the DOM using shadow-cljs and Reagent
Link: testing the dom using shadow and reagent
Description: Testing reagent dom components and screens using shadow-cljs and Clojurescript
Translation: dom_tests_pt_br
Date: 2021-09-13
Tags: shadow-cljs, reagent, tests, dom, react, clojurescript

I've been recently trying to build [brundij](https://github.com/arthurbarroso/brundij), an open-source tool for squad health checks. I ended up deciding on using Clojure and Clojurescript to build it (I've been learning clojure for a few months already but feel like a complete beginner, so I thought building something might help me grasp some stuff). I also decided to use `shadow-cljs` to set up my project.

Everything was going pretty smoothly until I needed to run DOM tests, which seemed like a big mistery to me -it seemed like most CLJS developers were only using Cypress and the [only post I could find talking about `react-testing-library` in clojurescript](https://francisvitullo.medium.com/a-way-of-testing-views-in-clojurescript-apps-98aaf57c5c2a) was two years old. All of this led me into creating this post, in which, I am going to try to help Clojurescript beginners, like me, run DOM tests on their shadow-cljs + reagent applications.

This post should be enough to get yourself a simple Clojurescript testing suite up and running.

## Table of contents:
1. [First try](#first)
2. [Getting things to render](#render)
3. [Making assertions about components](#assertions)
4. [Cleaning up the DOM in between tests](#cleaning)
5. [Setting up Karma](#karma)
6. [React testing library](#rtl)

### First try <a name="first"></a>

We'll start with a very basic reagent + shadow-cljs project. This will be done by running `lein new re-frame app` for simplicity's sake. With the app ready to go, we will also create a simple button component. It will look like this:
```clj
;;src/app/components/button.cljs
(ns app.components.button)

(defn button [{:keys [on-click text disabled]}]
  [:button
   {:type "button"
    :disabled disabled
    :on-click #(on-click)}
   text])
```
This is a very basic dummy component. It receives an `on-click` handler, a `text`, and a `disabled` boolean check as properties. With our button set-up, let's try rendering it in our tests. We'll first need to add the following build specification to `shadow-cljs.edn` and then create our test file
```clj
;;shadow-cljs.edn
  :test {:target :browser-test
         :test-dir "resources/public/js/test"
         :devtools  {:http-port          8021
                     :http-root          "resources/public/js/test"}}}}
```

```clj
;;test/app/button_test.cljs
(ns app.button-test
  (:require [app.components.button :refer [button]]
            [cljs.test :refer-macros [deftest is testing]]
            [reagent.dom :as rdom]))

(deftest button-component-test
  (testing "Renders correctly"
    (rdom/render [button {:on-click #(println "hi")
                          :text "button"
                          :disabled false}]
                 (.getElementById js/document "app"))
    (is (= true true))))

```

If we run `npx shadow-cljs watch test` our test environment will be up and running. Assuming you've set up the test build config the same way I did, you'll be able to navigate to `localhost:8021` and check that our test is failing with an uncaught exception:
`Error: target container is not a DOM element`

This happens because there is no `div id="app"` inside our test html file. We could change its content and insert a `div` with the id `app` directly to the file, but we'll do this another way.

### Getting things to render <a name="render"></a>
As seen above, we need to somehow have a div with the id `app` in our DOM before running the tests. A nice way of dealing with this is by using Clojurescript's `use-fixtures` -using it we'd be able to define a fixture that runs only once and uses javascript to create the div we need. Let's take a look at how we're able to do this:
```clj
;;test/app/button_test.cljs
(ns app.button-test
  (:require [app.components.button :refer [button]]
            [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [reagent.dom :as rdom]))

(defn create-app-element [f]
  (.appendChild (.-body js/document) ;; gets the Body element
                (doto (.createElement js/document "div") ;; creates a new div
                  (-> (.setAttribute "id" "app")) ;; sets the new div id to be `app`
                  (-> (.setAttribute "style" "display:none;")))) ;; makes that div invisible
  (f))

(use-fixtures :once create-app-element)

(deftest button-component-test
  (testing "Renders correctly"
    (rdom/render [button {:on-click #(println "hi")
                          :text "button"
                          :disabled false}]
                 (.getElementById js/document "app"))
    (is (= true true))))
```
We'll use this `create-app-element` function for each of our test files. It'll ensure there is the div with the id `app`. It basically creates a new div, set it's id to `app`, and set its `display` css property to `none`, so the components don't end up showing on our shadow test report page. This setup should be enough to get our tests running without the previous failure.

### Making assertions about components <a name="assertions"></a>
With components rendering in our tests, we're now able to make assertions about them. Let's say we want to check whether our button component actually renders. We could check if the `text` prop is being rendered within the button component using the following test

```clj
(deftest button-component-test
  (testing "Renders correctly"
    (rdom/render [button {:on-click #(println "hi")
                          :text "button"
                          :disabled false}]
                 (.getElementById js/document "app"))
    (let [app-element (.getElementById js/document "app")
          button (-> (.getElementsByTagName app-element "button")
                     (first))]
      (is (= "button" (.-textContent button))))))
```

This is a dummy test. We're simply checking if things are rendering. If we really want to test things and their behaviors we'd need to click buttons, change inputs and such. This is where `react-dom/test-utils` comes in.

With `react-dom/test-utils`, we're able to simulate user events and check whether our button uses the `on-click` handler property it receives. An example of test using `react-dom/test-utils` would look like the following:
```clj
(ns app.button-test
  (:require [app.components.button :refer [button]]
            [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [reagent.core :as reagent]
            [reagent.dom :as rdom]
            ["react-dom/test-utils" :as dom-test-utils]))

(defn create-app-element [f]
  (.appendChild (.-body js/document)
                (doto (.createElement js/document "div")
                  (-> (.setAttribute "id" "app"))
                  (-> (.setAttribute "style" "display:none;"))))
  (f))

(use-fixtures :once create-app-element)

(deftest button-component-click-test
  (testing "Uses the supplied `on-click` property"
    (let [ra (reagent/atom 1)] ;; we create a new atom with the value of 1
      (rdom/render [button {:on-click #(swap! ra inc) ;; we define that 
                                                      ;;on-click should increment 
                                                      ;;our atom's value
                            :text "button"
                            :disabled false}]
                   (.getElementById js/document "app"))
      (let [app-element (.getElementById js/document "app")
            button (-> (.getElementsByTagName app-element "button")
                       (first))] ;; gets the button element
        (.click dom-test-utils/Simulate button) 
        ;; react-dom/test-utils simulates a user click
        (is (= 2 @ra)))))) ;; ra's value should've been incremented
```
This example uses a reagent atom to check whether on-click has been called. This could also be done using re-frame, for example.

### Cleaning up the DOM in between tests <a name="cleaning"></a>
Let's say we want to clean up the DOM between each `deftest`. This is achievable, but not with the code we currently have. Since we're using `reagent-dom/render` to render stuff, we cant simply delete our `app`'s div children -our components aren't being rendered as children, the whole `app` div is becoming our components. We can fix this.

Our first step will be defining our `append-container` function. This function will take a target element and an `id` as arguments. It will then create a div with that `id` as the `target`'s children. We will then define our `dom-cleanup!` fixture, which will use's `clojure.browser.dom` `remove-children` function, which simply removes a DOM element's children.
```clj
(ns app.button-test
  (:require [app.components.button :refer [button]]
            [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [clojure.browser.dom :refer [remove-children]]
            [reagent.core :as reagent]
            [reagent.dom :as rdom]
            ["react-dom/test-utils" :as dom-test-utils]))

(defn create-app-element [f]
  (.appendChild (.-body js/document)
                (doto (.createElement js/document "div")
                  (-> (.setAttribute "id" "app"))
                  (-> (.setAttribute "style" "display:none;"))))
  (f))

(defn dom-cleanup! [f]
  (remove-children "app")
  (f))

(defn append-container [target id]
  (let [container (.getElementById js/document id)]
    (if container
      container
      (.appendChild target (doto (.createElement js/document "div")
                             (-> (.setAttribute "id" id)))))))

(use-fixtures :once create-app-element)
(use-fixtures :each dom-cleanup!)
```

Our tests will stay pretty much the same, except we'll render them using `append-container` and will query the DOM for them using the `id` we supply to `append-container`
```clj
(deftest button-component-click-test
  (testing "Uses the supplied `on-click` property"
    (let [ra (reagent/atom 1)]
      (rdom/render [button {:on-click #(swap! ra inc)
                            :text "button"
                            :disabled false}]
                   (append-container (.getElementById js/document "app")
                                     "button-click-test")) 
                                     ;; this creates a children div
                                     ;; with the id `button-click-test`
      (let [app-element (.getElementById js/document "button-click-test") 
      ;; queries for the div we've just created
            button (-> (.getElementsByTagName app-element "button")
                       (first))]
        (.click dom-test-utils/Simulate button)
        (is (= 2 @ra))))))
```

### Setting up Karma <a name="karma"></a>
In order to run the tests we've just created in a CI, we'll need [karma](https://karma-runner.github.io/latest/index.html). Karma is a javascript test runner and is recommended by the shadow-clj's user guide. Let's set it up. To do so, we'll add karma's dependencies to the `package.json` `devDependencies`, tweak our `shadow-cljs.edn` file a little and create a `karma.conf.js` file. Let's go ahead.

We'll need `karma`, `karma-cljs-test` (so we can write our tests using `cljs`) and `karma-chrome-launcher`, so karma spins up a chrome instance and runs our browser tests.
```json
//package.json

{
 "devDependencies": {
  "karma": "^2.0.0",
  "karma-chrome-launcher": "^2.2.0",
  "karma-cljs-test": "^0.1.0",
  "shadow-cljs": "2.15.2"
 }
}
```
We'll add a `:ci` build configuration in our shadow-cljs.edn file.
```edn
 ;;shadow-cljs.edn
  :ci {:target :karma
       :output-to "target/ci.js"}}}
```

```js
//karma.conf.js
module.exports = function (config) {
    config.set({
        browsers: ['ChromeHeadless'],
        // The directory where the output file lives
        basePath: 'target',
        // The file itself
        files: ['ci.js'],
        frameworks: ['cljs-test'],
        plugins: ['karma-cljs-test', 'karma-chrome-launcher'],
        colors: true,
        logLevel: config.LOG_INFO,
        client: {
            args: ["shadow.test.karma.init"],
            singleRun: true
        }
    })
};

```

We're now able to run our tests in a CI environment using `npx shadow-cljs compile ci && npm run karma start --single-run`


### React testing library <a name="rtl"></a>
I won't really write much aobut `react-testing-library` in here. There is a [great post by Francesco Vitullo on how to test Clojurescript apps using it](https://francisvitullo.medium.com/a-way-of-testing-views-in-clojurescript-apps-98aaf57c5c2a). I tried following it's steps (pinning react-testing-library's version to `6.1.2`) and managed to get it working. I think it's main advantage is that RTL makes it possible to avoid all that DOM manipulation we've done, but this didn't seem like a good enough reason for me to use it. I plan on giving it another try, though.
