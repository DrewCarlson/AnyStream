config.resolve.modules.push("../../processedResources/js/main");
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;

if (config.devServer) {
    config.plugins = config.plugins || []
    config.plugins.push(new BundleAnalyzerPlugin({
        analyzerMode: 'disabled'
    }))
    config.devServer.historyApiFallback = true;
    config.devServer.hot = true;
    config.devServer.proxy[0].ws = true;
    config.output.publicPath = '/';
}
