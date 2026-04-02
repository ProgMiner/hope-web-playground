                    let config = {
                      mode: 'production',
                      resolve: {
                        modules: [
                          "node_modules"
                        ]
                      },
                      plugins: [],
                      module: {
                        rules: []
                      },
                      resolveLoader: {
  modules: ['node_modules', process.env['KOTLIN_TOOLING_DIR']]
}
                    };
                    
// entry
config.entry = {
    main: [require('path').resolve(__dirname, "kotlin/hopec-driver.mjs")]
};
config.output = {
    filename: (chunkData) => {
        return chunkData.chunk.name === 'main'
            ? "driver.js"
            : "driver-[name].js";
    },
    library: "driver",
    libraryTarget: "umd",
    clean: true,
    globalObject: "globalThis"
};
config.output = config.output || {}
config.output.path = require('path').resolve(__dirname, "../../../../driver/build/kotlin-webpack/wasmJs/productionExecutable")
// source maps
config.module.rules.push({
        test: /\.m?js$/,
        use: ["source-map-loader"],
        enforce: "pre"
});
config.devtool = 'source-map';
config.ignoreWarnings = [
    /Failed to parse source map/,
    /Accessing import\.meta directly is unsupported \(only property access or destructuring is supported\)/
]

// noinspection JSUnnecessarySemicolon
;(function(config) {
    const tcErrorPlugin = require('kotlin-web-helpers/dist/tc-log-error-webpack');
    config.plugins.push(new tcErrorPlugin())
    config.stats = config.stats || {}
    Object.assign(config.stats, config.stats, {
        warnings: false,
        errors: false
    })
})(config);
config.experiments = {
    asyncWebAssembly: true,
    topLevelAwait: true,
}
module.exports = config
