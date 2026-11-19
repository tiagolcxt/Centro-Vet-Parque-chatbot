import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Em desenvolvimento, o Vite (porta 5173) repassa /app/api ao Spring (8080): mesmo domínio para o navegador, então o
// cookie de sessão e o CSRF funcionam sem CORS (ADR-0022). Em produção, o build é servido pelo próprio Spring.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/app/api": { target: "http://localhost:8080", changeOrigin: false },
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  test: {
    environment: "jsdom",
  },
});
