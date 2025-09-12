export default {
    server: {
        port: 3000,
        open: true,
        hmr: true,
        proxy: {
            '/api': {
                target: process.env.ANYSTREAM_SERVER_URL,
                changeOrigin: true,
                ws: true,
            },
        },
    },
}
