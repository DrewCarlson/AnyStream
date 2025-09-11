const EsbuildLoader = require("esbuild-loader");
config.module.rules.push({
    test: /\.js$/,
    loader: "esbuild-loader",
    options: {
        target: "es2015",
        loader: "js",
    },
});


if (!config.devServer) {
    config.optimization = {
        minimize: true,
        minimizer: [
            new EsbuildLoader.EsbuildPlugin({
                target: "es2015",
            }),
        ],
    };
}
