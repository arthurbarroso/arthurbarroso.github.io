Author: Arthur Barroso
Title: Testando a DOM usando shadow-cljs e Reagent
Link: dom_tests_pt_br
Description: Testing reagent dom components and screens using shadow-cljs and Clojurescript
Date: 2021-09-13
Tags: shadow-cljs, reagent, tests, dom, react, clojurescript

Há algum tempo tenho tentado desenvolver [brundij](https://github.com/arthurbarroso/brundij), uma ferramenta open-source para fazer squad health checks. Eu decidi usar Clojure e Clojurescript para construí-la (já estava estudando Clojure por alguns meses, mas ainda me sentia um completo iniciante, então decidi que construir uma ferramenta usando Clojure poderia me ajudar a entender algumas coisas). Também decidi usar `shadow-cljs` para o setup do projeto.

Tudo corria bem até o momento em que precisei rodar testes relacionados à DOM: aparentemente poucos dos desenvolvedores usando Clojurescript faziam testes no estilo react-testing-library. O único post que achei falando sobre testes de DOM/componentes/telas era de dois anos atrás. TUdo isso me levou a criar esse post, em que tentarei ajudar iniciantes em Clojurescript a fazer esse tipo de testes em suas aplicações `shadow-cljs` e `reagent`.

### Primeira tentativa:
Iniciaremos com um projeto básico utilizando shadow-cljs e reagent. Para criar o projeto, podemos usar o leiningen rodando o comando `lein new re-frame app`. Com o app inicializado, iremos criar nosso primeiro componente -um botão simples:
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

Esse é um componente *bem* básico: ele recebe um handler/função `on-click`, um texto a ser exibido (`text`) e um booleano `disabled`. Com o botão criado, vamos tentar renderizá-lo em nossos testes. Para isso, precisamos primeiro criar uma configuração de build de testes em nosso `shadow-cljs.edn` e depois sim escrever um teste:
```clj
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

Se rodarmos `npx shadow-cljs watch test`, teremos nosso ambiente de testes rodando. Se você configurou a build de testes do mesmo jeito que eu, você poderá navegar até `localhost:8021` e checar que nosso teste está falhando com uma "uncaught exception": `target container is not a DOM element`

Esse erro é causado pela ausência de um element `div` com o id `app` dentro de nosso arquivo HTML de teste (que é utilizado pelo shadow para montar o ambiente de testes). Poderíamos modificar esse arquivo manualmente adicionando essa div lá, mas abordaremos esse problema de uma outra forma.

### Renderizando
Como visto acima, precisamos ter uma `div` com o id `app` em nossa DOM antes de rodar os testes. UMa maneira de fazer isso sem editar diretamente o arquivo HTML dos testes é usando a função `use-fixtures` (disponível em clojure.test e cljs.test). Podemos então definir algumas funções que devem ser executadas antes de nossos testes. Vamos criar uma fixture que inserirá a div que precisamos em nossa DOM antes de rodar nossos test cases:

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
A função `create-app-element` nos será muito util e a utilizaremos em cada um de nossos arquivos de teste. Ela basicamente cria uma div, seta o id desta div como `app` e aplica `style= display:none` para que esse elemento não fique aparecendo na tela de testes do shadow-cljs (a que vimos acessando `localhost:8021`). Isso deve ser o suficiente para rodarmos o nosso teste sem recebermos erros quando acessarmos o report de testes.

### Fazendo asserções sobre os componentes
Com os componentes renderizando nos testes, podemos agora fazer asserções sobre eles. Suponha que o objetivo seja testar que o botão realmente renderiza: podemos fazer isso de várias maneiras, sendo uma delas checar se a prop `text` que esse botão recebe acaba sendo renderizada na DOM:
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

Esse, no entanto, não é um bom teste: ele simplesmente checa se o componente renderiza. Para testarmos os comportamentos de nossos componentes precisamos interagir com eles como um usuário faria, e é aqui que usaremos `react-dom/test-utils`.

Com o `react-dom/test-utils`, podemos simular eventos de um usuário e checar se esses eventos trazem mudanças para nossos componentes. No nosso caso, podemos clicar no botão e checar se o `on-click` dele é ativado:
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

O teste usa um `atom` do `reagent`, mas poderia usar re-frame, por exemplo.

### Limpando a DOM entre testes
O setup atual tem um problema: não limpamos a DOM entre os testes. Isso significa que podem ocorrer conflitos entre cada um de nossos `deftest`. Podemos resolver isso, mas não com o código que temos atualmente: como usamos `reagent-dom/render` para renderizar nossos componentes na div com id `app`, não temos esses componentes como "filhos" da div, e sim a div como o componente (o componente toma o lugar da div).

O primeiro passo para podermos limpar a DOM com esse setup será definir uma função `append-container`. Essa função receberá um elemento destino e um id como argumentos. Com esses dados em mãos, ela criará uma div com o id que recebeu e fará com que essa div seja "filha" do elemento destino. Definiremos então outra função chamada `dom-cleanup!`, que será uma fixture e que utilizará a função `clojure.browser.dom/remove-children`, que remove os elementos "filhos" de um elemento escolhido.

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

Com essas funções em mãos, apenas precisamos garantir que nossos testes usem a função `append-container` para renderizar os componentes:
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

### Usando o Karma
Para rodar os testes acima numa CI, será necessário usar o [karma](https://karma-runner.github.io/latest/index.html). Karma é um test runner para javascript e é recomendado pelo user guide do `shadow-cljs`. Para usá-lo, vamos adicionar as dependências do karma às nossas dependências de desenvolvimento no nosso `package.json`, faremos algumas alterações em nosso `shadow-cljs.edn` e criaremos um arquivo  `karma.conf.js`.

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

```clj
 ;;shadow-cljs.edn
  :ci {:target :karma
       :output-to "target/ci.js"}}}
```

```clj
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

Com tudo isso feito, poderemos rodar nossos testes em um ambiente de CI rodando o comando `npx shadow-cljs compile ci && npm run karma start --single-run`

# React testing library
Não falarei muito sobre o `react-testing-library` nesse post. Um excelente post explicando como utilizar o rtl para testar aplicativos Clojurescript já existe. Eu cheguei a tentar seguir o post/tutorial (forçando a versão do RTL para 6.1.2) e consegui fazer meus testes rodarem. Acredito que a maior vantagem do RTL é evitar todo esse processo de interop e manipular a DOM, mas isso não me parece o suficiente para usá-lo no momento.
