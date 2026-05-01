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
./gradlew wasmJsNodeProductionRun
```

```bat
gradlew.bat wasmJsNodeProductionRun
```

After this command, you should be able to locate built WASM & JS modules under the ```build/wasm/packages```.

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

## Usage

### Web interface

Click "Run"

### CLI

There is also a JVM-based CLI application which can be used to compile HOPE.
You can run it like a regular Kotlin app if you have JDK 21+.
