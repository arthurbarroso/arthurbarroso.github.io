Author: Arthur Barroso
Title: My Clojure(script) journey
Link: my clojure script journey
Description: A little post describing my journey into Clojure(script)
Date: 2021-09-13
Tags: shadow-cljs, reagent, tests, dom, react, clojurescript

I've been learning Functional Programming for quite some time: I studied a bit of Haskell, played around with ReasonML, and ultimately started to work with Elixir. At some point, though, I realized I wanted to try a different language: this is when Clojure comes in - it simply looked really different from the code I was used to writing, which made me decide on trying to learn it.

Clojure is a really nice language and the whole "REPL oriented development" thing is magic, but it took me some time to actually enjoy the language and learn what the REPL meant for the whole development experience. I'll use this post to talk a little about my Clojure(script) journey and how I eventually started loving it.

## Table of contents:
1. [The start](#start)
2. [The development environment](#devenv)
3. [Exercises](#exercises)
4. [Building things](#building)
5. [Closing thoughts](#closing)

### The start <a name="start"></a>
The first time I tried Clojure wasn't really great: I spun up a `repl.it` session and started going through the book Clojure for the Brave and True. Even though the book is great, I wasn't yet able to enjoy it: I didn't really have a nice development setup and had no idea how lisps worked - Clojujre's huge use of Sequences wasn't helpful either, since I felt like these were much different than Elixir's, for an example. 

The frustration eventually got me and I quit Clojure. Went back to ReasonML, Elixir, and even some Typescript using `fp-ts`. This situation reminded me of the reason I wanted to try Clojure: it often felt like the code I was writing in my free time was simply the code I wrote at work.

I ultimately decided to give Clojure another try, and this led me into buying Jacek Schae's reitit course. I felt like building an application using Clojure would help me get past the "exercise hell" I was in when I started.

### The development environment <a name="devenv"></a>
I feel like one of the things that weren't good for me was trying to set up a development environment:
- I used NeoVim for my daily activities and learning new keybindings seemed like a huge effort, so I didn't really want to switch editors
- I really disliked VSCode: I tried calva, and even though it is **great**, I wasn't willing to use VSCode
- Emacs is **great** and I use it now, but having to learn Clojure AND emacs at the same time seemed like a daunting task

The thing is: most of the "Clojure setup" tutorials use emacs (and to be quite honest I get it now) and setting up NeoVim to work nicely with Clojure was kinda hard: I knew there was fireplace, iced, and other REPL plugins, but:
- It was hard to decide which plugin to use
- Structural editing was still hard
- Jacek's course used IntelliJ and Cursive. I was mostly fine using it during the course's classes but didn't want to use it as my primary Clojure tool.

At some point though, I stumbled across Kelvin Mai's videos and started watching them. One of his videos showed his NeoVim Clojure setup using Conjure, clojure-lsp and some other tools that made me able to use and enjoy nvim with Clojure. I feel like the moment I found Kelvin's nvim setup was the moment I decided to **really** give Clojure a try.

### Exercises <a name="exercises"></a>
4Clojure was still up when I started to learn Clojure and it was a great tool for me to learn a little about the language itself. I didn't finish many exercises though: it sometimes felt too hard and I'd feel stuck - to overcome this feeling, I'd try and create applications using Clojure(script). `learn-clojurescript` really helped me with this: the book goes through many exercises and some applications as a way to "pin" what you learn.

I quickly learned that RoamResearch was built using Clojure and so was Athens. Both seemed like incredible applications and I decided I'd go for Athen's `clojure-fam` learning route. This meant one of my goals was to get at least a single commit into Athen's codebase and understand a little bit of the code. I didn't get a **really** "cool" commit into the codebase, but ended up commiting a fix to a bug with the help of others. 

`clojure-fam`'s learning path was really focused on technologies Athens used, such as re-frame. This made me learning re-frame one of my main goals and it made me enjoy Clojurescript **so much**.

A nice resource I was able to find was `lambdaisland`: I went through many of it's videos in order to learn a little more about datomic (I was heavily interested in learning datascript since Athens used it) and re-frame. Lambdaisland's re-frame videos and re-frame's documentation were just... fun to watch, read, and follow along.

### Building things <a name="building"></a>
After doing many exercises I decided I wanted to build applications. This is when Jacek's reitit course was really useful to me: I built a backend server using Integrant, next.jdbc, reitit, and clj-http. This made me realize how fun it was to build applications using Clojure and eventually made me want to try and build my own apps.

I work at a consultancy company and we often run squad health checks. We've been using these online free tools for quite some time, but most of the team didn't enjoy using them and didn't feel like they were safe. Two colleagues decided they'd build a squad health check tool, but one of them left the company and it didn't go through. I then decided I'd build it on my own, using Clojure(script).

Creating and developing my own application wasn't easy: I ended up trying many different approaches to stuff, which I think was a great way to learn Clojure and some of its libraries:
- I first tried creating an authenticated-only application, which I later decided wasn't good (I didn't really need auth for the app I was building)
- Tried using `next.jdb`, but I felt like I could learn more about other stuff, which led me into trying other "database libraries"
- Adopted `datahike` for my database connection and started to learn it.
- Made the client offline-first by storing all the data to a datascript db and persisting it to local storage but ended up removing it since it wasn't that useful
- [Pre-rendered stuff using etaoin but didn't really enjoy it and decided to try and pre-render it "manually"](https://www.arthurbrrs.me/prerendering-react-clojurescript-land.html)
- Tried creating a "native executable" of my application using Graal, and after many tries was able to do it
- [Set up DOM tests using shadow-cljs and Reagent](https://www.arthurbrrs.me/prerendering-react-clojurescript-land.html)

### Closing thoughts <a name="closing"></a>

I ultimately feel like the development setup can be a gatekeeper in Clojure. I wouldn't be able to get all the joy I now get writing Clojure if I hadn't found Kelvin's video. I do realize that isn't a big problem if you're okay with using VSCode (due to PEZ's amazing work on Calva), but that wasn't my case. Clojure without the REPL just doesn't feel right, to be honest.

Finding out most of the Clojure community is in favor of libraries instead of big frameworks was also quite strange coming from Javascript/Typescript land, but I eventually understood it and love the way people create small composable libraries in Clojure - I feel like I can better control and plug/unplug things whatever way I want.

Clojure's community is filled with nice people who are often kind-hearted and helpful. This means you can probably open up Clojurian's slack right now, ask some questions and get somewhat detailed answers, and often some explanation/bits of advice. I believe I would probably have had a greater time if I had joined Clojurian's slack earlier.

I also feel like you don't really need to buy subscriptions or courses to learn Clojure(script): I simply did it because I wanted a faster route and that's how I liked to learn things back then. All you need is some patience, a good dev setup, and good googling skills.

A thing I still don't really enjoy is that I feel it is kinda hard to find Clojure entry-level positions. I am not completely sure why this happens, but I assume the problem here isn't the lack of entry-level positions, but that Clojure's community is somewhat smaller than say JavaScript's community so people like me end up feeling there aren't many entry-level positions out there.

I'd encourage everyone with a little bit of programming experience to try ou Clojure. It took me some time, more than one try, and patience, but it completely changed the way I think about software and write software (even when I'm not writing Clojure - I work with Elixir). The REPL is magic, but to be quite honest, I feel like Clojure makes me think much more about the problems I am trying to solve.
