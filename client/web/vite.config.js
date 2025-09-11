export default {
    build: {
        outDir: "dist",
    },
    server: {
        port: 3000,
        open: true,
        proxy: {
            '/api': {
                target: process.env.ANYSTREAM_SERVER_URL,
                changeOrigin: true,
                ws: true,
            },
        },
    },
}
