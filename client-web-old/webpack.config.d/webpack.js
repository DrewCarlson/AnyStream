config.resolve.modules.push("../../processedResources/js/main");

if (config.devServer) {
    config.devServer.historyApiFallback = true;
    config.devServer.hot = true;
    config.output.publicPath = '/';
}
