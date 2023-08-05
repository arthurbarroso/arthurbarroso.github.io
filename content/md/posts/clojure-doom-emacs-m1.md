{:title "Clojure and Doom Emacs on a brand new M1 computer"
 :layout :post
 :date "2022-04-17"
 :translation "x"
 :tags ["clojure" "m1" "clj-kondo" "clojure-lsp" "emacs" "doom-emacs" "parinfer"]}

I've recently landed my first Clojure position and received an m1-powered MacBook. Two months before that, I had also bought myself an m1 MacBook air and had to set up Clojure to use it in my projects. Since I had no experience using macOS whatsoever I had to do some googling and managed to get a pretty simple setup running.

There are lots of tutorials out there on how to set up m1 laptops for Clojure usage, but there are a few quirks that aren't yet documented, such as how to get `clj-kondo` and `parinfer` to work in it. I'll try to cover these steps in this post, while also showing how to set up doom emacs and some other stuff I ended up using myself.

Let's get to it:

## Table of contents:
1. [Installing Java and Clojure](#java)
2. [Doom Emacs](#emacs)
3. [Clj-kondo](#kondo)
4. [Parinfer-rust](#parinfer)

### Installing Java and Clojure <a name="java"></a>
This post will describe two ways of installing Java and Clojure: one will use [homebrew](https://brew.sh) and the other will install things manually.

#### Using homebrew:
We'll go the easy route here: we will use Homebrew to install Zulu (an m1 native version of the JVM), Clojure, Java, and Leiningen. To do so, we'll start by adding homebrew:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```
After installing, homebrew will ask us to add it to our PATH. We'll do so and move on to installing Java, Clojure, and Leiningen:
```bash
arch -arm64 brew install zulu clojure/tools/clojure leiningen
```
With the above done, we'll now simply add our `JAVA_HOME` to our PATH:
```bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
```
To verify our setup, we can simply run `clojure` and open up a REPL.

#### Manual installation:
We will start by downloading Azul's Zulu m1 build from their [downloads page](https://www.azul.com/downloads/?version=java-17-lts&os=macos&architecture=arm-64-bit&package=jdk). With the download done, we'll open the `zip` file and copy the `zulu-17.jdk` folder to `/Library/Java/JavaVirtualMachines/`. We'll also set the `JAVA_HOME` variable:
```bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
```
With `JAVA_HOME` set up, it is time to install Leiningen, which will also install Clojure. To do so, we'll simply follow [Leiningen's instructions](https://leiningen.org/).

With the above done, we can now verify our setup by running `clojure`.

### Doom Emacs <a name="emacs"></a>
My editor of choice is Doom Emacs. To use it, we'll need to first download emacs. This will be done using homebrew:
```bash
brew tap railwaycat/emacsmacport
brew install --cask emacs-mac
```
With the step above done, we'll now move into getting [Doom Emacs](https://github.com/hlissner/doom-emacs):
```bash
git clone --depth 1 https://github.com/hlissner/doom-emacs ~/.emacs.d
~/.emacs.d/bin/doom install
```
Lets now configure it so we can use tools such as CIDER and clojure-lsp. This can be done by changing the `.doom.d/init.el` file. We'll start by uncommenting `lsp`, and `clojure` (under `:lang`). We will also want to add `+lsp` to that `clojure` expression, making it `(clojure +lsp)`. Having done that, we'll run `~/.emacs.d/bin/doom sync` and our pretty basic Clojure environment will be set up.

### Clj-kondo <a name="kondo"></a>
Adding `+lsp` to `clojure` in `init.el` made doom add `clojure-lsp` to our emacs configuration. Clojure-LSP comes with `clj-kondo`, but, for some reason I do not know, `flycheck` won't recognize it, so we'll probably also want to install kondo as a separate executable. To do so, we'll need to install GraalVM.

Setting up GraalVM on an m1 device currently requires using a snapshot, so we'll simply use it. Follow [this comment](https://github.com/oracle/graal/issues/2666#issuecomment-1074884020) to get it up and running. (The file you should download is `graalvm-ce-java17-darwin-aarch64-dev.tar.gz`). After setting up, also add the `GRAALVM_HOME` variable to `PATH` -it should match the value for the `JAVA_HOME` for Graal.

We can now compile and install clj-kondo on an m1 device:
```bash
git clone https://github.com/clj-kondo/clj-kondo.git
cd clj-kondo
script/compile
```
Now simply add it to your PATH and you'll be able to use `clj-kondo` in `flycheck`.

### Parinfer-rust <a name="parinfer"></a>
There is currently no releases of `parinfer-rust` for m1 devices. This means we need to compile it ourselves. We'll need to [install rust](https://www.rust-lang.org/tools/install):
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env
```
We'll then move into compiling parinfer-rust:
```bash
git clone https://github.com/eraserhd/parinfer-rust.git
cd parinfer-rust
cargo build --release --features emacs
```
After compiling it, we'll move it to emacs:
```bash
mkdir ~/.emacs.d/.local/etc/parinfer-rust
mv target/release/libparinfer_rust.dylib ~/.emacs.d/.local/etc/parinfer-rust/parinfer-rust-darwin.so
```

We'll activate parinfer on our doom config by uncommenting `parinfer` in `~/.doom.d/init.el` and adding `(use-package! parinfer-rust-mode)` to our `~/.doom.d/packages.el` file and run `~/.emacs.d/bin/doom sync` 


And that's pretty much it for a basic setup :) Hope someone finds this useful
