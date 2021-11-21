Author: Arthur Barroso
Title: Experimentos tentando fazer pre-rendering de React em Clojure(script)
Link: prerendering_pt_br
Description: A blog post about different React pre-rendering techniques and approaches in Clojure(script)
Date: 2021-09-21
Tags: shadow-cljs, react, clojure, clojurescript, pre-render, ssr

Por cerca de três meses eu estive meio fascinado com a ideia de fazer pre-render das minhas aplicações react. Tudo isso começou quando comecei a desenvolver minha aplicação [brundij](https://github.com/arthurbarroso/brundij) e percebi que não conseguiria obter boas performances sem usar técnicas de pre-rendering, o que, por fim, me levou a ler bastante coisa sobre o assunto.

Eu desenvolvo aplicações React por algum tempo, mas nunca precisei construir qualquer mecânismo de pre-rendering sem frameworks como Gatsby ou Next, então fazer o pre-render "na mão" foi um desafio para mim. Acabei decidindo escrever esse post falando sobre todas as coisas que tentei e o que penso sobre cada uma dessas. Esse post **não** deve ser visto como um tutorial: tudo aqui é altamente experimental.

Enquanto escrevia, percebi que este post ficaria *bem* longo, então também criei um repositório com o código necessário para seguir o tutorial. Você pode checá-lo [aqui](https://github.com/arthurbarroso/blog-prerendering-demo).

### Pré-render de aplicações React
Pre-rendering em aplicações React é comumente alcançado utilizando frameworks como Gatsby e Next.js. Essas ferramentas/frameworks compartilham uma feature: elas tornam possível gerar o HTML estático das páginas da aplicação enquanto se faz o build. (Next.js também torna possível gerar esse conteúdo a cada request, usando server-side rendering).

O que acontece para ambas as estratégias (static site generation e server-side rendering) é que as telas/componentes React são renderizadas para uma string que depois se conecta a seus event handlers. Isso significa que o client baixa um HTML estático pré-renderizado e, com o HTML montado, o javascript da página (React) conecta os devidos event handlers. Isso é feito usando `ReactDomServer.renderToString()` e `React.hydrate()`.

### Pré-render em Clojurescript
Eu gosto de escrever minhas aplicações usando Clojurescript e ainda sim gostaria de poder fazer o pré-render delas. O problema disso é que os frameworks supracitados não funcionam bem com Clojuresccript, como apontado pelo Thomas Heller [nesse post](https://clojureverse.org/t/creating-websites-with-shadow-cljs-gatsby-or-next-js/2912).

Isso me levou a pensar em como eu poderia fazer o pré-rendering das minhas aplicações Reagent/re-frame sem precisar subir um servidor Node. Comecei então a procurar por conteúdos sobre o assunto, que me levaram a alguns achados:
- [React Server Side Rendering with GraalVM for Clojure](https://nextjournal.com/kommen/react-server-side-rendering-with-graalvm-for-clojure)
- [Prerendering a re-frame app with Chrome headless](https://medium.com/@joelsanchezclj/prerendering-a-re-frame-app-with-chrome-headless-bb875de31fd0)
- [pupeno/Prerenderer](https://github.com/pupeno/prerenderer) - que ainda não testei e, portanto, não falarei sobre
- [borkdude/nbb](https://github.com/borkdude/nbb)

Esses links me ajudarem a entender algumas coisas e tentar alguns setups diferentes, que descreverei aqui.

### Primeiro setup: usando GraalVM e Polyglot
Esse setup é altamente baseado no post linkado acima, [React Server Side Rendering with GraalVM for Clojure](https://nextjournal.com/kommen/react-server-side-rendering-with-graalvm-for-clojure), mas decidi mudar alguns detalhes para que isso se adaptasse melhor ao meu workflow:
- Ao invés de usar a versão customizada de Clojurescript usada pelo Nextjournal, optei por usar `shadow-cljs` e sua versão de Clojurescript.
- Eu gostaria de usar re-frame para controlar o estado da aplicação.
Para conseguir isso, precisei procurar um pouco e realizar alguns setups meio "hackish" que não são tão recomendados. Os passos para fazer isso são:

Criar nosso arquivo `deps.edn`
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
Configurar o `shadow-cljs`
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

Se compilarmos o código usando a configuração acima e tentarmos usar qualquer função assíncrona do javascript como `setTimeout` ou `setInterval` receberemos uma mensagem de erro dizendo que async ainda não é suportado pelo target `graaljs`. Como visto [nessa issue](https://github.com/thheller/shadow-cljs/issues/685) do repositório do `shadow-cljs`, os "shims" necessários para que Clojurescript rodasse as funções `async` no target `graaljs` foram removidos. Para resolver isso, precisaremos fazer com que o `shadow-cljs` faça um prepend de nosso código com os shims e delete as definições de função não suportada. Isso pode ser feito adicionando a key `prepend-js` apontando para [esse arquivo](https://github.com/clojure/clojurescript/blob/e4300da64c4781735146cafc0ca029046b83944c/src/main/cljs/cljs/bootstrap_graaljs.js) em nosso módulo app e criando um build hook
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
Hook:
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
O build hook acima cria uma cópia do código buildado pelo shadow e remove todas as definições `async_not_supported` do código, fazendo com que nossas chamadas a essas funções rodem as funções dos "shims" ao invés de causar erros.
Com isso feito, é hora de configurar o Polyglot e nosso primeiro componente:

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
ps.: a função serialize-arg foi encontrada [aqui](https://github.com/wavejumper/clj-polyglot/blob/e56783822e85d0b75d048c3e6a8b597f0e26724a/src/clj_polyglot/core.clj)

O componente que utilizaremos para fazer pré-rendering:
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
Por agora só usaremos event handlers mockados.

Existem duas maneiras de usar esse setup:
- Com server-side rendering (como visto no post do NextJournal)
- Gerando HTML estático no build e hidratando as páginas no client.

#### Server-side rendering
Iremos rodar a função `app.render` em cada request que nossa aplicação receber. Para montar um servidor base irei usar reitit, ring e jetty.
```clj
metosin/reitit {:mvn/version "0.5.5"}
ring/ring {:mvn/version "1.8.1"}
```
Criando um handler/router base:
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
Agora rodaremos `clj -M:cljs watch app` e navegaremos para `http://localhost:4000` no browser, que mostrará para nós a aplicação pré-renderizada. Pronto, fizemos o server-side rendering usando Graal.

#### Gerando HTMLs estáticos
Também é possivel usar o Graal para gerar HTML estáticos de nossa aplicação durante o build. Esses arquivos HTMLs então podem ser hidratados pelo browser. Para fazer isso, podemos simplesmente modificar nosso build-hook para que ele gere HTML usando o graal e salve esses arquivos no nosso diretório `public`
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
Rodar `clj -M:cljs watch app` e navegar até `http://localhost:8280/prerendered.html` deve nos mostrar nossa tela pré-renderizada.

##### Requests HTTP no client
Eu optei por não mostrar o código dos event handlers do re-frame de propósito. Você pode ter percebido que existe um evento chamado `fetch`. Esse evento deveria inciar um request HTTP, que muito provavelmente seria feito usando `re-frame-http-fx` em uma SPA clojurescript comum.

O problema é: quando usando o target `graaljs` será impossível usar `cljs-ajax`, que é a biblioteca por trás das requests do `re-frame-http-fx`: no target `graaljs` não temos acesso à `XMLHTTPRequest`, que é usado por essas bibliotecas. Para contornar esse problema, criei uma biblioteca que "envelopa" a biblioteca `cljs-http` em eventos/efeitos do re-frame. Vamos adicioná-la a nossas dependências:
```clj
cljs-http/cljs-http {:mvn/version "0.1.46"}
org.clojars.arthurbarroso/re-frame-cljs-http {:mvn/version "0.1.0"}
```
Com as dependências instaladas, vamos mudar/criar nosso event handler `fetch`
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
Agora, ao acessar a aplicação e clicar o botão "Fetch users" deve adicionar os resultados da request HTTP à lista de usuários.

### Segundo setup: Chrome headless com Etaoin
Não escreverei muito deste setup aqui. [O post escrito por Joel Sánchez](https://medium.com/@joelsanchezclj/prerendering-a-re-frame-app-with-chrome-headless-bb875de31fd0) cobre bem o assunto. Esse setup é de longe um dos mais fáceis de se fazer funcionar. Você pode fazer como o post dele sugere (usando um esquema de server-side rendering da sua aplicação) ou usá-lo para gerar HTMLs estáticos.

### Terceiro setup: criando scripts de pré-render usando shadow-cljs
Outra abordagem possível é criar um projeto shadow que rode duas builds separadas: uma que tenha como target o browser e outra que tenha como target um script node. Esse script node será o responsável por gerar o HTML pré-renderizado.

Usarei a mesma codebase dos setups anteriores para facilitar o setup. Para configurar isso, adicionremos dois novos builds a nosso `shadow-cljs.edn` e criaremos um novo arquivo de código
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

A build `browser` cria a build de browser. Essa build é importante pois torna possível importar o javascript necessário para nossa página (existem outras maneiras de fazer isso, mas você provavelmente consegue descobrí-las sozinho). A build `pre-render` usa as funções do nosso novo namespace para criar um script node que usa `fs` para criar o HTML pré-renderizado.

Com isso configurado, é hora de rodar `clj -M:cljs compile pre-render`, depois `clj -M:cljs watch app` e depois acessar `http://localhost:8280/couting-view.html`.

### Quarto setup: usando nbb
[nbb](https://github.com/borkdude/nbb) é uma ferramenta para scripting ad-hoc em clojurescript. Ela permite que usemos CLojurescript para rodar scripts em Node.js.

Para pré-renderizarmos a aplicação com nbb, primeiro iremos modificar nosso `counting-component` para que ele aceite uma prop initial-data. Falaremos mais sobre essa prop em breve.
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

Iremos então modificar nossa função `app.render-server/main-to-html` para que ela também aceite dados (initial-data)
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

Agora, por fim, criaremos nosso script no root do projeto:
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
Você deve ter percebido algo estranho aqui: estamos redefinindo o namespace `re-frame.core` - isso é necessário pois o [nbb ainda não suporta o re-frame](https://github.com/borkdude/nbb/issues/79), então o mockamos. Essa também a razão por precisarmos passar um initial-data para nossos componentes. Nosso initial-data deve ser igual ao conteúdo que será inicializado no db do re-frame para que o React não aponte erros de diferença entre o conteúdo renderizado e o conteúdo hidratado.
Como nosso componente não está dentro do arquivo do script, precisamos passar nosso classpath ao script e rodá-lo:
```bash
classpath="$(clojure -A:nbb -Spath -Sdeps '{}')"
nbb --classpath "$classpath" script.cljs
```
Agora deve ser possível rodar `clj -M:cljs watch browser` e visitar `http://localhost:8280/counting-view.html` para checar a página pré-renderizada.

### Concluindo
Foi bem legal testar todas essas coisas: acabei aprendendo mais sobre React, Clojurescript e a web.

Gostaria de salientar que provavelmente existem outros setups melhores e mais seguros por aí, mas, como eu disse, eu queria testar e fazer as coisas sozinho. Se você precisa de algo pronto para a produção talvez faça mais sentido pesquisar no slack clojurians.

Se você gostaria de testar qualquer um dos setups listados neste post, recomendo que leve em conta as seguintes coisas:
- TOdos esses setups são **altamente experimentais**
- Você provavelmente não conseguirá utilizar `css-in-js`
- O terceiro setup requer mais boilerplate se você precisar usar bibliotecas "esm only"
- A decisão de usar HTMLs estáticos ou fazer SSR deve ser baseada na performance de sua aplicação bem como seus requisitos
- O setup com etaoin é provavelmente o mais fácil
- [Você pode fazer pull requests no repositório do nbb para adicionar coisas que ainda não estão disponíveis](https://github.com/borkdude/nbb/pull/87)
- Fazer o target `graaljs` rodar código assíncrono não é o melhor caminho
