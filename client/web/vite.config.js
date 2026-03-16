import { defineConfig } from "vite";

export default {
    server: {
        port: 3000,
        open: false,
        hmr: true,
        host: true,
        watch: true,
        allowedHosts: [],
        proxy: {
            '/api': {
                target: process.env.ANYSTREAM_SERVER_URL,
                changeOrigin: true,
                ws: true,
            },
        },
    },
    plugins: [],
    css: {
        preprocessorOptions: {
            scss: {
                quietDeps: true,
            },
        },
    },
}