const MiniCssExtractPlugin = require('mini-css-extract-plugin');

config.plugins.push(new MiniCssExtractPlugin())

config.module.rules.push({
    test: /\.(scss)$/,
    use: [
        {loader: 'style-loader'},
        {
            loader:MiniCssExtractPlugin.loader,
            options: {
                esModule: false,
            }
        },
        {loader: 'css-loader'},
        {
            loader: 'postcss-loader',
            options: {
                postcssOptions: {
                    plugins: function () {
                        return [
                            require('autoprefixer')
                        ];
                    }
                }
            }
        },
        {
            loader: 'sass-loader',
            options: {
                sassOptions: {
                    silenceDeprecations: ['color-functions', 'global-builtin', 'import', 'mixed-decls'],
                }
            }
        }
    ]
});