config.module.rules.push({
    test: /\.(woff(2)?|ttf|eot|svg)(\?v=\d+\.\d+\.\d+)?$/,
    type: "asset/resource",
    generator: {
        filename: "fonts/[name][ext]"
    }
});