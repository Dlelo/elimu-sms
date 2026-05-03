import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

// In dev, proxy /v1 and /sms to the local Flask cloud server so the PWA can
// hit the same endpoints the J2ME MIDlet hits in production.
const CLOUD_TARGET = process.env.ELIMU_CLOUD_URL || "http://localhost:5051";

export default defineConfig({
  base: "./",
  server: {
    port: 5173,
    proxy: {
      "/v1":  { target: CLOUD_TARGET, changeOrigin: true },
      "/sms": { target: CLOUD_TARGET, changeOrigin: true },
    },
  },
  plugins: [
    VitePWA({
      registerType: "autoUpdate",
      injectRegister: "auto",
      manifest: {
        name: "ElimuSMS",
        short_name: "ElimuSMS",
        description: "Offline-first STEM tutor for Kenya CBC Grade 6.",
        theme_color: "#0f4f8b",
        background_color: "#ffffff",
        display: "standalone",
        start_url: "./",
        icons: [
          {
            src: "icon-192.svg",
            sizes: "192x192",
            type: "image/svg+xml",
            purpose: "any maskable",
          },
        ],
      },
      workbox: {
        // Cache the model + UI shell so the app works fully offline once installed.
        globPatterns: ["**/*.{js,css,html,svg,webmanifest}"],
      },
    }),
  ],
});
