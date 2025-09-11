config.module.rules.push({
    test: /\.(css)$/,
    use: [
        'style-loader',
        'css-loader',
        {
            loader: 'postcss-loader',
            options: {
                postcssOptions: {
                    plugins: [
                        'postcss-import',
                        'autoprefixer',
                    ]
                }
            }
        },
    ]
});