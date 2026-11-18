import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Two builds share every screen:
 *
 *   vite build                 the app for the Spring Boot server (sign-in, server-run timers)
 *   vite build --mode pages    the GitHub Pages site, with an in-browser backend instead
 *
 * The only difference is which file `@env` points at.
 * The dev server (port 4200) proxies the API and the OAuth 2.0 endpoints to Spring Boot on :5001, so
 * the browser sees one origin, exactly as in production.
 */

/**
 * The page's security policy, as a meta tag, because GitHub Pages can't send headers (the Spring Boot
 * server also sends it as a header). It's added to built pages only: Vite's dev server needs an
 * inline script for hot reloading, which this policy rightly blocks.
 */
const CONTENT_POLICY =
  "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
  "font-src 'self'; connect-src 'self'; media-src 'self' blob:; manifest-src 'self'; base-uri 'self'; form-action 'none'";

function contentPolicyMeta() {
  return {
    name: 'brew-focus:content-policy',
    apply: 'build',
    transformIndexHtml: () => [
      { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: CONTENT_POLICY }, injectTo: 'head-prepend' },
    ],
  };
}

/** Where the dev server sends API calls. API_URL overrides it, say to run both on other ports. */
const API = process.env.API_URL ?? 'http://127.0.0.1:5001';

export default defineConfig(({ mode }) => ({
  plugins: [react(), contentPolicyMeta()],
  resolve: {
    alias: {
      '@env': fileURLToPath(new URL(mode === 'pages' ? './src/env/pages.js' : './src/env/server.js', import.meta.url)),
    },
  },
  server: {
    port: 4200,
    strictPort: true,
    // changeOrigin stays false: the server must see the dev server's host, so OAuth callbacks
    // and redirects come back here, and the cross-site check sees a same-origin request.
    proxy: {
      '/api': { target: API, changeOrigin: false },
      '/oauth2': { target: API, changeOrigin: false },
      '/login/oauth2': { target: API, changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    // Fonts and images stay files: the policy allows the site's own files, not data: URLs for fonts.
    assetsInlineLimit: 0,
    rolldownOptions: {
      // "main-<hash>.js", so CI's live-site check and the tests can recognize the app's own bundle.
      output: { entryFileNames: 'assets/main-[hash].js' },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,jsx}'],
    restoreMocks: true,
  },
}));
