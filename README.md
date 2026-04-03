== Build

To build compiler and playground, you will need NPM (easiest to install through [NVM](https://www.nvmnode.com/guide)).

=== Kotlin/WASM

```
gradlew[.bat] wasmJsBrowserDistribution
```

After this command, you should be able to locate built WASM & JS modules under the ```build/wasm/packages```.

=== Web

```
cd hopa
npm install
npm run dev
```

== Usage

Click "Run"

TODO
