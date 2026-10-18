import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "");
  const backendOrigin = env.VITE_BACKEND_ORIGIN || "http://localhost:8080";

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": {
          target: backendOrigin,
          changeOrigin: true,
        },
        "/auth": {
          target: backendOrigin,
          changeOrigin: true,
        },
        "/images": {
          target: backendOrigin,
          changeOrigin: true,
        },
      },
    },
  };
});

