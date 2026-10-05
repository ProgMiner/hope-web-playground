[![CI](https://github.com/ProgMiner/hope-web-playground/actions/workflows/ci.yml/badge.svg)](https://github.com/ProgMiner/hope-web-playground/actions/workflows/ci.yml)

## Build

To build compiler and playground, you will need NPM (easiest to install through [NVM](https://www.nvmnode.com/guide))
and [tree-sitter](https://tree-sitter.github.io/tree-sitter/creating-parsers/1-getting-started.html).

### Tree-sitter

```bash
cd tree-sitter-hope
tree-sitter build --wasm
tree-sitter build # builds .dll/.so for JVM bindings
```

### Kotlin/WASM

```bash
./gradlew wasmJsBrowserProductionWebpack
```

```bat
gradlew.bat wasmJsBrowserProductionWebpack
```

After this command, you should be able to locate built WASM & JS modules under the `build/wasm/packages`.

### Web

```bash
cd hope-web
npm run locals
npm install
npm run dev
```

If you're encountering errors with `node-gyp`, try this before `npm install`:

```bash
export CXXFLAGS="-std=c++20"
```

## Overloading

HOPE is extended with type classes, implemented by dictionary
passing (Wadler & Blott, "How to make ad-hoc polymorphism less ad hoc";
Hall et al., "Type classes in Haskell").

```hope
typevar alpha

class Size alpha with
  dec size : alpha -> num
end

instance Size truval with
  --- size(_) <= 1
end

instance Size (list alpha) given Size alpha with
  --- size(nil) <= 0
  --- size(x :: xs) <= size(x) + size(xs)
end

dec member : alpha # list alpha -> truval given Eq alpha
--- member(_, nil) <= false
--- member(x, y :: ys) <= (x = y) then true else member(x, ys)
```

- `=` is the method of the built-in class `Eq`. There are instances for `num`,
  `char`, `truval` (primitive comparison), lists and pairs (structural), and no
  instance for functions or sets. Write `instance Eq t` for your own data types.
- Constraints used by a function on its type variables must be listed after
  `given` in its `dec`; they are checked, not inferred.
- Instance heads are a type constructor (or `alpha # beta`) applied to distinct
  type variables; instances must not overlap. Classes and instances are global.
- Restrictions of the current version: method types mention only the class
  variable, constrained declarations must be functions,
  no superclasses or default methods, no defaulting of ambiguous types, and
  `let` does not generalize constrained type variables.

## Usage

### Web interface

Click "Run"

### CLI

There is also a JVM-based CLI application which can be used to compile HOPE.
You can run it like a regular Kotlin app if you have JDK 21+.

```bash
./gradlew :driver:jvmJar
# then run compile / bench subcommands via the driver main class
```

### JVM WASM benchmark

The fannkuch-redux example can be compiled to WASM and executed on the JVM
interpreter (Chicory) with timing output. This is useful for measuring compile
and run time without opening the browser.

```bash
# CLI (default n=5)
./gradlew :driver:fannkuchBench -Pfannkuch.n=7

# JUnit
./gradlew :driver:fannkuchBenchmark -Dfannkuch.bench=true -Pfannkuch.n=6
```
