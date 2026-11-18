import '@fontsource/press-start-2p/latin-400.css';
import '@fontsource/outfit/latin-300.css';
import '@fontsource/outfit/latin-400.css';
import '@fontsource/outfit/latin-500.css';
import '@fontsource/outfit/latin-600.css';
import '@fontsource/outfit/latin-700.css';
import './styles/global.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App';

// On GitHub Pages the app lives under the repo's path (/BrewFocus/), which Vite passes in as BASE_URL.
// The trailing slash stays, so the home link is /BrewFocus/ (Pages would redirect /BrewFocus to it).
const basename = import.meta.env.BASE_URL;

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter basename={basename}>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
